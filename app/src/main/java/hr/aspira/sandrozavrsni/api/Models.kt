package hr.aspira.sandrozavrsni.api

data class SensorNow(
    val timestamp: Long,
    val temp_c: Double?,
    val hum_pct: Double?,
    val cpu_temp_c: Double?
)

data class HistoryPoint(
    val ts: Long,
    val temp_c: Double?,
    val hum_pct: Double?
)

data class HistoryResponse(
    val from: Long,
    val to: Long,
    val count: Int,
    val data: List<HistoryPoint>
)
