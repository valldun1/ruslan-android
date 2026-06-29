# 00-context.md

## Проект: Руслан Agent (Android, Chaquopy)

### Стек
- Android 15 (Xiaomi 24117RN76E, HyperOS)
- Kotlin + Chaquopy (Python в APK)
- Python proxy (http.server, ThreadingHTTPServer)
- GitHub: valldun1/ruslan-android (master)
- CI: GitHub Actions (debug APK ~23 MB)
- Версия: v0.23.0

### Состояние
- Proxy запускается, health check OK
- DeepSeek выбран, модель = deepseek-chat / deepseek-v4-flash
- Чат: 401 "Authentication Fails (governor)" — ключ не принимается
- Wizard нет выбора модели
- Нет сохранения модели в config при первом запуске (model: "")
- HTTP 1-поточный (HTTPServer) → socket timeout при стриминге → FIXED: ThreadingHTTPServer
- readTimeout 60s → FIXED: 120s
- reload_config не вызывался после визарда → FIXED
- Кнопки зелёные в AlertDialog → FIXED

### Оставшиеся проблемы
1. 401 Authentication Fails — ключ DeepSeek не валидный? Или идёт на OpenRouter?
2. Нет выбора модели в визарде
