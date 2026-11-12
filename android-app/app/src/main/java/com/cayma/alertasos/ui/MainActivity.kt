package com.cayma.alertasos.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cayma.alertasos.location.AdaptiveLocationManager
import com.cayma.alertasos.mesh.OfflineMeshManager
import com.cayma.alertasos.network.AlertaRequest
import com.cayma.alertasos.network.SosApiService
import com.google.gson.Gson
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val locationManager = AdaptiveLocationManager(this)
        val apiService = SosApiService.create()
        val meshManager = OfflineMeshManager(this) { alertaVecino ->
            // Si recibimos alerta offline de un vecino y tenemos internet, la subimos al servidor
        }

        meshManager.activarNodoMalla("Vecino_Cayma_")

        setContent {
            MaterialTheme {
                PantallaPrincipalAlertaCayma(locationManager, apiService, meshManager)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaPrincipalAlertaCayma(
    locationManager: AdaptiveLocationManager,
    apiService: SosApiService,
    meshManager: OfflineMeshManager
) {
    var moduloSeleccionado by remember { mutableIntStateOf(0) }
    var estadoEnvio by remember { mutableStateOf("Sistema listo en Cayma, Arequipa") }
    val scope = rememberCoroutineScope()

    val modulos = listOf("Mapa Riesgos", "Reportar", "Prevencion", "Red Mesh")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Alerta Cayma — Gestión de Riesgos", fontWeight = FontWeight.Bold) }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    locationManager.iniciarRastreoAdaptativo(modoEmergencia = true) {}
                    val request = AlertaRequest(
                        usuario_id = "ciudadano_cayma_01",
                        latitud = locationManager.ultimaLatitud,
                        longitud = locationManager.ultimaLongitud,
                        tipo = "sos_critico",
                        descripcion = "Emergencia critica activada desde boton SOS",
                        nivel_bateria = locationManager.obtenerNivelBateria()
                    )
                    scope.launch {
                        try {
                            val resp = apiService.enviarAlerta(request)
                            estadoEnvio = "SOS ENVIADO () - ID: "
                        } catch (e: Exception) {
                            // Si no hay internet, retransmitir por Red de Malla Bluetooth/WiFi Direct
                            meshManager.retransmitirAlerta(Gson().toJson(request))
                            estadoEnvio = "Sin internet: SOS retransmitido por Red Mesh Offline"
                        }
                    }
                },
                containerColor = Color(0xFFD32F2F),
                contentColor = Color.White
            ) {
                Icon(Icons.Default.Warning, contentDescription = "SOS")
                Spacer(Modifier.width(8.dp))
                Text("BOTÓN SOS", fontWeight = FontWeight.Bold)
            }
        },
        bottomBar = {
            NavigationBar {
                modulos.forEachIndexed { index, titulo ->
                    NavigationBarItem(
                        selected = moduloSeleccionado == index,
                        onClick = { moduloSeleccionado = index },
                        icon = {
                            Icon(
                                when (index) {
                                    0 -> Icons.Default.Place
                                    1 -> Icons.Default.AddAlert
                                    2 -> Icons.Default.Info
                                    else -> Icons.Default.Share
                                },
                                contentDescription = titulo
                            )
                        },
                        label = { Text(titulo) }
                    )
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Módulo activo: ", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(estadoEnvio, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}