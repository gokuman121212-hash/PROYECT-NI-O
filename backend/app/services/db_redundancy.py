import asyncio
import uuid
from datetime import datetime, timezone, timedelta
import os
import firebase_admin
from firebase_admin import credentials, firestore
from supabase import create_client, Client
from app.core.config import settings
from app.models.schemas import AlertaCreate, AlertaResponse, EstadoAlerta
from app.integrations.external_risks import calcular_distancia_metros, evaluar_proximidad_geografica

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
        row_supabase = {
            "alerta_id": data["alerta_id"],
            "usuario_id": data["usuario_id"],
            "latitud": data["latitud"],
            "longitud": data["longitud"],
            "tipo": data["tipo"],
            "descripcion": data["descripcion"],
            "nivel_bateria": data["nivel_bateria"],
            "origen_offline_mesh": data["origen_offline_mesh"],
            "estado": data["estado"],
            "confianza_ia": data["confianza_ia"],
            "indice_riesgo": data["indice_riesgo"],
            "timestamp": data["timestamp"]
        }
        loop = asyncio.get_running_loop()
        await loop.run_in_executor(
            None,
            lambda: self.db_supabase.table("alertas_archivo").insert(row_supabase).execute()
        )

    async def detectar_enjambre_espaciotemporal(self, lat: float, lng: float, tipo: str) -> dict:
        alertas_recientes = await self.obtener_todas_alertas(solo_activas=True)
        ahora = datetime.now(timezone.utc)
        vecinos = 0

        for item in alertas_recientes:
            try:
                ts = datetime.fromisoformat(str(item["timestamp"]).replace("Z", "+00:00"))
                if (ahora - ts) <= timedelta(minutes=15):
                    dist = calcular_distancia_metros(lat, lng, float(item["latitud"]), float(item["longitud"]))
                    if dist <= 500.0:
                        vecinos += 1
            except Exception:
                continue

        es_enjambre = vecinos >= 2
        return {
            "reportes_cercanos_500m": vecinos,
            "enjambre_confirmado": es_enjambre,
            "nivel_prioridad": "P1 - CRITICO ENJAMBRE" if es_enjambre else "P2 - ALTA PRIORIDAD"
        }

    async def guardar_alerta_redundante(self, alerta: AlertaCreate, confianza_ia: float, indice_riesgo: float = 0.85) -> dict:
        alerta_id = str(uuid.uuid4())
        ahora = datetime.now(timezone.utc)

        geo_eval = evaluar_proximidad_geografica(alerta.latitud, alerta.longitud)
        enjambre_eval = await self.detectar_enjambre_espaciotemporal(alerta.latitud, alerta.longitud, alerta.tipo.value)

        riesgo_ajustado = min(0.99, round(indice_riesgo * geo_eval["factor_multiplicador"] + (0.10 if enjambre_eval["enjambre_confirmado"] else 0.0), 2))
        confianza_ajustada = min(0.99, round(confianza_ia + (0.08 if enjambre_eval["enjambre_confirmado"] else 0.0), 2))

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
            "confianza_ia": confianza_ajustada,
            "indice_riesgo": riesgo_ajustado,
            "zona_cercana": geo_eval["zona_detectada"],
            "distancia_riesgo_m": geo_eval["distancia_riesgo_m"],
            "en_zona_critica": geo_eval["en_zona_critica"],
            "enjambre_confirmado": enjambre_eval["enjambre_confirmado"],
            "prioridad_coe": enjambre_eval["nivel_prioridad"],
            "unidad_asignada": None,
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

        payload["nodo_guardado"] = nodo_usado
        self.cache_local.append(payload)
        return payload

    async def actualizar_estado_alerta(self, alerta_id: str, nuevo_estado: str, unidad: str | None = None):
        cambios = {"estado": nuevo_estado}
        if unidad:
            cambios["unidad_asignada"] = unidad

        if self.supabase_ready:
            try:
                loop = asyncio.get_running_loop()
                await loop.run_in_executor(
                    None,
                    lambda: self.db_supabase.table("alertas_archivo").update({"estado": nuevo_estado}).eq("alerta_id", alerta_id).execute()
                )
            except Exception:
                pass

        if self.firebase_ready:
            try:
                loop = asyncio.get_running_loop()
                await loop.run_in_executor(
                    None,
                    lambda: self.db_firestore.collection("alertas_activas").document(alerta_id).update(cambios)
                )
            except Exception:
                pass

        for item in self.cache_local:
            if item.get("alerta_id") == alerta_id:
                item.update(cambios)

    async def obtener_todas_alertas(self, solo_activas: bool = False) -> list[dict]:
        registros: list[dict] = []

        if self.firebase_ready:
            try:
                loop = asyncio.get_running_loop()
                docs = await loop.run_in_executor(
                    None,
                    lambda: list(self.db_firestore.collection("alertas_activas").limit(150).stream())
                )
                for d in docs:
                    item = d.to_dict()
                    if solo_activas and item.get("estado") == EstadoAlerta.ARCHIVADA.value:
                        continue
                    registros.append(item)
            except Exception:
                pass

        if not registros and self.supabase_ready:
            try:
                loop = asyncio.get_running_loop()
                res = await loop.run_in_executor(
                    None,
                    lambda: self.db_supabase.table("alertas_archivo").select("*").order("timestamp", desc=True).limit(150).execute()
                )
                for r in res.data:
                    if solo_activas and r.get("estado") == EstadoAlerta.ARCHIVADA.value:
                        continue
                    registros.append(r)
            except Exception:
                pass

        if not registros and self.cache_local:
            registros = [
                x for x in self.cache_local
                if not (solo_activas and x.get("estado") == EstadoAlerta.ARCHIVADA.value)
            ]

        for r in registros:
            if "zona_cercana" not in r:
                geo = evaluar_proximidad_geografica(float(r.get("latitud", 0)), float(r.get("longitud", 0)))
                r["zona_cercana"] = geo["zona_detectada"]
                r["distancia_riesgo_m"] = geo["distancia_riesgo_m"]
                r["en_zona_critica"] = geo["en_zona_critica"]
            r.setdefault("prioridad_coe", "P2 - ALTA PRIORIDAD")
            r.setdefault("enjambre_confirmado", False)
            r.setdefault("unidad_asignada", None)

        registros.sort(key=lambda x: str(x.get("timestamp", "")), reverse=True)
        return registros

    async def obtener_puntos_persistentes(self) -> list[dict]:
        alertas = await self.obtener_todas_alertas(solo_activas=True)
        return [
            {
                "alerta_id": r.get("alerta_id"),
                "usuario_id": r.get("usuario_id", "anonimo"),
                "lat": r["latitud"],
                "lng": r["longitud"],
                "intensidad": r.get("indice_riesgo", 0.8),
                "confianza_ia": r.get("confianza_ia", 0.85),
                "categoria": r["tipo"],
                "descripcion": r.get("descripcion", ""),
                "estado": r.get("estado", "ACTIVA"),
                "nivel_bateria": r.get("nivel_bateria", 100),
                "origen_offline_mesh": r.get("origen_offline_mesh", False),
                "zona_cercana": r.get("zona_cercana", "Cayma"),
                "distancia_riesgo_m": r.get("distancia_riesgo_m"),
                "en_zona_critica": r.get("en_zona_critica", False),
                "enjambre_confirmado": r.get("enjambre_confirmado", False),
                "prioridad_coe": r.get("prioridad_coe", "P2 - ALTA PRIORIDAD"),
                "unidad_asignada": r.get("unidad_asignada"),
                "timestamp": r.get("timestamp")
            }
            for r in alertas
        ]

db_manager = DualDatabaseManager()