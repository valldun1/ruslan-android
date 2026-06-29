# 03-done.md

## Шаг A — ProviderConfig.kt: PROVIDER_MODELS ✅
- Добавлена карта моделей для 14 провайдеров
- Добавлена функция getModelsForProvider()

## Шаг B — WizardStepModelFragment ✅
- Создан: WizardStepModelFragment.kt + fragment_wizard_step_model.xml
- RadioGroup со списком моделей для выбранного провайдера
- Авто-выбор первой модели

## Шаг C — WizardPagerAdapter ✅
- itemCount = 4 (добавлен шаг моделей на позицию 2)
- Step3 сдвинут на позицию 3

## Шаг D — SetupWizardActivity ✅
- finishWizard() читает модель с позиции 2, API-ключ с позиции 3
- baseUrl сохраняется из built-in (не пустая строка)
- Модель сохраняется в config + передаётся в провайдер
- reload_config() вызывается после сохранения
- PageChangeCallback синхронизирует выбор провайдера → модели

## Шаг E — CI Build ✅
- Собрано: debug APK (v0.23.0)
- APK скопирован в /storage/emulated/0/Download/ruslan-agent.apk

## Предыдущие фиксы (включены в сборку)
- ThreadingHTTPServer (многопоточный прокси)
- readTimeout 120с
- UTF-8 явно в ChatActivity
- Логирование через Logger (пишет в ruslan.log)
- Чёрный текст на кнопках диалогов
- AlertDialog theme
