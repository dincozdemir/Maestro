package maestro.cli.report

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import maestro.orchestra.Orchestra
import org.slf4j.LoggerFactory
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Serializable
data class AICallMetricJson(
    val model: String,
    val durationMs: Long,
    val success: Boolean,
)

@Serializable
data class FlowAIMetrics(
    val flowName: String,
    val aiCalls: List<AICallMetricJson>,
)

@Serializable
data class AIMetricsSummary(
    val totalCalls: Int,
    val successfulCalls: Int,
    val failedCalls: Int,
    val avgLatencyMs: Long,
    val totalLatencyMs: Long,
)

@Serializable
data class AIMetricsRun(
    val timestamp: String,
    val model: String,
    val provider: String,
    val flows: List<FlowAIMetrics>,
    val summary: AIMetricsSummary,
)

object AIMetricsReporter {

    private val logger = LoggerFactory.getLogger(AIMetricsReporter::class.java)
    private val json = Json { prettyPrint = true }

    fun writeMetrics(
        flowMetrics: Map<String, List<Orchestra.AICallMetric>>,
    ): File? {
        if (flowMetrics.values.all { it.isEmpty() }) {
            logger.info("No AI metrics to write")
            return null
        }

        val metricsDir = File(System.getProperty("user.home"), ".maestro/ai-metrics")
        metricsDir.mkdirs()

        val allCalls = flowMetrics.values.flatten()
        val model = allCalls.firstOrNull()?.model ?: "unknown"
        val provider = when {
            model.startsWith("gpt-") -> "openai"
            model.startsWith("claude-") -> "anthropic"
            else -> "unknown"
        }

        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"))
        val fileTimestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))

        val flows = flowMetrics.map { (flowName, metrics) ->
            FlowAIMetrics(
                flowName = flowName,
                aiCalls = metrics.map { AICallMetricJson(model = it.model, durationMs = it.durationMs, success = it.success) },
            )
        }

        val totalCalls = allCalls.size
        val successfulCalls = allCalls.count { it.success }
        val failedCalls = totalCalls - successfulCalls
        val totalLatencyMs = allCalls.sumOf { it.durationMs }
        val avgLatencyMs = if (totalCalls > 0) totalLatencyMs / totalCalls else 0

        val run = AIMetricsRun(
            timestamp = timestamp,
            model = model,
            provider = provider,
            flows = flows,
            summary = AIMetricsSummary(
                totalCalls = totalCalls,
                successfulCalls = successfulCalls,
                failedCalls = failedCalls,
                avgLatencyMs = avgLatencyMs,
                totalLatencyMs = totalLatencyMs,
            ),
        )

        val fileName = "${fileTimestamp}_${model}.json"
        val outputFile = File(metricsDir, fileName)
        outputFile.writeText(json.encodeToString(run))
        logger.info("AI metrics written to: ${outputFile.absolutePath}")

        return outputFile
    }
}
