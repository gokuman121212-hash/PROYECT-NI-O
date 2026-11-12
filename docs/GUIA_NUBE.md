# Guía Rápida de Credenciales y Despliegue (Cuando decidas activarlo)

## 1. Ollama (IA Gratuita en tu PC / Servidor)
1. Descarga Ollama desde https://ollama.com e instálalo.
2. En PowerShell ejecuta: `ollama run llama3.2:1b`
3. El backend FastAPI se conectará automáticamente a `http://localhost:11434`.

## 2. Supabase (Base de datos histórica)
1. Crea un proyecto gratuito en https://supabase.com.
2. Ve a **SQL Editor**, pega el contenido de `docs/supabase_schema.sql` y dale a **Run**.
3. Copia tu **Project URL** y **anon/service_role key** en `backend/.env`.

## 3. Firebase (Alertas en tiempo real)
1. Crea un proyecto en https://console.firebase.google.com y activa **Firestore Database**.
2. Ve a **Configuración del proyecto -> Cuentas de servicio -> Generar nueva clave privada**.
3. Guarda el archivo descargado como `backend/firebase-adminsdk.json`.