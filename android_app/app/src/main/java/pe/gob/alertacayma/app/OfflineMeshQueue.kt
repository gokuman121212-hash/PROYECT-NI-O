package pe.gob.alertacayma.app

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.content.Context
import android.os.ParcelUuid
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class OfflineMeshQueue(private val context: Context) {

    private val prefs = context.getSharedPreferences("alertacayma_mesh_queue", Context.MODE_PRIVATE)
    private val serviceUuid = ParcelUuid(UUID.fromString("0000180D-0000-1000-8000-00805F9B34FB"))

    fun encolarAlertaOffline(payload: JSONObject) {
        payload.put("origen_offline_mesh", true)
        val lista = obtenerCola()
        lista.put(payload)
        prefs.edit().putString("pendientes", lista.toString()).apply()
        activarBalizaBluetoothEmergencia()
    }

    fun obtenerCola(): JSONArray {
        val raw = prefs.getString("pendientes", "[]") ?: "[]"
        return try {
            JSONArray(raw)
        } catch (_: Exception) {
            JSONArray()
        }
    }

    fun limpiarCola() {
        prefs.edit().putString("pendientes", "[]").apply()
    }

    fun contarPendientes(): Int = obtenerCola().length()

    @SuppressLint("MissingPermission")
    fun activarBalizaBluetoothEmergencia() {
        try {
            val btManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            val advertiser = btManager?.adapter?.bluetoothLeAdvertiser ?: return
            val settings = AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                .setConnectable(false)
                .build()
            val data = AdvertiseData.Builder()
                .addServiceUuid(serviceUuid)
                .setIncludeDeviceName(false)
                .build()
            advertiser.startAdvertising(settings, data, object : AdvertiseCallback() {})
        } catch (_: Exception) {
            // Si el dispositivo no tiene BLE o permiso concedido, la cola local sigue protegida
        }
    }
}