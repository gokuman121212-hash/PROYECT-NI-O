package pe.gob.alertacayma.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class MainActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var etServerUrl: EditText
    private lateinit var spTipoEmergencia: Spinner
    private lateinit var etDescripcion: EditText
    private lateinit var tvStatusConexion: TextView
    private lateinit var tvGpsInfo: TextView
    private lateinit var tvSismografo: TextView
    private lateinit var tvZonaSegura: TextView
    private lateinit var tvMeshCola: TextView
    private lateinit var tvLogRespuesta: TextView

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private lateinit var meshQueue: OfflineMeshQueue

    private var ultimaLat: Double = -16.3425 // Coordenada por defecto: Plaza de Cayma
    private var ultimaLng: Double = -71.5467
    private var ultimoDisparoSismoMs: Long = 0L

    private val client = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .build()

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    data class ZonaSegura(val nombre: String, val lat: Double, val lng: Double)

    private val zonasSegurasCayma = listOf(
        ZonaSegura("Plaza Principal de Cayma", -16.3425, -71.5467),
        ZonaSegura("Estadio Municipal La Tomilla", -16.3532, -71.5431),
        ZonaSegura("Complejo Deportivo Buenos Aires", -16.3318, -71.5520),
        ZonaSegura("Parque Francisco Bolognesi (Cayma)", -16.3610, -71.5485)
    )

    private val tiposApi = listOf("sismo", "deslizamiento", "incendio", "medica", "seguridad", "sos_critico")
    private val tiposLabels = listOf(
        "SISMO / TERREMOTO",
        "HUAICO / TORRENTERA",
        "INCENDIO ESTRUCTURAL",
        "EMERGENCIA MÉDICA",
        "SEGURIDAD / RESCATE",
        "SOS CRÍTICO INMEDIATO"
    )

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        actualizarUbicacionGps()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        etServerUrl = findViewById(R.id.etServerUrl)
        spTipoEmergencia = findViewById(R.id.spTipoEmergencia)
        etDescripcion = findViewById(R.id.etDescripcion)
        tvStatusConexion = findViewById(R.id.tvStatusConexion)
        tvGpsInfo = findViewById(R.id.tvGpsInfo)
        tvSismografo = findViewById(R.id.tvSismografo)
        tvZonaSegura = findViewById(R.id.tvZonaSegura)
        tvMeshCola = findViewById(R.id.tvMeshCola)
        tvLogRespuesta = findViewById(R.id.tvLogRespuesta)

        etServerUrl.setText(BuildConfig.DEFAULT_SERVER_URL)
        meshQueue = OfflineMeshQueue(this)

        spTipoEmergencia.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            tiposLabels
        )

        findViewById<Button>(R.id.btnEnviarSos).setOnClickListener {
            val idx = spTipoEmergencia.selectedItemPosition
            val tipo = tiposApi[idx]
            val desc = etDescripcion.text.toString().ifBlank { "Alerta SOS activada por ciudadano en Cayma" }
            enviarAlertaSos(tipo, desc, forzarMesh = false)
        }

        findViewById<Button>(R.id.btnSincronizarMesh).setOnClickListener {
            sincronizarColaOffline()
        }

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        solicitarPermisos()
        actualizarIndicadorMesh()
        verificarSaludServidor()
    }

    private fun solicitarPermisos() {
        val perms = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            perms.add(Manifest.permission.BLUETOOTH_ADVERTISE)
            perms.add(Manifest.permission.BLUETOOTH_SCAN)
            perms.add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        permissionLauncher.launch(perms.toTypedArray())
    }

    @SuppressLint("MissingPermission")
    private fun actualizarUbicacionGps() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            calcularZonaSeguraCercana()
            return
        }

        val fusedClient = LocationServices.getFusedLocationProviderClient(this)
        fusedClient.lastLocation.addOnSuccessListener { loc: Location? ->
            if (loc != null) {
                ultimaLat = loc.latitude
                ultimaLng = loc.longitude
                tvGpsInfo.text = "GPS Activo: ${"%.5f".format(ultimaLat)}, ${"%.5f".format(ultimaLng)}"
            } else {
                tvGpsInfo.text = "GPS Cayma (Base): ${"%.5f".format(ultimaLat)}, ${"%.5f".format(ultimaLng)}"
            }
            calcularZonaSeguraCercana()
        }
    }

    private fun calcularZonaSeguraCercana() {
        var mejor = zonasSegurasCayma.first()
        var menorDist = Double.MAX_VALUE
        for (z in zonasSegurasCayma) {
            val d = distanciaMetros(ultimaLat, ultimaLng, z.lat, z.lng)
            if (d < menorDist) {
                menorDist = d
                mejor = z
            }
        }
        tvZonaSegura.text = "Evacuación Cercana: ${mejor.nombre} (${menorDist.toInt()} m)"
    }

    private fun distanciaMetros(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        return 2 * r * atan2(sqrt(a), sqrt(1 - a))
    }

    private fun obtenerNivelBateria(): Int {
        val intent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, 85) ?: 85
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        return ((level / scale.toFloat()) * 100).toInt().coerceIn(0, 100)
    }

    @SuppressLint("HardwareIds")
    private fun obtenerDeviceId(): String {
        val id = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID) ?: "movil_cayma"
        return "android_$id"
    }

    private fun verificarSaludServidor() {
        val baseUrl = etServerUrl.text.toString().trim().removeSuffix("/")
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                try {
                    val req = Request.Builder().url("$baseUrl/api/v1/alertas/health").get().build()
                    client.newCall(req).execute().use { it.isSuccessful }
                } catch (_: Exception) {
                    false
                }
            }
            if (ok) {
                tvStatusConexion.text = "● ENLACE EN VIVO CON COE CAYMA ($baseUrl)"
                tvStatusConexion.setTextColor(0xFF10B981.toInt())
                if (meshQueue.contarPendientes() > 0) sincronizarColaOffline()
            } else {
                tvStatusConexion.text = "▲ SIN RED CON COE — MODO RESILIENCIA MESH ACTIVO"
                tvStatusConexion.setTextColor(0xFFF59E0B.toInt())
            }
        }
    }

    private fun enviarAlertaSos(tipo: String, descripcion: String, forzarMesh: Boolean) {
        actualizarUbicacionGps()
        vibrarConfirmacion()

        val payload = JSONObject().apply {
            put("usuario_id", obtenerDeviceId())
            put("latitud", ultimaLat)
            put("longitud", ultimaLng)
            put("tipo", tipo)
            put("descripcion", descripcion)
            put("nivel_bateria", obtenerNivelBateria())
            put("origen_offline_mesh", forzarMesh)
        }

        val baseUrl = etServerUrl.text.toString().trim().removeSuffix("/")
        tvLogRespuesta.text = "Transmitiendo paquete SOS al Centro de Comando..."

        scope.launch {
            val respuesta = withContext(Dispatchers.IO) {
                try {
                    val body = payload.toString().toRequestBody("application/json".toMediaType())
                    val req = Request.Builder().url("$baseUrl/api/v1/alertas/sos").post(body).build()
                    client.newCall(req).execute().use { resp ->
                        if (resp.isSuccessful) Pair(true, resp.body?.string() ?: "OK")
                        else Pair(false, "HTTP ${resp.code}: ${resp.body?.string()}")
                    }
                } catch (e: Exception) {
                    Pair(false, e.message ?: "Sin conexión")
                }
            }

            if (respuesta.first) {
                val jsonResp = JSONObject(respuesta.second)
                val id = jsonResp.optString("alerta_id").take(8)
                val conf = (jsonResp.optDouble("confianza_ia", 0.9) * 100).toInt()
                val nodo = jsonResp.optString("nodo_guardado")
                tvLogRespuesta.text = "✓ ALERTA RECIBIDA POR COE\nID: $id... | Confianza IA: $conf% | Nodo: $nodo"
                tvStatusConexion.text = "● ENLACE EN VIVO CON COE CAYMA"
                tvStatusConexion.setTextColor(0xFF10B981.toInt())
            } else {
                meshQueue.encolarAlertaOffline(payload)
                actualizarIndicadorMesh()
                tvStatusConexion.text = "▲ SIN INTERNET — PAQUETE ENCOLADO EN RED MESH BLE"
                tvStatusConexion.setTextColor(0xFFD8B4FE.toInt())
                tvLogRespuesta.text = "Sin cobertura directa (${respuesta.second}).\nAlerta guardada y radiada por baliza Mesh Bluetooth."
            }
        }
    }

    private fun sincronizarColaOffline() {
        val cola = meshQueue.obtenerCola()
        if (cola.length() == 0) {
            // Si esta vacia, enviamos un paquete de prueba Mesh para validar telemetria
            tvLogRespuesta.text = "Cola Mesh vacía: no hay alertas pendientes por retransmitir."
            return
        }

        val baseUrl = etServerUrl.text.toString().trim().removeSuffix("/")
        scope.launch {
            var enviados = 0
            withContext(Dispatchers.IO) {
                for (i in 0 until cola.length()) {
                    val item = cola.getJSONObject(i)
                    try {
                        val body = item.toString().toRequestBody("application/json".toMediaType())
                        val req = Request.Builder().url("$baseUrl/api/v1/alertas/sos").post(body).build()
                        client.newCall(req).execute().use { if (it.isSuccessful) enviados++ }
                    } catch (_: Exception) {}
                }
            }
            if (enviados == cola.length()) {
                meshQueue.limpiarCola()
                actualizarIndicadorMesh()
                tvLogRespuesta.text = "✓ Sincronización Mesh completada: $enviados alertas entregadas al COE."
            } else {
                tvLogRespuesta.text = "Sincronización parcial ($enviados/${cola.length()}). Se reintentará al recuperar señal."
            }
        }
    }

    private fun actualizarIndicadorMesh() {
        val pendientes = meshQueue.contarPendientes()
        tvMeshCola.text = "Red Mesh BLE: Activa | Paquetes en cola offline: $pendientes"
    }

    private fun vibrarConfirmacion() {
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        vibrator?.vibrate(VibrationEffect.createOneShot(250, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    // SISMOGRAFO AUTOMATICO POR ACELEROMETRO
    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type != Sensor.TYPE_ACCELEROMETER) return
        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]
        val gForce = abs(sqrt(x * x + y * y + z * z) - SensorManager.GRAVITY_EARTH) / SensorManager.GRAVITY_EARTH

        tvSismografo.text = "Acelerómetro Sísmico: ${"%.2f".format(gForce)} G"
        if (gForce > 1.35f) {
            tvSismografo.setTextColor(0xFFEF4444.toInt())
            val ahora = System.currentTimeMillis()
            if (ahora - ultimoDisparoSismoMs > 30_000L) {
                ultimoDisparoSismoMs = ahora
                enviarAlertaSos(
                    "sismo",
                    "Detección sísmica automática por acelerómetro móvil (${"%.2f".format(gForce)} G) en Cayma",
                    forzarMesh = false
                )
            }
        } else {
            tvSismografo.setTextColor(0xFF10B981.toInt())
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onResume() {
        super.onResume()
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}