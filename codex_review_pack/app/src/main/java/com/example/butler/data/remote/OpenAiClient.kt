package com.example.butler.data.remote

import androidx.annotation.WorkerThread
import com.example.butler.data.remote.model.AiActionJsonSchema
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.UUID

enum class AiErrorCode {
    API_KEY_MISSING,
    CREDENTIAL_UNAVAILABLE,
    INVALID_REQUEST,
    AUTHENTICATION,
    RATE_LIMIT,
    SERVICE_UNAVAILABLE,
    NETWORK,
    INVALID_RESPONSE,
}

data class AiResponse(
    val replyMessage: String,
    val extractedActionJson: String? = null,
    val errorCode: AiErrorCode? = null,
) {
    val isSuccessful: Boolean get() = errorCode == null
}

internal data class HttpResult(val statusCode: Int, val body: String)

internal fun interface JsonHttpTransport {
    fun post(url: URL, headers: Map<String, String>, jsonBody: String): HttpResult
}

internal class UrlConnectionJsonTransport : JsonHttpTransport {
    override fun post(url: URL, headers: Map<String, String>, jsonBody: String): HttpResult {
        require(url.protocol == "https") { "HTTPS以外のAPI接続は禁止されています。" }
        val connection = url.openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "POST"
            connection.connectTimeout = CONNECT_TIMEOUT_MILLIS
            connection.readTimeout = READ_TIMEOUT_MILLIS
            connection.useCaches = false
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.setRequestProperty("Accept", "application/json")
            headers.forEach(connection::setRequestProperty)

            val requestBytes = jsonBody.toByteArray(StandardCharsets.UTF_8)
            try {
                connection.setFixedLengthStreamingMode(requestBytes.size)
                connection.outputStream.use { it.write(requestBytes) }
            } finally {
                requestBytes.fill(0)
            }

            val statusCode = connection.responseCode
            val stream = if (statusCode in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.use { it.readUtf8Limited(MAX_RESPONSE_BYTES) }.orEmpty()
            HttpResult(statusCode, body)
        } finally {
            connection.disconnect()
        }
    }

    private fun InputStream.readUtf8Limited(maxBytes: Int): String {
        val output = WipingByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        return try {
            var total = 0
            while (true) {
                val count = read(buffer)
                if (count < 0) break
                total += count
                if (total > maxBytes) {
                    throw IllegalStateException("API response exceeded size limit")
                }
                output.write(buffer, 0, count)
            }
            output.toUtf8AndWipe()
        } finally {
            buffer.fill(0)
            output.wipe()
        }
    }

    private class WipingByteArrayOutputStream : ByteArrayOutputStream() {
        fun toUtf8AndWipe(): String = try {
            String(buf, 0, count, StandardCharsets.UTF_8)
        } finally {
            wipe()
        }

        fun wipe() {
            buf.fill(0)
            reset()
        }
    }

    private companion object {
        const val CONNECT_TIMEOUT_MILLIS = 15_000
        const val READ_TIMEOUT_MILLIS = 60_000
        const val MAX_RESPONSE_BYTES = 2 * 1024 * 1024
    }
}

class OpenAiClient internal constructor(
    private val apiKeyProvider: () -> String,
    private val safetyIdentifierProvider: () -> String,
    private val transport: JsonHttpTransport,
    private val model: String,
    private val retrySleeper: (Long) -> Unit = Thread::sleep,
) {
    constructor(
        apiKeyProvider: () -> String,
        safetyIdentifierProvider: () -> String,
        model: String = DEFAULT_MODEL,
    ) : this(apiKeyProvider, safetyIdentifierProvider, UrlConnectionJsonTransport(), model)

    /** UIスレッドから直接呼ばず、コルーチンのIO dispatcher等で実行すること。 */
    @WorkerThread
    fun sendMessage(
        userMessage: String,
        persona: PersonaType = PersonaType.BUTLER,
        requireStructuredOutput: Boolean = false,
        operationId: String? = null,
    ): AiResponse {
        if (userMessage.isBlank() || userMessage.length > MAX_USER_MESSAGE_CHARS) {
            return failure("入力内容を確認してください。", AiErrorCode.INVALID_REQUEST)
        }
        val structuredOperationId = when {
            requireStructuredOutput && operationId == null ->
                return failure("操作IDが必要です。", AiErrorCode.INVALID_REQUEST)
            requireStructuredOutput &&
                operationId?.let(AiActionJsonSchema.OPERATION_ID::matches) != true ->
                return failure("操作IDの形式が不正です。", AiErrorCode.INVALID_REQUEST)
            !requireStructuredOutput && operationId != null ->
                return failure("構造化操作の指定が不正です。", AiErrorCode.INVALID_REQUEST)
            else -> operationId
        }

        val apiKey = try {
            apiKeyProvider().trim()
        } catch (_: Exception) {
            return failure(
                "APIキーを安全に読み込めませんでした。設定を確認してください。",
                AiErrorCode.CREDENTIAL_UNAVAILABLE,
            )
        }
        if (apiKey.isBlank()) {
            return failure(
                "APIキーが設定されていません。設定画面から登録してください。",
                AiErrorCode.API_KEY_MISSING,
            )
        }

        val safetyIdentifier = try {
            safetyIdentifierProvider().trim()
        } catch (_: Exception) {
            return failure("安全識別子を読み込めませんでした。", AiErrorCode.INVALID_REQUEST)
        }
        if (!SAFETY_IDENTIFIER.matches(safetyIdentifier)) {
            return failure("安全識別子の設定が不正です。", AiErrorCode.INVALID_REQUEST)
        }

        val requestJson = JSONObject().apply {
            put("model", model)
            put("instructions", PersonaPrompts.getSystemPrompt(persona))
            put("input", userMessage)
            put("store", false)
            put("safety_identifier", safetyIdentifier)
            if (structuredOperationId != null) {
                put(
                    "text",
                    JSONObject().put(
                        "format",
                        AiActionJsonSchema.createTodoTextFormat(structuredOperationId),
                    ),
                )
            }
        }

        val headers = mapOf(
            "Authorization" to "Bearer $apiKey",
            // 再試行時も同じ相関IDを維持し、同一要求として追跡可能にする。
            "X-Client-Request-Id" to UUID.randomUUID().toString(),
        )

        return try {
            val result = postWithBoundedRetry(headers, requestJson.toString())
            when (result.statusCode) {
                in 200..299 -> parseSuccess(result.body, structuredOperationId)
                401, 403 -> failure("API認証に失敗しました。設定を確認してください。", AiErrorCode.AUTHENTICATION)
                429 -> failure("APIの利用上限に達しました。しばらく待って再試行してください。", AiErrorCode.RATE_LIMIT)
                in 500..599 -> failure("AIサービスが一時的に利用できません。", AiErrorCode.SERVICE_UNAVAILABLE)
                else -> failure("AIサービスへの要求を処理できませんでした。", AiErrorCode.INVALID_REQUEST)
            }
        } catch (_: Exception) {
            // 例外本文、URL、ヘッダー、リクエスト本文は利用者向け応答やログへ出さない。
            failure("ネットワークエラーが発生しました。接続を確認してください。", AiErrorCode.NETWORK)
        }
    }

    private fun postWithBoundedRetry(
        headers: Map<String, String>,
        jsonBody: String,
    ): HttpResult {
        var attempt = 0
        while (true) {
            try {
                val result = transport.post(ENDPOINT, headers, jsonBody)
                if (result.statusCode !in 500..599 || attempt >= MAX_TRANSIENT_RETRIES) {
                    return result
                }
            } catch (error: Exception) {
                if (attempt >= MAX_TRANSIENT_RETRIES) throw error
            }
            attempt++
            retrySleeper(RETRY_DELAY_MILLIS)
        }
    }

    private fun parseSuccess(body: String, structuredOperationId: String?): AiResponse {
        return try {
            val response = JSONObject(body)
            val status = response.optString("status")
            if (status != "completed") {
                return failure("AIの応答が完了しませんでした。", AiErrorCode.INVALID_RESPONSE)
            }
            val output = response.optJSONArray("output")
                ?: return failure("AIの応答形式を確認できませんでした。", AiErrorCode.INVALID_RESPONSE)

            var hasRefusal = false
            val texts = buildList {
                for (outputIndex in 0 until output.length()) {
                    val item = output.optJSONObject(outputIndex) ?: continue
                    if (item.optString("type") != "message") continue
                    val content = item.optJSONArray("content") ?: continue
                    for (contentIndex in 0 until content.length()) {
                        val part = content.optJSONObject(contentIndex) ?: continue
                        when (part.optString("type")) {
                            "refusal" -> hasRefusal = true
                            "output_text" ->
                                part.optString("text").takeIf(String::isNotBlank)?.let(::add)
                        }
                    }
                }
            }

            if (hasRefusal) {
                failure("AIが操作候補の生成を拒否しました。", AiErrorCode.INVALID_RESPONSE)
            } else if (texts.isEmpty()) {
                failure("AIから回答本文を取得できませんでした。", AiErrorCode.INVALID_RESPONSE)
            } else if (structuredOperationId != null) {
                if (texts.size != 1) {
                    return failure("AIの構造化応答が不正です。", AiErrorCode.INVALID_RESPONSE)
                }
                val actionJson = texts.single().trim()
                val returnedOperationId = JSONObject(actionJson).optString("operationId")
                if (returnedOperationId != structuredOperationId) {
                    return failure("AIの操作IDが一致しません。", AiErrorCode.INVALID_RESPONSE)
                }
                AiResponse(
                    replyMessage = "AIから操作候補を受信しました。",
                    extractedActionJson = actionJson,
                )
            } else {
                AiResponse(replyMessage = texts.joinToString("\n").trim())
            }
        } catch (_: Exception) {
            failure("AIの応答形式を確認できませんでした。", AiErrorCode.INVALID_RESPONSE)
        }
    }

    private fun failure(message: String, code: AiErrorCode) = AiResponse(
        replyMessage = message,
        errorCode = code,
    )

    private companion object {
        val ENDPOINT = URL("https://api.openai.com/v1/responses")
        val SAFETY_IDENTIFIER = Regex("^[A-Za-z0-9_-]{8,64}$")
        const val DEFAULT_MODEL = "gpt-4o-mini"
        const val MAX_USER_MESSAGE_CHARS = 100_000
        const val MAX_TRANSIENT_RETRIES = 1
        const val RETRY_DELAY_MILLIS = 250L
    }
}
