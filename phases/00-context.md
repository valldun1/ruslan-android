# Context — Ruslan Android APK

## Проект
- Репозиторий: https://github.com/valldun1/ruslan-android (private)
- APK: Termux-based agent, собирается на GitHub Actions
- Устройство: Android 15 (huaqin GC02)

## Текущий статус
- ✅ Проблема найдена: в workflow `build-apk.yml` использовался `jarsigner` (только v1 подпись)
- ✅ Фикс запушен: `jarsigner` → `apksigner` с v2+v3, zipalign до подписи
- ✅ Новый билд #28301450965 завершён успешно
- ✅ GH CLI настроен, APK скачан (`ruslan-agent.apk`, 20MB)
- ⚠️ `pm install` из Termux не работает (SecurityException)
- ⚠️ `termux-open` запущен — ждём установку пользователем

## Инфраструктура
- GH_TOKEN: есть (классический PAT, частично рабочий — API чтение да, артефакты нет)
- gh CLI: залогинен через GH_TOKEN
- Android SDK/Tools: нет локально (только android-tools pkg без apksigner)
- JDK/Gradle: нет локально — всё через GH Actions

## Цикл
Нужен автоцикл: фикс → коммит → пуш → дождаться билда → скачать APK → установить → проверить ошибки → повторить.
