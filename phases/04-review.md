# Strategy (GLM-5.2) — 2026-06-26 v3

## Решение: ПОЛНЫЙ АУДИТ, не чекпойнты

**Объём:** 13 .kt файлов (1036 строк), 3 gradle (101 строка), 32 XML res, 2 workflow
**Время аудита:** 15-20 мин vs 50-100 мин на чекпойнты
**Скорость:** в 3-5 раз быстрее

## Найденные блокеры (100% не скомпилируется):

1. **GatewayService.kt:57,164** — `Companion.isRunning` — должен быть `GatewayService.isRunning`
2. **GatewayService.kt:223,226,227** — отсутствуют `ic_notification`, `ic_stop`, `ic_restart` в drawable
3. **TermuxBootstrap.kt:117 vs :182** — `copyConfigTemplates(context, prefixDir)` — File vs String
4. **TermuxBootstrap.kt:132-158** — `extractZstdTar` вызывает внешний zstd, его нет в Android
5. **2 workflow конфликтуют** — оба триггерятся на push

## План:

### Шаг 1 — Kotlin compile-fix (блокеры 1,2,3)
- Создать 3 VectorDrawable: ic_notification, ic_stop, ic_restart
- Исправить `Companion.isRunning` → `isRunning` или `GatewayService.isRunning`
- Исправить сигнатуру `copyConfigTemplates` (String → File)

### Шаг 2 — Runtime-fix (блокер 4)
- Заменить вызов внешнего zstd на `zstd-jni` или предраспакованный tar

### Шаг 3 — CI cleanup (блокер 5)
- Удалить debug-build.yml
- Добавить path-filter в build-apk.yml

**Ожидаемое время:** ~40 мин аудита + 1-2 CI прогона
