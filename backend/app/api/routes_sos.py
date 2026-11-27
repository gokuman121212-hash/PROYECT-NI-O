import random
from fastapi import APIRouter, Request, HTTPException
from pydantic import BaseModel
from slowapi import Limiter
from slowapi.util import get_remote_address
from app.models.schemas import (
    AlertaCreate, AlertaResponse, EstadoAlerta, TipoEmergencia,
    UsuarioRegistroRequest, UsuarioLoginRequest, UsuarioResponse,
    ApoyoVoluntarioRequest, MensajeChatRequest
)
from app.services.db_redundancy import db_manager
from app.ml.classifier import ia_classifier
from app.integrations.external_risks import (
    obtener_riesgo_climatico_cayma,
    esta_en_arequipa_metropolitana,
    CENTROS_SEGURIDAD_AREQUIPA,
    RUTAS_HISTORIAL_BLOQUEO,
    DISTRITOS_AREQUIPA_METRO
)
from app.core.config import settings

router = APIRouter(prefix="/api/v1/alertas", tags=["Alertas SOS Arequipa"])
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

@router.post("/auth/registro", response_model=UsuarioResponse)
async def registrar_ciudadano(req: UsuarioRegistroRequest):
    if req.distrito not in DISTRITOS_AREQUIPA_METRO or not esta_en_arequipa_metropolitana(req.latitud, req.longitud):
        raise HTTPException(
            status_code=403,
            detail="Cobertura disponible unicamente en Arequipa Metropolitana (provincia de Arequipa). Otras provincias estaran en la v2."
        )
    usuario = await db_manager.registrar_usuario_dual(req)
    return UsuarioResponse(**{k: v for k, v in usuario.items() if k in UsuarioResponse.model_fields})

@router.post("/auth/login", response_model=UsuarioResponse)
async def iniciar_sesion(req: UsuarioLoginRequest):
    usuario = await db_manager.autenticar_usuario_dual(req.telefono, req.password)
    if not usuario:
        raise HTTPException(status_code=401, detail="Telefono o contrasena incorrectos.")
    return UsuarioResponse(**{k: v for k, v in usuario.items() if k in UsuarioResponse.model_fields})

@router.get("/geodata-arequipa")
async def obtener_geodata_arequipa():
    return {
        "distritos_habilitados": DISTRITOS_AREQUIPA_METRO,
        "centros_seguridad": CENTROS_SEGURIDAD_AREQUIPA,
        "rutas_bloqueo_historico": RUTAS_HISTORIAL_BLOQUEO
    }

@router.post("/sos", response_model=AlertaResponse)
@limiter.limit("30/minute")
async def enviar_alerta_sos(request: Request, alerta: AlertaCreate):
    if not esta_en_arequipa_metropolitana(alerta.latitud, alerta.longitud):
        raise HTTPException(
            status_code=403,
            detail="Fuera de zona de cobertura: solo se permiten alertas dentro de Arequipa Ciudad."
        )

    analisis_ia = ia_classifier.analizar_reporte(alerta.descripcion, alerta.tipo.value)
    if not analisis_ia["aprobado"] and analisis_ia["confianza"] < settings.CONFIDENCE_THRESHOLD:
        raise HTTPException(
            status_code=422,
            detail=f"Reporte retenido por filtro IA (confianza: {analisis_ia['confianza']})"
        )

    resultado = await db_manager.guardar_alerta_redundante(
        alerta,
        confianza_ia=analisis_ia["confianza"],
        indice_riesgo=analisis_ia["indice_riesgo_calor"]
    )
    await ws_manager.broadcast({"evento": "NUEVA_ALERTA", "alerta": resultado})
    return AlertaResponse(**{k: v for k, v in resultado.items() if k in AlertaResponse.model_fields})

@router.post("/{alerta_id}/apoyar")
async def confirmar_ayuda_en_camino(alerta_id: str, body: ApoyoVoluntarioRequest):
    res = await db_manager.registrar_voluntario_en_camino(alerta_id, body.voluntario_id, body.nombre_voluntario)
    await ws_manager.broadcast({
        "evento": "VOLUNTARIO_EN_CAMINO",
        "alerta_id": alerta_id,
        "voluntarios_count": res["voluntarios_count"],
        "nombre_voluntario": body.nombre_voluntario
    })
    return res

@router.get("/{alerta_id}/chat")
async def listar_chat_alerta(alerta_id: str):
    mensajes = await db_manager.obtener_mensajes_chat(alerta_id)
    return {"alerta_id": alerta_id, "mensajes": mensajes}

@router.post("/{alerta_id}/chat")
async def enviar_mensaje_chat(alerta_id: str, body: MensajeChatRequest):
    msg = await db_manager.agregar_mensaje_chat(alerta_id, body.model_dump())
    await ws_manager.broadcast({
        "evento": "NUEVO_MENSAJE_CHAT",
        "alerta_id": alerta_id,
        "mensaje": msg
    })
    return msg

@router.get("/mapa-calor")
async def obtener_mapa_calor():
    puntos = await db_manager.obtener_puntos_persistentes()
    clima_actual = await obtener_riesgo_climatico_cayma()
    return {
        "zona": "Arequipa Metropolitana",
        "clima_y_torrenteras": clima_actual,
        "centros_seguridad": CENTROS_SEGURIDAD_AREQUIPA,
        "rutas_bloqueo_historico": RUTAS_HISTORIAL_BLOQUEO,
        "total_puntos": len(puntos),
        "puntos": puntos
    }

@router.get("/health")
async def health_check():
    return {
        "status": "online",
        "firebase_conectado": db_manager.firebase_ready,
        "supabase_conectado": db_manager.supabase_ready
    }

@router.patch("/{alerta_id}/archivar")
async def archivar_o_detener_alerta(alerta_id: str, motivo: str = "Falsa alarma / Detenida por administrador"):
    await db_manager.actualizar_estado_alerta(alerta_id, EstadoAlerta.ARCHIVADA.value)
    await ws_manager.broadcast({
        "evento": "ALERTA_ARCHIVADA",
        "alerta_id": alerta_id,
        "estado": EstadoAlerta.ARCHIVADA.value,
        "motivo": motivo
    })
    return {
        "ok": True,
        "alerta_id": alerta_id,
        "estado": EstadoAlerta.ARCHIVADA.value,
        "motivo": motivo
    }
