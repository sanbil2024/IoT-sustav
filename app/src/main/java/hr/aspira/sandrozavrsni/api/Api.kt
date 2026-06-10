package hr.aspira.sandrozavrsni.api

import retrofit2.http.GET
import retrofit2.http.Query

interface Api {
    @GET("/api/health")
    suspend fun health(): Map<String, Any?>

    @GET("/api/sensors")
    suspend fun current(): SensorNow

    @GET("/api/history")
    suspend fun history(
        @Query("minutes") minutes: Int? = null,
        @Query("since") since: Long? = null,
        @Query("until") until: Long? = null,
        @Query("limit") limit: Int? = null
    ): HistoryResponse
}
