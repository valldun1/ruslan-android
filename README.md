# 🤖 Руслан Agent для Android

[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Android](https://img.shields.io/badge/Android-8.0+-brightgreen)](https://developer.android.com/)
[![Go](https://img.shields.io/badge/Go-1.23-00ADD8)](https://go.dev/)

**Руслан** — AI-помощник с морской душой. Нативное Android-приложение.

## 🚀 Особенности

- Чат с AI через LLM (OpenAI, DeepSeek, YandexGPT, GigaChat и др.)
- Голосовой ввод
- Telegram-бот (встроенный)
- Работа с файлами
- Настройка провайдеров через UI
- Работает на Android 8.0+

## 🏗 Архитектура v2.0

```
Android App (Kotlin) → Go-бинарник (ruslan-agent) → LLM API
```

Go-ядро (репозиторий: [valldun1/go_ruslan_team](https://github.com/valldun1/go_ruslan_team)):
- HTTP API на :9123
- Поддержка 6+ LLM провайдеров
- Agent Loop с памятью и инструментами
- Telegram Gateway (long polling)
- Конфигурация через YAML

## 📲 Сборка

```bash
# Требуется: Android SDK, Java 17, Go 1.23

# 1. Собрать Go-бинарник для Android
cd go_ruslan_team
make build-android

# 2. Скопировать в assets
cp bin/ruslan-android-arm64 ../ruslan-android/android-app/app/src/main/assets/ruslan/arm64-v8a/

# 3. Собрать APK
cd ../ruslan-android/android-app
./gradlew assembleDebug
```

## 📦 Загрузка

Свежие сборки APK — в [GitHub Actions](https://github.com/valldun1/ruslan-android/actions).

## ⚙ Настройка

1. Установи APK
2. Открой Настройки → Провайдер
3. Выбери провайдера (OpenAI, DeepSeek, Yandex и др.)
4. Введи API-ключ
5. Готово! Можно общаться.

Для Telegram: укажи токен бота в Настройки → Telegram.

## 🔐 Лицензия

MIT License.
