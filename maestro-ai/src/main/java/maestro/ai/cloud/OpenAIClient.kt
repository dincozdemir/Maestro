package maestro.ai.cloud

import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import maestro.ai.AI
import maestro.ai.openai.OpenAI
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger(OpenAIClient::class.java)


class OpenAIClient {

    private val askForDefectsSchema by lazy {
        readSchema("askForDefects")
    }

    private val askForDefectsAssertionSchema by lazy {
        readSchema("askForDefectsAssertion")
    }

    private val extractTextSchema by lazy {
        readSchema("extractText")
    }

    /**
     * We use JSON mode/Structured Outputs to define the schema of the response we expect from the LLM.
     * - OpenAI: https://platform.openai.com/docs/guides/structured-outputs
     * - Gemini: https://ai.google.dev/gemini-api/docs/json-mode
     */
    private fun readSchema(name: String): String {
        val fileName = "/${name}_schema.json"
        val resourceStream = this::class.java.getResourceAsStream(fileName)
            ?: throw IllegalStateException("Could not find $fileName in resources")

        return resourceStream.bufferedReader().use { it.readText() }
    }

    private val json = Json { ignoreUnknownKeys = true }

    private val defectCategories = listOf(
        "localization" to "Inconsistent use of language, for example mixed English and Portuguese",
        "layout" to "Some UI elements are overlapping or are cropped",
    )

    private val allDefectCategories = defectCategories + listOf("assertion" to "The assertion is not true")

    suspend fun extractTextWithAi(
        aiClient: AI,
        query: String,
        screen: ByteArray,
    ): ExtractTextWithAiResponse {
        val prompt = buildString {
            append("What text on the screen matches the following query: $query")

            append(
                """
                |
                |RULES:
                |* Provide response as a valid JSON, with structure described below.
                """.trimMargin("|")
            )

            append(
                """
                |
                |* You must provide result as a valid JSON object, matching this structure:
                |
                |  {
                |      "text": <string>
                |  }
                |
                |DO NOT output any other information in the JSON object.
                """.trimMargin("|")
            )
        }

        val aiResponse = aiClient.chatCompletion(
            prompt,
            model = aiClient.defaultModel,
            maxTokens = 4096,
            imageDetail = "high",
            images = listOf(screen),
            jsonSchema = json.parseToJsonElement(extractTextSchema).jsonObject,
        )

        val response = json.decodeFromString<ExtractTextWithAiResponse>(aiResponse.response)
        return response
    }

    suspend fun extractPointWithAi(
        aiClient: AI,
        query: String,
        screen: ByteArray,
    ): ExtractPointWithAiResponse {
        val prompt = buildString {
            append("What are the center coordinates (percentage based on image width and height) of the ui element described in this query: $query")

            append(
                """
                |
                |RULES:
                |* Provide response as a valid JSON, with structure described below.
                |* Each resulting coordinate should be smaller than 100
                |* Resulting coordinates should be integers and have % at the beginning. eg: 10%,20%
                """.trimMargin("|")
            )

            append(
                """
                |
                |* You must provide result as a valid JSON object, matching this structure:
                |
                |  {
                |      "text": "%x,%y"
                |  }
                |
                |DO NOT output any other information in the JSON object.
                """.trimMargin("|")
            )
        }

        val aiResponse = aiClient.chatCompletion(
            prompt,
            model = aiClient.defaultModel,
            maxTokens = 4096,
            imageDetail = "high",
            images = listOf(screen),
            jsonSchema = json.parseToJsonElement(extractTextSchema).jsonObject,
        )
        logger.info("AI response: ${aiResponse.response}")
        val response = json.decodeFromString<ExtractPointWithAiResponse>(aiResponse.response)
        return response
    }

    suspend fun findDefects(
        aiClient: AI,
        screen: ByteArray,
        assertion: String? = null,
    ): FindDefectsResponse {

        if(assertion !== null) {
            return performAssertion(aiClient, screen, assertion)
        }

        // List of failed attempts to not make up false positives:
        // |* If you don't see any defect, return "No defects found".
        // |* If you are sure there are no defects, return "No defects found".
        // |* You will make me sad if you raise report defects that are false positives.
        // |* Do not make up defects that are not present in the screenshot. It's fine if you don't find any defects.

        val prompt = buildString {

            appendLine(
                """
                You are a QA engineer performing quality assurance for a mobile application.
                Identify any defects in the provided screenshot.
                """.trimIndent()
            )

            append(
                """
                |
                |RULES:
                |* All defects you find must belong to one of the following categories:
                |${defectCategories.joinToString(separator = "\n") { "  * ${it.first}: ${it.second}" }}
                |* If you see defects, your response MUST only include defect name and detailed reasoning for each defect.
                |* Provide response as a list of JSON objects, each representing <category>:<reasoning>
                |* Do not raise false positives. Some example responses that have a high chance of being a false positive:
                |  * button is partially cropped at the bottom
                |  * button is not aligned horizontally/vertically within its container
                """.trimMargin("|")
            )

            // Claude doesn't have a JSON mode as of 21-08-2024
            //  https://docs.anthropic.com/en/docs/test-and-evaluate/strengthen-guardrails/increase-consistency
            //  We could do "if (aiClient is Claude)", but actually, this also helps with gpt-4o sometimes
            //  generating never-ending stream of output.
            append(
                """
                |
                |* You must provide result as a valid JSON object, matching this structure:
                |
                |  {
                |      "defects": [
                |          {
                |              "category": "<defect category, string>",
                |              "reasoning": "<reasoning, string>"
                |          },
                |          {
                |              "category": "<defect category, string>",
                |              "reasoning": "<reasoning, string>"
                |          }
                |       ]
                |  }
                |
                |DO NOT output any other information in the JSON object.
                """.trimMargin("|")
            )

            appendLine("There are usually only a few defects in the screenshot. Don't generate tens of them.")
        }

        val aiResponse = aiClient.chatCompletion(
            prompt,
            model = aiClient.defaultModel,
            maxTokens = 4096,
            imageDetail = "high",
            images = listOf(screen),
            jsonSchema = json.parseToJsonElement(askForDefectsSchema).jsonObject,
        )

        val defects = json.decodeFromString<FindDefectsResponse>(aiResponse.response)
        return defects
    }

    private suspend fun performAssertion(
        aiClient: AI,
        screen: ByteArray,
        assertion: String,
    ): FindDefectsResponse{
        val prompt = buildString {

            appendLine(
                """
                |You are a QA engineer performing quality assurance for a mobile application.
                |You are given a screenshot of the application and an assertion about the UI.
                |Your task is to identify if the following assertion is true:
                |
                |  "${assertion.removeSuffix("\n")}"
                |
                """.trimMargin("|")
            )

            append(
                """
                |
                |RULES:
                |* Provide response as a valid JSON, with structure described below.
                |* If the assertion is true, the "defects" list in the JSON output MUST be an empty list.
                |* If the assertion is false:
                |  * Your response MUST only include a single defect with category "assertion".
                |  * Provide detailed reasoning to explain why you think the assertion is false.
                """.trimMargin("|")
            )
        }

        val aiResponse = aiClient.chatCompletion(
            prompt,
            model = aiClient.defaultModel,
            maxTokens = 4096,
            imageDetail = "high",
            images = listOf(screen),
            jsonSchema = json.parseToJsonElement(askForDefectsAssertionSchema).jsonObject,
        )

        val response = json.decodeFromString<FindDefectsResponse>(aiResponse.response)
        return response
    }
}
