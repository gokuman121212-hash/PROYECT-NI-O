package com.cayma.alertasos.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val FondoHielo = Color(0xFFF3F8FC)
val SuperficieBlanca = Color(0xFFFFFFFF)
val CelesteSuave = Color(0xFFE1F0FA)
val CelesteMedio = Color(0xFFB3E5FC)
val AzulPrincipal = Color(0xFF0277BD)
val AzulOscuroTexto = Color(0xFF0F2942)
val GrisTextoSecundario = Color(0xFF476072)
val RojoInfrarrojo = Color(0xFFC62828)
val RojoFondoSuave = Color(0xFFFFEBEE)
val VerdeAyuda = Color(0xFF1B5E20)
val VerdeFondoSuave = Color(0xFFE8F5E9)
val NaranjaAlerta = Color(0xFFEF6C00)

private val ArequipaLightColorScheme = lightColorScheme(
    primary = AzulPrincipal,
    onPrimary = Color.White,
    primaryContainer = CelesteSuave,
    onPrimaryContainer = AzulOscuroTexto,
    secondary = Color(0xFF00838F),
    onSecondary = Color.White,
    background = FondoHielo,
    onBackground = AzulOscuroTexto,
    surface = SuperficieBlanca,
    onSurface = AzulOscuroTexto,
    surfaceVariant = CelesteSuave,
    onSurfaceVariant = GrisTextoSecundario,
    error = RojoInfrarrojo
)

@Composable
fun AlertaArequipaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ArequipaLightColorScheme,
        content = content
    )
}
