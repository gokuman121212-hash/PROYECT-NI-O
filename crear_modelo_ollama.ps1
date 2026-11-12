Write-Host "Creando modelo especializado 'alerta-cayma' en Ollama..." -ForegroundColor Cyan
ollama pull llama3.2:1b
ollama create alerta-cayma -f backend/app/ml/Modelfile.cayma
Write-Host "[OK] Modelo 'alerta-cayma' listo y configurado exclusivamente para emergencias en Cayma." -ForegroundColor Green