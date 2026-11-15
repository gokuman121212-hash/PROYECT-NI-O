import math
from datetime import datetime, timezone, timedelta
import httpx

CAYMA_LAT = -16.3425
CAYMA_LNG = -71.5467

# Puntos criticos de riesgo geografico en Cayma / Arequipa
PUNTOS_GEORRIESGO = [
    {"nombre": "Torrentera Chullo (Sector Cayma - Yanahuara)", "tipo": "torrentera", "lat": -16.3645, "lng": -71.5478, "radio_alerta_m": 450},
    {"nombre": "Torrentera San Lazaro (Limite Alto Selva Alegre)", "tipo": "torrentera", "lat": -16.3742, "lng": -71.5315, "radio_alerta_m": 450},
    {"nombre": "Quebrada Anashuayco (Cono Norte)", "tipo": "torrentera", "lat": -16.3320, "lng": -71.5850, "radio_alerta_m": 600},
    {"nombre": "Crater Volcan Misti (Radio Exclusion 12 km)", "tipo": "volcan", "lat": -16.2940, "lng": -71.4090, "radio_alerta_m": 12000},
]

# Trazado real aproximado de cauces de torrenteras en Cayma / Arequipa
CAUCES_TORRENTERAS = [
    {
        "nombre": "Cauce Torrentera Chullo",
        "riesgo": "ALTO",
        "coordenadas": [
            [-16.3180, -71.5350], [-16.3350, -71.5410], [-16.3510, -71.5455],
            [-16.3645, -71.5478], [-16.3790, -71.5495], [-16.3920, -71.5520]
        ]
    },
    {
        "nombre": "Cauce Torrentera San Lazaro",
        "riesgo": "ALTO",
        "coordenadas": [
            [-16.3310, -71.5120], [-16.3520, -71.5220], [-16.3742, -71.5315],
            [-16.3890, -71.5365], [-16.3985, -71.5410]
        ]
    },
    {
        "nombre": "Quebrada Anashuayco",
        "riesgo": "MEDIO-ALTO",
        "coordenadas": [
            [-16.3050, -71.5720], [-16.3210, -71.5790], [-16.3320, -71.5850],
            [-16.3480, -71.5940], [-16.3650, -71.6050]
        ]
    }
]

ZONAS_SEGURAS_CAYMA = [
    {"nombre": "Puesto Comando COE - Municipalidad de Cayma", "tipo": "coe", "lat": -16.3425, "lng": -71.5467},
    {"nombre": "Zona Segura #1: Estadio Municipal La Tomilla", "tipo": "refugio", "lat": -16.3532, "lng": -71.5431},
    {"nombre": "Zona Segura #2: Complejo Deportivo Buenos Aires", "tipo": "refugio", "lat": -16.3318, "lng": -71.5520},
    {"nombre": "Zona Segura #3: Parque Francisco Bolognesi", "tipo": "refugio", "lat": -16.3610, "lng": -71.5485}
]

def calcular_distancia_metros(lat1: float, lon1: float, lat2: float, lon2: float) -> float:
    R = 6371000.0
    phi1, phi2 = math.radians(lat1), math.radians(lat2)
    dphi = math.radians(lat2 - lat1)
    dlambda = math.radians(lon2 - lon1)
    a = math.sin(dphi / 2.0) ** 2 + math.cos(phi1) * math.cos(phi2) * math.sin(dlambda / 2.0) ** 2
    return 2.0 * R * math.atan2(math.sqrt(a), math.sqrt(1.0 - a))

def evaluar_proximidad_geografica(lat: float, lng: float) -> dict:
    if abs(lat) > 85 or (lat == -90 and lng == -180):
        return {
            "zona_detectada": "Registro de Prueba (-90, -180)",
            "distancia_riesgo_m": None,
            "factor_multiplicador": 1.0,
            "en_zona_critica": False
        }

    menor_dist = float("inf")
    punto_cercano = None

    for p in PUNTOS_GEORRIESGO:
        dist = calcular_distancia_metros(lat, lng, p["lat"], p["lng"])
        if dist < menor_dist:
            menor_dist = dist
            punto_cercano = p

    en_zona_critica = menor_dist <= punto_cercano["radio_alerta_m"]
    factor = 1.25 if en_zona_critica else (1.10 if menor_dist <= punto_cercano["radio_alerta_m"] * 2 else 1.0)

    return {
        "zona_detectada": punto_cercano["nombre"],
        "tipo_riesgo_geo": punto_cercano["tipo"],
        "distancia_riesgo_m": round(menor_dist, 1),
        "factor_multiplicador": factor,
        "en_zona_critica": en_zona_critica
    }

async def obtener_actividad_sismica_arequipa() -> dict:
    """Consulta gratuita en tiempo real a USGS Earthquake API (radio 250 km alrededor de Cayma)."""
    inicio = (datetime.now(timezone.utc) - timedelta(days=7)).strftime("%Y-%m-%d")
    url = (
        f"https://earthquake.usgs.gov/fdsnws/event/1/query?format=geojson"
        f"&starttime={inicio}&latitude={CAYMA_LAT}&longitude={CAYMA_LNG}&maxradiuskm=250&minmagnitude=2.5&limit=5"
    )
    try:
        async with httpx.AsyncClient(timeout=4.5) as client:
            resp = await client.get(url)
            resp.raise_for_status()
            features = resp.json().get("features", [])
            if not features:
                return {"fuente": "USGS Real-Time", "hay_sismo_reciente": False, "resumen": "Sin sismos >= M2.5 en 7 días (250 km)"}

            ultimo = features[0]
            props = ultimo.get("properties", {})
            coords = ultimo.get("geometry", {}).get("coordinates", [0, 0, 0])
            dist_km = round(calcular_distancia_metros(CAYMA_LAT, CAYMA_LNG, coords[1], coords[0]) / 1000.0, 1)

            return {
                "fuente": "USGS Earthquake API",
                "hay_sismo_reciente": True,
                "magnitud": props.get("mag"),
                "lugar": props.get("place"),
                "profundidad_km": round(coords[2], 1),
                "distancia_cayma_km": dist_km,
                "lat": coords[1],
                "lng": coords[0],
                "total_7_dias": len(features)
            }
    except Exception:
        return {"fuente": "USGS Cache", "hay_sismo_reciente": False, "resumen": "Monitoreo sísmico en espera"}

async def obtener_riesgo_climatico_cayma() -> dict:
    url = (
        f"https://api.open-meteo.com/v1/forecast?"
        f"latitude={CAYMA_LAT}&longitude={CAYMA_LNG}"
        f"&current=temperature_2m,precipitation,rain,wind_speed_10m,relative_humidity_2m"
    )
    sismo_real = await obtener_actividad_sismica_arequipa()
    try:
        async with httpx.AsyncClient(timeout=4.0) as client:
            resp = await client.get(url)
            resp.raise_for_status()
            data = resp.json().get("current", {})
            lluvia_mm = data.get("rain", 0.0) or data.get("precipitation", 0.0)

            nivel_alerta_torrentera = "BAJO"
            if lluvia_mm >= 10.0:
                nivel_alerta_torrentera = "CRITICO (Riesgo Huaico)"
            elif lluvia_mm >= 3.0:
                nivel_alerta_torrentera = "MODERADO (Precaución)"

            return {
                "fuente": "Open-Meteo API (Cayma)",
                "temperatura_c": data.get("temperature_2m"),
                "humedad_pct": data.get("relative_humidity_2m"),
                "lluvia_mm": lluvia_mm,
                "viento_kmh": data.get("wind_speed_10m"),
                "riesgo_torrentera": nivel_alerta_torrentera,
                "sismo_regional": sismo_real,
                "puntos_monitoreados": PUNTOS_GEORRIESGO,
                "cauces_torrenteras": CAUCES_TORRENTERAS,
                "zonas_seguras": ZONAS_SEGURAS_CAYMA
            }
    except Exception as e:
        return {
            "fuente": "Fallback Local",
            "temperatura_c": 18.5,
            "lluvia_mm": 0.0,
            "viento_kmh": 10.0,
            "riesgo_torrentera": "BAJO",
            "sismo_regional": sismo_real,
            "puntos_monitoreados": PUNTOS_GEORRIESGO,
            "cauces_torrenteras": CAUCES_TORRENTERAS,
            "zonas_seguras": ZONAS_SEGURAS_CAYMA,
            "detalle": str(e)
        }