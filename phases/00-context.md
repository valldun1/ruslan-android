# Ruslan Android — Контекст проекта

**Дата:** 2026-06-26
**Текущая версия:** cc04687 (master)
**Репозиторий:** https://github.com/valldun1/ruslan-android (private)

## Цель проекта
AI-агент "Руслан" на Android. Один APK включает Termux + Python + agent.
Telegram gateway как UI. Auto-restart, boot start, устойчивость к HyperOS.

## Технологический стек
- **Android:** Kotlin, AGP, Gradle 8.4, AndroidX
- **Embedding:** Termux bootstrap (Python + bash), упакованный в assets
- **Build:** QEMU aarch64 в Docker (ubuntu:22.04) для кросс-компиляции Termux prefix
- **CI:** GitHub Actions
  - `build-apk.yml` — полная сборка с Termux prefix (тяжёлая, ~5+ мин)
  - `debug-build.yml` — быстрая debug-сборка без QEMU (лёгкая, ~2 мин)

## Структура
```
~/Desktop/ruslan-android/
├── android-app/                    ← Gradle Android проект
│   └── app/src/main/
│       ├── AndroidManifest.xml     ← ссылается на @mipmap/ic_launcher
│       ├── kotlin/ru/valldun/ruslan/
│       ├── res/values/             ← strings, styles (Theme.Ruslan.WithActionBar)
│       ├── res/layout/
│       ├── res/menu/
│       ├── res/color/
│       ├── res/xml/
│       └── assets/                 ← сюда ложится Termux prefix
├── termux-bundle/
│   └── build-prefix.sh             ← создаёт usr.tar.zst через proot
└── .github/workflows/
    ├── build-apk.yml               ← полная сборка
    └── debug-build.yml             ← быстрая сборка
```

## Последние фиксы (cc04687)
1. ✅ repositories в build.gradle.kts убраны
2. ✅ gradle.properties с android.useAndroidX=true
3. ✅ splits abi убраны (конфликт с ndk abiFilters)
4. ✅ Theme.Ruslan → Theme.Ruslan.WithActionBar (устранён дубль)
5. ✅ Termux bootstrap извлекается из APK (старый URL 404)
6. ✅ Добавлен proot в apt-get install

## Текущие проблемы (из логов CI)

### Проблема A: Build APK (run #7) — proot error
**Ошибка:** `proot error: '/usr/bin/apt' not found (root = /prefix, cwd = /, $PATH=(null))`
**Причина:** `build-prefix.sh` line 34-36 оборачивает `proot` в shell-функцию:
```bash
proot() {
    command proot -0 -r "$PREFIX_DIR" -b /dev -b /proc -b /sys "$@"
}
```
Затем вызывает `proot /usr/bin/apt update`. Но `proot` запускает утилиту **относительно** нового rootfs (`$PREFIX_DIR`). В Termux prefix **нет** `/usr/bin/apt` — он использует pkg или apt из `/data/data/com.termux/files/usr/bin/`. Нужно проверить как Termux официально ставит пакеты.

### Проблема B: Debug Build (run #5) — иконки
**Ошибка:**
```
AAPT: error: resource mipmap/ic_launcher not found
AAPT: error: resource mipmap/ic_launcher_round not found
```
**Причина:** AndroidManifest ссылается на `@mipmap/ic_launcher` и `@mipmap/ic_launcher_round`, но в `res/` нет **ни одной** папки `mipmap-*`. Нужно создать иконки в 5 плотностях:
- mipmap-mdpi (48x48)
- mipmap-hdpi (72x72)
- mipmap-xhdpi (96x96)
- mipmap-xxhdpi (144x144)
- mipmap-xxxhdpi (192x192)

Для каждой: `ic_launcher.png` (квадрат) и `ic_launcher_round.png` (круглый).

## Окружение
- macOS 10.13.6
- JDK 17
- GitHub Actions на ubuntu-22.04 + ubuntu-latest
- Рабочая директория: ~/Desktop/ruslan-android
- Push в GitHub = автоматический trigger Actions
