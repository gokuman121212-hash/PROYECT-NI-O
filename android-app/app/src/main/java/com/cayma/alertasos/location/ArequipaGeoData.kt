package com.cayma.alertasos.location

import kotlin.math.*

data class CentroSeguridadArequipa(
    val nombre: String,
    val tipo: String, // "hospital", "comisaria", "zona_segura"
    val distrito: String,
    val lat: Double,
    val lng: Double
)

data class RutaBloqueadaHistorica(
    val id: String,
    val nombre: String,
    val latInicio: Double,
    val lngInicio: Double,
    val latFin: Double,
    val lngFin: Double,
    val riesgoHistorico: String
)

object ArequipaGeoData {

    val DISTRITOS_HABILITADOS = listOf(
        "Cercado de Arequipa", "Cayma", "Alto Selva Alegre", "Yanahuara",
        "Cerro Colorado", "Jacobo Hunter", "Jose Luis Bustamante y Rivero",
        "Mariano Melgar", "Miraflores", "Paucarpata", "Sabandia",
        "Sachaca", "Socabaya", "Tiabaya", "Uchumayo", "Characato", "Chiguata", "Yura"
    )

    // Coordenadas centrales por distrito para validacion y registro
    val COORDENADAS_DISTRITOS = mapOf(
        "Cercado de Arequipa" to Pair(-16.3989, -71.5350),
        "Cayma" to Pair(-16.3422, -71.5458),
        "Alto Selva Alegre" to Pair(-16.3821, -71.5245),
        "Yanahuara" to Pair(-16.3879, -71.5425),
        "Cerro Colorado" to Pair(-16.3752, -71.5630),
        "Jacobo Hunter" to Pair(-16.4412, -71.5565),
        "Jose Luis Bustamante y Rivero" to Pair(-16.4255, -71.5268),
        "Mariano Melgar" to Pair(-16.4045, -71.5082),
        "Miraflores" to Pair(-16.3948, -71.5218),
        "Paucarpata" to Pair(-16.4265, -71.5038),
        "Sabandia" to Pair(-16.4530, -71.4980),
        "Sachaca" to Pair(-16.4258, -71.5684),
        "Socabaya" to Pair(-16.4485, -71.5312),
        "Tiabaya" to Pair(-16.4495, -71.5910),
        "Uchumayo" to Pair(-16.4220, -71.6350),
        "Characato" to Pair(-16.4690, -71.4840),
        "Chiguata" to Pair(-16.4010, -71.4020),
        "Yura" to Pair(-16.2520, -71.6790)
    )

    // Geocerca estricta de Arequipa Ciudad / Metropolitana (bloquea Islay, Camana, etc.)
    fun estaEnArequipaMetropolitana(lat: Double, lng: Double): Boolean {
        return lat in -16.5200..-16.2450 && lng in -71.6900..-71.3950
    }

    // 38 Instituciones verificadas en Arequipa Ciudad
    val CENTROS_SEGURIDAD = listOf(
        // =========================================================================
        // RED COMPLETA DE SALUD AREQUIPA METROPOLITANA (HOSPITALES, C.S. Y POSTAS)
        // Solo atencion medica especializada (sin farmacias ni boticas)
        // =========================================================================
        // ALTO SELVA ALEGRE
        CentroSeguridadArequipa("Centro de Salud Apurimac", "hospital", "Alto Selva Alegre", -16.3738, -71.5222),
        CentroSeguridadArequipa("P.S. Independencia", "hospital", "Alto Selva Alegre", -16.3765, -71.5245),
        CentroSeguridadArequipa("P.S. Villa Ecologica", "hospital", "Alto Selva Alegre", -16.3612, -71.5168),
        CentroSeguridadArequipa("P.S. Javier Heraud", "hospital", "Alto Selva Alegre", -16.3685, -71.5192),
        CentroSeguridadArequipa("P.S. San Luis Gonzaga", "hospital", "Alto Selva Alegre", -16.3821, -71.5264),
        CentroSeguridadArequipa("Posta Medica Alto Selva Alegre (Av. Espana)", "hospital", "Alto Selva Alegre", -16.3862, -71.5271),
        // CAYMA
        CentroSeguridadArequipa("C.S. Francisco Bolognesi", "hospital", "Cayma", -16.3698, -71.5442),
        CentroSeguridadArequipa("C.S. Buenos Aires - Cayma", "hospital", "Cayma", -16.3545, -71.5418),
        CentroSeguridadArequipa("P.S. Dean Valdivia (La Tomilla)", "hospital", "Cayma", -16.3618, -71.5385),
        CentroSeguridadArequipa("P.S. Carmen Alto", "hospital", "Cayma", -16.3815, -71.5412),
        CentroSeguridadArequipa("P.S. Acequia Alta", "hospital", "Cayma", -16.3742, -71.5378),
        CentroSeguridadArequipa("Clinica San Juan de Dios", "hospital", "Cayma", -16.3845, -71.5468),
        // MIRAFLORES
        CentroSeguridadArequipa("Hospital Miraflores 3", "hospital", "Miraflores", -16.3932, -71.5228),
        CentroSeguridadArequipa("Centro de Salud Porvenir Miraflores", "hospital", "Miraflores", -16.3868, -71.5194),
        CentroSeguridadArequipa("C.S. Edificadores Misti", "hospital", "Miraflores", -16.3845, -71.5142),
        CentroSeguridadArequipa("P.S. Mateo Pumacahua", "hospital", "Miraflores", -16.3812, -71.5115),
        CentroSeguridadArequipa("P.S. Juan XXIII", "hospital", "Miraflores", -16.3889, -71.5162),
        CentroSeguridadArequipa("P.S. Tomasa Tito Condemayta", "hospital", "Miraflores", -16.3795, -71.5088),
        // CERCADO
        CentroSeguridadArequipa("Hospital Nacional Carlos Alberto Seguin Escobedo", "hospital", "Cercado", -16.3976, -71.5318),
        CentroSeguridadArequipa("Hospital Regional Honorio Delgado Espinoza", "hospital", "Cercado", -16.4158, -71.5315),
        CentroSeguridadArequipa("Centro de Emergencias Criticas", "hospital", "Cercado", -16.4162, -71.5312),
        CentroSeguridadArequipa("Hospital Goyeneche", "hospital", "Cercado", -16.4045, -71.5278),
        CentroSeguridadArequipa("IREN Sur (Instituto Regional de Enfermedades Neoplasicas)", "hospital", "Cercado", -16.4172, -71.5295),
        CentroSeguridadArequipa("Hospital Militar Regional", "hospital", "Cercado", -16.4028, -71.5392),
        CentroSeguridadArequipa("Hospital Regional Policial PNP", "hospital", "Cercado", -16.3948, -71.5355),
        CentroSeguridadArequipa("Clinica Arequipa", "hospital", "Cercado", -16.3925, -71.5388),
        // YANAHUARA
        CentroSeguridadArequipa("Hospital III Yanahuara (EsSalud)", "hospital", "Yanahuara", -16.3912, -71.5445),
        CentroSeguridadArequipa("C.S. Yanahuara", "hospital", "Yanahuara", -16.3895, -71.5422),
        CentroSeguridadArequipa("P.S. Pampa de Camarones", "hospital", "Yanahuara", -16.3832, -71.5485),
        // CERRO COLORADO
        CentroSeguridadArequipa("C.S. Zamacola", "hospital", "Cerro Colorado", -16.3582, -71.5695),
        CentroSeguridadArequipa("C.S. Mariscal Castilla", "hospital", "Cerro Colorado", -16.3768, -71.5582),
        CentroSeguridadArequipa("C.S. Cerro Colorado", "hospital", "Cerro Colorado", -16.3735, -71.5615),
        CentroSeguridadArequipa("P.S. Semi Rural Pachacutec", "hospital", "Cerro Colorado", -16.3885, -71.5712),
        CentroSeguridadArequipa("P.S. Rio Seco", "hospital", "Cerro Colorado", -16.3645, -71.5785),
        CentroSeguridadArequipa("P.S. Ciudad Municipal", "hospital", "Cerro Colorado", -16.3412, -71.5845),
        CentroSeguridadArequipa("P.S. Peruarbo", "hospital", "Cerro Colorado", -16.3485, -71.5762),
        // MARIANO MELGAR
        CentroSeguridadArequipa("C.S. Mariano Melgar", "hospital", "Mariano Melgar", -16.4048, -71.5035),
        CentroSeguridadArequipa("P.S. Jerusalen", "hospital", "Mariano Melgar", -16.3982, -71.4985),
        CentroSeguridadArequipa("P.S. Generalisimo San Martin", "hospital", "Mariano Melgar", -16.4095, -71.5092),
        CentroSeguridadArequipa("P.S. Atalaya", "hospital", "Mariano Melgar", -16.3945, -71.4942),
        // PAUCARPATA
        CentroSeguridadArequipa("Hospital Edmundo Escomel (EsSalud)", "hospital", "Paucarpata", -16.4215, -71.5082),
        CentroSeguridadArequipa("C.S. Ampliacion Paucarpata", "hospital", "Paucarpata", -16.4242, -71.4945),
        CentroSeguridadArequipa("C.S. Ciudad Blanca", "hospital", "Paucarpata", -16.4285, -71.5012),
        CentroSeguridadArequipa("C.S. 15 de Agosto", "hospital", "Paucarpata", -16.4335, -71.4968),
        CentroSeguridadArequipa("P.S. Miguel Grau", "hospital", "Paucarpata", -16.4182, -71.4912),
        CentroSeguridadArequipa("P.S. Israel", "hospital", "Paucarpata", -16.4368, -71.4885),
        // JOSE LUIS BUSTAMANTE Y RIVERO
        CentroSeguridadArequipa("C.S. Victor Raul Hinojosa", "hospital", "Jose Luis Bustamante y Rivero", -16.4255, -71.5218),
        CentroSeguridadArequipa("P.S. Simon Bolivar", "hospital", "Jose Luis Bustamante y Rivero", -16.4218, -71.5285),
        CentroSeguridadArequipa("P.S. 13 de Enero", "hospital", "Jose Luis Bustamante y Rivero", -16.4325, -71.5182),
        CentroSeguridadArequipa("Clinica Paz Holandesa", "hospital", "Jose Luis Bustamante y Rivero", -16.4348, -71.5165),
        // JACOBO HUNTER
        CentroSeguridadArequipa("C.S. Javier Llosa Garcia", "hospital", "Jacobo Hunter", -16.4388, -71.5632),
        CentroSeguridadArequipa("P.S. Caminos del Inca", "hospital", "Jacobo Hunter", -16.4445, -71.5585),
        CentroSeguridadArequipa("P.S. Upis Paisajista", "hospital", "Jacobo Hunter", -16.4482, -71.5665),
        // SOCABAYA
        CentroSeguridadArequipa("C.S. San Martin de Socabaya", "hospital", "Socabaya", -16.4485, -71.5342),
        CentroSeguridadArequipa("P.S. Salaverry", "hospital", "Socabaya", -16.4532, -71.5298),
        CentroSeguridadArequipa("P.S. Horacio Zeballos Gamez", "hospital", "Socabaya", -16.4625, -71.5195),
        CentroSeguridadArequipa("P.S. Las Esmeraldas", "hospital", "Socabaya", -16.4412, -71.5315),
        // SACHACA, TIABAYA, CHARACATO, SABANDIA Y YURA
        CentroSeguridadArequipa("C.S. Sachaca", "hospital", "Sachaca", -16.4248, -71.5675),
        CentroSeguridadArequipa("P.S. Pampa de Camarones Sachaca", "hospital", "Sachaca", -16.4125, -71.5592),
        CentroSeguridadArequipa("C.S. Tiabaya", "hospital", "Tiabaya", -16.4495, -71.5912),
        CentroSeguridadArequipa("P.S. Alata", "hospital", "Tiabaya", -16.4415, -71.5825),
        CentroSeguridadArequipa("C.S. Characato", "hospital", "Characato", -16.4685, -71.4842),
        CentroSeguridadArequipa("C.S. Sabandia", "hospital", "Sabandia", -16.4538, -71.4975),
        CentroSeguridadArequipa("C.S. Ciudad de Dios", "hospital", "Yura", -16.3085, -71.6185),
        CentroSeguridadArequipa("P.S. Cono Norte Yura", "hospital", "Yura", -16.3245, -71.6042),
        // PORVENIR - MIRAFLORES (VERIFICADOS EN CAPTURA)
        CentroSeguridadArequipa("Centro de Salud Porvenir Miraflores", "hospital", "Miraflores", -16.3868, -71.5194),
        CentroSeguridadArequipa("Comisaria PNP El Porvenir", "comisaria", "Miraflores", -16.3876, -71.5198),
        // HOSPITALES VERIFICADOS POR EL USUARIO EN MAPA REAL
        CentroSeguridadArequipa("Hospital Miraflores 3", "hospital", "Miraflores", -16.3932, -71.5228),
        CentroSeguridadArequipa("Hospital Nacional III-1 Carlos Alberto Seguin Escobedo (EsSalud)", "hospital", "Cercado", -16.3976, -71.5318),
        CentroSeguridadArequipa("Centro de Emergencias Criticas (Honorio Delgado)", "hospital", "Cercado", -16.4162, -71.5312),
        // Hospitales y Centros de Salud
        CentroSeguridadArequipa("Hospital Regional Honorio Delgado Espinoza", "hospital", "Cercado", -16.4148, -71.5318),
        CentroSeguridadArequipa("Hospital Goyeneche", "hospital", "Cercado", -16.4042, -71.5293),
        CentroSeguridadArequipa("Hospital Nacional Carlos Alberto Seguin Escobedo (EsSalud)", "hospital", "Cercado", -16.4021, -71.5285),
        CentroSeguridadArequipa("Hospital III Yanahuara - EsSalud", "hospital", "Yanahuara", -16.3916, -71.5422),
        CentroSeguridadArequipa("Clinica San Juan de Dios Arequipa", "hospital", "Cayma", -16.3869, -71.5468),
        CentroSeguridadArequipa("Hospital Militar Regional Arequipa", "hospital", "Cercado", -16.4095, -71.5372),
        CentroSeguridadArequipa("Centro de Salud Francisco Bolognesi", "hospital", "Cayma", -16.3556, -71.5464),
        CentroSeguridadArequipa("Centro de Salud Buenos Aires Cayma", "hospital", "Cayma", -16.3412, -71.5448),
        CentroSeguridadArequipa("Centro de Salud Alto Selva Alegre", "hospital", "Alto Selva Alegre", -16.3789, -71.5228),
        CentroSeguridadArequipa("Centro de Salud Mariscal Castilla", "hospital", "Cerro Colorado", -16.3725, -71.5642),
        CentroSeguridadArequipa("Centro de Salud Zamacola", "hospital", "Cerro Colorado", -16.3445, -71.5768),
        CentroSeguridadArequipa("Centro de Salud Javier Llosa Garcia - Hunter", "hospital", "Jacobo Hunter", -16.4426, -71.5582),
        CentroSeguridadArequipa("Hospital Edmundo Escomel - EsSalud Paucarpata", "hospital", "Paucarpata", -16.4265, -71.5038),
        CentroSeguridadArequipa("Centro de Salud Ampliacion Paucarpata", "hospital", "Paucarpata", -16.4184, -71.4955),
        CentroSeguridadArequipa("Centro de Salud Edificadores Misti", "hospital", "Miraflores", -16.3895, -71.5162),
        CentroSeguridadArequipa("Centro de Salud Mariano Melgar", "hospital", "Mariano Melgar", -16.4018, -71.5065),
        CentroSeguridadArequipa("Centro de Salud San Martin de Socabaya", "hospital", "Socabaya", -16.4485, -71.5312),
        CentroSeguridadArequipa("Centro de Salud Sachaca", "hospital", "Sachaca", -16.4258, -71.5684),

        // Comisarias PNP
        CentroSeguridadArequipa("Comisaria PNP Cayma", "comisaria", "Cayma", -16.3422, -71.5458),
        CentroSeguridadArequipa("Comisaria PNP Acequia Alta", "comisaria", "Cayma", -16.3688, -71.5461),
        CentroSeguridadArequipa("Comisaria PNP Alto Selva Alegre", "comisaria", "Alto Selva Alegre", -16.3821, -71.5245),
        CentroSeguridadArequipa("Comisaria PNP Yanahuara", "comisaria", "Yanahuara", -16.3879, -71.5425),
        CentroSeguridadArequipa("Comisaria PNP Cerro Colorado", "comisaria", "Cerro Colorado", -16.3762, -71.5618),
        CentroSeguridadArequipa("Comisaria PNP Jacobo Hunter", "comisaria", "Jacobo Hunter", -16.4412, -71.5565),
        CentroSeguridadArequipa("Comisaria PNP Santa Marta (Cercado)", "comisaria", "Cercado", -16.4005, -71.5332),
        CentroSeguridadArequipa("Comisaria PNP Palacio Viejo", "comisaria", "Cercado", -16.4038, -71.5378),
        CentroSeguridadArequipa("Comisaria PNP Miraflores", "comisaria", "Miraflores", -16.3948, -71.5218),
        CentroSeguridadArequipa("Comisaria PNP Mariano Melgar", "comisaria", "Mariano Melgar", -16.4045, -71.5082),
        CentroSeguridadArequipa("Comisaria PNP Paucarpata", "comisaria", "Paucarpata", -16.4282, -71.5015),
        CentroSeguridadArequipa("Comisaria PNP Jose Luis Bustamante y Rivero", "comisaria", "J.L. Bustamante", -16.4255, -71.5268),
        CentroSeguridadArequipa("Comisaria PNP Socabaya", "comisaria", "Socabaya", -16.4562, -71.5285),

        // Zonas Seguras y Albergues
        CentroSeguridadArequipa("Estadio Monumental UNSA (Zona Segura Amplia)", "zona_segura", "Cercado", -16.4068, -71.5205),
        CentroSeguridadArequipa("Estadio Melgar - IV Centenario", "zona_segura", "Cercado", -16.4085, -71.5325),
        CentroSeguridadArequipa("Complejo Deportivo La Tomilla", "zona_segura", "Cayma", -16.3645, -71.5435),
        CentroSeguridadArequipa("Plaza Principal de Cayma", "zona_segura", "Cayma", -16.3418, -71.5459),
        CentroSeguridadArequipa("Parque Selva Alegre / Zona Alta", "zona_segura", "Alto Selva Alegre", -16.3885, -71.5285),
        CentroSeguridadArequipa("Plaza Las Americas - Cerro Colorado", "zona_segura", "Cerro Colorado", -16.3752, -71.5630)
    )

    // Vias y torrenteras con historial de bloqueo en anos anteriores
    val RUTAS_BLOQUEADAS_HISTORICAS = listOf(
        RutaBloqueadaHistorica("torrentera_chullo", "Torrentera El Chullo (Cayma - Yanahuara)", -16.3480, -71.5490, -16.3940, -71.5480, "Desborde de lodo y piedras en temporada de lluvias (2020-2025)"),
        RutaBloqueadaHistorica("torrentera_san_lazaro", "Torrentera San Lazaro (ASA - Cercado)", -16.3720, -71.5210, -16.3990, -71.5340, "Inundacion recurrente de puentes y vias bajas"),
        RutaBloqueadaHistorica("quebrada_anashuayco", "Quebrada Anashuayco / Via Evitamiento", -16.3315, -71.5840, -16.3650, -71.5890, "Ingreso de lahar y corte de transito pesado"),
        RutaBloqueadaHistorica("torrentera_venezuela", "Av. Venezuela / Torrentera Los Incas", -16.4050, -71.5150, -16.4180, -71.5350, "Colapso de drenaje pluvial y bloqueo vehicular"),
        RutaBloqueadaHistorica("bypass_ejercito", "Bypass Av. Ejercito / Av. Grau", -16.3910, -71.5450, -16.3965, -71.5415, "Anegamiento profundo durante lluvias intensas")
    )

    fun calcularDistanciaMetros(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val dPhi = Math.toRadians(lat2 - lat1)
        val dLambda = Math.toRadians(lon2 - lon1)
        val a = sin(dPhi / 2).pow(2) + cos(phi1) * cos(phi2) * sin(dLambda / 2).pow(2)
        return 2 * r * atan2(sqrt(a), sqrt(1 - a))
    }

    /**
     * Genera una ruta sugerida libre de torrenteras/vias bloqueadas entre el rescatista y el afectado.
     * Si el tramo cruza cerca de una torrentera critica, desvia el punto intermedio hacia una via alta segura.
     */
    fun calcularRutaLibreDeBloqueos(
        latOrigen: Double,
        lngOrigen: Double,
        latDestino: Double,
        lngDestino: Double
    ): List<Pair<Double, Double>> {
        val midLat = (latOrigen + latDestino) / 2.0
        val midLng = (lngOrigen + lngDestino) / 2.0

        var desvioLat = midLat
        var desvioLng = midLng

        for (bloqueo in RUTAS_BLOQUEADAS_HISTORICAS) {
            val bLat = (bloqueo.latInicio + bloqueo.latFin) / 2.0
            val bLng = (bloqueo.lngInicio + bloqueo.lngFin) / 2.0
            val dist = calcularDistanciaMetros(midLat, midLng, bLat, bLng)
            if (dist < 600.0) {
                // Desviar 450m hacia el oeste/norte elevado para esquivar el cauce bloqueado
                desvioLat += 0.0038
                desvioLng -= 0.0042
                break
            }
        }

        val p1 = Pair(latOrigen + (desvioLat - latOrigen) * 0.5, lngOrigen + (desvioLng - lngOrigen) * 0.5)
        val p2 = Pair(desvioLat, desvioLng)
        val p3 = Pair(desvioLat + (latDestino - desvioLat) * 0.5, desvioLng + (lngDestino - desvioLng) * 0.5)

        return listOf(
            Pair(latOrigen, lngOrigen),
            p1,
            p2,
            p3,
            Pair(latDestino, lngDestino)
        )
    }
}



