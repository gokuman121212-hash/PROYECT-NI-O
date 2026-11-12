package com.cayma.alertasos.network

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST

data class AlertaRequest(
    val usuario_id: String,
    val latitud: Double,
    val longitud: Double,
    val tipo: String,
    val descripcion: String,
    val nivel_bateria: Int,
    val origen_offline_mesh: Boolean = false
)

data class AlertaResponse(
    val alerta_id: String,
    val estado: String,
    val confianza_ia: Double,
    val nodo_guardado: String
)

interface SosApiService {
    @POST("/api/v1/alertas/sos")
    suspend fun enviarAlerta(@Body alerta: AlertaRequest): AlertaResponse

    companion object {
        // 10.0.2.2 apunta al localhost de tu PC desde el emulador de Android Studio
        private const val BASE_URL = "http://10.0.2.2:8000/"

        fun create(): SosApiService {
            return Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(SosApiService::class.java)
        }
    }
}