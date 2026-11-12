package com.cayma.alertasos.mesh

import android.content.Context
import android.util.Log
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import java.nio.charset.StandardCharsets

class OfflineMeshManager(
    private val context: Context,
    private val onAlertaRecibidaDeVecino: (String) -> Unit
) {
    private val SERVICE_ID = "com.cayma.alertasos.MESH_NETWORK"
    private val STRATEGY = Strategy.P2P_CLUSTER
    private val nodosConectados = mutableSetOf<String>()

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            payload.asBytes()?.let { bytes ->
                val mensajeJson = String(bytes, StandardCharsets.UTF_8)
                Log.d("MeshNetwork", "Alerta recibida por malla offline desde $endpointId: $mensajeJson")
                onAlertaRecibidaDeVecino(mensajeJson)
                // Retransmitir a otros vecinos conectados (salto multi-hop)
                retransmitirAlerta(mensajeJson, excluirEndpoint = endpointId)
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

    fun retransmitirAlerta(alertaJson: String, excluirEndpoint: String? = null) {
        val destinos = nodosConectados.filter { it != excluirEndpoint }
        if (destinos.isNotEmpty()) {
            val payload = Payload.fromBytes(alertaJson.toByteArray(StandardCharsets.UTF_8))
            Nearby.getConnectionsClient(context).sendPayload(destinos, payload)
        }
    }
}