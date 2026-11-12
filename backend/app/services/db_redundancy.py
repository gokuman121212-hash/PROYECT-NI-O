import asyncio
import uuid
from datetime import datetime, timezone
import os
import firebase_admin
from firebase_admin import credentials, firestore
from supabase import create_client, Client
from app.core.config import settings
from app.models.schemas import AlertaCreate, AlertaResponse, EstadoAlerta

class DualDatabaseManager:
    def __init__(self):
        self.firebase_ready = False
        self.supabase_ready = False
        self.db_firestore = None
        self.db_supabase: Client | None = None
        self._init_clients()

    def _init_clients(self):
        # Inicializar Firebase si existe el archivo JSON de credenciales
        if os.path.exists(settings.FIREBASE_CREDENTIALS_PATH):
            try:
                if not firebase_admin._apps:
                    cred = credentials.Certificate(settings.FIREBASE_CREDENTIALS_PATH)
                    firebase_admin.initialize_app(cred)
                self.db_firestore = firestore.client()
                self.firebase_ready = True
            except Exception as e:
                print(f"[WARN] Firebase no disponible: {e}")

        # Inicializar Supabase si existen URL y KEY reales
        if settings.SUPABASE_URL.startswith("https://") and "tu-proyecto" not in settings.SUPABASE_URL:
            try:
                self.db_supabase = create_client(settings.SUPABASE_URL, settings.SUPABASE_KEY)
                self.supabase_ready = True
            except Exception as e:
                print(f"[WARN] Supabase no disponible: {e}")

    async def _save_firebase(self, data: dict):
        if not self.firebase_ready:
            raise ConnectionError("Firebase no inicializado")
        loop = asyncio.get_running_loop()
        await loop.run_in_executor(
            None,
            lambda: self.db_firestore.collection("alertas_activas").document(data["alerta_id"]).set(data)
        )

    async def _save_supabase(self, data: dict):
        if not self.supabase_ready:
            raise ConnectionError("Supabase no inicializado")
        loop = asyncio.get_running_loop()
        await loop.run_in_executor(
            None,
            lambda: self.db_supabase.table("alertas_archivo").insert(data).execute()
        )

    async def guardar_alerta_redundante(self, alerta: AlertaCreate, confianza_ia: float) -> AlertaResponse:
        alerta_id = str(uuid.uuid4())
        ahora = datetime.now(timezone.utc)
        
        payload = {
            "alerta_id": alerta_id,
            "usuario_id": alerta.usuario_id,
            "latitud": alerta.latitud,
            "longitud": alerta.longitud,
            "tipo": alerta.tipo.value,
            "descripcion": alerta.descripcion,
            "nivel_bateria": alerta.nivel_bateria,
            "origen_offline_mesh": alerta.origen_offline_mesh,
            "estado": EstadoAlerta.ACTIVA.value,
            "confianza_ia": confianza_ia,
            "timestamp": ahora.isoformat()
        }

        nodo_usado = "LOCAL_FALLBACK"

        # 1. Intentar escritura primaria en Firebase con timeout de 3 segundos
        try:
            await asyncio.wait_for(self._save_firebase(payload), timeout=3.0)
            nodo_usado = "FIREBASE_PRIMARY"
            # Replicar asincronamente en Supabase para auditoria inmutable
            if self.supabase_ready:
                asyncio.create_task(self._save_supabase(payload))
        except Exception:
            # 2. Failover automatico a Supabase si Firebase cae o hay latencia critica
            try:
                await asyncio.wait_for(self._save_supabase(payload), timeout=4.0)
                nodo_usado = "SUPABASE_FAILOVER"
            except Exception:
                # 3. Modo simulacion/contingencia local si aun no configuras claves en .env
                nodo_usado = "MODO_LOCAL_SIN_CLAVES"

        return AlertaResponse(
            **alerta.model_dump(),
            alerta_id=alerta_id,
            estado=EstadoAlerta.ACTIVA,
            confianza_ia=confianza_ia,
            nodo_guardado=nodo_usado,
            timestamp=ahora
        )

db_manager = DualDatabaseManager()
