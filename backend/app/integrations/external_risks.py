import math
import httpx

# Geocerca estricta de Arequipa Metropolitana (excluye Islay, Camana, Caylloma, etc.)
AREQUIPA_METRO_BOUNDS = {
    "lat_min": -16.5200,
    "lat_max": -16.2800,
    "lng_min": -71.6600,
    "lng_max": -71.4500
}

DISTRITOS_AREQUIPA_METRO = [
    "Cercado de Arequipa", "Cayma", "Alto Selva Alegre", "Yanahuara",
    "Cerro Colorado", "Jacobo Hunter", "Jose Luis Bustamante y Rivero",
    "Mariano Melgar", "Miraflores", "Paucarpata", "Sabandia",
    "Sachaca", "Socabaya", "Tiabaya", "Uchumayo", "Characato", "Chiguata", "Yura"
]

# Coordenadas verificadas de Hospitales, Comisarias y Zonas Seguras en Arequipa Ciudad
CENTROS_SEGURIDAD_AREQUIPA = [
    {"nombre": "Hospital Regional Honorio Delgado Espinoza", "tipo": "hospital", "distrito": "Cercado", "lat": -16.4148, "lng": -71.5318},
    {"nombre": "Hospital Goyeneche", "tipo": "hospital", "distrito": "Cercado", "lat": -16.4042, "lng": -71.5293},
    {"nombre": "Hospital Nacional Carlos Alberto Seguin Escobedo (EsSalud)", "tipo": "hospital", "distrito": "Cercado", "lat": -16.4021, "lng": -71.5285},
    {"nombre": "Hospital III Yanahuara - EsSalud", "tipo": "hospital", "distrito": "Yanahuara", "lat": -16.3916, "lng": -71.5422},
    {"nombre": "Clinica San Juan de Dios Arequipa", "tipo": "hospital", "distrito": "Cayma", "lat": -16.3869, "lng": -71.5468},
    {"nombre": "Hospital Militar Regional Arequipa", "tipo": "hospital", "distrito": "Cercado", "lat": -16.4095, "lng": -71.5372},
    {"nombre": "Centro de Salud Francisco Bolognesi", "tipo": "hospital", "distrito": "Cayma", "lat": -16.3556, "lng": -71.5464},
    {"nombre": "Centro de Salud Buenos Aires Cayma", "tipo": "hospital", "distrito": "Cayma", "lat": -16.3412, "lng": -71.5448},
    {"nombre": "Centro de Salud Alto Selva Alegre", "tipo": "hospital", "distrito": "Alto Selva Alegre", "lat": -16.3789, "lng": -71.5228},
    {"nombre": "Centro de Salud Mariscal Castilla", "tipo": "hospital", "distrito": "Cerro Colorado", "lat": -16.3725, "lng": -71.5642},
    {"nombre": "Centro de Salud Zamacola", "tipo": "hospital", "distrito": "Cerro Colorado", "lat": -16.3445, "lng": -71.5768},
    {"nombre": "Centro de Salud Javier Llosa Garcia - Hunter", "tipo": "hospital", "distrito": "Jacobo Hunter", "lat": -16.4426, "lng": -71.5582},
    {"nombre": "Hospital Edmundo Escomel - EsSalud Paucarpata", "tipo": "hospital", "distrito": "Paucarpata", "lat": -16.4265, "lng": -71.5038},
    {"nombre": "Centro de Salud Ampliacion Paucarpata", "tipo": "hospital", "distrito": "Paucarpata", "lat": -16.4184, "lng": -71.4955},
    {"nombre": "Centro de Salud Edificadores Misti", "tipo": "hospital", "distrito": "Miraflores", "lat": -16.3895, "lng": -71.5162},
    {"nombre": "Centro de Salud Mariano Melgar", "tipo": "hospital", "distrito": "Mariano Melgar", "lat": -16.4018, "lng": -71.5065},
    {"nombre": "Centro de Salud San Martin de Socabaya", "tipo": "hospital", "distrito": "Socabaya", "lat": -16.4485, "lng": -71.5312},
    {"nombre": "Centro de Salud Sachaca", "tipo": "hospital", "distrito": "Sachaca", "lat": -16.4258, "lng": -71.5684},
    # Comisarias PNP
    {"nombre": "Comisaria PNP Cayma", "tipo": "comisaria", "distrito": "Cayma", "lat": -16.3422, "lng": -71.5458},
    {"nombre": "Comisaria PNP Acequia Alta", "tipo": "comisaria", "distrito": "Cayma", "lat": -16.3688, "lng": -71.5461},
    {"nombre": "Comisaria PNP Alto Selva Alegre", "tipo": "comisaria", "distrito": "Alto Selva Alegre", "lat": -16.3821, "lng": -71.5245},
    {"nombre": "Comisaria PNP Yanahuara", "tipo": "comisaria", "distrito": "Yanahuara", "lat": -16.3879, "lng": -71.5425},
    {"nombre": "Comisaria PNP Cerro Colorado", "tipo": "comisaria", "distrito": "Cerro Colorado", "lat": -16.3762, "lng": -71.5618},
    {"nombre": "Comisaria PNP Jacobo Hunter", "tipo": "comisaria", "distrito": "Jacobo Hunter", "lat": -16.4412, "lng": -71.5565},
    {"nombre": "Comisaria PNP Santa Marta (Cercado)", "tipo": "comisaria", "distrito": "Cercado", "lat": -16.4005, "lng": -71.5332},
    {"nombre": "Comisaria PNP Palacio Viejo", "tipo": "comisaria", "distrito": "Cercado", "lat": -16.4038, "lng": -71.5378},
    {"nombre": "Comisaria PNP Miraflores", "tipo": "comisaria", "distrito": "Miraflores", "lat": -16.3948, "lng": -71.5218},
    {"nombre": "Comisaria PNP Mariano Melgar", "tipo": "comisaria", "distrito": "Mariano Melgar", "lat": -16.4045, "lng": -71.5082},
    {"nombre": "Comisaria PNP Paucarpata", "tipo": "comisaria", "distrito": "Paucarpata", "lat": -16.4282, "lng": -71.5015},
    {"nombre": "Comisaria PNP Jose Luis Bustamante y Rivero", "tipo": "comisaria", "distrito": "J.L. Bustamante", "lat": -16.4255, "lng": -71.5268},
    {"nombre": "Comisaria PNP Socabaya", "tipo": "comisaria", "distrito": "Socabaya", "lat": -16.4562, "lng": -71.5285},
    # Zonas Seguras y Albergues
    {"nombre": "Estadio Monumental UNSA (Zona Segura Amplia)", "tipo": "zona_segura", "distrito": "Cercado", "lat": -16.4068, "lng": -71.5205},
    {"nombre": "Estadio Melgar - IV Centenario", "tipo": "zona_segura", "distrito": "Cercado", "lat": -16.4085, "lng": -71.5325},
    {"nombre": "Complejo Deportivo La Tomilla", "tipo": "zona_segura", "distrito": "Cayma", "lat": -16.3645, "lng": -71.5435},
    {"nombre": "Plaza Principal de Cayma", "tipo": "zona_segura", "distrito": "Cayma", "lat": -16.3418, "lng": -71.5459},
    {"nombre": "Parque Selva Alegre / Zona Alta", "tipo": "zona_segura", "distrito": "Alto Selva Alegre", "lat": -16.3885, "lng": -71.5285},
    {"nombre": "Plaza Las Americas - Cerro Colorado", "tipo": "zona_segura", "distrito": "Cerro Colorado", "lat": -16.3752, "lng": -71.5630}
]

# Zonas y rutas con historial de bloqueos por huaicos/torrenteras en anos anteriores
RUTAS_HISTORIAL_BLOQUEO = [
    {"id": "torrentera_chullo", "nombre": "Torrentera El Chullo (Cayma - Yanahuara)", "lat_inicio": -16.3480, "lng_inicio": -71.5490, "lat_fin": -16.3940, "lng_fin": -71.5480, "riesgo_historico": "Desborde de lodo y piedras en temporada de lluvias (2020-2025)"},
    {"id": "torrentera_san_lazaro", "nombre": "Torrentera San Lazaro (ASA - Cercado)", "lat_inicio": -16.3720, "lng_inicio": -71.5210, "lat_fin": -16.3990, "lng_fin": -71.5340, "riesgo_historico": "Inundacion recurrente de puentes y vias bajas"},
    {"id": "quebrada_anashuayco", "nombre": "Quebrada Anashuayco / Via Evitamiento", "lat_inicio": -16.3315, "lng_inicio": -71.5840, "lat_fin": -16.3650, "lng_fin": -71.5890, "riesgo_historico": "Ingreso de lahar y corte de transito pesado"},
    {"id": "torrentera_venezuela", "nombre": "Av. Venezuela / Torrentera Los Incas", "lat_inicio": -16.4050, "lng_inicio": -71.5150, "lat_fin": -16.4180, "lng_fin": -71.5350, "riesgo_historico": "Colapso de drenaje pluvial y bloqueo vehicular"},
    {"id": "puente_baixo_grado", "nombre": "Bypass Av. Ejercito / Av. Grau", "lat_inicio": -16.3910, "lng_inicio": -71.5450, "lat_fin": -16.3965, "lng_fin": -71.5415, "riesgo_historico": "Anegamiento profundo durante lluvias intensas"}
]

def esta_en_arequipa_metropolitana(lat: float, lng: float) -> bool:
    return (
        AREQUIPA_METRO_BOUNDS["lat_min"] <= lat <= AREQUIPA_METRO_BOUNDS["lat_max"]
        and AREQUIPA_METRO_BOUNDS["lng_min"] <= lng <= AREQUIPA_METRO_BOUNDS["lng_max"]
    )

def calcular_distancia_metros(lat1: float, lon1: float, lat2: float, lon2: float) -> float:
    R = 6371000.0
    phi1, phi2 = math.radians(lat1), math.radians(lat2)
    dphi = math.radians(lat2 - lat1)
    dlambda = math.radians(lon2 - lon1)
    a = math.sin(dphi / 2) ** 2 + math.cos(phi1) * math.cos(phi2) * math.sin(dlambda / 2) ** 2
    return 2 * R * math.atan2(math.sqrt(a), math.sqrt(1 - a))

def evaluar_proximidad_geografica(lat: float, lng: float) -> dict:
    zona_mas_cercana = "Arequipa Metropolitana"
    min_dist = 999999.0

    for ruta in RUTAS_HISTORIAL_BLOQUEO:
        d1 = calcular_distancia_metros(lat, lng, ruta["lat_inicio"], ruta["lng_inicio"])
        d2 = calcular_distancia_metros(lat, lng, ruta["lat_fin"], ruta["lng_fin"])
        d = min(d1, d2)
        if d < min_dist:
            min_dist = d
            zona_mas_cercana = ruta["nombre"]

    en_zona_critica = min_dist <= 450.0
    return {
        "zona_detectada": zona_mas_cercana,
        "distancia_riesgo_m": round(min_dist, 1),
        "en_zona_critica": en_zona_critica,
        "factor_multiplicador": 1.18 if en_zona_critica else 1.0
    }

async def obtener_riesgo_climatico_cayma() -> dict:
    url = "https://api.open-meteo.com/v1/forecast?latitude=-16.3989&longitude=-71.5350&current=precipitation,rain,temperature_2m&timezone=America%2FLima"
    try:
        async with httpx.AsyncClient(timeout=4.0) as client:
            resp = await client.get(url)
            data = resp.json().get("current", {})
            lluvia = float(data.get("precipitation", 0.0))
            nivel = "ALTO" if lluvia >= 5.0 else ("MODERADO" if lluvia >= 1.0 else "BAJO")
            return {
                "precipitacion_mm": lluvia,
                "temperatura_c": data.get("temperature_2m", 18.0),
                "alerta_torrenteras": nivel,
                "riesgo_activo_detectado": lluvia >= 1.5
            }
    except Exception:
        return {
            "precipitacion_mm": 0.0,
            "temperatura_c": 18.5,
            "alerta_torrenteras": "MONITOREO_LOCAL",
            "riesgo_activo_detectado": True
        }
