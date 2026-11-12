from fastapi import APIRouter, Request, HTTPException
from slowapi import Limiter
from slowapi.util import get_remote_address
from app.models.schemas import AlertaCreate, AlertaResponse, EstadoAlerta
from app.services.db_redundancy import db_manager
from app.ml.classifier import ia_classifier
from app.integrations.external_risks import obtener_riesgo_climatico_cayma
from app.core.config import settings

router = APIRouter(prefix="/api/v1/alertas", tags=["Alertas SOS e IA"])
limiter = Limiter(key_func=get_remote_address)

@router.post("/sos", response_model=AlertaResponse)
@limiter.limit("5/minute")
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
    return resultado

@router.patch("/{alerta_id}/archivar")
async def archivar_alerta_atendida(alerta_id: str):
    """Protocolo de retencion: nunca se elimina una alerta, pasa a estado ARCHIVADA para auditoria."""
    if db_manager.supabase_ready:
        try:
            db_manager.db_supabase.table("alertas_archivo").update(
                {"estado": EstadoAlerta.ARCHIVADA.value}
            ).eq("alerta_id", alerta_id).execute()
        except Exception as e:
            raise HTTPException(status_code=500, detail=f"Error archivando en Supabase: {e}")

    if db_manager.firebase_ready:
        try:
            db_manager.db_firestore.collection("alertas_activas").document(alerta_id).update(
                {"estado": EstadoAlerta.ARCHIVADA.value}
            )
        except Exception:
            pass

    return {"alerta_id": alerta_id, "estado": EstadoAlerta.ARCHIVADA.value, "mensaje": "Incidente archivado para auditoria historica."}

@router.get("/mapa-calor")
async def obtener_mapa_calor():
    puntos = await db_manager.obtener_puntos_persistentes()
    clima_actual = await obtener_riesgo_climatico_cayma()
    return {
        "zona": "Cayma - Arequipa",
        "clima_y_torrenteras": clima_actual,
        "total_puntos": len(puntos),
        "puntos": puntos
    }

@router.get("/health")
async def health_check():
    return {
        "status": "online",
        "ia_modelo_cargado": ia_classifier.fallback_model is not None,
        "firebase_conectado": db_manager.firebase_ready,
        "supabase_conectado": db_manager.supabase_ready
    }