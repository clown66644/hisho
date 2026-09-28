package com.example.butler.data.remote

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URL

class OpenAiClientTest {
    private class RecordingTransport(
        private val result: HttpResult = HttpResult(200, successResponse("承知しました。")),
        private val error: Exception? = null,
    ) : JsonHttpTransport {
        var calls = 0
        var lastUrl: URL? = null
        var lastHeaders: Map<String, String>? = null
        var lastBody: String? = null

        override fun post(url: URL, headers: Map<String, String>, jsonBody: String): HttpResult {
            calls++
            lastUrl = url
            lastHeaders = headers
            lastBody = jsonBody
            error?.let { throw it }
            return result
        }
    }

    private class SequencedTransport(
        private val outcomes: ArrayDeque<Any>,
    ) : JsonHttpTransport {
        val requestIds = mutableListOf<String>()
        var calls = 0

        override fun post(url: URL, headers: Map<String, String>, jsonBody: String): HttpResult {
            calls++
            requestIds += headers.getValue("X-Client-Request-Id")
            return when (val outcome = outcomes.removeFirst()) {
                is HttpResult -> outcome
                is Exception -> throw outcome
                else -> error("unsupported test outcome")
            }
        }
    }

    private fun client(
        transport: JsonHttpTransport,
        retrySleeper: (Long) -> Unit = {},
    ) = OpenAiClient(
        apiKeyProvider = { "unit-test-credential" },
        safetyIdentifierProvider = { "user_hash_123456" },
        transport = transport,
        model = "gpt-4o-mini",
        retrySleeper = retrySleeper,
    )

    @Test
    fun successfulResponseIsParsedAndPrivacyFlagsAreSent() {
        val transport = RecordingTransport()

        val response = client(transport).sendMessage("明日の予定を整理して")

        assertTrue(response.isSuccessful)
        assertEquals("承知しました。", response.replyMessage)
        assertNull(response.errorCode)
        assertEquals("https://api.openai.com/v1/responses", transport.lastUrl.toString())

        val body = JSONObject(transport.lastBody!!)
        assertEquals(false, body.getBoolean("store"))
        assertEquals("user_hash_123456", body.getString("safety_identifier"))
        assertEquals("明日の予定を整理して", body.getString("input"))
        assertFalse(transport.lastBody!!.contains("unit-test-credential"))
    }

    @Test
    fun errorResponseBodyAndApiKeyAreNotExposed() {
        val secret = "unit-test-credential"
        val transport = RecordingTransport(
            result = HttpResult(401, "server leaked $secret and conversation"),
        )

        val response = client(transport).sendMessage("秘密の会話")

        assertEquals(AiErrorCode.AUTHENTICATION, response.errorCode)
        assertFalse(response.replyMessage.contains(secret))
        assertFalse(response.replyMessage.contains("秘密の会話"))
        assertFalse(response.replyMessage.contains("server leaked"))
    }

    @Test
    fun exceptionDetailsAreNotExposed() {
        val transport = RecordingTransport(error = IllegalStateException("secret internal detail"))

        val response = client(transport).sendMessage("入力本文")

        assertEquals(AiErrorCode.NETWORK, response.errorCode)
        assertFalse(response.replyMessage.contains("secret internal detail"))
        assertFalse(response.replyMessage.contains("入力本文"))
    }

    @Test
    fun invalidSafetyIdentifierStopsBeforeNetwork() {
        val transport = RecordingTransport()
        val client = OpenAiClient(
            apiKeyProvider = { "test-key" },
            safetyIdentifierProvider = { "raw email@example.com" },
            transport = transport,
            model = "gpt-4o-mini",
        )

        val response = client.sendMessage("こんにちは")

        assertEquals(AiErrorCode.INVALID_REQUEST, response.errorCode)
        assertEquals(0, transport.calls)
    }

    @Test
    fun credentialProviderFailureIsConvertedToSafeError() {
        val transport = RecordingTransport()
        val client = OpenAiClient(
            apiKeyProvider = { error("sensitive keystore detail") },
            safetyIdentifierProvider = { "user_hash_123456" },
            transport = transport,
            model = "gpt-4o-mini",
        )

        val response = client.sendMessage("入力本文")

        assertEquals(AiErrorCode.CREDENTIAL_UNAVAILABLE, response.errorCode)
        assertFalse(response.replyMessage.contains("sensitive keystore detail"))
        assertFalse(response.replyMessage.contains("入力本文"))
        assertEquals(0, transport.calls)
    }

    @Test
    fun safetyIdentifierProviderFailureStopsBeforeNetwork() {
        val transport = RecordingTransport()
        val client = OpenAiClient(
            apiKeyProvider = { "unit-test-credential" },
            safetyIdentifierProvider = { error("private identifier detail") },
            transport = transport,
            model = "gpt-4o-mini",
        )

        val response = client.sendMessage("こんにちは")

        assertEquals(AiErrorCode.INVALID_REQUEST, response.errorCode)
        assertFalse(response.replyMessage.contains("private identifier detail"))
        assertEquals(0, transport.calls)
    }

    @Test
    fun transientNetworkFailureRetriesOnceWithSameRequestId() {
        val transport = SequencedTransport(
            ArrayDeque(
                listOf(
                    IllegalStateException("temporary network failure"),
                    HttpResult(200, successResponse("再試行成功")),
                )
            )
        )
        val delays = mutableListOf<Long>()

        val response = client(transport, delays::add).sendMessage("再試行してください")

        assertTrue(response.isSuccessful)
        assertEquals("再試行成功", response.replyMessage)
        assertEquals(2, transport.calls)
        assertEquals(1, transport.requestIds.distinct().size)
        assertEquals(listOf(250L), delays)
    }

    @Test
    fun serviceFailureRetriesOnlyOnce() {
        val transport = SequencedTransport(
            ArrayDeque(
                listOf(
                    HttpResult(503, "temporary"),
                    HttpResult(503, "still unavailable"),
                )
            )
        )

        val response = client(transport).sendMessage("サービス確認")

        assertEquals(AiErrorCode.SERVICE_UNAVAILABLE, response.errorCode)
        assertEquals(2, transport.calls)
        assertEquals(1, transport.requestIds.distinct().size)
    }

    @Test
    fun authenticationAndRateLimitFailuresAreNotRetried() {
        for ((status, expected) in listOf(
            401 to AiErrorCode.AUTHENTICATION,
            429 to AiErrorCode.RATE_LIMIT,
        )) {
            val transport = SequencedTransport(
                ArrayDeque(listOf(HttpResult(status, "do not retry")))
            )

            val response = client(transport).sendMessage("再試行禁止")

            assertEquals(expected, response.errorCode)
            assertEquals(1, transport.calls)
        }
    }

    @Test
    fun structuredOutputRequiresValidOperationIdBeforeNetwork() {
        val transport = RecordingTransport()

        val missing = client(transport).sendMessage(
            userMessage = "ToDoを作成",
            requireStructuredOutput = true,
        )
        val invalid = client(transport).sendMessage(
            userMessage = "ToDoを作成",
            requireStructuredOutput = true,
            operationId = "bad id",
        )

        assertEquals(AiErrorCode.INVALID_REQUEST, missing.errorCode)
        assertEquals(AiErrorCode.INVALID_REQUEST, invalid.errorCode)
        assertEquals(0, transport.calls)
    }

    @Test
    fun structuredOutputUsesStrictResponsesApiSchemaAndExtractsAction() {
        val operationId = "op_structured_123"
        val actionJson = """
            {
              "operationId":"$operationId",
              "actionType":"CREATE_TODO",
              "payload":{
                "title":"書類提出",
                "detail":null,
                "dueDate":null,
                "estimatedMinutes":30,
                "isHealthOrSafety":false,
                "financialImpact":2,
                "workImpact":4,
                "mentalLoad":2
              },
              "rationale":"期限が近いため"
            }
        """.trimIndent()
        val transport = RecordingTransport(
            result = HttpResult(200, successResponse(actionJson)),
        )

        val response = client(transport).sendMessage(
            userMessage = "書類提出をToDoにして",
            requireStructuredOutput = true,
            operationId = operationId,
        )

        assertTrue(response.isSuccessful)
        assertEquals(JSONObject(actionJson).toString(), JSONObject(response.extractedActionJson!!).toString())
        val format = JSONObject(transport.lastBody!!)
            .getJSONObject("text")
            .getJSONObject("format")
        assertEquals("json_schema", format.getString("type"))
        assertTrue(format.getBoolean("strict"))
        val schema = format.getJSONObject("schema")
        assertEquals(false, schema.getBoolean("additionalProperties"))
        assertEquals(
            operationId,
            schema.getJSONObject("properties")
                .getJSONObject("operationId")
                .getJSONArray("enum")
                .getString(0),
        )
    }

    @Test
    fun mismatchedStructuredOperationIdIsRejected() {
        val transport = RecordingTransport(
            result = HttpResult(
                200,
                successResponse(
                    """{"operationId":"different_id","actionType":"CREATE_TODO"}"""
                ),
            ),
        )

        val response = client(transport).sendMessage(
            userMessage = "ToDoを作成",
            requireStructuredOutput = true,
            operationId = "expected_operation",
        )

        assertEquals(AiErrorCode.INVALID_RESPONSE, response.errorCode)
        assertNull(response.extractedActionJson)
    }

    @Test
    fun incompleteStructuredResponseIsRejectedEvenWithOutputText() {
        val operationId = "op_incomplete_01"
        val actionJson = """{"operationId":"$operationId","actionType":"CREATE_TODO"}"""
        val transport = RecordingTransport(
            result = HttpResult(200, successResponse(actionJson, status = "incomplete")),
        )

        val response = client(transport).sendMessage(
            userMessage = "ToDoを作成",
            requireStructuredOutput = true,
            operationId = operationId,
        )

        assertEquals(AiErrorCode.INVALID_RESPONSE, response.errorCode)
        assertNull(response.extractedActionJson)
    }

    @Test
    fun responseWithoutStatusIsRejectedInNormalMode() {
        val transport = RecordingTransport(
            result = HttpResult(
                200,
                """
                    {
                      "output": [{
                        "type": "message",
                        "content": [{"type": "output_text", "text": "partial"}]
                      }]
                    }
                """.trimIndent(),
            ),
        )

        val response = client(transport).sendMessage("通常対話")

        assertEquals(AiErrorCode.INVALID_RESPONSE, response.errorCode)
    }

    @Test
    fun refusalResponseIsRejectedWithoutExposingRefusalText() {
        val refusalText = "sensitive refusal detail"
        val transport = RecordingTransport(
            result = HttpResult(
                200,
                """
                    {
                      "status": "completed",
                      "output": [{
                        "type": "message",
                        "content": [{"type": "refusal", "refusal": "$refusalText"}]
                      }]
                    }
                """.trimIndent(),
            ),
        )

        val response = client(transport).sendMessage("ToDoを作成")

        assertEquals(AiErrorCode.INVALID_RESPONSE, response.errorCode)
        assertFalse(response.replyMessage.contains(refusalText))
    }

    companion object {
        private fun successResponse(text: String, status: String = "completed"): String = """
            {
              "status": "$status",
              "output": [
                {
                  "type": "message",
                  "content": [
                    {"type": "output_text", "text": ${JSONObject.quote(text)}}
                  ]
                }
              ]
            }
        """.trimIndent()
    }
}
