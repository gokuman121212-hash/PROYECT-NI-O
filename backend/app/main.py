from fastapi import FastAPI, WebSocket, WebSocketDisconnect
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import FileResponse
from slowapi import _rate_limit_exceeded_handler
from slowapi.errors import RateLimitExceeded
from app.core.config import settings
from app.api.routes_sos import router as sos_router, limiter, ws_manager

app = FastAPI(
    title=settings.APP_NAME,
    description="Centro de Comando y Alerta Temprana Multi-Riesgo — Cayma / Arequipa",
    version="2.1.0"
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

app.state.limiter = limiter
app.add_exception_handler(RateLimitExceeded, _rate_limit_exceeded_handler)
app.include_router(sos_router)

@app.websocket("/ws/coe")
async def websocket_coe(websocket: WebSocket):
    await ws_manager.connect(websocket)
    try:
        while True:
            await websocket.receive_text()
    except WebSocketDisconnect:
        ws_manager.disconnect(websocket)

@app.get("/")
def root():
    return {"mensaje": "Servidor AlertaCayma COE v2.1 operativo", "docs": "/docs", "panel_autoridades": "/panel-control"}

@app.get("/panel-control")
def panel_control():
    return FileResponse(
        "app/static/dashboard.html",
        headers={"Cache-Control": "no-store, no-cache, must-revalidate, max-age=0"}
    )
@app.get("/simulador-campo")
def simulador_campo():
    return FileResponse(
        "app/static/simulador.html",
        headers={"Cache-Control": "no-store, no-cache, must-revalidate, max-age=0"}
    )