package com.cayma.alertasos.ui

import android.Manifest
import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.os.Bundle
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.cayma.alertasos.ai.OfflineAiEngine
import com.cayma.alertasos.data.*
import com.cayma.alertasos.location.AdaptiveLocationManager
import com.cayma.alertasos.location.ArequipaGeoData
import com.cayma.alertasos.mesh.OfflineMeshManager
import com.cayma.alertasos.mesh.PaqueteMesh
import com.cayma.alertasos.network.*
import com.cayma.alertasos.sensors.SensoresRiesgoManager
import com.google.gson.Gson
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.Polyline

class MainActivity : ComponentActivity() {

    private lateinit var offlineAiEngine: OfflineAiEngine
    private lateinit var sensoresManager: SensoresRiesgoManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Configuration.getInstance().userAgentValue = packageName

        offlineAiEngine = OfflineAiEngine(this)
        sensoresManager = SensoresRiesgoManager(this)
        val locationManager = AdaptiveLocationManager(this)
        val apiService = SosApiService.create()
        val dbLocal = AppDatabase.getDatabase(this)

        setContent {
            AlertaArequipaTheme {
                var perfilUsuario by remember { mutableStateOf(SessionManager.obtenerSesion(this)) }

                if (perfilUsuario == null) {
                    PantallaAutenticacionArequipa(
                        apiService = apiService,
                        latGpsActual = locationManager.ultimaLatitud,
                        lngGpsActual = locationManager.ultimaLongitud,
                        onAutenticado = { nuevoPerfil -> perfilUsuario = nuevoPerfil }
                    )
                } else {
                    PantallaPrincipalArequipa(
                        perfil = perfilUsuario!!,
                        locationManager = locationManager,
                        apiService = apiService,
                        dbLocal = dbLocal,
                        aiEngine = offlineAiEngine,
                        sensoresManager = sensoresManager,
                        onCerrarSesion = {
                            SessionManager.cerrarSesion(this)
                            perfilUsuario = null
                        }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        offlineAiEngine.inicializarMientrasAppEnUso()
        sensoresManager.iniciarMonitoreo()
    }

    override fun onStop() {
        super.onStop()
        offlineAiEngine.liberarMemoriaAlCerrar()
        sensoresManager.detenerMonitoreo()
    }
}

fun crearIconoSeguridadDinamico(
    context: android.content.Context,
    tipo: String,
    zoomActual: Double
): BitmapDrawable {
    // Escala inversa: grande cuando el mapa esta alejado (58px) y se achica al acercarse (22px)
    val factor = ((17.5 - zoomActual) / 5.0).coerceIn(0.0, 1.0)
    val sizePx = (22 + (36 * factor)).toInt()

    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val centro = sizePx / 2f
    val radio = (sizePx / 2f) - 3f

    // Fondo circular blanco con sombra/borde de color segun institucion
    paint.style = Paint.Style.FILL
    paint.color = AndroidColor.WHITE
    canvas.drawCircle(centro, centro, radio, paint)

    paint.style = Paint.Style.STROKE
    paint.strokeWidth = (sizePx * 0.10f).coerceAtLeast(2.5f)
    paint.color = when (tipo) {
        "hospital" -> AndroidColor.rgb(211, 47, 47)   // Rojo Salud
        "comisaria" -> AndroidColor.rgb(21, 101, 192) // Azul PNP
        else -> AndroidColor.rgb(46, 125, 50)         // Verde Zona Segura
    }
    canvas.drawCircle(centro, centro, radio, paint)

    // Simbolo interior limpio (Cruz roja para salud, Escudo/Cuadro azul para PNP, Punto verde para refugio)
    paint.style = Paint.Style.FILL
    val rInner = radio * 0.52f
    when (tipo) {
        "hospital" -> {
            val grosor = rInner * 0.42f
            canvas.drawRect(centro - grosor, centro - rInner, centro + grosor, centro + rInner, paint)
            canvas.drawRect(centro - rInner, centro - grosor, centro + rInner, centro + grosor, paint)
        }
        "comisaria" -> {
            canvas.drawRect(centro - rInner * 0.75f, centro - rInner * 0.75f, centro + rInner * 0.75f, centro + rInner * 0.75f, paint)
        }
        else -> {
            canvas.drawCircle(centro, centro, rInner * 0.75f, paint)
        }
    }

    return BitmapDrawable(context.resources, bitmap)
}

@SuppressLint("ClickableViewAccessibility")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaPrincipalArequipa(
    perfil: PerfilCiudadano,
    locationManager: AdaptiveLocationManager,
    apiService: SosApiService,
    dbLocal: AppDatabase,
    aiEngine: OfflineAiEngine,
    sensoresManager: SensoresRiesgoManager,
    onCerrarSesion: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val gson = remember { Gson() }

    // 3 PESTAÑAS SEPARADAS: 0 = Mapa, 1 = Alertas, 2 = Riesgos e Historial
    var pestanaActiva by remember { mutableIntStateOf(0) }
    val estadoSensor by sensoresManager.estadoRiesgo.collectAsState()

    var alertasActivas by remember {
        mutableStateOf(
            listOf(
                PuntoAlertaMapa(
                    alerta_id = "alerta_demo_1",
                    usuario_id = "vecino_asa_01",
                    nombre_usuario = "Carlos Mamani",
                    distrito = "Alto Selva Alegre",
                    sector_referencia = "Parte Alta Av. Obrera, cerca a torrentera",
                    lat = -16.3745,
                    lng = -71.5220,
                    intensidad = 0.90,
                    categoria = "deslizamiento",
                    descripcion = "Agua y lodo bajando por pasaje, vecino adulto mayor necesita apoyo para salir.",
                    voluntarios_count = 1
                )
            )
        )
    }

    var alertaEnApoyo by remember { mutableStateOf<PuntoAlertaMapa?>(null) }
    var alertaChatAbierto by remember { mutableStateOf<PuntoAlertaMapa?>(null) }
    var mensajesChat by remember { mutableStateOf<List<MensajeChatDto>>(emptyList()) }
    var textoChatInput by remember { mutableStateOf("") }

    var mostrarDialogoEmitir by remember { mutableStateOf(false) }
    var tipoAlertaEmitir by remember { mutableStateOf("deslizamiento") }
    var contextoAlertaEmitir by remember { mutableStateOf("") }
    var filtroInstituciones by remember { mutableStateOf("todos") }
    var vecinosMeshCount by remember { mutableIntStateOf(0) }
    var bannerAviso by remember { mutableStateOf<String?>(null) }
    var mapViewRef by remember { mutableStateOf<MapView?>(null) }

    val meshManager = remember {
        OfflineMeshManager(context) { paquete ->
            scope.launch {
                when (paquete.tipoPaquete) {
                    "ALERTA" -> {
                        try {
                            val req = gson.fromJson(paquete.payloadJson, AlertaRequest::class.java)
                            val nueva = PuntoAlertaMapa(
                                alerta_id = "mesh_${System.currentTimeMillis()}",
                                usuario_id = req.usuario_id,
                                nombre_usuario = "${req.nombre_usuario} (Via Red Mesh)",
                                distrito = req.distrito,
                                sector_referencia = req.sector_referencia,
                                lat = req.latitud,
                                lng = req.longitud,
                                intensidad = 0.92,
                                categoria = req.tipo,
                                descripcion = req.descripcion,
                                origen_offline_mesh = true
                            )
                            alertasActivas = listOf(nueva) + alertasActivas
                        } catch (_: Exception) {}
                    }
                    "APOYO_EN_CAMINO" -> {
                        alertasActivas = alertasActivas.map {
                            if (it.alerta_id == paquete.alertaId) it.copy(voluntarios_count = it.voluntarios_count + 1) else it
                        }
                    }
                    "CHAT_VECINAL" -> {
                        try {
                            val msg = gson.fromJson(paquete.payloadJson, MensajeChatDto::class.java)
                            if (alertaChatAbierto?.alerta_id == paquete.alertaId) {
                                mensajesChat = mensajesChat + msg
                            }
                        } catch (_: Exception) {}
                    }
                }
            }
        }
    }

    val permisos = remember {
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
    ) { res ->
        if (res[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            locationManager.iniciarRastreoAdaptativo(modoEmergencia = false) {}
            meshManager.activarNodoMalla("Vecino_${perfil.distrito}_${(10..99).random()}")
        }
    }

    suspend fun sincronizarConServidor() {
        try {
            val mapaResp = apiService.obtenerMapaCalor()
            if (mapaResp.puntos.isNotEmpty()) {
                alertasActivas = mapaResp.puntos
            }
            mapaResp.clima_y_torrenteras?.let { clima ->
                if (clima.riesgo_activo_detectado) {
                    sensoresManager.actualizarRiesgoClimaticoDesdeRed(
                        true,
                        "Lluvia ${clima.precipitacion_mm}mm - Torrenteras ${clima.alerta_torrenteras}"
                    )
                }
            }
        } catch (_: Exception) { }
        vecinosMeshCount = meshManager.cantidadVecinosConectados
    }

    LaunchedEffect(Unit) {
        launcherPermisos.launch(permisos)
        while (true) {
            sincronizarConServidor()
            delay(8000)
        }
    }

    val miLat = if (ArequipaGeoData.estaEnArequipaMetropolitana(locationManager.ultimaLatitud, locationManager.ultimaLongitud) && locationManager.ultimaLatitud != 0.0) {
        locationManager.ultimaLatitud
    } else {
        perfil.latitud
    }
    val miLng = if (ArequipaGeoData.estaEnArequipaMetropolitana(locationManager.ultimaLatitud, locationManager.ultimaLongitud) && locationManager.ultimaLongitud != 0.0) {
        locationManager.ultimaLongitud
    } else {
        perfil.longitud
    }

    Scaffold(
        containerColor = FondoHielo,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SuperficieBlanca),
                title = {
                    Column {
                        Text("Alerta Arequipa", fontWeight = FontWeight.Bold, color = AzulOscuroTexto)
                        Text(
                            "${perfil.nombreCompleto} • ${perfil.distrito}",
                            style = MaterialTheme.typography.bodySmall,
                            color = GrisTextoSecundario
                        )
                    }
                },
                actions = {
                    // Indicador compacto de emergencias activas
                    Surface(
                        color = if (alertasActivas.isNotEmpty()) RojoFondoSuave else CelesteSuave,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.padding(end = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(
                                        if (alertasActivas.isNotEmpty()) RojoInfrarrojo else AzulPrincipal,
                                        CircleShape
                                    )
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "${alertasActivas.size} SOS",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = AzulOscuroTexto
                            )
                        }
                    }
                    IconButton(onClick = { scope.launch { sincronizarConServidor() } }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Actualizar", tint = AzulPrincipal)
                    }
                    IconButton(onClick = onCerrarSesion) {
                        Icon(Icons.Default.ExitToApp, contentDescription = "Salir", tint = GrisTextoSecundario)
                    }
                }
            )
        },
        bottomBar = {
            Column {
                // Barra inferior persistente cuando vas en apoyo a un vecino
                if (alertaEnApoyo != null) {
                    Surface(
                        color = VerdeAyuda,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "AYUDA EN CAMINO",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "Ruta limpia hacia ${alertaEnApoyo!!.nombre_usuario} (${alertaEnApoyo!!.distrito})",
                                    color = Color.White.copy(alpha = 0.9f),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Button(
                                    onClick = {
                                        alertaChatAbierto = alertaEnApoyo
                                        scope.launch {
                                            try {
                                                mensajesChat = apiService.obtenerChat(alertaEnApoyo!!.alerta_id).mensajes
                                            } catch (_: Exception) {}
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Default.Email, null, tint = VerdeAyuda, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Chat", color = VerdeAyuda, fontWeight = FontWeight.Bold)
                                }
                                IconButton(onClick = { alertaEnApoyo = null }) {
                                    Icon(Icons.Default.Close, contentDescription = "Finalizar", tint = Color.White)
                                }
                            }
                        }
                    }
                }

                // BARRA DE NAVEGACION DE 3 SECCIONES SEPARADAS
                NavigationBar(containerColor = SuperficieBlanca) {
                    NavigationBarItem(
                        selected = pestanaActiva == 0,
                        onClick = { pestanaActiva = 0 },
                        icon = { Icon(Icons.Default.Place, contentDescription = "Mapa") },
                        label = { Text("Mapa") }
                    )
                    NavigationBarItem(
                        selected = pestanaActiva == 1,
                        onClick = { pestanaActiva = 1 },
                        icon = { Icon(Icons.Default.Warning, contentDescription = "Alertas") },
                        label = { Text("Alertas (${alertasActivas.size})") }
                    )
                    NavigationBarItem(
                        selected = pestanaActiva == 2,
                        onClick = { pestanaActiva = 2 },
                        icon = { Icon(Icons.Default.Info, contentDescription = "Riesgos e Historial") },
                        label = { Text("Riesgos e Historial") }
                    )
                }
            }
        }
    ) { padding ->
        when (pestanaActiva) {
            // =========================================================================
            // PESTAÑA 1: MAPA A PANTALLA COMPLETA (MOVIMIENTO FLUIDO SIN SCROLL)
            // =========================================================================
            0 -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                ) {
                    AndroidView(
                        factory = { ctx ->
                            MapView(ctx).apply {
                                setTileSource(TileSourceFactory.MAPNIK)
                                setMultiTouchControls(true)
                                isHorizontalMapRepetitionEnabled = false
                                isVerticalMapRepetitionEnabled = false
                                tilesScaleFactor = 1.6f
                                minZoomLevel = 11.5
                                maxZoomLevel = 19.0
                                controller.setZoom(13.2)
                                controller.setCenter(GeoPoint(miLat, miLng))
                                addMapListener(object : org.osmdroid.events.MapListener {
                                    override fun onScroll(event: org.osmdroid.events.ScrollEvent?): Boolean = false
                                    override fun onZoom(event: org.osmdroid.events.ZoomEvent?): Boolean {
                                        val z = event?.zoomLevel ?: zoomLevelDouble
                                        overlays.filterIsInstance<Marker>().forEach { m ->
                                            val tipoCentro = m.relatedObject as? String
                                            if (tipoCentro != null) {
                                                m.icon = crearIconoSeguridadDinamico(ctx, tipoCentro, z)
                                            }
                                        }
                                        invalidate()
                                        return true
                                    }
                                })
                                // Evitar que el contenedor padre intercepte los gestos tactiles del mapa
                                setOnTouchListener { v, event ->
                                    when (event.action) {
                                        MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                                            v.parent?.requestDisallowInterceptTouchEvent(true)
                                        }
                                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                                            v.parent?.requestDisallowInterceptTouchEvent(false)
                                        }
                                    }
                                    false
                                }
                                mapViewRef = this
                            }
                        },
                        update = { mapView ->
                            mapView.overlays.clear()

                            // A) Rutas historicamente bloqueadas (anos anteriores) en rojo
                            ArequipaGeoData.RUTAS_BLOQUEADAS_HISTORICAS.forEach { bloqueo ->
                                val lineaBloqueo = Polyline().apply {
                                    addPoint(GeoPoint(bloqueo.latInicio, bloqueo.lngInicio))
                                    addPoint(GeoPoint(bloqueo.latFin, bloqueo.lngFin))
                                    outlinePaint.color = AndroidColor.argb(190, 211, 47, 47)
                                    outlinePaint.strokeWidth = 9f
                                    title = "VIA CON HISTORIAL DE BLOQUEO: ${bloqueo.nombre}"
                                }
                                mapView.overlays.add(lineaBloqueo)
                            }

                            // B) Ruta sugerida libre de bloqueos (solo visual, sin voz) si aceptaste apoyar
                            alertaEnApoyo?.let { destino ->
                                val puntosRuta = ArequipaGeoData.calcularRutaLibreDeBloqueos(
                                    miLat, miLng, destino.lat, destino.lng
                                )
                                val rutaVerde = Polyline().apply {
                                    puntosRuta.forEach { (lat, lng) -> addPoint(GeoPoint(lat, lng)) }
                                    outlinePaint.color = AndroidColor.rgb(27, 94, 32)
                                    outlinePaint.strokeWidth = 12f
                                    title = "Ruta recomendada libre hacia ${destino.nombre_usuario}"
                                }
                                mapView.overlays.add(rutaVerde)
                            }

                            // C) Senal Infrarroja de Desastres (sin manitos, pulso radial limpio)
                            alertasActivas.forEach { alerta ->
                                val centro = GeoPoint(alerta.lat, alerta.lng)

                                val haloExterno = Polygon().apply {
                                    points = Polygon.pointsAsCircle(centro, 400.0)
                                    fillPaint.color = AndroidColor.argb(50, 255, 23, 68)
                                    outlinePaint.color = AndroidColor.argb(130, 213, 0, 0)
                                    outlinePaint.strokeWidth = 2f
                                }
                                val nucleoInfrarrojo = Polygon().apply {
                                    points = Polygon.pointsAsCircle(centro, 140.0)
                                    fillPaint.color = AndroidColor.argb(120, 213, 0, 0)
                                    outlinePaint.color = AndroidColor.RED
                                    outlinePaint.strokeWidth = 3f
                                }
                                val puntoCentralSos = Polygon().apply {
                                    points = Polygon.pointsAsCircle(centro, 25.0)
                                    fillPaint.color = AndroidColor.rgb(198, 40, 40)
                                    outlinePaint.color = AndroidColor.WHITE
                                    outlinePaint.strokeWidth = 4f
                                }
                                mapView.overlays.add(haloExterno)
                                mapView.overlays.add(nucleoInfrarrojo)
                                mapView.overlays.add(puntoCentralSos)
                            }

                                                        // C2) Hospitales, Comisarias PNP y Zonas Seguras (grandes de lejos, se achican al acercar)
                            val zoomActual = mapView.zoomLevelDouble
                            ArequipaGeoData.CENTROS_SEGURIDAD.forEach { centro ->
                                val m = Marker(mapView).apply {
                                    position = GeoPoint(centro.lat, centro.lng)
                                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                                    relatedObject = centro.tipo
                                    icon = crearIconoSeguridadDinamico(context, centro.tipo, zoomActual)
                                    title = centro.nombre
                                    snippet = "Distrito: ${centro.distrito}"
                                }
                                mapView.overlays.add(m)
                            }

                            // D) Tu ubicacion GPS actual (punto azul limpio con aura celeste, sin manito)
                            val miCentro = GeoPoint(miLat, miLng)
                            val auraGps = Polygon().apply {
                                points = Polygon.pointsAsCircle(miCentro, 85.0)
                                fillPaint.color = AndroidColor.argb(45, 2, 119, 189)
                                outlinePaint.color = AndroidColor.argb(100, 2, 119, 189)
                                outlinePaint.strokeWidth = 2f
                            }
                            val puntoGps = Polygon().apply {
                                points = Polygon.pointsAsCircle(miCentro, 22.0)
                                fillPaint.color = AndroidColor.rgb(2, 119, 189)
                                outlinePaint.color = AndroidColor.WHITE
                                outlinePaint.strokeWidth = 5f
                            }
                            mapView.overlays.add(auraGps)
                            mapView.overlays.add(puntoGps)

                            mapView.invalidate()
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    // Boton flotante para centrar el mapa en mi ubicacion actual
                    FloatingActionButton(
                        onClick = {
                            mapViewRef?.controller?.animateTo(GeoPoint(miLat, miLng))
                            mapViewRef?.controller?.setZoom(15.5)
                        },
                        containerColor = SuperficieBlanca,
                        contentColor = AzulPrincipal,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(16.dp)
                    ) {
                        Icon(Icons.Default.LocationOn, contentDescription = "Mi ubicacion")
                    }
                }
            }

            // =========================================================================
            // PESTAÑA 2: ALERTAS (EMISION POR SENSOR + APOYO VECINAL + CHAT)
            // =========================================================================
            1 -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item { Spacer(Modifier.height(4.dp)) }

                    // Indicadores de estado de emergencias y red sin internet
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                color = if (alertasActivas.isNotEmpty()) RojoFondoSuave else CelesteSuave,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        "${alertasActivas.size} Emergencias Activas",
                                        fontWeight = FontWeight.Bold,
                                        color = AzulOscuroTexto,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        "En Arequipa Ciudad",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = GrisTextoSecundario
                                    )
                                }
                            }

                            Surface(
                                color = CelesteSuave,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        "Red Mesh P2P",
                                        fontWeight = FontWeight.Bold,
                                        color = AzulOscuroTexto,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        "$vecinosMeshCount vecinos cerca sin red",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = GrisTextoSecundario
                                    )
                                }
                            }
                        }
                    }

                    // Panel de Sensor + Boton para Emitir Alerta (en la seccion de Alertas)
                    item {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (estadoSensor.riesgoRealDetectado) RojoFondoSuave else CelesteSuave
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (estadoSensor.riesgoRealDetectado) Icons.Default.Warning else Icons.Default.Notifications,
                                        contentDescription = null,
                                        tint = if (estadoSensor.riesgoRealDetectado) RojoInfrarrojo else AzulPrincipal
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = estadoSensor.detalleSensor,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = AzulOscuroTexto,
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            tipoAlertaEmitir = if (estadoSensor.tipoRiesgoDetectado != "NINGUNO") estadoSensor.tipoRiesgoDetectado else "deslizamiento"
                                            mostrarDialogoEmitir = true
                                        },
                                        enabled = estadoSensor.riesgoRealDetectado,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = RojoInfrarrojo,
                                            disabledContainerColor = Color(0xFFB0BEC5)
                                        ),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Default.Warning, null, tint = Color.White)
                                        Spacer(Modifier.width(6.dp))
                                        Text("EMITIR ALERTA A AREQUIPA", fontWeight = FontWeight.Bold, color = Color.White)
                                    }

                                    if (!estadoSensor.riesgoRealDetectado) {
                                        OutlinedButton(
                                            onClick = { sensoresManager.activarConfirmacionSensorPrueba("sismo") },
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            Text("Probar Sensor", style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (bannerAviso != null) {
                        item {
                            Surface(
                                color = VerdeFondoSuave,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = bannerAviso!!,
                                    color = VerdeAyuda,
                                    fontWeight = FontWeight.SemiBold,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }
                    }

                    item {
                        Text(
                            "Alertas Vecinales Activas (${alertasActivas.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = AzulOscuroTexto
                        )
                    }

                    items(alertasActivas) { alerta ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = SuperficieBlanca),
                            shape = RoundedCornerShape(14.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        color = RojoFondoSuave,
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = alerta.categoria.uppercase(),
                                            color = RojoInfrarrojo,
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.labelSmall,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }

                                    Surface(
                                        color = VerdeFondoSuave,
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Default.Person, null, tint = VerdeAyuda, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text(
                                                text = "${alerta.voluntarios_count} en apoyo",
                                                color = VerdeAyuda,
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                        }
                                    }
                                }

                                Text(
                                    text = "${alerta.nombre_usuario} — ${alerta.distrito}",
                                    fontWeight = FontWeight.Bold,
                                    color = AzulOscuroTexto
                                )
                                if (alerta.sector_referencia.isNotBlank()) {
                                    Text(
                                        text = "Ubicacion: ${alerta.sector_referencia}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = AzulPrincipal,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                                Text(
                                    text = alerta.descripcion,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = AzulOscuroTexto
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            alertaEnApoyo = alerta
                                            alertasActivas = alertasActivas.map {
                                                if (it.alerta_id == alerta.alerta_id) it.copy(voluntarios_count = it.voluntarios_count + 1) else it
                                            }
                                            // Cambiar automaticamente a la pestana Mapa y centrar en la ruta
                                            pestanaActiva = 0
                                            mapViewRef?.controller?.animateTo(GeoPoint(alerta.lat, alerta.lng))

                                            scope.launch {
                                                try {
                                                    apiService.confirmarAyudaEnCamino(
                                                        alerta.alerta_id,
                                                        ApoyoRequest(perfil.usuarioId, perfil.nombreCompleto, miLat, miLng)
                                                    )
                                                } catch (_: Exception) {
                                                    meshManager.retransmitirPaquete(
                                                        PaqueteMesh("APOYO_EN_CAMINO", alerta.alerta_id, perfil.nombreCompleto)
                                                    )
                                                }
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = VerdeAyuda),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Default.ArrowForward, null, tint = Color.White, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Ir en Apoyo", color = Color.White, fontWeight = FontWeight.Bold)
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            alertaChatAbierto = alerta
                                            scope.launch {
                                                try {
                                                    mensajesChat = apiService.obtenerChat(alerta.alerta_id).mensajes
                                                } catch (_: Exception) {
                                                    val locales = dbLocal.alertaDao().obtenerChatPorAlerta(alerta.alerta_id)
                                                    mensajesChat = locales.map {
                                                        MensajeChatDto(
                                                            remitente_id = it.remitenteId,
                                                            nombre_remitente = it.nombreRemitente,
                                                            rol = it.rol,
                                                            mensaje = it.mensaje,
                                                            origen_mesh = it.origenMesh
                                                        )
                                                    }
                                                }
                                            }
                                        },
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Icon(Icons.Default.Email, null, tint = AzulPrincipal, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Chat", color = AzulPrincipal)
                                    }
                                }
                            }
                        }
                    }

                    item { Spacer(Modifier.height(24.dp)) }
                }
            }

            // =========================================================================
            // PESTAÑA 3: RIESGOS E HISTORIAL (SEPARADO)
            // =========================================================================
            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Text(
                            "Analisis de Riesgos e Historial de Bloqueos (Arequipa)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = AzulOscuroTexto
                        )
                        Text(
                            "Registros de anos anteriores usados para calcular rutas limpias en emergencias:",
                            style = MaterialTheme.typography.bodySmall,
                            color = GrisTextoSecundario
                        )
                    }

                    items(ArequipaGeoData.RUTAS_BLOQUEADAS_HISTORICAS) { ruta ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = SuperficieBlanca),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(ruta.nombre, fontWeight = FontWeight.Bold, color = RojoInfrarrojo)
                                Spacer(Modifier.height(4.dp))
                                Text(ruta.riesgoHistorico, style = MaterialTheme.typography.bodySmall, color = AzulOscuroTexto)
                            }
                        }
                    }

                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = CelesteSuave),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("Protocolos de Seguridad Ciudadana", fontWeight = FontWeight.Bold, color = AzulOscuroTexto)
                                Text("• Huaicos y Torrenteras: Nunca cruces puentes inundados en Chullo, San Lazaro o Anashuayco.", style = MaterialTheme.typography.bodySmall)
                                Text("• Sismos en Arequipa: Dirigete a las 38 plazas, estadios o postas registradas en el mapa.", style = MaterialTheme.typography.bodySmall)
                                Text("• Sin Internet: Manten Bluetooth y Wi-Fi encendidos; tus alertas y chat saltan por la Red Mesh vecinal.", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }

    // DIALOGO PARA EMITIR ALERTA CON CONTEXTO CORTO
    if (mostrarDialogoEmitir) {
        AlertDialog(
            onDismissRequest = { mostrarDialogoEmitir = false },
            title = { Text("Emitir Alerta Comunitaria", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Se enviara tu ubicacion exacta en ${perfil.distrito} (${perfil.sectorReferencia}) a los vecinos de Arequipa.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("deslizamiento", "sismo", "incendio", "medica").forEach { cat ->
                            FilterChip(
                                selected = tipoAlertaEmitir == cat,
                                onClick = { tipoAlertaEmitir = cat },
                                label = { Text(cat, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = contextoAlertaEmitir,
                        onValueChange = { contextoAlertaEmitir = it },
                        label = { Text("Contexto breve del percance") },
                        placeholder = { Text("Ej. Inundacion en calle, vecino sin app atrapado") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val descFinal = contextoAlertaEmitir.ifBlank { "Emergencia verificada por sensor en ${perfil.distrito}" }
                        val evalLocal = aiEngine.clasificarReporteSinInternet(descFinal, tipoAlertaEmitir)
                        val req = AlertaRequest(
                            usuario_id = perfil.usuarioId,
                            nombre_usuario = perfil.nombreCompleto,
                            distrito = perfil.distrito,
                            sector_referencia = perfil.sectorReferencia,
                            latitud = miLat,
                            longitud = miLng,
                            tipo = evalLocal.tipoClasificado,
                            descripcion = descFinal,
                            nivel_bateria = locationManager.obtenerNivelBateria(),
                            riesgo_sensor_verificado = true
                        )
                        mostrarDialogoEmitir = false
                        contextoAlertaEmitir = ""

                        scope.launch {
                            try {
                                val resp = apiService.enviarAlerta(req)
                                bannerAviso = "Alerta publicada en Arequipa (${resp.nodo_guardado}). Vecinos notificados."
                                sincronizarConServidor()
                            } catch (_: Exception) {
                                dbLocal.alertaDao().insertarAlerta(
                                    AlertaLocalEntity(
                                        usuarioId = req.usuario_id,
                                        nombreUsuario = req.nombre_usuario,
                                        distrito = req.distrito,
                                        sectorReferencia = req.sector_referencia,
                                        latitud = req.latitud,
                                        longitud = req.longitud,
                                        tipo = req.tipo,
                                        descripcion = req.descripcion,
                                        nivelBateria = req.nivel_bateria
                                    )
                                )
                                meshManager.retransmitirAlerta(gson.toJson(req))
                                alertasActivas = listOf(
                                    PuntoAlertaMapa(
                                        alerta_id = "local_${System.currentTimeMillis()}",
                                        usuario_id = req.usuario_id,
                                        nombre_usuario = req.nombre_usuario,
                                        distrito = req.distrito,
                                        sector_referencia = req.sector_referencia,
                                        lat = req.latitud,
                                        lng = req.longitud,
                                        intensidad = 0.90,
                                        categoria = req.tipo,
                                        descripcion = req.descripcion,
                                        origen_offline_mesh = true
                                    )
                                ) + alertasActivas
                                bannerAviso = "Sin internet: Alerta transmitida por Red Mesh Bluetooth/Wi-Fi a vecinos cercanos."
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RojoInfrarrojo)
                ) {
                    Text("ENVIAR AHORA", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { mostrarDialogoEmitir = false }) { Text("Cancelar") }
            }
        )
    }

    // DIALOGO DE CHAT DIRECTO ENTRE AFECTADO Y VOLUNTARIO (Online + Red Mesh)
    if (alertaChatAbierto != null) {
        val alertaActual = alertaChatAbierto!!
        AlertDialog(
            onDismissRequest = { alertaChatAbierto = null },
            title = {
                Column {
                    Text("Chat de Coordinacion Directa", fontWeight = FontWeight.Bold)
                    Text(
                        "Con ${alertaActual.nombre_usuario} (${alertaActual.distrito})",
                        style = MaterialTheme.typography.bodySmall,
                        color = GrisTextoSecundario
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (mensajesChat.isEmpty()) {
                            item {
                                Text(
                                    "Escribe aqui si necesitas herramientas (pala, linterna, botiquin) o indicaciones exactas para llegar.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = GrisTextoSecundario
                                )
                            }
                        }
                        items(mensajesChat) { msg ->
                            val esMio = msg.remitente_id == perfil.usuarioId
                            Surface(
                                color = if (esMio) CelesteSuave else FondoHielo,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Text(
                                        text = "${msg.nombre_remitente} ${if (msg.origen_mesh) "[Mesh]" else ""}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = AzulPrincipal
                                    )
                                    Text(msg.mensaje, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedTextField(
                            value = textoChatInput,
                            onValueChange = { textoChatInput = it },
                            placeholder = { Text("Ej. Llevo pala y cuerda...") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        IconButton(
                            onClick = {
                                if (textoChatInput.isBlank()) return@IconButton
                                val nuevoMsg = MensajeChatDto(
                                    remitente_id = perfil.usuarioId,
                                    nombre_remitente = perfil.nombreCompleto,
                                    rol = if (alertaActual.usuario_id == perfil.usuarioId) "afectado" else "vecino_apoyo",
                                    mensaje = textoChatInput.trim()
                                )
                                textoChatInput = ""
                                mensajesChat = mensajesChat + nuevoMsg

                                scope.launch {
                                    dbLocal.alertaDao().insertarMensajeChat(
                                        MensajeChatLocalEntity(
                                            alertaId = alertaActual.alerta_id,
                                            remitenteId = nuevoMsg.remitente_id,
                                            nombreRemitente = nuevoMsg.nombre_remitente,
                                            rol = nuevoMsg.rol,
                                            mensaje = nuevoMsg.mensaje
                                        )
                                    )
                                    try {
                                        apiService.enviarMensajeChat(alertaActual.alerta_id, nuevoMsg)
                                    } catch (_: Exception) {
                                        meshManager.retransmitirPaquete(
                                            PaqueteMesh("CHAT_VECINAL", alertaActual.alerta_id, gson.toJson(nuevoMsg))
                                        )
                                    }
                                }
                            }
                        ) {
                            Icon(Icons.Default.Send, contentDescription = "Enviar", tint = AzulPrincipal)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { alertaChatAbierto = null }) { Text("Cerrar Chat") }
            }
        )
    }
}



