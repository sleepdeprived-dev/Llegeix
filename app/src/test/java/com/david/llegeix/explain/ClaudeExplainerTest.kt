package com.david.llegeix.explain

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the request shape and the response handling.
 *
 * A live call cannot be part of the test suite — it would bill a real account
 * on every run — so the two halves that can be got wrong silently are checked
 * here instead: that the body matches the documented API, and that a refusal or
 * an error payload is recognised rather than being read as an answer.
 */
class ClaudeExplainerTest {

    private val explainer = ClaudeExplainer()

    @Test
    fun `the request body matches the documented shape`() {
        val body = explainer.buildBody("Explain \"serp\".")

        assertEquals("claude-opus-5", body.getString("model"))
        // Server-side fallback: a policy decline is re-run rather than returned.
        assertEquals("default", body.getString("fallbacks"))
        assertEquals("low", body.getJSONObject("output_config").getString("effort"))
        assertTrue(body.getInt("max_tokens") > 0)
        assertTrue(body.getString("system").isNotBlank())

        val messages = body.getJSONArray("messages")
        assertEquals(1, messages.length())
        assertEquals("user", messages.getJSONObject(0).getString("role"))
        assertTrue(messages.getJSONObject(0).getString("content").contains("serp"))
    }

    @Test
    fun `budget_tokens is never sent - it is rejected on this model`() {
        val body = explainer.buildBody("anything")
        assertTrue(body.optJSONObject("thinking") == null)
        assertTrue(!body.toString().contains("budget_tokens"))
    }

    @Test
    fun `the prompt carries the sentence and the document`() {
        val prompt = explainer.buildPrompt("serp", "Representava una serp boa.", "El Petit Princep")
        assertTrue(prompt.contains("serp"))
        assertTrue(prompt.contains("Representava una serp boa."))
        assertTrue(prompt.contains("El Petit Princep"))
        assertTrue(prompt.contains("word"))
    }

    @Test
    fun `a multi word selection is described as a phrase`() {
        assertTrue(explainer.buildPrompt("una serp boa", "", "").contains("phrase"))
    }

    @Test
    fun `text blocks are concatenated`() {
        val payload = JSONObject(
            """
            {"stop_reason":"end_turn","content":[
              {"type":"text","text":"Serp is a noun. "},
              {"type":"thinking","thinking":""},
              {"type":"text","text":"It means snake."}
            ]}
            """.trimIndent(),
        ).toString()

        val result = explainer.parse(payload)
        assertTrue(result is Explanation.Ready)
        assertEquals("Serp is a noun. It means snake.", (result as Explanation.Ready).text)
    }

    @Test
    fun `a refusal is not read as an answer`() {
        // A declined request is a successful HTTP 200 whose content can be empty:
        // reading content[0] without checking stop_reason would crash or lie.
        val payload = """{"stop_reason":"refusal","content":[]}"""
        assertEquals(Explanation.Failed("refusal"), explainer.parse(payload))
    }

    @Test
    fun `an empty response is reported rather than shown as a blank explanation`() {
        assertEquals(
            Explanation.Failed("empty"),
            explainer.parse("""{"stop_reason":"end_turn","content":[]}"""),
        )
    }

    @Test
    fun `the API's own error message is preferred over the status code`() {
        val payload = """{"type":"error","error":{"type":"invalid_request_error","message":"max_tokens too large"}}"""
        assertEquals("max_tokens too large", explainer.errorMessage(400, payload))
    }

    @Test
    fun `a bare 401 maps to the unauthorized case`() {
        assertEquals("unauthorized", explainer.errorMessage(401, ""))
    }
}
