import httpx

CAYMA_LAT = -16.3425
CAYMA_LNG = -71.5467

async def obtener_riesgo_climatico_cayma() -> dict:
    url = (
        f"https://api.open-meteo.com/v1/forecast?"
        f"latitude={CAYMA_LAT}&longitude={CAYMA_LNG}"
        f"&current=temperature_2m,precipitation,rain,wind_speed_10m"
    )
    try:
        async with httpx.AsyncClient(timeout=4.0) as client:
            resp = await client.get(url)
            resp.raise_for_status()
            data = resp.json().get("current", {})
            lluvia_mm = data.get("rain", 0.0) or data.get("precipitation", 0.0)
            
            nivel_alerta_torrentera = "BAJO"
            if lluvia_mm >= 10.0:
                nivel_alerta_torrentera = "CRITICO (Riesgo alto de huaico/torrentera)"
            elif lluvia_mm >= 3.0:
                nivel_alerta_torrentera = "MODERADO (Precaucion en torrenteras)"

            return {
                "fuente": "Open-Meteo API",
                "temperatura_c": data.get("temperature_2m"),
                "lluvia_mm": lluvia_mm,
                "viento_kmh": data.get("wind_speed_10m"),
                "riesgo_torrentera": nivel_alerta_torrentera
            }
    except Exception as e:
        return {
            "fuente": "Fallback Local",
            "riesgo_torrentera": "SIN_CONEXION_EXTERNA",
            "detalle": str(e)
        }