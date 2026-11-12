package com.cayma.alertasos.ui

import android.Manifest
import android.graphics.Color as AndroidColor
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.cayma.alertasos.ai.OfflineAiEngine
import com.cayma.alertasos.data.AlertaLocalEntity
import com.cayma.alertasos.data.AppDatabase
import com.cayma.alertasos.data.OfflineSyncScheduler
import com.cayma.alertasos.location.AdaptiveLocationManager
import com.cayma.alertasos.mesh.OfflineMeshManager
import com.cayma.alertasos.network.AlertaRequest
import com.cayma.alertasos.network.PuntoCalor
import com.cayma.alertasos.network.SosApiService
import com.google.gson.Gson
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polygon

class MainActivity : ComponentActivity() {

    private lateinit var offlineAiEngine: OfflineAiEngine

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Configurar cache de mapas OpenStreetMap
        Configuration.getInstance().userAgentValue = packageName

        offlineAiEngine = OfflineAiEngine(this)
        val activarDesdeWidget = intent.getBooleanExtra("ACTIVAR_SOS_INMEDIATO", false)
        val locationManager = AdaptiveLocationManager(this)
        val apiService = SosApiService.create()
        val dbLocal = AppDatabase.getDatabase(this)
        val meshManager = OfflineMeshManager(this) { alertaVecinoJson ->
            try {
                val alertaRecibida = Gson().fromJson(alertaVecinoJson, AlertaRequest::class.java)
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                    dbLocal.alertaDao().insertarAlerta(
                        AlertaLocalEntity(
                            usuarioId = alertaRecibida.usuario_id,
                            latitud = alertaRecibida.latitud,
                            longitud = alertaRecibida.longitud,
                            tipo = alertaRecibida.tipo,
                            descripcion = "[Relevo Mesh] ${alertaRecibida.descripcion}",
                            nivelBateria = alertaRecibida.nivel_bateria
                        )
                    )
                    OfflineSyncScheduler.programarSincronizacionAlVolverInternet(this@MainActivity)
                }
            } catch (_: Exception) { }
        }

        setContent {
            MaterialTheme {
                PantallaPrincipalAlertaCayma(
                    locationManager = locationManager,
                    apiService = apiService,
                    meshManager = meshManager,
                    aiEngine = offlineAiEngine,
                    sosDesdeWidget = activarDesdeWidget
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Activar IA local solo mientras el usuario usa la app
        offlineAiEngine.inicializarMientrasAppEnUso()
    }

    override fun onStop() {
        super.onStop()
        // Liberar RAM de la IA cuando la app pasa a segundo plano
        offlineAiEngine.liberarMemoriaAlCerrar()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaPrincipalAlertaCayma(
    locationManager: AdaptiveLocationManager,
    apiService: SosApiService,
    meshManager: OfflineMeshManager,
    aiEngine: OfflineAiEngine,
    sosDesdeWidget: Boolean
) {
    val context = LocalContext.current
    val db = remember { AppDatabase.getDatabase(context) }
    var moduloSeleccionado by remember { mutableIntStateOf(0) }
    var estadoEnvio by remember { mutableStateOf("Sistema listo en Cayma, Arequipa") }
    var puntosCalor by remember {
        mutableStateOf(
            listOf(
                PuntoCalor(-16.3380, -71.5450, 0.85, "torrentera_cayma"),
                PuntoCalor(-16.3510, -71.5490, 0.75, "zona_sismica")
            )
        )
    }
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
        if (resultados[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            locationManager.iniciarRastreoAdaptativo(modoEmergencia = false) {}
            meshManager.activarNodoMalla("Vecino_Cayma_${(100..999).random()}")
            meshActivo = true
        }
    }

    fun dispararAlerta(tipo: String, desc: String, emergencia: Boolean) {
        if (emergencia) locationManager.iniciarRastreoAdaptativo(modoEmergencia = true) {}

        // 1. Evaluar primero con la IA embebida en el celular (100% offline)
        val evaluacionLocal = aiEngine.clasificarReporteSinInternet(desc, tipo)
        if (!evaluacionLocal.aprobado) {
            estadoEnvio = "IA Local (${evaluacionLocal.motorUsado}): Reporte poco claro, añade más detalle."
            return
        }

        val req = AlertaRequest(
            usuario_id = "ciudadano_cayma_01",
            latitud = locationManager.ultimaLatitud,
            longitud = locationManager.ultimaLongitud,
            tipo = evaluacionLocal.tipoClasificado,
            descripcion = desc,
            nivel_bateria = locationManager.obtenerNivelBateria()
        )

        scope.launch {
            try {
                val resp = apiService.enviarAlerta(req)
                estadoEnvio = "Enviado (${resp.nodo_guardado}) | IA: ${resp.confianza_ia}"
            } catch (_: Exception) {
                // Sin internet: Guardar en SQLite local (Room), programar subida y retransmitir por Malla P2P
                db.alertaDao().insertarAlerta(
                    AlertaLocalEntity(
                        usuarioId = req.usuario_id,
                        latitud = req.latitud,
                        longitud = req.longitud,
                        tipo = req.tipo,
                        descripcion = req.descripcion,
                        nivelBateria = req.nivel_bateria
                    )
                )
                OfflineSyncScheduler.programarSincronizacionAlVolverInternet(context)
                meshManager.retransmitirAlerta(Gson().toJson(req))
                estadoEnvio = "Sin internet: Guardado en SQLite + Enviado por Malla Offline (${evaluacionLocal.motorUsado})"
            }
        }
    }

    LaunchedEffect(Unit) {
        launcherPermisos.launch(permisosRequeridos)
        if (sosDesdeWidget) {
            dispararAlerta("sos_critico", "SOS activado desde Widget", true)
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
                                estadoEnvio = "Mostrando zonas de riesgo cacheadas (Modo Offline)"
                            }
                        }
                    }) {
                        Text("Sincronizar Mapa de Calor (Ollama + Clima)")
                    }

                    // Mapa Visual Interactivo de Cayma, Arequipa con circulos de calor
                    Card(modifier = Modifier.fillMaxWidth().weight(1f)) {
                        AndroidView(
                            factory = { ctx ->
                                MapView(ctx).apply {
                                    setTileSource(TileSourceFactory.MAPNIK)
                                    setMultiTouchControls(true)
                                    controller.setZoom(14.5)
                                    controller.setCenter(GeoPoint(-16.3425, -71.5467))
                                }
                            },
                            update = { mapView ->
                                mapView.overlays.clear()
                                puntosCalor.forEach { punto ->
                                    val centro = GeoPoint(punto.lat, punto.lng)
                                    val circuloCalor = Polygon().apply {
                                        points = Polygon.pointsAsCircle(centro, 280.0 * punto.intensidad)
                                        fillPaint.color = if (punto.intensidad >= 0.8) {
                                            AndroidColor.argb(110, 220, 38, 38) // Rojo critico
                                        } else {
                                            AndroidColor.argb(100, 245, 158, 11) // Naranja moderado
                                        }
                                        outlinePaint.color = AndroidColor.RED
                                        outlinePaint.strokeWidth = 3f
                                        title = "${punto.categoria.uppercase()} (Riesgo: ${punto.intensidad})"
                                    }
                                    mapView.overlays.add(circuloCalor)
                                }
                                mapView.invalidate()
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                1 -> {
                    Text("Nuevo Reporte (Evaluado por IA en el Celular + Ollama)", style = MaterialTheme.typography.titleMedium)
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
                        label = { Text("Describe qué ocurre (ej. huaico bajando por torrentera)") },
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
                            Text("IA On-Device: Activa en memoria solo mientras usas la aplicación para clasificar alertas sin internet.")
                        }
                    }
                }
            }
        }
    }
}