# 🤖 Руслан Agent для Android

[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Android](https://img.shields.io/badge/Android-7.0+-brightgreen)](https://developer.android.com/)
[![Python](https://img.shields.io/badge/Chaquopy-Python%203.11-3776AB)](https://chaquo.com/chaquopy/)

> **Ruslan Agent** — AI-агент на Android. Один APK, полная автономность, Telegram gateway.
> **v0.23.0:** 15 провайдеров, экран логов, Telegram UI, YandexGPT, GigaChat.

## ⚡ Быстрый старт

1. Скачай `ruslan-agent.apk` из [Releases](../../releases)
2. Установи на Android 7.0+
3. Открой → пройди Setup Wizard (4 шага)
4. Готово! Gateway запустится автоматически

## ✨ Возможности

| Функция | Описание |
|---------|----------|
| 🎮 **One APK** | Python 3.11 + Руслан в одном файле (Chaquopy) |
| 🤖 **AI-агент** | 15 провайдеров: OpenAI, Anthropic, Google, YandexGPT, GigaChat, Grok, DeepSeek, OpenRouter, Ollama |
| 🎤 **Голосовой ввод** | Android SpeechRecognizer, без API-ключей |
| 💬 **Telegram** | Полный gateway с голосовыми сообщениями |
| 🔄 **Auto-restart** | При падении — восстановление за 5 сек |
| 🔋 **Boot start** | Автозапуск при включении телефона |
| 🛡️ **HyperOS fix** | Не убивается системой |
| 📡 **Streaming** | SSE-потоковая передача токенов в реальном времени |
| 🩺 **Health-check** | Автоматический мониторинг /health endpoint |

## 🏗️ Архитектура (Chaquopy)

```
ruslan-agent.apk
├── Android App (Kotlin)     ← UI, управление, уведомления
├── Python 3.11 (Chaquopy)   ← Встроенный CPython, НЕ Termux
│   ├── ruslan_proxy.py      ← HTTP-proxy с SSE streaming
│   ├── httpx, pydantic, etc ← pip-пакеты, предустановлены при сборке
│   └── Providers: opencode-go, deepseek, openai, google, anthropic
└── Config & Providers       ← SharedPreferences + JSON
```

### Отличия от Termux-версии (v0.17)

| Что | Termux (старая) | Chaquopy (новая) |
|-----|----------------|-------------------|
| Размер APK | ~120 MB | ~50 MB |
| Первый запуск | Распаковка .tar.zst (30-120 сек) | Мгновенный |
| Python | Отдельный Linux userspace | Встроен в APK |
| pip пакеты | Установка на устройстве | Предустановлены при сборке |
| Зависимости | zstd, tar, commons-compress | Только Chaquopy |

## 📁 Структура репозитория

```
ruslan-android/
├── android-app/          ← Android проект (Kotlin + Chaquopy)
│   └── app/src/main/
│       ├── kotlin/       ← Activities, Services, Receivers
│       └── python/       ← Python proxy (Chaquopy)
├── scripts/              ← CI/CD скрипты
├── .github/workflows/    ← GitHub Actions
└── phases/               ← Проектная документация
```

## 🚀 Сборка из исходников

### Требования
- Ubuntu 22.04+ или macOS
- Android SDK + NDK (26.x)
- JDK 17

### Локальная сборка

```bash
git clone https://github.com/valldun1/ruslan-android.git
cd ruslan-android/android-app
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

### CI/CD (GitHub Actions)

При пуше в `main`:
1. Chaquopy скачивает и встраивает Python 3.11
2. pip-пакеты устанавливаются на этапе сборки
3. Gradle собирает APK (debug + release)
4. Release APK подписывается apksigner (v2+v3)

## 🎨 Дизайн

- **Тема:** Dark cyberpunk
- **Акцент:** Cyan (#00d4ff)
- **Персонаж:** Бородатый воин с молнией
- **Шрифт:** Inter / Roboto

## 🔐 Безопасность

- API ключи в `SharedPreferences` (app-private storage)
- `allowBackup=false` — ключи НЕ утекают в Google Drive
- Никакой телеметрии без explicit opt-in
- Код открыт, можно проверить

## 📄 Лицензия

MIT License — см. [LICENSE](LICENSE)

---

**Сделано с ❤️ для Руслана**
