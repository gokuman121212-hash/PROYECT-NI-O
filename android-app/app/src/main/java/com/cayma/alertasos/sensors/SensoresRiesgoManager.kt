package com.cayma.alertasos.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.sqrt

data class EstadoRiesgoSensor(
    val riesgoRealDetectado: Boolean = false,
    val tipoRiesgoDetectado: String = "NINGUNO",
    val detalleSensor: String = "Monitoreando sensores sismicos y climaticos en Arequipa...",
    val magnitudAceleracion: Float = 0f
)

class SensoresRiesgoManager(context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val acelerometro: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private val _estadoRiesgo = MutableStateFlow(EstadoRiesgoSensor())
    val estadoRiesgo: StateFlow<EstadoRiesgoSensor> = _estadoRiesgo

    private var riesgoExternoClima = false

    fun iniciarMonitoreo() {
        acelerometro?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    fun detenerMonitoreo() {
        sensorManager.unregisterListener(this)
    }

    fun actualizarRiesgoClimaticoDesdeRed(hayLluviaOTorrentera: Boolean, alertaTexto: String) {
        riesgoExternoClima = hayLluviaOTorrentera
        if (hayLluviaOTorrentera) {
            _estadoRiesgo.value = EstadoRiesgoSensor(
                riesgoRealDetectado = true,
                tipoRiesgoDetectado = "deslizamiento",
                detalleSensor = "Alerta activa: $alertaTexto (Emision ciudadana habilitada)"
            )
        }
    }

    /**
     * Permite activar el disparador de prueba de sensor cuando no hay un terremoto real en curso
     */
    fun activarConfirmacionSensorPrueba(tipo: String = "sismo") {
        _estadoRiesgo.value = EstadoRiesgoSensor(
            riesgoRealDetectado = true,
            tipoRiesgoDetectado = tipo,
            detalleSensor = "Riesgo corroborado por sensor/alerta temprana ($tipo). Ya puedes emitir alerta."
        )
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_ACCELEROMETER) {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]
            val aceleracionTotal = sqrt(x * x + y * y + z * z)
            val deltaSismico = kotlin.math.abs(aceleracionTotal - SensorManager.GRAVITY_EARTH)

            // Umbral de sacudida sismica real (> 4.2 m/s2 sobre gravedad)
            if (deltaSismico > 4.2f) {
                _estadoRiesgo.value = EstadoRiesgoSensor(
                    riesgoRealDetectado = true,
                    tipoRiesgoDetectado = "sismo",
                    detalleSensor = "Sacudida sismica detectada (${String.format("%.1f", deltaSismico)} m/s2). Alerta habilitada.",
                    magnitudAceleracion = deltaSismico
                )
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
