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
        self.cache_local: list[dict] = []
        self._init_clients()

    def _init_clients(self):
        if os.path.exists(settings.FIREBASE_CREDENTIALS_PATH):
            try:
                if not firebase_admin._apps:
                    cred = credentials.Certificate(settings.FIREBASE_CREDENTIALS_PATH)
                    firebase_admin.initialize_app(cred)
                self.db_firestore = firestore.client()
                self.firebase_ready = True
            except Exception as e:
                print(f"[WARN] Firebase no disponible: {e}")

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

    async def guardar_alerta_redundante(self, alerta: AlertaCreate, confianza_ia: float, indice_riesgo: float = 0.85) -> AlertaResponse:
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
            "indice_riesgo": indice_riesgo,
            "timestamp": ahora.isoformat()
        }

        nodo_usado = "MODO_LOCAL_SIN_CLAVES"

        try:
            await asyncio.wait_for(self._save_firebase(payload), timeout=3.0)
            nodo_usado = "FIREBASE_PRIMARY"
            if self.supabase_ready:
                asyncio.create_task(self._save_supabase(payload))
        except Exception:
            try:
                await asyncio.wait_for(self._save_supabase(payload), timeout=4.0)
                nodo_usado = "SUPABASE_FAILOVER"
            except Exception:
                self.cache_local.append(payload)

        return AlertaResponse(
            **alerta.model_dump(),
            alerta_id=alerta_id,
            estado=EstadoAlerta.ACTIVA,
            confianza_ia=confianza_ia,
            nodo_guardado=nodo_usado,
            timestamp=ahora
        )

    async def obtener_puntos_persistentes(self) -> list[dict]:
        if self.supabase_ready:
            try:
                loop = asyncio.get_running_loop()
                res = await loop.run_in_executor(
                    None,
                    lambda: self.db_supabase.table("alertas_archivo")
                    .select("latitud,longitud,indice_riesgo,tipo")
                    .order("timestamp", desc=True)
                    .limit(100)
                    .execute()
                )
                return [
                    {
                        "lat": r["latitud"],
                        "lng": r["longitud"],
                        "intensidad": r.get("indice_riesgo", 0.8),
                        "categoria": r["tipo"]
                    }
                    for r in res.data
                ]
            except Exception:
                pass

        if self.firebase_ready:
            try:
                loop = asyncio.get_running_loop()
                docs = await loop.run_in_executor(
                    None,
                    lambda: list(self.db_firestore.collection("alertas_activas").limit(100).stream())
                )
                puntos = []
                for d in docs:
                    item = d.to_dict()
                    puntos.append({
                        "lat": item["latitud"],
                        "lng": item["longitud"],
                        "intensidad": item.get("indice_riesgo", 0.8),
                        "categoria": item["tipo"]
                    })
                return puntos
            except Exception:
                pass

        puntos_base = [
            {"lat": -16.3380, "lng": -71.5450, "intensidad": 0.85, "categoria": "torrentera_cayma"},
            {"lat": -16.3510, "lng": -71.5490, "intensidad": 0.75, "categoria": "zona_sismica"}
        ]
        puntos_cache = [
            {
                "lat": x["latitud"],
                "lng": x["longitud"],
                "intensidad": x.get("indice_riesgo", 0.85),
                "categoria": x["tipo"]
            }
            for x in self.cache_local
        ]
        return puntos_base + puntos_cache

db_manager = DualDatabaseManager()