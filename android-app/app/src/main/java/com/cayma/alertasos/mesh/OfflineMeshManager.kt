package com.cayma.alertasos.mesh

import android.content.Context
import android.util.Log
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import com.google.gson.Gson
import java.nio.charset.StandardCharsets

data class PaqueteMesh(
    val tipoPaquete: String, // "ALERTA", "APOYO_EN_CAMINO", "CHAT_VECINAL"
    val alertaId: String = "",
    val payloadJson: String
)

class OfflineMeshManager(
    private val context: Context,
    private val onPaqueteRecibido: (PaqueteMesh) -> Unit
) {
    private val SERVICE_ID = "com.cayma.alertasos.AREQUIPA_MESH_V2"
    private val STRATEGY = Strategy.P2P_CLUSTER
    private val nodosConectados = mutableSetOf<String>()
    private val gson = Gson()

    val cantidadVecinosConectados: Int
        get() = nodosConectados.size

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            payload.asBytes()?.let { bytes ->
                val raw = String(bytes, StandardCharsets.UTF_8)
                Log.d("ArequipaMesh", "Paquete P2P recibido desde $endpointId: $raw")
                try {
                    val paquete = gson.fromJson(raw, PaqueteMesh::class.java)
                    onPaqueteRecibido(paquete)
                    retransmitirPaquete(paquete, excluirEndpoint = endpointId)
                } catch (_: Exception) {
                    // Compatibilidad con paquetes de alerta directa
                    val fallback = PaqueteMesh(tipoPaquete = "ALERTA", payloadJson = raw)
                    onPaqueteRecibido(fallback)
                }
            }
        }
        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {}
    }

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            Nearby.getConnectionsClient(context).acceptConnection(endpointId, payloadCallback)
        }
        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            if (result.status.isSuccess) {
                nodosConectados.add(endpointId)
            }
        }
        override fun onDisconnected(endpointId: String) {
            nodosConectados.remove(endpointId)
        }
    }

    fun activarNodoMalla(nombreDispositivo: String) {
        val client = Nearby.getConnectionsClient(context)
        val advOptions = AdvertisingOptions.Builder().setStrategy(STRATEGY).build()
        val discOptions = DiscoveryOptions.Builder().setStrategy(STRATEGY).build()

        client.startAdvertising(nombreDispositivo, SERVICE_ID, connectionLifecycleCallback, advOptions)
        client.startDiscovery(SERVICE_ID, object : EndpointDiscoveryCallback() {
            override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
                client.requestConnection(nombreDispositivo, endpointId, connectionLifecycleCallback)
            }
            override fun onEndpointLost(endpointId: String) {}
        }, discOptions)
    }

    fun retransmitirPaquete(paquete: PaqueteMesh, excluirEndpoint: String? = null) {
        val destinos = nodosConectados.filter { it != excluirEndpoint }
        if (destinos.isNotEmpty()) {
            val json = gson.toJson(paquete)
            val payload = Payload.fromBytes(json.toByteArray(StandardCharsets.UTF_8))
            Nearby.getConnectionsClient(context).sendPayload(destinos, payload)
        }
    }

    fun retransmitirAlerta(alertaJson: String) {
        retransmitirPaquete(PaqueteMesh(tipoPaquete = "ALERTA", payloadJson = alertaJson))
    }
}
