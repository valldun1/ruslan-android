# Plan — APK Build-Fix-Install Cycle

# План: APK Build-Fix-Install Автоцикл

## Шаг 1: Локальный фикс кода/конфигурации
- **Файлы:** `.github/workflows/build-apk.yml`, `app/build.gradle`, исходный код
- **Команды:**
  ```bash
  nano .github/workflows/build-apk.yml
  git diff
  ```
- **Проверки:** Изменения внесены корректно, YAML синтаксис валиден, нет очевидных ошибок в коде.

## Шаг 2: Коммит и пуш в master
- **Файлы:** Измененные файлы проекта
- **Команды:**
  ```bash
  git add .
  git commit -m "fix: update build config"
  git push origin master
  ```
- **Проверки:** `git status` показывает чистое дерево, пуш проходит без ошибок авторизации.

## Шаг 3: Ожидание сборки GitHub Actions
- **Файлы:** Нет
- **Команды:**
  ```bash
  gh run watch
  # или
  gh run list --workflow=build-apk.yml --limit 1
  ```
- **Проверки:** Статус последнего запуска (run) меняется на `completed`, результат (conclusion) — `success`.

## Шаг 4: Скачивание APK артефакта
- **Файлы:** `./artifacts/ruslan-agent.apk`
- **Команды:**
  ```bash
  RUN_ID=$(gh run list --workflow=build-apk.yml --limit 1 --json databaseId -q '.[0].databaseId')
  gh run download $RUN_ID -n apk-artifact -D ./artifacts
  ```
- **Проверки:** Файл `./artifacts/ruslan-agent.apk` существует, размер > 0 байт (около 20MB).

## Шаг 5: Установка APK на устройство
- **Файлы:** `./artifacts/ruslan-agent.apk`
- **Команды:**
  ```bash
  # Так как pm install из Termux вызывает SecurityException без root:
  termux-open ./artifacts/ruslan-agent.apk
  ```
- **Проверки:** На экране телефона появился системный диалог установки (Package Installer). Пользователь подтвердил установку.

## Шаг 6: Проверка установки и запуска
- **Файлы:** Нет
- **Команды:**
  ```bash
  # Проверка наличия пакета в системе
  pm list packages | grep ruslan
  # Запуск приложения (замените package.name на реальный)
  termux-open ruslan.package.name
  ```
- **Проверки:** Пакет найден в выводе `pm list packages`. Приложение открывается на экране и не падает сразу (нет сообщения "Приложение остановлено").

## Шаг 7: Сбор логов при неудаче (если Шаг 6 провален)
- **Файлы:** `crash_log.txt`
- **Команды:**
  ```bash
  # Если есть доступ к logcat (через root или встроенный termux logcat)
  logcat -d > crash_log.txt
  ```
- **Проверки:** Логи содержат stack trace ошибки. Переход к Шагу 1 с новыми данными для фикса.