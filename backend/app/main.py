from fastapi import FastAPI
from slowapi import _rate_limit_exceeded_handler
from slowapi.errors import RateLimitExceeded
from app.core.config import settings
from app.api.routes_sos import router as sos_router, limiter

app = FastAPI(
    title=settings.APP_NAME,
    description="Backend de alta disponibilidad para alertas de emergencia en Cayma / Arequipa",
    version="1.0.0"
)

app.state.limiter = limiter
app.add_exception_handler(RateLimitExceeded, _rate_limit_exceeded_handler)

app.include_router(sos_router)

@app.get("/")
def root():
    return {"mensaje": "Servidor AlertaCayma operativo", "docs": "/docs"}
