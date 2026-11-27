package com.cayma.alertasos.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.cayma.alertasos.data.PerfilCiudadano
import com.cayma.alertasos.data.SessionManager
import com.cayma.alertasos.location.ArequipaGeoData
import com.cayma.alertasos.network.LoginRequest
import com.cayma.alertasos.network.RegistroRequest
import com.cayma.alertasos.network.SosApiService
import kotlinx.coroutines.launch
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaAutenticacionArequipa(
    apiService: SosApiService,
    latGpsActual: Double,
    lngGpsActual: Double,
    onAutenticado: (PerfilCiudadano) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var modoRegistro by remember { mutableStateOf(true) }

    var nombreCompleto by remember { mutableStateOf("") }
    var telefono by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var distritoSeleccionado by remember { mutableStateOf("Alto Selva Alegre") }
    var sectorReferencia by remember { mutableStateOf("") }
    var expandirDistritos by remember { mutableStateOf(false) }

    var cargando by remember { mutableStateOf(false) }
    var mensajeError by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(FondoHielo)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = CelesteSuave,
            modifier = Modifier.padding(bottom = 16.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Lock, contentDescription = null, tint = AzulPrincipal)
                Spacer(Modifier.width(8.dp))
                Text(
                    "RED VECINAL AREQUIPA SOS",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = AzulOscuroTexto
                )
            }
        }

        Text(
            text = if (modoRegistro) "Registro Ciudadano — Arequipa" else "Iniciar Sesion",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = AzulOscuroTexto
        )
        Text(
            text = "Cobertura exclusiva para Arequipa Ciudad. Respaldo activo en Firebase + Supabase.",
            style = MaterialTheme.typography.bodySmall,
            color = GrisTextoSecundario,
            modifier = Modifier.padding(top = 4.dp, bottom = 18.dp)
        )

        Card(
            colors = CardDefaults.cardColors(containerColor = SuperficieBlanca),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (modoRegistro) {
                    OutlinedTextField(
                        value = nombreCompleto,
                        onValueChange = { nombreCompleto = it },
                        label = { Text("Nombres y Apellidos", style = MaterialTheme.typography.bodySmall) },
                        leadingIcon = { Icon(Icons.Default.Person, null) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                OutlinedTextField(
                    value = telefono,
                    onValueChange = { telefono = it },
                    label = { Text("Numero de Celular", style = MaterialTheme.typography.bodySmall) },
                    leadingIcon = { Icon(Icons.Default.Phone, null) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Contrasena", style = MaterialTheme.typography.bodySmall) },
                    leadingIcon = { Icon(Icons.Default.Lock, null) },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                if (modoRegistro) {
                    ExposedDropdownMenuBox(
                        expanded = expandirDistritos,
                        onExpandedChange = { expandirDistritos = !expandirDistritos }
                    ) {
                        OutlinedTextField(
                            value = distritoSeleccionado,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Distrito en Arequipa Ciudad", style = MaterialTheme.typography.bodySmall) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandirDistritos) },
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = expandirDistritos,
                            onDismissRequest = { expandirDistritos = false }
                        ) {
                            ArequipaGeoData.DISTRITOS_HABILITADOS.forEach { dist ->
                                DropdownMenuItem(
                                    text = { Text(dist, style = MaterialTheme.typography.bodyMedium) },
                                    onClick = {
                                        distritoSeleccionado = dist
                                        expandirDistritos = false
                                    }
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = sectorReferencia,
                        onValueChange = { sectorReferencia = it },
                        label = { Text("Urbanizacion / Sector / Referencia exacta", style = MaterialTheme.typography.bodySmall) },
                        placeholder = { Text("Ej. Av. Obrera cdra 4, frente a parque", style = MaterialTheme.typography.bodySmall) },
                        leadingIcon = { Icon(Icons.Default.Home, null) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (mensajeError != null) {
                    Surface(
                        color = RojoFondoSuave,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = mensajeError!!,
                            color = RojoInfrarrojo,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                Button(
                    onClick = {
                        mensajeError = null
                        if (telefono.length < 6 || password.length < 4) {
                            mensajeError = "Ingresa un celular valido y contrasena de al menos 4 caracteres."
                            return@Button
                        }

                        scope.launch {
                            cargando = true
                            val coordsDistrito = ArequipaGeoData.COORDENADAS_DISTRITOS[distritoSeleccionado]
                                ?: Pair(-16.3989, -71.5350)
                            val latFinal = if (ArequipaGeoData.estaEnArequipaMetropolitana(latGpsActual, lngGpsActual) && latGpsActual != 0.0) {
                                latGpsActual
                            } else {
                                coordsDistrito.first
                            }
                            val lngFinal = if (ArequipaGeoData.estaEnArequipaMetropolitana(latGpsActual, lngGpsActual) && lngGpsActual != 0.0) {
                                lngGpsActual
                            } else {
                                coordsDistrito.second
                            }

                            if (modoRegistro) {
                                if (nombreCompleto.isBlank() || sectorReferencia.isBlank()) {
                                    mensajeError = "Completa tu nombre y sector de ubicacion en Arequipa."
                                    cargando = false
                                    return@launch
                                }
                                try {
                                    val resp = apiService.registrarUsuario(
                                        RegistroRequest(
                                            nombre_completo = nombreCompleto.trim(),
                                            telefono = telefono.trim(),
                                            password = password,
                                            distrito = distritoSeleccionado,
                                            sector_referencia = sectorReferencia.trim(),
                                            latitud = latFinal,
                                            longitud = lngFinal
                                        )
                                    )
                                    val perfil = PerfilCiudadano(
                                        usuarioId = resp.usuario_id,
                                        nombreCompleto = resp.nombre_completo,
                                        telefono = resp.telefono,
                                        distrito = resp.distrito,
                                        sectorReferencia = resp.sector_referencia,
                                        latitud = resp.latitud,
                                        longitud = resp.longitud
                                    )
                                    SessionManager.guardarSesion(context, perfil)
                                    onAutenticado(perfil)
                                } catch (_: Exception) {
                                    val perfilLocal = PerfilCiudadano(
                                        usuarioId = "usr_${UUID.randomUUID().toString().take(8)}",
                                        nombreCompleto = nombreCompleto.trim(),
                                        telefono = telefono.trim(),
                                        distrito = distritoSeleccionado,
                                        sectorReferencia = sectorReferencia.trim(),
                                        latitud = latFinal,
                                        longitud = lngFinal
                                    )
                                    SessionManager.guardarSesion(context, perfilLocal)
                                    onAutenticado(perfilLocal)
                                }
                            } else {
                                try {
                                    val resp = apiService.iniciarSesion(LoginRequest(telefono.trim(), password))
                                    val perfil = PerfilCiudadano(
                                        usuarioId = resp.usuario_id,
                                        nombreCompleto = resp.nombre_completo,
                                        telefono = resp.telefono,
                                        distrito = resp.distrito,
                                        sectorReferencia = resp.sector_referencia,
                                        latitud = resp.latitud,
                                        longitud = resp.longitud
                                    )
                                    SessionManager.guardarSesion(context, perfil)
                                    onAutenticado(perfil)
                                } catch (_: Exception) {
                                    mensajeError = "No se pudo verificar en la nube o credenciales incorrectas."
                                }
                            }
                            cargando = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AzulPrincipal),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    enabled = !cargando
                ) {
                    Text(
                        text = if (cargando) "Verificando respaldo dual..." else if (modoRegistro) "CREAR CUENTA SEGURA" else "INGRESAR A LA RED",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                TextButton(
                    onClick = {
                        modoRegistro = !modoRegistro
                        mensajeError = null
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (modoRegistro) "Ya tengo cuenta — Iniciar Sesion" else "No tengo cuenta — Registrarme en Arequipa",
                        style = MaterialTheme.typography.labelLarge,
                        color = AzulPrincipal,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}