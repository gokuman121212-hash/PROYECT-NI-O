package com.cayma.alertasos.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val FondoHielo = Color(0xFFECF4FC)
val SuperficieBlanca = Color(0xFFFFFFFF)
val CelesteSuave = Color(0xFFA7EBF2)
val CelesteMedio = Color(0xFF7CC4FA)
val AzulPrincipal = Color(0xFF26658C)
val AzulVibrante = Color(0xFF54ACBF)
val AzulOscuroTexto = Color(0xFF011C40)
val GrisTextoSecundario = Color(0xFF3A546E)
val RojoInfrarrojo = Color(0xFFD32F2F)
val RojoEmergenciaVivo = Color(0xFFFF2E55)
val RojoFondoSuave = Color(0xFFFFE3E8)
val VerdeAyuda = Color(0xFF16A34A)
val VerdeFondoSuave = Color(0xFFDCFCE7)
val NaranjaAlerta = Color(0xFFEA580C)
val AmbarSensorVivo = Color(0xFFF59E0B)
val MoradoMeshVivo = Color(0xFF7C3AED)

private val ArequipaLightColorScheme = lightColorScheme(
    primary = AzulPrincipal,
    onPrimary = Color.White,
    primaryContainer = CelesteSuave,
    onPrimaryContainer = AzulOscuroTexto,
    secondary = AzulVibrante,
    onSecondary = Color.White,
    background = FondoHielo,
    onBackground = AzulOscuroTexto,
    surface = SuperficieBlanca,
    onSurface = AzulOscuroTexto,
    surfaceVariant = CelesteSuave,
    onSurfaceVariant = GrisTextoSecundario,
    error = RojoEmergenciaVivo
)

private val ArequipaTypography = Typography(
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Black,
        fontSize = 25.sp,
        lineHeight = 31.sp,
        letterSpacing = (-0.5).sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Black,
        fontSize = 20.sp,
        lineHeight = 25.sp,
        letterSpacing = (-0.35).sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 16.5.sp,
        lineHeight = 22.sp,
        letterSpacing = (-0.15).sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.5.sp,
        lineHeight = 17.sp,
        letterSpacing = 0.2.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Black,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.65.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.6.sp
    )
)

@Composable
fun AlertaArequipaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ArequipaLightColorScheme,
        typography = ArequipaTypography,
        content = content
    )
}