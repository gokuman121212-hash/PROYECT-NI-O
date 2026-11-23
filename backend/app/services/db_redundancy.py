import asyncio
import uuid
import hashlib
from datetime import datetime, timezone, timedelta
import os
import firebase_admin
from firebase_admin import credentials, firestore
from supabase import create_client, Client
from app.core.config import settings
from app.models.schemas import AlertaCreate, EstadoAlerta, UsuarioRegistroRequest
from app.integrations.external_risks import calcular_distancia_metros, evaluar_proximidad_geografica

class DualDatabaseManager:
    def __init__(self):
        self.firebase_ready = False
        self.supabase_ready = False
        self.db_firestore = None
        self.db_supabase: Client | None = None
        self.cache_local: list[dict] = []
        self.usuarios_local: dict[str, dict] = {}
        self.chat_local: dict[str, list[dict]] = {}
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

    def _hash_pass(self, raw: str) -> str:
        return hashlib.sha256(raw.encode("utf-8")).hexdigest()

    async def registrar_usuario_dual(self, req: UsuarioRegistroRequest) -> dict:
        usuario_id = f"usr_{uuid.uuid4().hex[:10]}"
        user_doc = {
            "usuario_id": usuario_id,
            "nombre_completo": req.nombre_completo,
            "telefono": req.telefono.strip(),
            "password_hash": self._hash_pass(req.password),
            "distrito": req.distrito,
            "sector_referencia": req.sector_referencia,
            "latitud": req.latitud,
            "longitud": req.longitud,
            "creado_en": datetime.now(timezone.utc).isoformat()
        }
        nodo = "CACHE_LOCAL_SEGURO"
        if self.firebase_ready:
            try:
                loop = asyncio.get_running_loop()
                await loop.run_in_executor(
                    None,
                    lambda: self.db_firestore.collection("usuarios_arequipa").document(req.telefono.strip()).set(user_doc)
                )
                nodo = "FIREBASE + SUPABASE" if self.supabase_ready else "FIREBASE_PRIMARY"
            except Exception:
                pass
        if self.supabase_ready:
            try:
                loop = asyncio.get_running_loop()
                await loop.run_in_executor(
                    None,
                    lambda: self.db_supabase.table("usuarios_arequipa").upsert(user_doc).execute()
                )
                if nodo == "CACHE_LOCAL_SEGURO":
                    nodo = "SUPABASE_FAILOVER"
            except Exception:
                pass

        self.usuarios_local[req.telefono.strip()] = user_doc
        user_doc["nodo_guardado"] = nodo
        return user_doc

    async def autenticar_usuario_dual(self, telefono: str, password: str) -> dict | None:
        tel = telefono.strip()
        pwd_hash = self._hash_pass(password)
        encontrado = None

        if self.firebase_ready:
            try:
                loop = asyncio.get_running_loop()
                snap = await loop.run_in_executor(
                    None,
                    lambda: self.db_firestore.collection("usuarios_arequipa").document(tel).get()
                )
                if snap.exists:
                    encontrado = snap.to_dict()
                    encontrado["nodo_guardado"] = "FIREBASE_PRIMARY"
            except Exception:
                pass

        if not encontrado and self.supabase_ready:
            try:
                loop = asyncio.get_running_loop()
                res = await loop.run_in_executor(
                    None,
                    lambda: self.db_supabase.table("usuarios_arequipa").select("*").eq("telefono", tel).execute()
                )
                if res.data:
                    encontrado = res.data[0]
                    encontrado["nodo_guardado"] = "SUPABASE_FAILOVER"
            except Exception:
                pass

        if not encontrado:
            encontrado = self.usuarios_local.get(tel)
            if encontrado:
                encontrado["nodo_guardado"] = "CACHE_LOCAL_SEGURO"

        if encontrado and encontrado.get("password_hash") == pwd_hash:
            return encontrado
        return None

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

    async def detectar_enjambre_espaciotemporal(self, lat: float, lng: float) -> dict:
        alertas_recientes = await self.obtener_todas_alertas(solo_activas=True)
        ahora = datetime.now(timezone.utc)
        vecinos = 0
        for item in alertas_recientes:
            try:
                ts = datetime.fromisoformat(str(item["timestamp"]).replace("Z", "+00:00"))
                if (ahora - ts) <= timedelta(minutes=15):
                    if calcular_distancia_metros(lat, lng, float(item["latitud"]), float(item["longitud"])) <= 500.0:
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
        enjambre_eval = await self.detectar_enjambre_espaciotemporal(alerta.latitud, alerta.longitud)

        riesgo_ajustado = min(0.99, round(indice_riesgo * geo_eval["factor_multiplicador"] + (0.10 if enjambre_eval["enjambre_confirmado"] else 0.0), 2))
        confianza_ajustada = min(0.99, round(confianza_ia + (0.08 if enjambre_eval["enjambre_confirmado"] else 0.0), 2))

        payload = {
            "alerta_id": alerta_id,
            "usuario_id": alerta.usuario_id,
            "nombre_usuario": alerta.nombre_usuario or "Vecino Arequipa",
            "distrito": alerta.distrito or "Arequipa",
            "sector_referencia": alerta.sector_referencia or "",
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
            "voluntarios": [],
            "voluntarios_count": 0,
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
                pass

        payload["nodo_guardado"] = nodo_usado
        self.cache_local.append(payload)
        return payload

    async def registrar_voluntario_en_camino(self, alerta_id: str, voluntario_id: str, nombre_voluntario: str) -> dict:
        voluntarios_actuales = []
        for item in self.cache_local:
            if item.get("alerta_id") == alerta_id:
                lista = item.setdefault("voluntarios", [])
                if not any(v.get("id") == voluntario_id for v in lista):
                    lista.append({"id": voluntario_id, "nombre": nombre_voluntario, "hora": datetime.now(timezone.utc).isoformat()})
                item["voluntarios_count"] = len(lista)
                item["estado"] = EstadoAlerta.EN_PROCESO.value
                voluntarios_actuales = lista
                break

        if self.firebase_ready:
            try:
                loop = asyncio.get_running_loop()
                doc_ref = self.db_firestore.collection("alertas_activas").document(alerta_id)
                snap = await loop.run_in_executor(None, doc_ref.get)
                if snap.exists:
                    data = snap.to_dict()
                    lista = data.get("voluntarios", [])
                    if not any(v.get("id") == voluntario_id for v in lista):
                        lista.append({"id": voluntario_id, "nombre": nombre_voluntario, "hora": datetime.now(timezone.utc).isoformat()})
                    voluntarios_actuales = lista
                    await loop.run_in_executor(None, lambda: doc_ref.update({
                        "voluntarios": lista,
                        "voluntarios_count": len(lista),
                        "estado": EstadoAlerta.EN_PROCESO.value
                    }))
            except Exception:
                pass

        return {
            "alerta_id": alerta_id,
            "voluntarios_count": len(voluntarios_actuales) if voluntarios_actuales else 1,
            "voluntarios": voluntarios_actuales
        }

    async def agregar_mensaje_chat(self, alerta_id: str, msg_data: dict) -> dict:
        msg_data["mensaje_id"] = uuid.uuid4().hex[:8]
        msg_data["timestamp"] = datetime.now(timezone.utc).isoformat()
        self.chat_local.setdefault(alerta_id, []).append(msg_data)

        if self.firebase_ready:
            try:
                loop = asyncio.get_running_loop()
                await loop.run_in_executor(
                    None,
                    lambda: self.db_firestore.collection("alertas_activas").document(alerta_id).collection("chat").document(msg_data["mensaje_id"]).set(msg_data)
                )
            except Exception:
                pass
        return msg_data

    async def obtener_mensajes_chat(self, alerta_id: str) -> list[dict]:
        if self.firebase_ready:
            try:
                loop = asyncio.get_running_loop()
                docs = await loop.run_in_executor(
                    None,
                    lambda: list(self.db_firestore.collection("alertas_activas").document(alerta_id).collection("chat").order_by("timestamp").stream())
                )
                if docs:
                    return [d.to_dict() for d in docs]
            except Exception:
                pass
        return self.chat_local.get(alerta_id, [])

    async def actualizar_estado_alerta(self, alerta_id: str, nuevo_estado: str, unidad: str | None = None):
        cambios = {"estado": nuevo_estado}
        if unidad:
            cambios["unidad_asignada"] = unidad
        if self.firebase_ready:
            try:
                loop = asyncio.get_running_loop()
                await loop.run_in_executor(None, lambda: self.db_firestore.collection("alertas_activas").document(alerta_id).update(cambios))
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
                docs = await loop.run_in_executor(None, lambda: list(self.db_firestore.collection("alertas_activas").limit(150).stream()))
                for d in docs:
                    item = d.to_dict()
                    if solo_activas and item.get("estado") == EstadoAlerta.ARCHIVADA.value:
                        continue
                    registros.append(item)
            except Exception:
                pass

        if not registros and self.cache_local:
            registros = [x for x in self.cache_local if not (solo_activas and x.get("estado") == EstadoAlerta.ARCHIVADA.value)]

        for r in registros:
            r.setdefault("nombre_usuario", "Vecino Arequipa")
            r.setdefault("distrito", "Arequipa")
            r.setdefault("sector_referencia", "")
            r.setdefault("voluntarios_count", len(r.get("voluntarios", [])))
            r.setdefault("prioridad_coe", "P2 - ALTA PRIORIDAD")
            r.setdefault("enjambre_confirmado", False)

        registros.sort(key=lambda x: str(x.get("timestamp", "")), reverse=True)
        return registros

    async def obtener_puntos_persistentes(self) -> list[dict]:
        alertas = await self.obtener_todas_alertas(solo_activas=True)
        return [
            {
                "alerta_id": r.get("alerta_id"),
                "usuario_id": r.get("usuario_id", "anonimo"),
                "nombre_usuario": r.get("nombre_usuario", "Vecino Arequipa"),
                "distrito": r.get("distrito", "Arequipa"),
                "sector_referencia": r.get("sector_referencia", ""),
                "lat": r["latitud"],
                "lng": r["longitud"],
                "intensidad": r.get("indice_riesgo", 0.8),
                "confianza_ia": r.get("confianza_ia", 0.85),
                "categoria": r["tipo"],
                "descripcion": r.get("descripcion", ""),
                "estado": r.get("estado", "ACTIVA"),
                "nivel_bateria": r.get("nivel_bateria", 100),
                "origen_offline_mesh": r.get("origen_offline_mesh", False),
                "voluntarios_count": r.get("voluntarios_count", 0),
                "zona_cercana": r.get("zona_cercana", "Arequipa"),
                "en_zona_critica": r.get("en_zona_critica", False),
                "enjambre_confirmado": r.get("enjambre_confirmado", False),
                "timestamp": r.get("timestamp")
            }
            for r in alertas
        ]

db_manager = DualDatabaseManager()
