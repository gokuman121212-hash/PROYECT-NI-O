package com.cayma.alertasos.ui

import android.Manifest
import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.os.Bundle
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.Polyline

private val FondoCarbonSerio = Color(0xFF121417)
private val SuperficieCarbon = Color(0xFF1A1D22)
private val SuperficieElevadaCarbon = Color(0xFF23272E)
private val BordeGrafito = Color(0xFF2F3540)
private val TextoBlancoSerio = Color(0xFFF3F4F6)
private val TextoGrisSerio = Color(0xFF9CA3AF)
private val NaranjaFuegoSerio = Color(0xFFFF4500)
private val NaranjaSuaveSerio = Color(0xFFFF7A3D)
private val PlataAceroSerio = Color(0xFFD1D5DB)

private data class RegistroCatastrofeEvento(
    val id: String,
    val fechaHora: String,
    val tipoEvento: String,
    val queOcurrio: String,
    val dondeOcurrio: String,
    val coordenadas: String,
    val origen: String
)

private fun obtenerMarcaTiempoActual(): String {
    return SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date())
}

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
}

private fun crearFiltroAzulMarinoTech(): ColorMatrixColorFilter {
    val monoGrafito = ColorMatrix(
        floatArrayOf(
            -0.55f, 0f, 0f, 0f, 176f,
            0f, -0.55f, 0f, 0f, 180f,
            0f, 0f, -0.55f, 0f, 188f,
            0f, 0f, 0f, 1f, 0f
        )
    )
    val sat = ColorMatrix().apply { setSaturation(0.22f) }
    val contrast = 1.22f
    val translate = (-0.5f * contrast + 0.5f) * 255f
    val contrastMatrix = ColorMatrix(
        floatArrayOf(
            contrast, 0f, 0f, 0f, translate,
            0f, contrast, 0f, 0f, translate,
            0f, 0f, contrast, 0f, translate,
            0f, 0f, 0f, 1f, 0f
        )
    )
    monoGrafito.postConcat(sat)
    monoGrafito.postConcat(contrastMatrix)
    return ColorMatrixColorFilter(monoGrafito)
}

private fun crearIconoSeguridadDinamico(
    context: android.content.Context,
    tipoCentro: String,
    zoom: Double
): BitmapDrawable {
    val factor = ((zoom - 11.5) / 7.5).coerceIn(0.0, 1.0)
    val radioPx = (30f - (factor * 14f)).toFloat().coerceAtLeast(15f)
    val tamano = (radioPx * 2f + 16f).toInt().coerceAtLeast(38)
    val bitmap = Bitmap.createBitmap(tamano, tamano, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val centro = tamano / 2f

    val colorFondo = when (tipoCentro.lowercase()) {
        "hospital" -> AndroidColor.rgb(255, 69, 0)
        "comisaria" -> AndroidColor.rgb(209, 213, 219)
        else -> AndroidColor.rgb(156, 163, 175)
    }
    val letra = when (tipoCentro.lowercase()) {
        "hospital" -> "H"
        "comisaria" -> "P"
        else -> "S"
    }

    val paintGlow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.argb(95, AndroidColor.red(colorFondo), AndroidColor.green(colorFondo), AndroidColor.blue(colorFondo))
        style = Paint.Style.FILL
    }
    val paintCirculo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorFondo
        style = Paint.Style.FILL
    }
    val paintBorde = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 3.5f
    }
    val paintTexto = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.rgb(18, 20, 23)
        textSize = radioPx * 0.92f
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    canvas.drawCircle(centro, centro, radioPx + 5f, paintGlow)
    canvas.drawCircle(centro, centro, radioPx, paintCirculo)
    canvas.drawCircle(centro, centro, radioPx, paintBorde)
    val yTexto = centro - ((paintTexto.descent() + paintTexto.ascent()) / 2f)
    canvas.drawText(letra, centro, yTexto, paintTexto)

    return BitmapDrawable(context.resources, bitmap)
}

@Composable
private fun BotonIconoTranslúcidoReactivo(
    icono: ImageVector,
    descripcion: String,
    colorTacto: Color,
    enabled: Boolean = true,
    onClick: () -> Unit,
    contenidoExtra: (@Composable () -> Unit)? = null
) {
    val interaccion = remember { MutableInteractionSource() }
    val presionado by interaccion.collectIsPressedAsState()

    val fondoAnimado by animateColorAsState(
        targetValue = if (presionado) colorTacto.copy(alpha = 0.24f) else SuperficieElevadaCarbon.copy(alpha = 0.72f),
        animationSpec = tween(140),
        label = "fondo_boton_reactivo"
    )
    val bordeAnimado by animateColorAsState(
        targetValue = if (presionado) colorTacto.copy(alpha = 0.9f) else BordeGrafito,
        animationSpec = tween(140),
        label = "borde_boton_reactivo"
    )
    val iconoColorAnimado by animateColorAsState(
        targetValue = if (presionado) colorTacto else TextoBlancoSerio,
        animationSpec = tween(140),
        label = "icono_boton_reactivo"
    )

    Surface(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interaccion,
        color = fondoAnimado,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, bordeAnimado),
        modifier = Modifier.size(40.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (contenidoExtra != null) {
                contenidoExtra()
            } else {
                Icon(
                    imageVector = icono,
                    contentDescription = descripcion,
                    tint = iconoColorAnimado,
                    modifier = Modifier.size(20.dp)
                )
            }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 4.dp)
                    .size(if (presionado) 6.dp else 4.dp)
                    .background(
                        color = if (presionado) colorTacto else colorTacto.copy(alpha = 0.35f),
                        shape = CircleShape
                    )
            )
        }
    }
}

@Composable
private fun ItemApartadoAnimado(
    titulo: String,
    icono: ImageVector,
    seleccionado: Boolean,
    acentoColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interaccion = remember { MutableInteractionSource() }
    val presionado by interaccion.collectIsPressedAsState()
    val resplandor = seleccionado || presionado

    val saltoY by animateFloatAsState(
        targetValue = if (seleccionado) -6f else if (presionado) -3f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "salto_icono"
    )
    val escala by animateFloatAsState(
        targetValue = if (resplandor) 1.1f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "escala_icono"
    )
    val cajaFondo by animateColorAsState(
        targetValue = when {
            presionado -> acentoColor.copy(alpha = 0.25f)
            seleccionado -> acentoColor.copy(alpha = 0.14f)
            else -> Color.Transparent
        },
        animationSpec = tween(150),
        label = "caja_apartado"
    )
    val colorIcono by animateColorAsState(
        targetValue = if (resplandor) acentoColor else TextoGrisSerio,
        animationSpec = tween(150),
        label = "color_icono_apartado"
    )

    Column(
        modifier = modifier
            .clickable(
                interactionSource = interaccion,
                indication = null,
                onClick = onClick
            )
            .padding(vertical = 5.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .offset(y = saltoY.dp)
                .scale(escala)
                .background(color = cajaFondo, shape = RoundedCornerShape(14.dp))
                .padding(horizontal = 14.dp, vertical = 5.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icono,
                contentDescription = titulo,
                tint = colorIcono,
                modifier = Modifier.size(21.dp)
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = titulo,
            style = MaterialTheme.typography.labelSmall,
            color = if (resplandor) TextoBlancoSerio else TextoGrisSerio,
            maxLines = 1
        )
        Spacer(Modifier.height(2.dp))
        Box(
            modifier = Modifier
                .size(if (resplandor) 5.dp else 0.dp)
                .background(acentoColor, CircleShape)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("ClickableViewAccessibility")
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

    var pestanaActiva by remember { mutableIntStateOf(0) }
    var pestanaAnterior by remember { mutableIntStateOf(0) }
    val estadoSensor by sensoresManager.estadoRiesgo.collectAsState()
    var sincronizando by remember { mutableStateOf(false) }
    var filtroCapaMapa by remember { mutableStateOf("TODOS") }

    // Estado sincronizado desde el Panel Web de Administrador (sin controles admin en el celular)
    var registroCatastrofeActivo by remember { mutableStateOf(false) }
    var registrosCatastrofeAutomaticos by remember { mutableStateOf<List<RegistroCatastrofeEvento>>(emptyList()) }
    var idsAlertasRegistradasCatastrofe by remember { mutableStateOf(setOf<String>()) }
    var ultimoRiesgoSensorRegistrado by remember { mutableStateOf(false) }

    var alertasActivas by remember {
        mutableStateOf<List<PuntoAlertaMapa>>(emptyList())
    }

    var alertaEnApoyo by remember { mutableStateOf<PuntoAlertaMapa?>(null) }
    var alertaChatAbierto by remember { mutableStateOf<PuntoAlertaMapa?>(null) }
    var mensajesChat by remember { mutableStateOf<List<MensajeChatDto>>(emptyList()) }
    var textoChatInput by remember { mutableStateOf("") }

    var mostrarDialogoEmitir by remember { mutableStateOf(false) }
    var tipoAlertaEmitir by remember { mutableStateOf("deslizamiento") }
    var contextoAlertaEmitir by remember { mutableStateOf("") }
    var vecinosMeshCount by remember { mutableIntStateOf(0) }
    var bannerAviso by remember { mutableStateOf<String?>(null) }
    var mapViewRef by remember { mutableStateOf<MapView?>(null) }
    var ondaLat by remember { mutableStateOf<Double?>(null) }
    var ondaLng by remember { mutableStateOf<Double?>(null) }
    var ondaRadioMetros by remember { mutableDoubleStateOf(3000.0) }
    var ondaEtiqueta by remember { mutableStateOf("Epicentro Arequipa") }
    var historialRiesgosAbierto by remember { mutableStateOf(false) }
    var consultaIaTexto by remember { mutableStateOf("") }
    var respuestaIaTitulo by remember { mutableStateOf("ASISTENTE OFFLINE AREQUIPA") }
    var respuestaIaCuerpo by remember { mutableStateOf("Selecciona un escenario rápido o escribe tu consulta sobre sismos, torrenteras, incendios o red mesh.") }
    var respuestaIaMotor by remember { mutableStateOf("IA-Edge-Offline") }

    val miLatActual = if (ArequipaGeoData.estaEnArequipaMetropolitana(locationManager.ultimaLatitud, locationManager.ultimaLongitud) && locationManager.ultimaLatitud != 0.0) {
        locationManager.ultimaLatitud
    } else {
        perfil.latitud
    }
    val miLngActual = if (ArequipaGeoData.estaEnArequipaMetropolitana(locationManager.ultimaLatitud, locationManager.ultimaLongitud) && locationManager.ultimaLongitud != 0.0) {
        locationManager.ultimaLongitud
    } else {
        perfil.longitud
    }

    fun registrarEventoCatastrofeSiActivo(
        tipo: String,
        queOcurrio: String,
        dondeOcurrio: String,
        lat: Double,
        lng: Double,
        origen: String
    ) {
        if (!registroCatastrofeActivo) return
        val nuevo = RegistroCatastrofeEvento(
            id = "cat_${System.currentTimeMillis()}_${registrosCatastrofeAutomaticos.size + 1}",
            fechaHora = obtenerMarcaTiempoActual(),
            tipoEvento = tipo.uppercase(),
            queOcurrio = queOcurrio,
            dondeOcurrio = dondeOcurrio,
            coordenadas = "${String.format(Locale.US, "%.5f", lat)}, ${String.format(Locale.US, "%.5f", lng)}",
            origen = origen
        )
        registrosCatastrofeAutomaticos = listOf(nuevo) + registrosCatastrofeAutomaticos
    }

    LaunchedEffect(estadoSensor.riesgoRealDetectado, registroCatastrofeActivo) {
        if (registroCatastrofeActivo && estadoSensor.riesgoRealDetectado && !ultimoRiesgoSensorRegistrado) {
            registrarEventoCatastrofeSiActivo(
                tipo = if (estadoSensor.tipoRiesgoDetectado != "NINGUNO") estadoSensor.tipoRiesgoDetectado else "SISMO",
                queOcurrio = estadoSensor.detalleSensor,
                dondeOcurrio = "${perfil.distrito} — ${perfil.sectorReferencia}",
                lat = miLatActual,
                lng = miLngActual,
                origen = "SENSOR AUTOMATICO"
            )
        }
        ultimoRiesgoSensorRegistrado = estadoSensor.riesgoRealDetectado
    }

    LaunchedEffect(alertasActivas, registroCatastrofeActivo) {
        if (registroCatastrofeActivo) {
            val nuevas = alertasActivas.filter { it.alerta_id !in idsAlertasRegistradasCatastrofe }
            if (nuevas.isNotEmpty()) {
                nuevas.forEach { al ->
                    registrarEventoCatastrofeSiActivo(
                        tipo = al.categoria,
                        queOcurrio = "${al.nombre_usuario}: ${al.descripcion}",
                        dondeOcurrio = "${al.distrito}${if (al.sector_referencia.isNotBlank()) " — ${al.sector_referencia}" else ""}",
                        lat = al.lat,
                        lng = al.lng,
                        origen = if (al.origen_offline_mesh) "RED MESH" else "ALERTA VECINAL"
                    )
                }
                idsAlertasRegistradasCatastrofe = idsAlertasRegistradasCatastrofe + nuevas.map { it.alerta_id }
            }
        }
    }

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

    suspend fun sincronizarConServidor(mostrarMensajeManual: Boolean = false) {
        sincronizando = true
        var actualizadoRemoto = false
        try {
            val mapaResp = apiService.obtenerMapaCalor()
            alertasActivas = mapaResp.puntos.distinctBy { it.alerta_id }
            mapaResp.clima_y_torrenteras?.let { clima ->
                if (clima.riesgo_activo_detectado) {
                    sensoresManager.actualizarRiesgoClimaticoDesdeRed(
                        true,
                        "Lluvia ${clima.precipitacion_mm}mm - Torrenteras ${clima.alerta_torrenteras}"
                    )
                    if (!registroCatastrofeActivo) {
                        registroCatastrofeActivo = true
                    }
                }
            }
            actualizadoRemoto = true
        } catch (_: Exception) { }
        vecinosMeshCount = meshManager.cantidadVecinosConectados
        sincronizando = false
        if (mostrarMensajeManual) {
            bannerAviso = if (actualizadoRemoto) {
                "Sincronizado con el servidor ($vecinosMeshCount nodos mesh cercanos)."
            } else {
                "Modo sin internet activo: usando base local + Red Mesh ($vecinosMeshCount vecinos)."
            }
        }
    }

    LaunchedEffect(Unit) {
        launcherPermisos.launch(permisos)
        while (true) {
            if (!sincronizando) {
                sincronizarConServidor(mostrarMensajeManual = false)
            }
            delay(10_000)
        }
    }

    val miLat = miLatActual
    val miLng = miLngActual

    Scaffold(
        containerColor = FondoCarbonSerio,
        topBar = {
            Surface(
                color = SuperficieCarbon.copy(alpha = 0.88f),
                tonalElevation = 0.dp,
                shadowElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = TextoBlancoSerio,
                        actionIconContentColor = TextoBlancoSerio
                    ),
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(
                                        if (registroCatastrofeActivo) NaranjaFuegoSerio else NaranjaSuaveSerio,
                                        CircleShape
                                    )
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "ALERTA AREQUIPA",
                                style = MaterialTheme.typography.titleLarge,
                                color = TextoBlancoSerio
                            )
                        }
                    },
                    actions = {
                        BotonIconoTranslúcidoReactivo(
                            icono = Icons.Default.Refresh,
                            descripcion = "Actualizar",
                            colorTacto = PlataAceroSerio,
                            enabled = !sincronizando,
                            onClick = { scope.launch { sincronizarConServidor(mostrarMensajeManual = true) } },
                            contenidoExtra = if (sincronizando) {
                                {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = NaranjaFuegoSerio
                                    )
                                }
                            } else null
                        )
                        Spacer(Modifier.width(6.dp))
                        BotonIconoTranslúcidoReactivo(
                            icono = Icons.Default.ExitToApp,
                            descripcion = "Cerrar sesion",
                            colorTacto = NaranjaFuegoSerio,
                            onClick = onCerrarSesion
                        )
                        Spacer(Modifier.width(10.dp))
                    }
                )
            }
        },
        bottomBar = {
            Column {
                if (alertaEnApoyo != null) {
                    Surface(
                        color = NaranjaFuegoSerio.copy(alpha = 0.94f),
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
                                    style = MaterialTheme.typography.labelLarge,
                                    color = TextoBlancoSerio
                                )
                                Text(
                                    "Ruta segura hacia ${alertaEnApoyo!!.nombre_usuario} (${alertaEnApoyo!!.distrito})",
                                    color = TextoBlancoSerio.copy(alpha = 0.95f),
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
                                            } catch (_: Exception) {
                                                val locales = dbLocal.alertaDao().obtenerChatPorAlerta(alertaEnApoyo!!.alerta_id)
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
                                    colors = ButtonDefaults.buttonColors(containerColor = FondoCarbonSerio),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Default.Email, null, tint = TextoBlancoSerio, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Chat", style = MaterialTheme.typography.labelLarge, color = TextoBlancoSerio)
                                }
                                IconButton(onClick = { alertaEnApoyo = null }) {
                                    Icon(Icons.Default.Close, contentDescription = "Finalizar apoyo", tint = TextoBlancoSerio)
                                }
                            }
                        }
                    }
                }

                Surface(
                    color = SuperficieCarbon.copy(alpha = 0.88f),
                    tonalElevation = 0.dp,
                    shadowElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ItemApartadoAnimado(
                            titulo = "MAPA",
                            icono = Icons.Default.Place,
                            seleccionado = pestanaActiva == 0,
                            acentoColor = PlataAceroSerio,
                            onClick = {
                                pestanaAnterior = pestanaActiva
                                pestanaActiva = 0
                            },
                            modifier = Modifier.weight(1f)
                        )
                        ItemApartadoAnimado(
                            titulo = "ALERTAS (${alertasActivas.size})",
                            icono = Icons.Default.Warning,
                            seleccionado = pestanaActiva == 1,
                            acentoColor = NaranjaFuegoSerio,
                            onClick = {
                                pestanaAnterior = pestanaActiva
                                pestanaActiva = 1
                            },
                            modifier = Modifier.weight(1f)
                        )
                        ItemApartadoAnimado(
                            titulo = "RIESGOS",
                            icono = Icons.Default.Info,
                            seleccionado = pestanaActiva == 2,
                            acentoColor = if (registroCatastrofeActivo) NaranjaFuegoSerio else PlataAceroSerio,
                            onClick = {
                                pestanaAnterior = pestanaActiva
                                pestanaActiva = 2
                            },
                            modifier = Modifier.weight(1f)
                        )
                        ItemApartadoAnimado(
                            titulo = "IA",
                            icono = Icons.Default.Star,
                            seleccionado = pestanaActiva == 3,
                            acentoColor = Color(0xFF54ACBF),
                            onClick = {
                                pestanaAnterior = pestanaActiva
                                pestanaActiva = 3
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    ) { padding ->
        val direccionSalto = if (pestanaActiva >= pestanaAnterior) 1 else -1

        AnimatedContent(
            targetState = pestanaActiva,
            transitionSpec = {
                (slideInHorizontally(
                    animationSpec = tween(durationMillis = 280),
                    initialOffsetX = { fullWidth -> direccionSalto * (fullWidth / 3) }
                ) + fadeIn(animationSpec = tween(durationMillis = 220)))
                    .togetherWith(
                        slideOutHorizontally(
                            animationSpec = tween(durationMillis = 240),
                            targetOffsetX = { fullWidth -> -direccionSalto * (fullWidth / 4) }
                        ) + fadeOut(animationSpec = tween(durationMillis = 180))
                    )
            },
            label = "salto_apartado"
        ) { targetPestana ->
            when (targetPestana) {
                0 -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .background(FondoCarbonSerio)
                    ) {
                        AndroidView(
                            factory = { ctx ->
                                MapView(ctx).apply {
                                    setTileSource(TileSourceFactory.MAPNIK)
                                    setMultiTouchControls(true)
                                    isHorizontalMapRepetitionEnabled = false
                                    isVerticalMapRepetitionEnabled = false
                                    tilesScaleFactor = 1.65f
                                    minZoomLevel = 11.5
                                    maxZoomLevel = 19.0
                                    controller.setZoom(13.6)
                                    controller.setCenter(GeoPoint(miLat, miLng))

                                    overlayManager.tilesOverlay.setColorFilter(crearFiltroAzulMarinoTech())

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

                                if (filtroCapaMapa == "TODOS" || filtroCapaMapa == "BLOQUEOS") {
                                    ArequipaGeoData.RUTAS_BLOQUEADAS_HISTORICAS.forEach { bloqueo ->
                                        val haloBloqueo = Polyline().apply {
                                            addPoint(GeoPoint(bloqueo.latInicio, bloqueo.lngInicio))
                                            addPoint(GeoPoint(bloqueo.latFin, bloqueo.lngFin))
                                            outlinePaint.color = AndroidColor.argb(90, 255, 69, 0)
                                            outlinePaint.strokeWidth = 20f
                                        }
                                        val lineaBloqueo = Polyline().apply {
                                            addPoint(GeoPoint(bloqueo.latInicio, bloqueo.lngInicio))
                                            addPoint(GeoPoint(bloqueo.latFin, bloqueo.lngFin))
                                            outlinePaint.color = AndroidColor.rgb(255, 69, 0)
                                            outlinePaint.strokeWidth = 8f
                                            title = "VIA CON HISTORIAL DE BLOQUEO: ${bloqueo.nombre}"
                                        }
                                        mapView.overlays.add(haloBloqueo)
                                        mapView.overlays.add(lineaBloqueo)
                                    }
                                }

                                alertaEnApoyo?.let { destino ->
                                    val puntosRuta = ArequipaGeoData.calcularRutaLibreDeBloqueos(
                                        miLat, miLng, destino.lat, destino.lng
                                    )
                                    val glowRuta = Polyline().apply {
                                        puntosRuta.forEach { (lat, lng) -> addPoint(GeoPoint(lat, lng)) }
                                        outlinePaint.color = AndroidColor.argb(115, 255, 69, 0)
                                        outlinePaint.strokeWidth = 26f
                                    }
                                    val rutaFuego = Polyline().apply {
                                        puntosRuta.forEach { (lat, lng) -> addPoint(GeoPoint(lat, lng)) }
                                        outlinePaint.color = AndroidColor.rgb(255, 69, 0)
                                        outlinePaint.strokeWidth = 11f
                                        title = "Ruta recomendada libre hacia ${destino.nombre_usuario}"
                                    }
                                    mapView.overlays.add(glowRuta)
                                    mapView.overlays.add(rutaFuego)
                                }

                                if (filtroCapaMapa == "TODOS" || filtroCapaMapa == "SOS") {
                                    alertasActivas.forEach { alerta ->
                                        val centro = GeoPoint(alerta.lat, alerta.lng)

                                        val haloExterno = Polygon().apply {
                                            points = Polygon.pointsAsCircle(centro, 430.0)
                                            fillPaint.color = AndroidColor.argb(65, 255, 69, 0)
                                            outlinePaint.color = AndroidColor.argb(190, 255, 69, 0)
                                            outlinePaint.strokeWidth = 3f
                                        }
                                        val nucleoCalor = Polygon().apply {
                                            points = Polygon.pointsAsCircle(centro, 155.0)
                                            fillPaint.color = AndroidColor.argb(150, 255, 69, 0)
                                            outlinePaint.color = AndroidColor.rgb(255, 122, 61)
                                            outlinePaint.strokeWidth = 4f
                                        }
                                        val puntoCentralSos = Polygon().apply {
                                            points = Polygon.pointsAsCircle(centro, 30.0)
                                            fillPaint.color = AndroidColor.rgb(255, 69, 0)
                                            outlinePaint.color = AndroidColor.WHITE
                                            outlinePaint.strokeWidth = 4f
                                        }
                                        mapView.overlays.add(haloExterno)
                                        mapView.overlays.add(nucleoCalor)
                                        mapView.overlays.add(puntoCentralSos)
                                    }
                                }

                                val zoomActual = mapView.zoomLevelDouble
                                ArequipaGeoData.CENTROS_SEGURIDAD
                                    .filter { centro ->
                                        when (filtroCapaMapa) {
                                            "TODOS" -> true
                                            "HOSPITAL" -> centro.tipo.equals("hospital", ignoreCase = true)
                                            "COMISARIA" -> centro.tipo.equals("comisaria", ignoreCase = true)
                                            "REFUGIO" -> !centro.tipo.equals("hospital", ignoreCase = true) && !centro.tipo.equals("comisaria", ignoreCase = true)
                                            else -> false
                                        }
                                    }
                                    .forEach { centro ->
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

                                val miCentro = GeoPoint(miLat, miLng)
                                val auraGps = Polygon().apply {
                                    points = Polygon.pointsAsCircle(miCentro, 100.0)
                                    fillPaint.color = AndroidColor.argb(75, 229, 231, 235)
                                    outlinePaint.color = AndroidColor.argb(210, 243, 244, 246)
                                    outlinePaint.strokeWidth = 3f
                                }
                                val puntoGps = Polygon().apply {
                                    points = Polygon.pointsAsCircle(miCentro, 26.0)
                                    fillPaint.color = AndroidColor.rgb(243, 244, 246)
                                    outlinePaint.color = AndroidColor.rgb(255, 69, 0)
                                    outlinePaint.strokeWidth = 5f
                                }
                                mapView.overlays.add(auraGps)
                                mapView.overlays.add(puntoGps)

                                if (ondaLat != null && ondaLng != null) {
                    val cLat = ondaLat!!
                    val cLng = ondaLng!!
                    val latDelta = (ondaRadioMetros / 111320.0)
                    val lngDelta = (ondaRadioMetros / (111320.0 * kotlin.math.cos(Math.toRadians(cLat))))
                    val ptsOnda = (0..36).map { idx ->
                        val ang = (idx * 10.0) * Math.PI / 180.0
                        GeoPoint(cLat + latDelta * kotlin.math.sin(ang), cLng + lngDelta * kotlin.math.cos(ang))
                    }
                    val poligonoOnda = Polygon(mapView).apply {
                        points = ptsOnda
                        fillColor = android.graphics.Color.argb(46, 84, 172, 191)
                        strokeColor = android.graphics.Color.rgb(167, 235, 242)
                        strokeWidth = 4f
                        title = "$ondaEtiqueta (${String.format("%.1f", ondaRadioMetros / 1000.0)} km)"
                    }
                    mapView.overlays.add(poligonoOnda)
                }
                mapView.invalidate()
                            },
                            modifier = Modifier.fillMaxSize()
                        )

                        Row(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val capas = listOf(
                                Triple("TODOS", "Todo", PlataAceroSerio),
                                Triple("SOS", "Alertas SOS", NaranjaFuegoSerio),
                                Triple("HOSPITAL", "Hospitales (H)", NaranjaFuegoSerio),
                                Triple("COMISARIA", "Comisarias (P)", PlataAceroSerio),
                                Triple("REFUGIO", "Zonas Seguras (S)", TextoGrisSerio)
                            )
                            capas.forEach { (clave, etiqueta, colorActivo) ->
                                val seleccionado = filtroCapaMapa == clave
                                FilterChip(
                                    selected = seleccionado,
                                    onClick = { filtroCapaMapa = clave },
                                    label = {
                                        Text(
                                            etiqueta,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (seleccionado) FondoCarbonSerio else TextoBlancoSerio
                                        )
                                    },
                                    border = BorderStroke(1.dp, if (seleccionado) colorActivo else BordeGrafito),
                                    colors = FilterChipDefaults.filterChipColors(
                                        containerColor = SuperficieCarbon.copy(alpha = 0.88f),
                                        selectedContainerColor = colorActivo
                                    )
                                )
                            }
                        }

                        Surface(
                            color = SuperficieCarbon.copy(alpha = 0.90f),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, BordeGrafito),
                            shadowElevation = 4.dp,
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(14.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text("HUD AREQUIPA • SERIE", style = MaterialTheme.typography.labelSmall, color = NaranjaSuaveSerio)
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Box(Modifier.size(10.dp).background(NaranjaFuegoSerio, CircleShape))
                                    Text("H: Hospital / SOS", style = MaterialTheme.typography.bodySmall, color = TextoBlancoSerio)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Box(Modifier.size(10.dp).background(PlataAceroSerio, CircleShape))
                                    Text("P: Comisaria / Tu GPS", style = MaterialTheme.typography.bodySmall, color = TextoBlancoSerio)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Box(Modifier.size(10.dp).background(TextoGrisSerio, CircleShape))
                                    Text("S: Zona Segura", style = MaterialTheme.typography.bodySmall, color = TextoBlancoSerio)
                                }
                            }
                        }

                        FloatingActionButton(
                            onClick = {
                                mapViewRef?.controller?.animateTo(GeoPoint(miLat, miLng))
                                mapViewRef?.controller?.setZoom(15.5)
                            },
                            containerColor = NaranjaFuegoSerio,
                            contentColor = TextoBlancoSerio,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(16.dp)
                        ) {
                            Icon(Icons.Default.LocationOn, contentDescription = "Centrar en mi ubicacion")
                        }
                    }
                }

                1 -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(FondoCarbonSerio)
                            .padding(padding)
                            .padding(horizontal = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item { Spacer(Modifier.height(6.dp)) }

                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Surface(
                                    color = SuperficieCarbon,
                                    border = BorderStroke(1.dp, NaranjaFuegoSerio.copy(alpha = 0.55f)),
                                    shape = RoundedCornerShape(18.dp),
                                    shadowElevation = 4.dp,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(13.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "EMERGENCIAS",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = NaranjaSuaveSerio
                                            )
                                            Box(
                                                modifier = Modifier
                                                    .size(8.dp)
                                                    .background(NaranjaFuegoSerio, CircleShape)
                                            )
                                        }
                                        Text(
                                            text = "${alertasActivas.size}",
                                            style = MaterialTheme.typography.headlineSmall,
                                            color = TextoBlancoSerio
                                        )
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(5.dp)
                                                .background(NaranjaFuegoSerio.copy(alpha = 0.22f), CircleShape)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth(if (alertasActivas.isNotEmpty()) 0.78f else 0.15f)
                                                    .fillMaxHeight()
                                                    .background(NaranjaFuegoSerio, CircleShape)
                                            )
                                        }
                                        Text(
                                            text = "Activas en Arequipa",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = TextoGrisSerio
                                        )
                                    }
                                }

                                Surface(
                                    color = SuperficieCarbon,
                                    border = BorderStroke(1.dp, BordeGrafito),
                                    shape = RoundedCornerShape(18.dp),
                                    shadowElevation = 4.dp,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(13.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "RED MESH P2P",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = PlataAceroSerio
                                            )
                                            Box(
                                                modifier = Modifier
                                                    .size(8.dp)
                                                    .background(PlataAceroSerio, CircleShape)
                                            )
                                        }
                                        Text(
                                            text = "$vecinosMeshCount",
                                            style = MaterialTheme.typography.headlineSmall,
                                            color = TextoBlancoSerio
                                        )
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(5.dp)
                                                .background(PlataAceroSerio.copy(alpha = 0.2f), CircleShape)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth(if (vecinosMeshCount > 0) 0.72f else 0.25f)
                                                    .fillMaxHeight()
                                                    .background(PlataAceroSerio, CircleShape)
                                            )
                                        }
                                        Text(
                                            text = "Vecinos cerca sin red",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = TextoGrisSerio
                                        )
                                    }
                                }
                            }
                        }

                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = SuperficieCarbon),
                                border = BorderStroke(
                                    1.2.dp,
                                    if (estadoSensor.riesgoRealDetectado) NaranjaFuegoSerio else BordeGrafito
                                ),
                                shape = RoundedCornerShape(18.dp),
                                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(34.dp)
                                                .background(
                                                    if (estadoSensor.riesgoRealDetectado) NaranjaFuegoSerio.copy(alpha = 0.22f) else SuperficieElevadaCarbon,
                                                    RoundedCornerShape(10.dp)
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = if (estadoSensor.riesgoRealDetectado) Icons.Default.Warning else Icons.Default.Notifications,
                                                contentDescription = null,
                                                tint = if (estadoSensor.riesgoRealDetectado) NaranjaFuegoSerio else PlataAceroSerio,
                                                modifier = Modifier.size(19.dp)
                                            )
                                        }
                                        Spacer(Modifier.width(10.dp))
                                        Text(
                                            text = estadoSensor.detalleSensor,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Bold,
                                            color = TextoBlancoSerio,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Button(
                                            onClick = {
                                                tipoAlertaEmitir = if (estadoSensor.tipoRiesgoDetectado != "NINGUNO") estadoSensor.tipoRiesgoDetectado else "deslizamiento"
                                                mostrarDialogoEmitir = true
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = NaranjaFuegoSerio),
                                            border = BorderStroke(1.dp, NaranjaSuaveSerio.copy(alpha = 0.6f)),
                                            shape = RoundedCornerShape(14.dp),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                                            modifier = Modifier.weight(1.25f)
                                        ) {
                                            Icon(Icons.Default.Warning, null, tint = TextoBlancoSerio, modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text(
                                                "EMITIR SOS",
                                                style = MaterialTheme.typography.labelLarge,
                                                color = TextoBlancoSerio,
                                                maxLines = 1
                                            )
                                        }

                                        Button(
                                            onClick = { sensoresManager.activarConfirmacionSensorPrueba("sismo") },
                                            colors = ButtonDefaults.buttonColors(containerColor = SuperficieElevadaCarbon),
                                            border = BorderStroke(1.dp, BordeGrafito),
                                            shape = RoundedCornerShape(14.dp),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 12.dp),
                                            modifier = Modifier.weight(0.95f)
                                        ) {
                                            Text(
                                                "Probar Sensor",
                                                style = MaterialTheme.typography.labelLarge,
                                                color = PlataAceroSerio,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (bannerAviso != null) {
                            item {
                                Surface(
                                    color = SuperficieElevadaCarbon,
                                    border = BorderStroke(1.dp, NaranjaFuegoSerio.copy(alpha = 0.65f)),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = bannerAviso!!,
                                            color = TextoBlancoSerio,
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.bodySmall,
                                            modifier = Modifier.weight(1f)
                                        )
                                        IconButton(
                                            onClick = { bannerAviso = null },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(Icons.Default.Close, contentDescription = "Ocultar aviso", tint = PlataAceroSerio, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            }
                        }

                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "ALERTAS VECINALES ACTIVAS",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = TextoGrisSerio
                                )
                                Text(
                                    "${alertasActivas.size} REGISTRO(S)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = NaranjaSuaveSerio
                                )
                            }
                        }

                        items(alertasActivas) { alerta ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = SuperficieCarbon),
                                border = BorderStroke(1.dp, BordeGrafito),
                                shape = RoundedCornerShape(18.dp),
                                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(15.dp),
                                    verticalArrangement = Arrangement.spacedBy(9.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Surface(
                                            color = NaranjaFuegoSerio.copy(alpha = 0.18f),
                                            border = BorderStroke(1.dp, NaranjaFuegoSerio.copy(alpha = 0.7f)),
                                            shape = RoundedCornerShape(9.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(6.dp)
                                                        .background(NaranjaFuegoSerio, CircleShape)
                                                )
                                                Spacer(Modifier.width(6.dp))
                                                Text(
                                                    text = alerta.categoria.uppercase(),
                                                    color = NaranjaSuaveSerio,
                                                    style = MaterialTheme.typography.labelSmall
                                                )
                                            }
                                        }

                                        Surface(
                                            color = SuperficieElevadaCarbon,
                                            border = BorderStroke(1.dp, BordeGrafito),
                                            shape = RoundedCornerShape(9.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(Icons.Default.Person, null, tint = PlataAceroSerio, modifier = Modifier.size(15.dp))
                                                Spacer(Modifier.width(4.dp))
                                                Text(
                                                    text = "${alerta.voluntarios_count} en apoyo",
                                                    color = TextoBlancoSerio,
                                                    style = MaterialTheme.typography.labelSmall
                                                )
                                            }
                                        }
                                    }

                                    Text(
                                        text = "${alerta.nombre_usuario} — ${alerta.distrito}",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = TextoBlancoSerio
                                    )
                                    if (alerta.sector_referencia.isNotBlank()) {
                                        Text(
                                            text = "Ubicacion: ${alerta.sector_referencia}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = PlataAceroSerio,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Text(
                                        text = alerta.descripcion,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = TextoGrisSerio
                                    )

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Button(
                                            onClick = {
                                                alertaEnApoyo = alerta
                                                alertasActivas = alertasActivas.map {
                                                    if (it.alerta_id == alerta.alerta_id) it.copy(voluntarios_count = it.voluntarios_count + 1) else it
                                                }
                                                pestanaAnterior = pestanaActiva
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
                                            colors = ButtonDefaults.buttonColors(containerColor = NaranjaFuegoSerio),
                                            border = BorderStroke(1.dp, NaranjaSuaveSerio.copy(alpha = 0.65f)),
                                            shape = RoundedCornerShape(14.dp),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 11.dp),
                                            modifier = Modifier.weight(1.25f)
                                        ) {
                                            Icon(Icons.Default.ArrowForward, null, tint = TextoBlancoSerio, modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text("Ir en Apoyo", style = MaterialTheme.typography.labelLarge, color = TextoBlancoSerio, maxLines = 1)
                                        }

                                        Button(
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
                                            colors = ButtonDefaults.buttonColors(containerColor = SuperficieElevadaCarbon),
                                            border = BorderStroke(1.dp, BordeGrafito),
                                            shape = RoundedCornerShape(14.dp),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 11.dp),
                                            modifier = Modifier.weight(0.85f)
                                        ) {
                                            Icon(Icons.Default.Email, null, tint = PlataAceroSerio, modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.width(5.dp))
                                            Text("Chat", style = MaterialTheme.typography.labelLarge, color = TextoBlancoSerio, maxLines = 1)
                                        }
                                    }
                                }
                            }
                        }

                        item { Spacer(Modifier.height(24.dp)) }
                    }
                }

                3 -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .background(FondoCarbonSerio),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = SuperficieCarbon),
                                border = BorderStroke(1.dp, Color(0xFF54ACBF).copy(alpha = 0.5f)),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text(
                                        "SOE · IA TÁCTICA AREQUIPA",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = Color(0xFF54ACBF)
                                    )
                                    Text(
                                        "SOE · Asistente de Riesgos y Protocolos",
                                        style = MaterialTheme.typography.titleLarge,
                                        color = Color(0xFFA7EBF2)
                                    )
                                    Text(
                                        "Alertas activas: ${alertasActivas.size} - Distrito: ${perfil.distrito} - Motor: $respuestaIaMotor",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextoGrisSerio
                                    )

                                    fun ejecutarConsultaIa(prompt: String, tipoSugerido: String) {
                                        val textoEval = prompt.ifBlank { "protocolo preventivo en arequipa" }
                                        val res = aiEngine.clasificarReporteSinInternet(textoEval, tipoSugerido)
                                        respuestaIaMotor = res.motorUsado
                                        val lower = textoEval.lowercase()
                                        when {
                                            res.tipoClasificado == "sismo" || lower.contains("sismo") || lower.contains("temblor") -> {
                                                respuestaIaTitulo = "PROTOCOLO SISMICO (AREQUIPA)"
                                                respuestaIaCuerpo = "Alejate de vidrios, estanterias o cables durante el movimiento. Tras detenerse, evacua a zonas abiertas y verifica posibles replicas o cortes viales."
                                                ondaLat = miLat
                                                ondaLng = miLng
                                                ondaRadioMetros = 5200.0
                                                ondaEtiqueta = "Onda Sismica Estimada"
                                            }
                                            res.tipoClasificado == "deslizamiento" || lower.contains("huaico") || lower.contains("torrentera") -> {
                                                respuestaIaTitulo = "ALERTA TORRENTERAS / LADERAS"
                                                respuestaIaCuerpo = "No cruces cauces, badenes ni puentes con caudal activo en torrenteras o laderas. Desplazate a zonas altas y reporta bloqueos en la red."
                                                ondaLat = miLat
                                                ondaLng = miLng
                                                ondaRadioMetros = 3200.0
                                                ondaEtiqueta = "Zona de Riesgo Hidrometeorologico"
                                            }
                                            res.tipoClasificado == "incendio" || lower.contains("incendio") || lower.contains("fuego") -> {
                                                respuestaIaTitulo = "RESPUESTA ANTE INCENDIO"
                                                respuestaIaCuerpo = "Evacua en sentido opuesto al humo, mantente bajo si hay humo denso y no uses ascensores ni obstruyas el paso de unidades."
                                                ondaLat = miLat
                                                ondaLng = miLng
                                                ondaRadioMetros = 1800.0
                                                ondaEtiqueta = "Perimetro Preventivo Incendio"
                                            }
                                            else -> {
                                                respuestaIaTitulo = "EVALUACION LOCAL (${res.tipoClasificado.uppercase()})"
                                                respuestaIaCuerpo = "Confianza offline: ${String.format("%.0f", res.confianza * 100)}%. Manten GPS, Bluetooth y Wi-Fi activos para la Red Mesh vecinal y revisa las alertas en curso."
                                            }
                                        }
                                    }

                                    Row(
                                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        FilterChip(
                                            selected = false,
                                            onClick = { ejecutarConsultaIa("sismo fuerte en arequipa", "sismo") },
                                            label = { Text("Sismo", color = Color(0xFFA7EBF2)) }
                                        )
                                        FilterChip(
                                            selected = false,
                                            onClick = { ejecutarConsultaIa("huaico y torrentera activa", "deslizamiento") },
                                            label = { Text("Torrentera / Huaico", color = Color(0xFFA7EBF2)) }
                                        )
                                        FilterChip(
                                            selected = false,
                                            onClick = { ejecutarConsultaIa("incendio en zona urbana", "incendio") },
                                            label = { Text("Incendio", color = Color(0xFFA7EBF2)) }
                                        )
                                        FilterChip(
                                            selected = false,
                                            onClick = { ejecutarConsultaIa("estado red mesh sin internet", "seguridad") },
                                            label = { Text("Red Mesh", color = Color(0xFFA7EBF2)) }
                                        )
                                    }

                                    OutlinedTextField(
                                        value = consultaIaTexto,
                                        onValueChange = { consultaIaTexto = it },
                                        placeholder = { Text("Escribe una consulta o situacion...", color = TextoGrisSerio) },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true
                                    )

                                    Button(
                                        onClick = { ejecutarConsultaIa(consultaIaTexto, "seguridad") },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("Consultar a SOE")
                                    }
                                }
                            }
                        }

                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = SuperficieElevadaCarbon),
                                border = BorderStroke(1.dp, Color(0xFF54ACBF).copy(alpha = 0.45f)),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(respuestaIaTitulo, style = MaterialTheme.typography.titleMedium, color = Color(0xFFA7EBF2))
                                    Text(respuestaIaCuerpo, style = MaterialTheme.typography.bodyMedium, color = TextoBlancoSerio)
                                }
                            }
                        }

                        item { Spacer(Modifier.height(24.dp)) }
                    }
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(FondoCarbonSerio)
                            .padding(padding)
                            .padding(horizontal = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item { Spacer(Modifier.height(6.dp)) }

                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = SuperficieCarbon),
                                border = BorderStroke(
                                    1.2.dp,
                                    if (registroCatastrofeActivo) NaranjaFuegoSerio else BordeGrafito
                                ),
                                shape = RoundedCornerShape(18.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(9.dp)
                                                .background(
                                                    if (registroCatastrofeActivo) NaranjaFuegoSerio else TextoGrisSerio,
                                                    CircleShape
                                                )
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            text = if (registroCatastrofeActivo) {
                                                "BITACORA CATASTROFICA ACTIVA (DESDE ADMIN WEB)"
                                            } else {
                                                "BITACORA CATASTROFICA EN ESPERA"
                                            },
                                            style = MaterialTheme.typography.labelLarge,
                                            color = if (registroCatastrofeActivo) NaranjaSuaveSerio else PlataAceroSerio
                                        )
                                    }

                                    Text(
                                        text = if (registroCatastrofeActivo) {
                                            "Registrando automaticamente fecha, hora, evento, ubicacion y coordenadas activados por la consola central."
                                        } else {
                                            "Cuando la consola central web active el modo catastrofe o terremoto, aqui apareceran automaticamente todos los registros."
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextoGrisSerio
                                    )
                                }
                            }
                        }

                        if (registrosCatastrofeAutomaticos.isNotEmpty()) {
                            item {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "EVENTOS DE CATASTROFE REGISTRADOS",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = TextoBlancoSerio
                                    )
                                    Text(
                                        "${registrosCatastrofeAutomaticos.size} EVENTO(S)",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = NaranjaSuaveSerio
                                    )
                                }
                            }

                            items(registrosCatastrofeAutomaticos) { ev ->
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = SuperficieCarbon),
                                    border = BorderStroke(1.dp, NaranjaFuegoSerio.copy(alpha = 0.65f)),
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Surface(
                                                color = NaranjaFuegoSerio.copy(alpha = 0.2f),
                                                border = BorderStroke(1.dp, NaranjaFuegoSerio.copy(alpha = 0.75f)),
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Text(
                                                    text = ev.tipoEvento,
                                                    color = NaranjaSuaveSerio,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
                                                )
                                            }
                                            Text(
                                                text = ev.fechaHora,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = PlataAceroSerio
                                            )
                                        }

                                        Text(
                                            text = "Que ocurrio: ${ev.queOcurrio}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = TextoBlancoSerio
                                        )
                                        Text(
                                            text = "Donde ocurrio: ${ev.dondeOcurrio}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = PlataAceroSerio
                                        )
                                        Text(
                                            text = "Coordenadas: ${ev.coordenadas} • Fuente: ${ev.origen}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = TextoGrisSerio
                                        )
                                    }
                                }
                            }
                        }

                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = SuperficieCarbon),
                                border = BorderStroke(1.dp, Color(0xFF54ACBF).copy(alpha = 0.45f)),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { historialRiesgosAbierto = !historialRiesgosAbierto }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            "Analisis de Riesgos e Historial de Bloqueos (Arequipa)",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = Color(0xFFA7EBF2)
                                        )
                                        Text(
                                            if (historialRiesgosAbierto) "Toca para contraer historial" else "Desplegable: toca para ver registros históricos",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = TextoGrisSerio
                                        )
                                    }
                                    Text(
                                        if (historialRiesgosAbierto) "▲" else "▼",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = Color(0xFF54ACBF)
                                    )
                                }
                            }
                        }

                        if (historialRiesgosAbierto) {
                            items(ArequipaGeoData.RUTAS_BLOQUEADAS_HISTORICAS) { ruta ->
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = SuperficieCarbon),
                                    border = BorderStroke(1.dp, BordeGrafito),
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            ondaLat = miLat
                                            ondaLng = miLng
                                            ondaRadioMetros = 3500.0
                                            ondaEtiqueta = ruta.nombre
                                            pestanaAnterior = pestanaActiva
                                            pestanaActiva = 0
                                            mapViewRef?.controller?.animateTo(GeoPoint(miLat, miLng))
                                        }
                                ) {
                                    Column(modifier = Modifier.padding(14.dp)) {
                                        Text(ruta.nombre, style = MaterialTheme.typography.titleMedium, color = Color(0xFFA7EBF2))
                                        Spacer(Modifier.height(4.dp))
                                        Text(ruta.riesgoHistorico, style = MaterialTheme.typography.bodySmall, color = TextoGrisSerio)
                                    }
                                }
                            }
                        }

                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = SuperficieElevadaCarbon),
                                border = BorderStroke(1.dp, BordeGrafito),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("Protocolos de Seguridad Ciudadana", style = MaterialTheme.typography.titleMedium, color = TextoBlancoSerio)
                                    Text("• Huaicos y Torrenteras: Nunca cruces puentes inundados en Chullo, San Lazaro o Anashuayco.", style = MaterialTheme.typography.bodySmall, color = TextoGrisSerio)
                                    Text("• Sismos en Arequipa: Dirigete a las 38 plazas, estadios o postas registradas en el mapa.", style = MaterialTheme.typography.bodySmall, color = TextoGrisSerio)
                                    Text("• Sin Internet: Manten Bluetooth y Wi-Fi encendidos; tus alertas y chat saltan por la Red Mesh vecinal.", style = MaterialTheme.typography.bodySmall, color = TextoGrisSerio)
                                }
                            }
                        }

                        item { Spacer(Modifier.height(24.dp)) }
                    }
                }
            }
        }
    }

    if (mostrarDialogoEmitir) {
        AlertDialog(
            onDismissRequest = { mostrarDialogoEmitir = false },
            containerColor = SuperficieCarbon,
            titleContentColor = TextoBlancoSerio,
            textContentColor = TextoGrisSerio,
            title = { Text("Emitir Alerta Comunitaria", style = MaterialTheme.typography.titleLarge, color = TextoBlancoSerio) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Se enviara tu ubicacion exacta en ${perfil.distrito} (${perfil.sectorReferencia}) a los vecinos de Arequipa.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextoGrisSerio
                    )
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("deslizamiento", "sismo", "incendio", "medica").forEach { cat ->
                            FilterChip(
                                selected = tipoAlertaEmitir == cat,
                                onClick = { tipoAlertaEmitir = cat },
                                label = {
                                    Text(
                                        cat,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (tipoAlertaEmitir == cat) TextoBlancoSerio else PlataAceroSerio
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = SuperficieElevadaCarbon,
                                    selectedContainerColor = NaranjaFuegoSerio
                                )
                            )
                        }
                    }
                    OutlinedTextField(
                        value = contextoAlertaEmitir,
                        onValueChange = { contextoAlertaEmitir = it },
                        singleLine = false,
                        maxLines = 3,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = TextoBlancoSerio),
                        label = { Text("Contexto breve del percance", style = MaterialTheme.typography.bodySmall, color = PlataAceroSerio) },
                        placeholder = { Text("Ej. Inundacion en calle, vecino sin app atrapado", style = MaterialTheme.typography.bodySmall, color = PlataAceroSerio) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextoBlancoSerio,
                            unfocusedTextColor = TextoBlancoSerio,
                            cursorColor = NaranjaFuegoSerio,
                            focusedContainerColor = FondoOscuroSerio,
                            unfocusedContainerColor = FondoOscuroSerio,
                            focusedBorderColor = NaranjaFuegoSerio,
                            unfocusedBorderColor = PlataAceroSerio.copy(alpha = 0.55f),
                            focusedLabelColor = NaranjaFuegoSerio,
                            unfocusedLabelColor = PlataAceroSerio
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
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
                    colors = ButtonDefaults.buttonColors(containerColor = NaranjaFuegoSerio)
                ) {
                    Text("ENVIAR AHORA", style = MaterialTheme.typography.labelLarge, color = TextoBlancoSerio)
                }
            },
            dismissButton = {
                TextButton(onClick = { mostrarDialogoEmitir = false }) {
                    Text("Cancelar", style = MaterialTheme.typography.labelLarge, color = PlataAceroSerio)
                }
            }
        )
    }

    if (alertaChatAbierto != null) {
        val alertaActual = alertaChatAbierto!!
        AlertDialog(
            onDismissRequest = { alertaChatAbierto = null },
            containerColor = SuperficieCarbon,
            titleContentColor = TextoBlancoSerio,
            textContentColor = TextoGrisSerio,
            title = {
                Column {
                    Text("Chat de Coordinacion Directa", style = MaterialTheme.typography.titleLarge, color = TextoBlancoSerio)
                    Text(
                        "Con ${alertaActual.nombre_usuario} (${alertaActual.distrito})",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextoGrisSerio
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
                                    color = TextoGrisSerio
                                )
                            }
                        }
                        items(mensajesChat) { msg ->
                            val esMio = msg.remitente_id == perfil.usuarioId
                            Surface(
                                color = if (esMio) NaranjaFuegoSerio.copy(alpha = 0.22f) else SuperficieElevadaCarbon,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Text(
                                        text = "${msg.nombre_remitente} ${if (msg.origen_mesh) "[Mesh]" else ""}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (esMio) NaranjaSuaveSerio else PlataAceroSerio
                                    )
                                    Text(msg.mensaje, style = MaterialTheme.typography.bodySmall, color = TextoBlancoSerio)
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
                            placeholder = { Text("Ej. Llevo pala y cuerda...", style = MaterialTheme.typography.bodySmall, color = TextoGrisSerio) },
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
                            Icon(Icons.Default.Send, contentDescription = "Enviar", tint = NaranjaFuegoSerio)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { alertaChatAbierto = null }) {
                    Text("Cerrar Chat", style = MaterialTheme.typography.labelLarge, color = PlataAceroSerio)
                }
            }
        )
    }
}
