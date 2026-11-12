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
OLLAMA_MODEL = os.getenv("OLLAMA_MODEL", "llama3.2:1b")

DATOS_ENTRENAMIENTO = [
    ("fuego humo incendio forestal quemando pastizal quebrada", "incendio"),
    ("llamas casa quemandose explosion gas humo negro", "incendio"),
    ("temblor fuerte terremoto sismo sacudida paredes rajadas", "sismo"),
    ("movimiento telurico replica fuerte caida de muros", "sismo"),
    ("huaico deslizamiento lodo piedras bajando torrentera", "deslizamiento"),
    ("derrumbe cerro bloqueo via rocas cayendo", "deslizamiento"),
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

    def _consultar_ollama(self, descripcion: str, tipo_manual: str) -> dict | None:
        prompt = (
            f"Eres un sistema experto de gestion de riesgos en Cayma, Arequipa. "
            f"Analiza este reporte ciudadano: '{descripcion}' (categoria sugerida: '{tipo_manual}'). "
            f"Responde UNICAMENTE con un objeto JSON valido con estas 3 claves exactas: "
            f'"tipo_clasificado" (valores permitidos: sismo, incendio, deslizamiento, medica, seguridad), '
            f'"confianza" (numero decimal entre 0.0 y 1.0 segun que tan creible y claro es el reporte), '
            f'"indice_riesgo_calor" (numero decimal entre 0.1 y 1.0 indicando gravedad para el mapa de calor).'
        )
        try:
            with httpx.Client(timeout=4.0) as client:
                resp = client.post(
                    OLLAMA_URL,
                    json={
                        "model": OLLAMA_MODEL,
                        "prompt": prompt,
                        "format": "json",
                        "stream": False
                    }
                )
                resp.raise_for_status()
                raw_json = resp.json().get("response", "{}")
                data = json.loads(raw_json)
                confianza = float(data.get("confianza", 0.85))
                return {
                    "motor_ia": f"Ollama ({OLLAMA_MODEL})",
                    "tipo_clasificado": data.get("tipo_clasificado", tipo_manual),
                    "confianza": round(confianza, 2),
                    "aprobado": confianza >= settings.CONFIDENCE_THRESHOLD,
                    "indice_riesgo_calor": round(float(data.get("indice_riesgo_calor", 0.80)), 2)
                }
        except Exception:
            return None

    def analizar_reporte(self, descripcion: str, tipo_manual: str) -> dict:
        if tipo_manual == "sos_critico":
            return {
                "motor_ia": "Prioridad Critica SOS",
                "tipo_clasificado": "sos_critico",
                "confianza": 0.99,
                "aprobado": True,
                "indice_riesgo_calor": 1.0
            }

        # 1. Intentar analizar primero con Ollama local gratuito
        if descripcion and len(descripcion.strip()) >= 4:
            resultado_ollama = self._consultar_ollama(descripcion, tipo_manual)
            if resultado_ollama:
                return resultado_ollama

        # 2. Respaldo instantaneo con Scikit-Learn si Ollama no esta abierto en la PC
        if not descripcion or len(descripcion.strip()) < 4:
            return {
                "motor_ia": "Validacion Rapida",
                "tipo_clasificado": tipo_manual,
                "confianza": 0.82,
                "aprobado": True,
                "indice_riesgo_calor": 0.65
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
            "indice_riesgo_calor": round(pesos.get(clase_predicha, 0.60) * max_prob, 2)
        }

ia_classifier = EmergencyClassifier()