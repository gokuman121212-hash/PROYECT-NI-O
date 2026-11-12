package com.cayma.alertasos.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.BatteryManager
import android.os.Looper
import com.google.android.gms.location.*

class AdaptiveLocationManager(private val context: Context) {

    private val fusedClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    private var locationCallback: LocationCallback? = null
    var ultimaLatitud: Double = -16.3425  // Coordenada base Cayma, Arequipa
    var ultimaLongitud: Double = -71.5467

    fun obtenerNivelBateria(): Int {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        return bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }

    @SuppressLint("MissingPermission")
    fun iniciarRastreoAdaptativo(modoEmergencia: Boolean, onLocationUpdate: (Location) -> Unit) {
        detenerRastreo()

        val bateria = obtenerNivelBateria()
        // En emergencia: cada 5 segundos. Modo normal: 15 min (o 30 min si bateria < 20%)
        val intervaloMs = when {
            modoEmergencia -> 5_000L
            bateria < 20 -> 1_800_000L
            else -> 900_000L
        }

        val prioridad = if (modoEmergencia) {
            Priority.PRIORITY_HIGH_ACCURACY
        } else {
            Priority.PRIORITY_BALANCED_POWER_ACCURACY
        }

        val request = LocationRequest.Builder(prioridad, intervaloMs)
            .setMinUpdateIntervalMillis(if (modoEmergencia) 3_000L else 300_000L)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { loc ->
                    ultimaLatitud = loc.latitude
                    ultimaLongitud = loc.longitude
                    onLocationUpdate(loc)
                }
            }
        }

        fusedClient.requestLocationUpdates(request, locationCallback!!, Looper.getMainLooper())
    }

    fun detenerRastreo() {
        locationCallback?.let { fusedClient.removeLocationUpdates(it) }
    }
}