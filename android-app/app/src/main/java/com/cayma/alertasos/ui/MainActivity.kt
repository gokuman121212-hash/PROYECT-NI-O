package com.cayma.alertasos.ui

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.cayma.alertasos.network.PuntoCalor
import com.cayma.alertasos.network.SosApiService
import com.google.gson.Gson
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val activarDesdeWidget = intent.getBooleanExtra("ACTIVAR_SOS_INMEDIATO", false)
        val locationManager = AdaptiveLocationManager(this)
        val apiService = SosApiService.create()
        val meshManager = OfflineMeshManager(this) { _ -> }

        setContent {
            MaterialTheme {
                PantallaPrincipalAlertaCayma(
                    locationManager = locationManager,
                    apiService = apiService,
                    meshManager = meshManager,
                    sosDesdeWidget = activarDesdeWidget
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaPrincipalAlertaCayma(
    locationManager: AdaptiveLocationManager,
    apiService: SosApiService,
    meshManager: OfflineMeshManager,
    sosDesdeWidget: Boolean
) {
    var moduloSeleccionado by remember { mutableIntStateOf(0) }
    var estadoEnvio by remember { mutableStateOf("Sistema listo en Cayma, Arequipa") }
    var puntosCalor by remember { mutableStateOf<List<PuntoCalor>>(emptyList()) }
    var descripcionReporte by remember { mutableStateOf("") }
    var tipoReporte by remember { mutableStateOf("deslizamiento") }
    var meshActivo by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val permisosRequeridos = remember {
        buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_SCAN)
                add(Manifest.permission.BLUETOOTH_CONNECT)
                add(Manifest.permission.BLUETOOTH_ADVERTISE)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.NEARBY_WIFI_DEVICES)
            }
        }.toTypedArray()
    }

    val launcherPermisos = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { resultados ->
        val gpsOk = resultados[Manifest.permission.ACCESS_FINE_LOCATION] == true
        if (gpsOk) {
            locationManager.iniciarRastreoAdaptativo(modoEmergencia = false) {}
            meshManager.activarNodoMalla("Vecino_Cayma_${(100..999).random()}")
            meshActivo = true
        }
    }

    fun dispararAlerta(tipo: String, desc: String, emergencia: Boolean) {
        if (emergencia) locationManager.iniciarRastreoAdaptativo(modoEmergencia = true) {}
        val req = AlertaRequest(
            usuario_id = "ciudadano_cayma_01",
            latitud = locationManager.ultimaLatitud,
            longitud = locationManager.ultimaLongitud,
            tipo = tipo,
            descripcion = desc,
            nivel_bateria = locationManager.obtenerNivelBateria()
        )
        scope.launch {
            try {
                val resp = apiService.enviarAlerta(req)
                estadoEnvio = "Enviado (${resp.nodo_guardado}) | Confianza IA: ${resp.confianza_ia}"
            } catch (e: Exception) {
                meshManager.retransmitirAlerta(Gson().toJson(req))
                estadoEnvio = "Sin red: Retransmitido por Malla Offline P2P"
            }
        }
    }

    LaunchedEffect(Unit) {
        launcherPermisos.launch(permisosRequeridos)
        if (sosDesdeWidget) {
            dispararAlerta("sos_critico", "SOS activado desde Widget de inicio", true)
        }
    }

    val modulos = listOf("Mapa Riesgos", "Reportar", "Prevención", "Red Mesh")

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Alerta Cayma — Gestión de Riesgos", fontWeight = FontWeight.Bold) })
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { dispararAlerta("sos_critico", "Emergencia critica boton SOS", true) },
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Estado: $estadoEnvio", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Text("GPS: ${locationManager.ultimaLatitud}, ${locationManager.ultimaLongitud} | Batería: ${locationManager.obtenerNivelBateria()}%", style = MaterialTheme.typography.bodySmall)
                }
            }

            when (moduloSeleccionado) {
                0 -> {
                    Button(onClick = {
                        scope.launch {
                            try {
                                puntosCalor = apiService.obtenerMapaCalor().puntos
                            } catch (_: Exception) {
                                estadoEnvio = "No se pudo actualizar el mapa (Modo Offline)"
                            }
                        }
                    }) {
                        Text("Actualizar Mapa de Calor (Cayma)")
                    }
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(puntosCalor) { punto ->
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    "Riesgo: ${punto.categoria.uppercase()} | Intensidad: ${punto.intensidad} (${punto.lat}, ${punto.lng})",
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }
                    }
                }
                1 -> {
                    Text("Nuevo Reporte Ciudadano (Filtrado por IA)", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("deslizamiento", "sismo", "incendio", "medica").forEach { cat ->
                            FilterChip(
                                selected = tipoReporte == cat,
                                onClick = { tipoReporte = cat },
                                label = { Text(cat) }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = descripcionReporte,
                        onValueChange = { descripcionReporte = it },
                        label = { Text("Describe qué ocurre (ej. huaico en torrentera)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = {
                            dispararAlerta(tipoReporte, descripcionReporte, false)
                            descripcionReporte = ""
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Enviar Reporte")
                    }
                }
                2 -> {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Protocolos de Emergencia — Cayma", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("• Torrenteras y Huaicos: Aléjate de cauces secos en temporada de lluvias y dirígete a zonas altas.")
                            Text("• Sismos (Volcán Misti / Fallas locales): Ubícate en zonas seguras externas y mantén activo el nodo Mesh.")
                            Text("• Emergencia Médica / Bomberos: Usa el botón SOS para enviar tu coordenada exacta.")
                        }
                    }
                }
                3 -> {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Red de Malla Offline (Bluetooth + Wi-Fi Direct)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("Estado del nodo P2P: ${if (meshActivo) "ACTIVO (Buscando vecinos cercanos)" else "INACTIVO"}")
                            Text("Si caen las antenas celulares, tu teléfono retransmitirá alertas SOS automáticamente de dispositivo en dispositivo hasta encontrar uno con salida a Internet.")
                        }
                    }
                }
            }
        }
    }
}