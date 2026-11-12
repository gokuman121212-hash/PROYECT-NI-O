from pydantic_settings import BaseSettings

class Settings(BaseSettings):
    APP_NAME: str = "AlertaCayma-API"
    ENVIRONMENT: str = "development"
    FIREBASE_CREDENTIALS_PATH: str = "firebase-adminsdk.json"
    SUPABASE_URL: str = ""
    SUPABASE_KEY: str = ""
    CONFIDENCE_THRESHOLD: float = 0.80

    class Config:
        env_file = ".env"

settings = Settings()
