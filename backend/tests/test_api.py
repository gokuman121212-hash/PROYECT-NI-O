from fastapi.testclient import TestClient
from app.main import app

client = TestClient(app)

def test_health_check():
    response = client.get("/api/v1/alertas/health")
    assert response.status_code == 200
    data = response.json()
    assert data["status"] == "online"
    assert data["ia_modelo_cargado"] is True

def test_envio_alerta_sos():
    payload = {
        "usuario_id": "test_user_cayma",
        "latitud": -16.3425,
        "longitud": -71.5467,
        "tipo": "deslizamiento",
        "descripcion": "Huaico bajando por la torrentera Chullo en Cayma",
        "nivel_bateria": 85,
        "origen_offline_mesh": False
    }
    response = client.post("/api/v1/alertas/sos", json=payload)
    assert response.status_code == 200
    data = response.json()
    assert "alerta_id" in data
    assert data["estado"] == "ACTIVA"

def test_mapa_calor():
    response = client.get("/api/v1/alertas/mapa-calor")
    assert response.status_code == 200
    assert "puntos" in response.json()