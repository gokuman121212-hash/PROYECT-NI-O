from pydantic import BaseModel, Field
from typing import Optional, List
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
    ARCHIVADA = "ARCHIVADA"

class UsuarioRegistroRequest(BaseModel):
    nombre_completo: str = Field(..., min_length=3)
    telefono: str = Field(..., min_length=6)
    password: str = Field(..., min_length=4)
    distrito: str
    sector_referencia: str
    latitud: float
    longitud: float

class UsuarioLoginRequest(BaseModel):
    telefono: str
    password: str

class UsuarioResponse(BaseModel):
    usuario_id: str
    nombre_completo: str
    telefono: str
    distrito: str
    sector_referencia: str
    latitud: float
    longitud: float
    nodo_guardado: str

class AlertaCreate(BaseModel):
    usuario_id: str
    nombre_usuario: Optional[str] = "Vecino Arequipa"
    distrito: Optional[str] = "Arequipa"
    sector_referencia: Optional[str] = ""
    latitud: float = Field(..., ge=-90, le=90)
    longitud: float = Field(..., ge=-180, le=180)
    tipo: TipoEmergencia
    descripcion: Optional[str] = ""
    nivel_bateria: int = Field(..., ge=0, le=100)
    origen_offline_mesh: bool = False
    riesgo_sensor_verificado: bool = True

class AlertaResponse(AlertaCreate):
    alerta_id: str
    estado: EstadoAlerta = EstadoAlerta.ACTIVA
    confianza_ia: float
    nodo_guardado: str
    voluntarios_count: int = 0
    timestamp: datetime

class ApoyoVoluntarioRequest(BaseModel):
    voluntario_id: str
    nombre_voluntario: str
    latitud_voluntario: Optional[float] = None
    longitud_voluntario: Optional[float] = None

class MensajeChatRequest(BaseModel):
    remitente_id: str
    nombre_remitente: str
    rol: str = "vecino"  # "afectado" o "vecino"
    mensaje: str = Field(..., min_length=1, max_length=500)
    origen_mesh: bool = False
