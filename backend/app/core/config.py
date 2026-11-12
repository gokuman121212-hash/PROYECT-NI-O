from pydantic_settings import BaseSettings, SettingsConfigDict

class Settings(BaseSettings):
    APP_NAME: str = "AlertaCayma-API"
    ENVIRONMENT: str = "development"
    FIREBASE_CREDENTIALS_PATH: str = "firebase-adminsdk.json"
    SUPABASE_URL: str = ""
    SUPABASE_KEY: str = ""
    CONFIDENCE_THRESHOLD: float = 0.80

    model_config = SettingsConfigDict(env_file=".env", env_file_encoding="utf-8-sig", extra="ignore")

settings = Settings()