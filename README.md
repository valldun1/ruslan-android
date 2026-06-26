# 🤖 Руслан Agent для Android

[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Android](https://img.shields.io/badge/Android-7.0+-brightgreen)](https://developer.android.com/)
[![Termux](https://img.shields.io/badge/Termux-Embedded-4CAF50)](https://termux.com/)

> **Ruslan Agent** — AI-агент на Android. Один APK, полная автономность, Telegram gateway.

![Руслан](docs/assets/ruslan-banner.png)

## ⚡ Быстрый старт

1. Скачай `ruslan-agent.apk` из [Releases](../../releases)
2. Установи на Android 7.0+
3. Открой → пройди Setup Wizard (4 шага)
4. Готово! Gateway запустится автоматически

## ✨ Возможности

| Функция | Описание |
|---------|----------|
| 🎮 **One APK** | Termux + Python + Руслан в одном файле |
| 🤖 **AI-агент** | DeepSeek, OpenAI, Anthropic — на выбор |
| 💬 **Telegram** | Полный gateway с голосовыми сообщениями |
| 🔄 **Auto-restart** | При падении — восстановление за 5 сек |
| 🔋 **Boot start** | Автозапуск при включении телефона |
| 🛡️ **HyperOS fix** | Не убивается системой |

## 🏗️ Архитектура

```
ruslan-agent.apk
├── Android App (Kotlin)     ← UI, управление, уведомления
├── Termux Rootfs            ← Python 3.11 + ruslan-agent
└── Config & Scripts         ← Автонастройка при первом запуске
```

## 📁 Структура репозитория

```
ruslan-android/
├── android-app/          ← Android проект
├── termux-bundle/        ← Сборка Termux rootfs
├── scripts/              ← CI/CD скрипты
├── .github/workflows/    ← GitHub Actions
└── docs/                 ← Документация
```

## 🚀 Сборка из исходников

### Требования
- Ubuntu 22.04+ или macOS (для local build)
- Docker (для сборки Termux rootfs)
- Android SDK

### Локальная сборка

```bash
# 1. Клонируй репозиторий
git clone https://github.com/valldun1/ruslan-android.git
cd ruslan-android

# 2. Собери Termux rootfs (требует Docker)
./scripts/build-termux-prefix.sh

# 3. Собери APK
cd android-app
./gradlew assembleRelease

# 4. Подпишь (или используй debug APK)
```

### CI/CD (GitHub Actions)

При пуше в `main`:
1. Собирается Termux rootfs (arm64)
2. Собирается APK через Gradle
3. APK подписывается и публикуется в Releases

## 🎨 Дизайн

- **Тема:** Dark cyberpunk
- **Акцент:** Cyan (#00d4ff)
- **Персонаж:** Бородатый воин с молнией
- **Шрифт:** Inter / Roboto

## 🔐 Безопасность

- API ключи хранятся локально в `/data/data/.../files/usr/home/.env`
- Никакой телеметрии без explicit opt-in
- Код открыт, можно проверить

## 📄 Лицензия

MIT License — см. [LICENSE](LICENSE)

---

**Сделано с ❤️ для Руслана**
