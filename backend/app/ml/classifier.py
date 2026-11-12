import os
import json
import httpx
import joblib
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.naive_bayes import MultinomialNB
from sklearn.pipeline import make_pipeline
from app.core.config import settings

MODEL_PATH = "app/ml/modelo_emergencias.joblib"
OLLAMA_URL = os.getenv("OLLAMA_URL", "http://localhost:11434/api/generate")
OLLAMA_DEDICATED_MODEL = os.getenv("OLLAMA_MODEL", "alerta-cayma")
OLLAMA_BASE_MODEL = "llama3.2:1b"

DATOS_ENTRENAMIENTO = [
    ("fuego humo incendio forestal quemando pastizal quebrada", "incendio"),
    ("llamas casa quemandose explosion gas humo negro", "incendio"),
    ("temblor fuerte terremoto sismo sacudida paredes rajadas sillar", "sismo"),
    ("movimiento telurico replica fuerte caida de muros misti", "sismo"),
    ("huaico deslizamiento lodo piedras bajando torrentera chullo", "deslizamiento"),
    ("derrumbe cerro bloqueo via rocas cayendo lluvia fuerte", "deslizamiento"),
    ("herido accidente choque ambulancia desmayo sangre", "medica"),
    ("persona inconsciente fractura emergencia medica paro", "medica"),
    ("robo asalto delincuentes sospechosos peligro calle", "seguridad"),
    ("pandillas violencia pelea agresion auxilio policia", "seguridad"),
]

class EmergencyClassifier:
    def __init__(self):
        self.fallback_model = None
        self._load_or_train_fallback()

    def _load_or_train_fallback(self):
        if os.path.exists(MODEL_PATH):
            self.fallback_model = joblib.load(MODEL_PATH)
        else:
            textos = [item[0] for item in DATOS_ENTRENAMIENTO]
            etiquetas = [item[1] for item in DATOS_ENTRENAMIENTO]
            self.fallback_model = make_pipeline(TfidfVectorizer(), MultinomialNB(alpha=0.1))
            self.fallback_model.fit(textos, etiquetas)
            joblib.dump(self.fallback_model, MODEL_PATH)

    def _consultar_ollama_especializado(self, descripcion: str, tipo_manual: str) -> dict | None:
        prompt = f"Reporte ciudadano en Cayma (categoria seleccionada: {tipo_manual}): {descripcion}"
        
        for modelo in [OLLAMA_DEDICATED_MODEL, OLLAMA_BASE_MODEL]:
            try:
                with httpx.Client(timeout=4.5) as client:
                    resp = client.post(
                        OLLAMA_URL,
                        json={
                            "model": modelo,
                            "prompt": prompt,
                            "format": "json",
                            "stream": False
                        }
                    )
                    if resp.status_code == 404:
                        continue
                    resp.raise_for_status()
                    raw_json = resp.json().get("response", "{}")
                    data = json.loads(raw_json)
                    confianza = float(data.get("confianza", 0.85))
                    return {
                        "motor_ia": f"Ollama ({modelo})",
                        "tipo_clasificado": data.get("tipo_clasificado", tipo_manual),
                        "confianza": round(confianza, 2),
                        "aprobado": confianza >= settings.CONFIDENCE_THRESHOLD,
                        "indice_riesgo_calor": round(float(data.get("indice_riesgo_calor", 0.80)), 2),
                        "accion_inmediata": data.get("accion_inmediata", "Mantenga la calma y dirijase a una zona segura.")
                    }
            except Exception:
                continue
        return None

    def analizar_reporte(self, descripcion: str, tipo_manual: str) -> dict:
        if tipo_manual == "sos_critico":
            return {
                "motor_ia": "Prioridad Critica SOS",
                "tipo_clasificado": "sos_critico",
                "confianza": 0.99,
                "aprobado": True,
                "indice_riesgo_calor": 1.0,
                "accion_inmediata": "Alerta SOS enviada a unidades de respuesta en Cayma."
            }

        # 1. Consultar modelo Ollama entrenado exclusivamente para Alerta Cayma
        if descripcion and len(descripcion.strip()) >= 3:
            resultado_ollama = self._consultar_ollama_especializado(descripcion, tipo_manual)
            if resultado_ollama:
                return resultado_ollama

        # 2. Respaldo local instantaneo con Scikit-Learn
        if not descripcion or len(descripcion.strip()) < 4:
            return {
                "motor_ia": "Validacion Rapida",
                "tipo_clasificado": tipo_manual,
                "confianza": 0.82,
                "aprobado": True,
                "indice_riesgo_calor": 0.65,
                "accion_inmediata": "Reporte registrado en el sistema."
            }

        probabilidades = self.fallback_model.predict_proba([descripcion.lower()])[0]
        max_prob = float(max(probabilidades))
        clase_predicha = str(self.fallback_model.classes_[probabilidades.argmax()])
        pesos = {"sismo": 0.95, "deslizamiento": 0.90, "incendio": 0.88, "medica": 0.75, "seguridad": 0.70}

        return {
            "motor_ia": "Scikit-Learn Local",
            "tipo_clasificado": clase_predicha,
            "confianza": round(max_prob, 2),
            "aprobado": max_prob >= settings.CONFIDENCE_THRESHOLD or len(descripcion.split()) >= 2,
            "indice_riesgo_calor": round(pesos.get(clase_predicha, 0.60) * max_prob, 2),
            "accion_inmediata": "Evacue zonas de riesgo y mantenga activo su GPS."
        }

ia_classifier = EmergencyClassifier()