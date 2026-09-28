package com.example.butler.data.remote

import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

data class AiResponse(
    val replyMessage: String,
    val extractedActionJson: String? = null
)

class OpenAiClient(private val apiKeyProvider: () -> String) {

    private val endpointUrl = "https://api.openai.com/v1/chat/completions"

    fun sendMessage(
        userMessage: String,
        persona: PersonaType = PersonaType.BUTLER,
        requireStructuredOutput: Boolean = false,
        operationId: String? = null
    ): AiResponse {
        val apiKey = apiKeyProvider()
        if (apiKey.isBlank()) {
            return AiResponse("APIキーが設定されていません。設定画面から登録してください。")
        }

        val url = URL(endpointUrl)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("Authorization", "Bearer $apiKey")
        conn.connectTimeout = 15000
        conn.readTimeout = 60000
        conn.doOutput = true

        val systemPrompt = PersonaPrompts.getSystemPrompt(persona)

        val requestJson = JSONObject().apply {
            put("model", "gpt-4o-mini")
            put("store", false) // 個人情報保護のためAPI学習/保存を拒否
            
            // 匿名化識別子の付与 (個人情報は含めない)
            put("safety_identifier", "anon_user_session")

            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", systemPrompt)
                })
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", userMessage)
                })
            })

            if (requireStructuredOutput && operationId != null) {
                put("response_format", JSONObject().apply {
                    put("type", "json_object")
                })
            }

            put("temperature", 0.3)
        }

        return try {
            OutputStreamWriter(conn.outputStream).use { writer ->
                writer.write(requestJson.toString())
                writer.flush()
            }

            val responseCode = conn.responseCode
            if (responseCode == 200) {
                val responseText = conn.inputStream.bufferedReader().use { it.readText() }
                val jsonResponse = JSONObject(responseText)
                val choices = jsonResponse.getJSONArray("choices")
                val content = choices.getJSONObject(0).getJSONObject("message").getString("content")

                if (requireStructuredOutput) {
                    AiResponse(replyMessage = "AIからの構造化操作を受信しました。", extractedActionJson = content)
                } else {
                    AiResponse(replyMessage = content)
                }
            } else {
                AiResponse(replyMessage = "通信エラーが発生しました。(HTTP $responseCode)")
            }
        } catch (e: Exception) {
            // APIキーや会話本文をエラー文へ露出させない
            AiResponse(replyMessage = "ネットワーク通信エラーが発生しました。")
        } finally {
            conn.disconnect()
        }
    }
}
