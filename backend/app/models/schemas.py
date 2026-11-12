from pydantic import BaseModel, Field
from typing import Optional
from datetime import datetime
from enum import Enum

class TipoEmergencia(str, Enum):
    SISMO = "sismo"
    INCENDIO = "incendio"
    DESLIZAMIENTO = "deslizamiento"
    MEDICA = "medica"
    SEGURIDAD = "seguridad"
    SOS_CRITICO = "sos_critico"

class EstadoAlerta(str, Enum):
    ACTIVA = "ACTIVA"
    EN_PROCESO = "EN_PROCESO"
    ARCHIVADA = "ARCHIVADA"  # Nunca se elimina, pasa a auditoria post-desastre

class AlertaCreate(BaseModel):
    usuario_id: str
    latitud: float = Field(..., ge=-90, le=90)
    longitud: float = Field(..., ge=-180, le=180)
    tipo: TipoEmergencia
    descripcion: Optional[str] = ""
    nivel_bateria: int = Field(..., ge=0, le=100)
    origen_offline_mesh: bool = False

class AlertaResponse(AlertaCreate):
    alerta_id: str
    estado: EstadoAlerta = EstadoAlerta.ACTIVA
    confianza_ia: float
    nodo_guardado: str
    timestamp: datetime
