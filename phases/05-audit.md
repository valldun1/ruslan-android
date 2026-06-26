# Полный аудит ruslan-android — 2026-06-26

## Сводка
- **13 .kt файлов** (часто используют ViewBinding)
- **Только 1 layout XML** (`activity_main.xml`), а ждут 7
- **3 gradle файла** — build.gradle.kts ✅ настроен
- **2 workflow** — оба триггерятся на push, оба собирают APK
- **ViewBinding включён** в build.gradle.kts:50 ✅
- **themes.xml** — Theme.Ruslan.NoActionBar без parent (⚠️ потенциальная ошибка)

## БЛОКЕР 1: Отсутствуют 6 layout файлов
ViewBinding пытается сгенерировать:
- `ActivityChatBinding` → нужен `layout/activity_chat.xml`
- `ActivitySetupWizardBinding` → нужен `layout/activity_setup_wizard.xml`
- `FragmentWizardStep1Binding` → нужен `layout/fragment_wizard_step1.xml`
- `FragmentWizardStep2Binding` → нужен `layout/fragment_wizard_step2.xml`
- `FragmentWizardStep3Binding` → нужен `layout/fragment_wizard_step3.xml`
- `FragmentWizardStep4Binding` → нужен `layout/fragment_wizard_step4.xml`

Каждый фрагмент/activity ссылается на свои views:
- **activity_chat:** `btnBack`, `etMessage`, `btnSend`, `btnVoice`, `btnAttachment`
- **activity_setup_wizard:** `viewPager`, `tabLayout`, `btnNext`
- **fragment_wizard_step1:** пустой (только inflate)
- **fragment_wizard_step2:** `providerDeepseek`, `providerOpenai`, `providerAnthropic` (RadioButtons?)
- **fragment_wizard_step3:** `etApiKey`, `btnTogglePassword`
- **fragment_wizard_step4:** `etBotToken`, `btnToggleToken`, `etAllowedUsers` (нужно для финиша)

## БЛОКЕР 2: Theme.Ruslan.NoActionBar без parent
`themes.xml:41-44`:
```xml
<style name="Theme.Ruslan.NoActionBar">
    <item name="windowActionBar">false</item>
    <item name="windowNoTitle">true</item>
</style>
```
Без `parent` Android не знает какой это вариант темы. Будет runtime error или app crash.

## БЛОКЕР 3: WizardStep "Variable expected"
Это **каскад от БЛОКЕРА 1**. Когда binding не генерируется, Kotlin парсер не может разрешить `binding.btnNext` → "Variable expected". Как только создам layouts — ошибки исчезнут.

## БЛОКЕР 4: Build APK (proot) — python в bootstrap
`build-prefix.sh` пытается использовать python через `proot -r $PREFIX_DIR /bin/python3`. Но Termux bootstrap в `libtermux-bootstrap.so` **не содержит python** (только базовые утилиты, README, etc.).

**Стратегия:** Упростить `build-prefix.sh` — он должен только:
1. Скачать Termux APK
2. Извлечь `libtermux-bootstrap.so`
3. Достать из него `bootstrap.zip`
4. Распаковать в assets (без pip install, без proot, без pkg)
5. Реальный python ставится на устройстве при первом запуске через `pkg install python` в Termux

## БЛОКЕР 5: extractZstdTar runtime crash
`TermuxBootstrap.kt:132-158` — вызывает `ProcessBuilder("zstd", ...)`. На Android нет `zstd` бинарника. Будет `IOException` при первом запуске.

**Стратегия:** Заменить на Java-библиотеку `com.github.luben:zstd-jni` или распаковывать без сжатия (использовать `tar` без zstd, пересобрав `usr.tar`).

## БЛОКЕР 6: 2 workflow собирают APK одновременно
- `build-apk.yml` — собирает Termux prefix + APK (долго, ~10 мин)
- `debug-build.yml` — скачивает bootstrap + собирает APK (быстрее, ~5 мин)

Оба триггерятся на push в master → **дублирование работы, конкуренция за ресурсы**. 

**Стратегия:** Оставить `build-apk.yml` как основной. Удалить `debug-build.yml` (он и так почти ничего не даёт — Termux prefix без python бесполезен).

## БЛОКЕР 7: build-apk.yml не использует sign config
В build.gradle.kts нет блока `signingConfigs`. `jarsigner` в workflow пытается подписать APK с keystore из secrets, но:
- В build.gradle.kts нет `signingConfig release`
- `jarsigner` устарел, Gradle сам подписывает если есть signingConfig

Нужно добавить signingConfig в build.gradle.kts или использовать apksigner.

## План фиксов (приоритеты)

### Tier 1 — Kotlin компиляция
1. Создать 6 layout XML
2. Поправить `Theme.Ruslan.NoActionBar` — добавить `parent="Theme.Ruslan"`

### Tier 2 — Android runtime
3. Заменить `extractZstdTar` на Java-based распаковку
4. Добавить signingConfig в build.gradle.kts

### Tier 3 — Build APK
5. Упростить `build-prefix.sh` — без python
6. Удалить `debug-build.yml`

**Оценка:** ~60-90 мин на всё. После Tier 1 + Tier 2 Debug Build должен стать зелёным. После Tier 3 — Build APK.
