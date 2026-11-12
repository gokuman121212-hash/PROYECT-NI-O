import os
import joblib
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.naive_bayes import MultinomialNB
from sklearn.pipeline import make_pipeline
from app.core.config import settings

MODEL_PATH = "app/ml/modelo_emergencias.joblib"

# Dataset base contextualizado a riesgos en Cayma / Arequipa
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
        self.model = None
        self._load_or_train()

    def _load_or_train(self):
        if os.path.exists(MODEL_PATH):
            self.model = joblib.load(MODEL_PATH)
        else:
            textos = [item[0] for item in DATOS_ENTRENAMIENTO]
            etiquetas = [item[1] for item in DATOS_ENTRENAMIENTO]
            self.model = make_pipeline(TfidfVectorizer(), MultinomialNB(alpha=0.1))
            self.model.fit(textos, etiquetas)
            joblib.dump(self.model, MODEL_PATH)

    def analizar_reporte(self, descripcion: str, tipo_manual: str) -> dict:
        # Si es SOS critico directo desde boton flotante, pasa con maxima confianza
        if tipo_manual == "sos_critico":
            return {
                "tipo_clasificado": "sos_critico",
                "confianza": 0.99,
                "aprobado": True,
                "indice_riesgo_calor": 1.0
            }

        if not descripcion or len(descripcion.strip()) < 4:
            return {
                "tipo_clasificado": tipo_manual,
                "confianza": 0.82,
                "aprobado": True,
                "indice_riesgo_calor": 0.65
            }

        probabilidades = self.model.predict_proba([descripcion.lower()])[0]
        max_prob = float(max(probabilidades))
        clase_predicha = self.model.classes_[probabilidades.argmax()]

        # Calcular indice de riesgo para el mapa de calor (0.0 a 1.0)
        pesos_riesgo = {
            "sismo": 0.95,
            "deslizamiento": 0.90,
            "incendio": 0.88,
            "medica": 0.75,
            "seguridad": 0.70
        }
        riesgo_calor = round(pesos_riesgo.get(clase_predicha, 0.60) * max_prob, 2)

        return {
            "tipo_clasificado": clase_predicha,
            "confianza": round(max_prob, 2),
            "aprobado": max_prob >= settings.CONFIDENCE_THRESHOLD or len(descripcion.split()) >= 2,
            "indice_riesgo_calor": riesgo_calor
        }

ia_classifier = EmergencyClassifier()
