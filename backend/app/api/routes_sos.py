import random
from fastapi import APIRouter, Request, HTTPException
from pydantic import BaseModel
from slowapi import Limiter
from slowapi.util import get_remote_address
from app.models.schemas import AlertaCreate, AlertaResponse, EstadoAlerta, TipoEmergencia
from app.services.db_redundancy import db_manager
from app.ml.classifier import ia_classifier
from app.integrations.external_risks import obtener_riesgo_climatico_cayma
from app.core.config import settings

router = APIRouter(prefix="/api/v1/alertas", tags=["Alertas SOS e IA"])
limiter = Limiter(key_func=get_remote_address)

class ConnectionManager:
    def __init__(self):
        self.active_connections: list = []

    async def connect(self, websocket):
        await websocket.accept()
        self.active_connections.append(websocket)

    def disconnect(self, websocket):
        if websocket in self.active_connections:
            self.active_connections.remove(websocket)

    async def broadcast(self, message: dict):
        for connection in list(self.active_connections):
            try:
                await connection.send_json(message)
            except Exception:
                self.disconnect(connection)

ws_manager = ConnectionManager()

class DespachoRequest(BaseModel):
    unidad: str = "Patrulla Serenazgo Cayma #04"

@router.post("/sos", response_model=AlertaResponse)
@limiter.limit("30/minute")
async def enviar_alerta_sos(request: Request, alerta: AlertaCreate):
    analisis_ia = ia_classifier.analizar_reporte(alerta.descripcion, alerta.tipo.value)

    if not analisis_ia["aprobado"] and analisis_ia["confianza"] < settings.CONFIDENCE_THRESHOLD:
        raise HTTPException(
            status_code=422,
            detail=f"Reporte retenido para revision manual (confianza IA: {analisis_ia['confianza']})"
        )

    resultado = await db_manager.guardar_alerta_redundante(
        alerta,
        confianza_ia=analisis_ia["confianza"],
        indice_riesgo=analisis_ia["indice_riesgo_calor"]
    )

    await ws_manager.broadcast({
        "evento": "NUEVA_ALERTA",
        "alerta": resultado
    })

    return AlertaResponse(**{k: v for k, v in resultado.items() if k in AlertaResponse.model_fields})

@router.patch("/{alerta_id}/despachar")
async def despachar_unidad_emergencia(alerta_id: str, body: DespachoRequest):
    await db_manager.actualizar_estado_alerta(alerta_id, EstadoAlerta.EN_PROCESO.value, unidad=body.unidad)
    await ws_manager.broadcast({
        "evento": "ACTUALIZACION_ESTADO",
        "alerta_id": alerta_id,
        "estado": EstadoAlerta.EN_PROCESO.value,
        "unidad_asignada": body.unidad
    })
    return {
        "alerta_id": alerta_id,
        "estado": EstadoAlerta.EN_PROCESO.value,
        "unidad_asignada": body.unidad,
        "mensaje": f"Unidad '{body.unidad}' despachada al incidente."
    }

@router.patch("/{alerta_id}/archivar")
async def archivar_alerta_atendida(alerta_id: str):
    await db_manager.actualizar_estado_alerta(alerta_id, EstadoAlerta.ARCHIVADA.value)
    await ws_manager.broadcast({
        "evento": "ACTUALIZACION_ESTADO",
        "alerta_id": alerta_id,
        "estado": EstadoAlerta.ARCHIVADA.value
    })
    return {"alerta_id": alerta_id, "estado": EstadoAlerta.ARCHIVADA.value, "mensaje": "Incidente archivado para auditoria historica."}

@router.post("/simular-cayma")
async def simular_incidente_tactico():
    escenarios = [
        {"lat": -16.3642, "lng": -71.5475, "tipo": TipoEmergencia.DESLIZAMIENTO, "desc": "Desborde de torrentera Chullo cerca a puente, agua subiendo rapido", "mesh": True, "bat": 22},
        {"lat": -16.3418, "lng": -71.5459, "tipo": TipoEmergencia.SISMO, "desc": "Sismo fuerte sentido en Plaza de Cayma, caida de muros antiguos", "mesh": False, "bat": 78},
        {"lat": -16.3525, "lng": -71.5512, "tipo": TipoEmergencia.INCENDIO, "desc": "Incendio estructural en vivienda sector Acequia Alta, humo denso", "mesh": False, "bat": 54},
        {"lat": -16.3315, "lng": -71.5840, "tipo": TipoEmergencia.SOS_CRITICO, "desc": "Personas atrapadas por lahar en quebrada Anashuayco sin senal celular", "mesh": True, "bat": 14},
    ]
    esc = random.choice(escenarios)
    alerta = AlertaCreate(
        usuario_id=f"ciudadano_cayma_{random.randint(100,999)}",
        latitud=esc["lat"] + random.uniform(-0.0015, 0.0015),
        longitud=esc["lng"] + random.uniform(-0.0015, 0.0015),
        tipo=esc["tipo"],
        descripcion=esc["desc"],
        nivel_bateria=esc["bat"],
        origen_offline_mesh=esc["mesh"]
    )
    analisis_ia = ia_classifier.analizar_reporte(alerta.descripcion, alerta.tipo.value)
    resultado = await db_manager.guardar_alerta_redundante(
        alerta,
        confianza_ia=analisis_ia["confianza"],
        indice_riesgo=analisis_ia["indice_riesgo_calor"]
    )
    await ws_manager.broadcast({"evento": "NUEVA_ALERTA", "alerta": resultado})
    return resultado

@router.get("/mapa-calor")
async def obtener_mapa_calor():
    puntos = await db_manager.obtener_puntos_persistentes()
    clima_actual = await obtener_riesgo_climatico_cayma()
    mesh_count = sum(1 for p in puntos if p.get("origen_offline_mesh"))
    en_proceso_count = sum(1 for p in puntos if p.get("estado") == EstadoAlerta.EN_PROCESO.value)
    criticos_count = sum(1 for p in puntos if p.get("en_zona_critica") or p.get("enjambre_confirmado"))

    return {
        "zona": "Cayma - Arequipa",
        "clima_y_torrenteras": clima_actual,
        "metricas_coe": {
            "total_activos": len(puntos),
            "en_atencion": en_proceso_count,
            "origen_mesh_offline": mesh_count,
            "zona_critica_o_enjambre": criticos_count
        },
        "total_puntos": len(puntos),
        "puntos": puntos
    }

@router.get("/health")
async def health_check():
    return {
        "status": "online",
        "ia_modelo_cargado": ia_classifier.fallback_model is not None,
        "firebase_conectado": db_manager.firebase_ready,
        "supabase_conectado": db_manager.supabase_ready,
        "clientes_coe_conectados": len(ws_manager.active_connections)
    }
@router.post("/purgar-pruebas")
async def purgar_alertas_prueba():
    """Archiva automaticamente registros con coordenadas invalidas o de prueba (-90, -180)."""
    alertas = await db_manager.obtener_todas_alertas(solo_activas=True)
    archivadas = 0
    for a in alertas:
        lat = float(a.get("latitud", 0))
        lng = float(a.get("longitud", 0))
        if abs(lat) > 85 or (lat == -90 and lng == -180):
            await db_manager.actualizar_estado_alerta(a["alerta_id"], EstadoAlerta.ARCHIVADA.value)
            archivadas += 1
    await ws_manager.broadcast({"evento": "PURGA_COMPLETADA", "archivadas": archivadas})
    return {"archivadas": archivadas, "mensaje": f"Se archivaron {archivadas} registros de prueba."}