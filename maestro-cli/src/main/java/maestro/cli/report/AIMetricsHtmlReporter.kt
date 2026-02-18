package maestro.cli.report

import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.io.File

object AIMetricsHtmlReporter {

    private val logger = LoggerFactory.getLogger(AIMetricsHtmlReporter::class.java)
    private val json = Json { ignoreUnknownKeys = true }

    fun generateReport(): File? {
        val metricsDir = File(System.getProperty("user.home"), ".maestro/ai-metrics")
        if (!metricsDir.exists()) return null

        val jsonFiles = metricsDir.listFiles { f -> f.extension == "json" } ?: return null
        if (jsonFiles.isEmpty()) return null

        val runs = jsonFiles.mapNotNull { file ->
            try {
                json.decodeFromString(AIMetricsRun.serializer(), file.readText())
            } catch (e: Exception) {
                logger.warn("Failed to parse metrics file: ${file.name}", e)
                null
            }
        }.sortedBy { it.timestamp }

        if (runs.isEmpty()) return null

        val runsJson = Json { prettyPrint = false }.encodeToString(
            kotlinx.serialization.builtins.ListSerializer(AIMetricsRun.serializer()),
            runs,
        )

        val html = buildHtml(runsJson, runs)
        val outputFile = File(metricsDir, "ai-agents-performance-report.html")
        outputFile.writeText(html)
        logger.info("AI metrics HTML report: ${outputFile.absolutePath}")
        return outputFile
    }

    private fun buildHtml(runsJson: String, runs: List<AIMetricsRun>): String {
        // Aggregate stats per model
        data class ModelStats(
            var totalCalls: Int = 0,
            var successfulCalls: Int = 0,
            var totalLatencyMs: Long = 0,
            var runCount: Int = 0,
        )

        val modelStatsMap = mutableMapOf<String, ModelStats>()
        for (run in runs) {
            val stats = modelStatsMap.getOrPut(run.model) { ModelStats() }
            stats.totalCalls += run.summary.totalCalls
            stats.successfulCalls += run.summary.successfulCalls
            stats.totalLatencyMs += run.summary.totalLatencyMs
            stats.runCount++
        }

        val summaryCardsHtml = buildString {
            append("<div class=\"summary-cards\">")
            for ((model, stats) in modelStatsMap) {
                val avgLatency = if (stats.totalCalls > 0) stats.totalLatencyMs / stats.totalCalls else 0
                val successRate = if (stats.totalCalls > 0) (stats.successfulCalls * 100.0 / stats.totalCalls) else 0.0
                append("""
                    <div class="card">
                        <h3>$model</h3>
                        <div class="stat"><span class="label">Runs:</span> ${stats.runCount}</div>
                        <div class="stat"><span class="label">Total Calls:</span> ${stats.totalCalls}</div>
                        <div class="stat"><span class="label">Success Rate:</span> ${"%.1f".format(successRate)}%</div>
                        <div class="stat"><span class="label">Avg Latency:</span> ${avgLatency}ms</div>
                    </div>
                """)
            }
            append("</div>")
        }

        val runsTableHtml = buildString {
            append("""
                <table>
                    <thead>
                        <tr>
                            <th>Timestamp</th>
                            <th>Model</th>
                            <th>Provider</th>
                            <th>Total Calls</th>
                            <th>Success Rate</th>
                            <th>Avg Latency (ms)</th>
                            <th>Total Latency (ms)</th>
                        </tr>
                    </thead>
                    <tbody>
            """)
            for (run in runs.reversed()) {
                val successRate = if (run.summary.totalCalls > 0)
                    "%.1f".format(run.summary.successfulCalls * 100.0 / run.summary.totalCalls)
                else "N/A"
                append("""
                    <tr>
                        <td>${run.timestamp}</td>
                        <td>${run.model}</td>
                        <td>${run.provider}</td>
                        <td>${run.summary.totalCalls}</td>
                        <td>${successRate}%</td>
                        <td>${run.summary.avgLatencyMs}</td>
                        <td>${run.summary.totalLatencyMs}</td>
                    </tr>
                """)
            }
            append("</tbody></table>")
        }

        return """
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>AI Agents Performance Report</title>
    <script src="https://cdn.jsdelivr.net/npm/chart.js@4"></script>
    <style>
        * { margin: 0; padding: 0; box-sizing: border-box; }
        body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; background: #f5f5f5; color: #333; padding: 20px; }
        h1 { text-align: center; margin-bottom: 10px; color: #1a1a1a; }
        .subtitle { text-align: center; color: #666; margin-bottom: 30px; }
        .summary-cards { display: flex; gap: 20px; justify-content: center; flex-wrap: wrap; margin-bottom: 30px; }
        .card { background: white; border-radius: 8px; padding: 20px; min-width: 200px; box-shadow: 0 2px 4px rgba(0,0,0,0.1); }
        .card h3 { margin-bottom: 10px; color: #2563eb; font-size: 14px; word-break: break-all; }
        .stat { margin: 5px 0; font-size: 14px; }
        .stat .label { font-weight: 600; }
        .charts { display: grid; grid-template-columns: 1fr 1fr; gap: 20px; margin-bottom: 30px; }
        .chart-container { background: white; border-radius: 8px; padding: 20px; box-shadow: 0 2px 4px rgba(0,0,0,0.1); }
        .chart-container h3 { margin-bottom: 15px; font-size: 16px; }
        canvas { max-height: 300px; }
        table { width: 100%; border-collapse: collapse; background: white; border-radius: 8px; overflow: hidden; box-shadow: 0 2px 4px rgba(0,0,0,0.1); }
        th, td { padding: 10px 15px; text-align: left; border-bottom: 1px solid #eee; font-size: 13px; }
        th { background: #f8f9fa; font-weight: 600; }
        tr:hover { background: #f8f9fa; }
        h2 { margin: 20px 0 15px; }
        @media (max-width: 768px) { .charts { grid-template-columns: 1fr; } }
    </style>
</head>
<body>
    <h1>AI Agents Performance Report</h1>
    <p class="subtitle">Auto-generated comparison of AI providers across test runs</p>

    $summaryCardsHtml

    <div class="charts">
        <div class="chart-container">
            <h3>Average Latency by Model</h3>
            <canvas id="latencyChart"></canvas>
        </div>
        <div class="chart-container">
            <h3>Success Rate by Model</h3>
            <canvas id="successChart"></canvas>
        </div>
        <div class="chart-container">
            <h3>Latency Over Time</h3>
            <canvas id="latencyTimeChart"></canvas>
        </div>
        <div class="chart-container">
            <h3>Success Rate Over Time</h3>
            <canvas id="successTimeChart"></canvas>
        </div>
    </div>

    <h2>Run History</h2>
    $runsTableHtml

    <script>
        const runs = $runsJson;

        // Aggregate by model
        const modelData = {};
        runs.forEach(run => {
            if (!modelData[run.model]) {
                modelData[run.model] = { totalCalls: 0, successCalls: 0, totalLatency: 0 };
            }
            modelData[run.model].totalCalls += run.summary.totalCalls;
            modelData[run.model].successCalls += run.summary.successfulCalls;
            modelData[run.model].totalLatency += run.summary.totalLatencyMs;
        });

        const models = Object.keys(modelData);
        const colors = ['#2563eb', '#dc2626', '#16a34a', '#ea580c', '#7c3aed', '#0891b2'];

        // Latency pie chart
        new Chart(document.getElementById('latencyChart'), {
            type: 'bar',
            data: {
                labels: models,
                datasets: [{
                    label: 'Avg Latency (ms)',
                    data: models.map(m => modelData[m].totalCalls > 0 ? Math.round(modelData[m].totalLatency / modelData[m].totalCalls) : 0),
                    backgroundColor: colors.slice(0, models.length),
                }]
            },
            options: { responsive: true, plugins: { legend: { display: false } } }
        });

        // Success rate pie chart
        new Chart(document.getElementById('successChart'), {
            type: 'pie',
            data: {
                labels: models,
                datasets: [{
                    data: models.map(m => modelData[m].totalCalls > 0 ? parseFloat((modelData[m].successCalls * 100 / modelData[m].totalCalls).toFixed(1)) : 0),
                    backgroundColor: colors.slice(0, models.length),
                }]
            },
            options: { responsive: true }
        });

        // Latency over time line chart
        const modelTimeSeries = {};
        runs.forEach(run => {
            if (!modelTimeSeries[run.model]) modelTimeSeries[run.model] = [];
            modelTimeSeries[run.model].push({ x: run.timestamp, y: run.summary.avgLatencyMs });
        });

        new Chart(document.getElementById('latencyTimeChart'), {
            type: 'line',
            data: {
                datasets: Object.keys(modelTimeSeries).map((model, i) => ({
                    label: model,
                    data: modelTimeSeries[model],
                    borderColor: colors[i % colors.length],
                    fill: false,
                    tension: 0.1,
                }))
            },
            options: {
                responsive: true,
                scales: { x: { type: 'category', labels: [...new Set(runs.map(r => r.timestamp))] }, y: { title: { display: true, text: 'ms' } } }
            }
        });

        // Success rate over time line chart
        const modelSuccessTimeSeries = {};
        runs.forEach(run => {
            if (!modelSuccessTimeSeries[run.model]) modelSuccessTimeSeries[run.model] = [];
            const rate = run.summary.totalCalls > 0 ? parseFloat((run.summary.successfulCalls * 100 / run.summary.totalCalls).toFixed(1)) : 0;
            modelSuccessTimeSeries[run.model].push({ x: run.timestamp, y: rate });
        });

        new Chart(document.getElementById('successTimeChart'), {
            type: 'line',
            data: {
                datasets: Object.keys(modelSuccessTimeSeries).map((model, i) => ({
                    label: model,
                    data: modelSuccessTimeSeries[model],
                    borderColor: colors[i % colors.length],
                    fill: false,
                    tension: 0.1,
                }))
            },
            options: {
                responsive: true,
                scales: { x: { type: 'category', labels: [...new Set(runs.map(r => r.timestamp))] }, y: { title: { display: true, text: '%' }, min: 0, max: 100 } }
            }
        });
    </script>
</body>
</html>
        """.trimIndent()
    }
}
