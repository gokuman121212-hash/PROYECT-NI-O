from fastapi import APIRouter, Request, HTTPException
from slowapi import Limiter
from slowapi.util import get_remote_address
from app.models.schemas import AlertaCreate, AlertaResponse, TipoEmergencia
from app.services.db_redundancy import db_manager
from app.core.config import settings

router = APIRouter(prefix="/api/v1/alertas", tags=["Alertas SOS"])
limiter = Limiter(key_func=get_remote_address)

@router.post("/sos", response_model=AlertaResponse)
@limiter.limit("5/minute")  # Proteccion contra saturacion por panico o spam
async def enviar_alerta_sos(request: Request, alerta: AlertaCreate):
    # Prioridad maxima si es boton SOS critico
    confianza_ia = 0.99 if alerta.tipo == TipoEmergencia.SOS_CRITICO else 0.85

    if confianza_ia < settings.CONFIDENCE_THRESHOLD:
        raise HTTPException(status_code=422, detail="Alerta descartada por bajo umbral de confianza")

    resultado = await db_manager.guardar_alerta_redundante(alerta, confianza_ia)
    return resultado

@router.get("/health")
async def health_check():
    return {
        "status": "online",
        "firebase_conectado": db_manager.firebase_ready,
        "supabase_conectado": db_manager.supabase_ready
    }
