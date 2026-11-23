package com.cayma.alertasos.network

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*

data class RegistroRequest(
    val nombre_completo: String,
    val telefono: String,
    val password: String,
    val distrito: String,
    val sector_referencia: String,
    val latitud: Double,
    val longitud: Double
)

data class LoginRequest(
    val telefono: String,
    val password: String
)

data class UsuarioApiResponse(
    val usuario_id: String,
    val nombre_completo: String,
    val telefono: String,
    val distrito: String,
    val sector_referencia: String,
    val latitud: Double,
    val longitud: Double,
    val nodo_guardado: String
)

data class AlertaRequest(
    val usuario_id: String,
    val nombre_usuario: String,
    val distrito: String,
    val sector_referencia: String,
    val latitud: Double,
    val longitud: Double,
    val tipo: String,
    val descripcion: String,
    val nivel_bateria: Int,
    val origen_offline_mesh: Boolean = false,
    val riesgo_sensor_verificado: Boolean = true
)

data class AlertaResponse(
    val alerta_id: String,
    val estado: String,
    val confianza_ia: Double,
    val nodo_guardado: String,
    val voluntarios_count: Int = 0
)

data class PuntoAlertaMapa(
    val alerta_id: String = "",
    val usuario_id: String = "",
    val nombre_usuario: String = "Vecino Arequipa",
    val distrito: String = "Arequipa",
    val sector_referencia: String = "",
    val lat: Double,
    val lng: Double,
    val intensidad: Double,
    val categoria: String,
    val descripcion: String = "",
    val estado: String = "ACTIVA",
    val origen_offline_mesh: Boolean = false,
    val voluntarios_count: Int = 0
)

data class ClimaInfo(
    val precipitacion_mm: Double = 0.0,
    val alerta_torrenteras: String = "BAJO",
    val riesgo_activo_detectado: Boolean = false
)

data class MapaCalorResponse(
    val zona: String,
    val clima_y_torrenteras: ClimaInfo?,
    val total_puntos: Int,
    val puntos: List<PuntoAlertaMapa>
)

data class ApoyoRequest(
    val voluntario_id: String,
    val nombre_voluntario: String,
    val latitud_voluntario: Double?,
    val longitud_voluntario: Double?
)

data class ApoyoResponse(
    val alerta_id: String,
    val voluntarios_count: Int
)

data class MensajeChatDto(
    val mensaje_id: String = "",
    val remitente_id: String,
    val nombre_remitente: String,
    val rol: String,
    val mensaje: String,
    val origen_mesh: Boolean = false,
    val timestamp: String = ""
)

data class ListaChatResponse(
    val alerta_id: String,
    val mensajes: List<MensajeChatDto>
)

interface SosApiService {
    @POST("/api/v1/alertas/auth/registro")
    suspend fun registrarUsuario(@Body req: RegistroRequest): UsuarioApiResponse

    @POST("/api/v1/alertas/auth/login")
    suspend fun iniciarSesion(@Body req: LoginRequest): UsuarioApiResponse

    @POST("/api/v1/alertas/sos")
    suspend fun enviarAlerta(@Body alerta: AlertaRequest): AlertaResponse

    @GET("/api/v1/alertas/mapa-calor")
    suspend fun obtenerMapaCalor(): MapaCalorResponse

    @POST("/api/v1/alertas/{alertaId}/apoyar")
    suspend fun confirmarAyudaEnCamino(
        @Path("alertaId") alertaId: String,
        @Body req: ApoyoRequest
    ): ApoyoResponse

    @GET("/api/v1/alertas/{alertaId}/chat")
    suspend fun obtenerChat(@Path("alertaId") alertaId: String): ListaChatResponse

    @POST("/api/v1/alertas/{alertaId}/chat")
    suspend fun enviarMensajeChat(
        @Path("alertaId") alertaId: String,
        @Body req: MensajeChatDto
    ): MensajeChatDto

    companion object {
        // 10.0.2.2 apunta a localhost de tu PC desde el emulador Android.
        // Si usas celular fisico por USB/Wi-Fi, cambia por la IP local de tu PC (ej. http://192.168.1.X:8000/)
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
