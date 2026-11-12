from fastapi import APIRouter, Request, HTTPException
from slowapi import Limiter
from slowapi.util import get_remote_address
from app.models.schemas import AlertaCreate, AlertaResponse, TipoEmergencia
from app.services.db_redundancy import db_manager
from app.ml.classifier import ia_classifier
from app.core.config import settings

router = APIRouter(prefix="/api/v1/alertas", tags=["Alertas SOS e IA"])
limiter = Limiter(key_func=get_remote_address)

# Registro temporal en memoria para pruebas rapidas del mapa de calor
historial_calor = []

@router.post("/sos", response_model=AlertaResponse)
@limiter.limit("5/minute")
async def enviar_alerta_sos(request: Request, alerta: AlertaCreate):
    analisis_ia = ia_classifier.analizar_reporte(alerta.descripcion, alerta.tipo.value)

    if not analisis_ia["aprobado"] and analisis_ia["confianza"] < settings.CONFIDENCE_THRESHOLD:
        raise HTTPException(
            status_code=422,
            detail=f"Reporte retenido para revision manual (confianza IA: {analisis_ia['confianza']})"
        )

    resultado = await db_manager.guardar_alerta_redundante(alerta, analisis_ia["confianza"])
    
    historial_calor.append({
        "lat": alerta.latitud,
        "lng": alerta.longitud,
        "intensidad": analisis_ia["indice_riesgo_calor"],
        "categoria": analisis_ia["tipo_clasificado"]
    })
    return resultado

@router.get("/mapa-calor")
async def obtener_mapa_calor():
    return {
        "zona": "Cayma - Arequipa",
        "total_puntos": len(historial_calor),
        "puntos": historial_calor
    }

@router.get("/health")
async def health_check():
    return {
        "status": "online",
        "ia_modelo_cargado": ia_classifier.model is not None,
        "firebase_conectado": db_manager.firebase_ready,
        "supabase_conectado": db_manager.supabase_ready
    }
