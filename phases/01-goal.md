# Goal — починить сборку ruslan-android

## Что нужно сделать
Добиться успешной (success) сборки **обоих** workflows:
1. `build-apk.yml` (Build APK) — полная сборка с Termux prefix
2. `debug-build.yml` (Debug Build) — быстрая debug-сборка

## Два независимых бага

### Bug A: Build APK — proot error
`build-prefix.sh` вызывает `proot /usr/bin/apt`, но в Termux rootfs нет `/usr/bin/apt`.
**Задача:** сделать чтобы Termux bootstrap работал — ставил python, pip, git в prefix через правильный путь или без proot-apt.

### Bug B: Debug Build — иконки
`AndroidManifest.xml` ссылается на `@mipmap/ic_launcher*`, но в `res/` нет ни одной `mipmap-*` папки.
**Задача:** создать иконки приложения во всех 5 плотностях (mdpi → xxxhdpi) для `ic_launcher.png` и `ic_launcher_round.png`.

## Acceptance criteria
- [ ] Коммит запушен в master
- [ ] GitHub Actions показывает **success** для build-apk.yml
- [ ] GitHub Actions показывает **success** для debug-build.yml
- [ ] APK артефакт скачивается

## Ограничения
- Минимальные изменения (не переписывать build-prefix.sh с нуля)
- Иконки можно сгенерировать программно (PIL/Pillow)
- Не трогать то что работает (manifest, gradle, kotlin код)
