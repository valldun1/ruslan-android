
# Plan (GLM-5.2) — 2026-06-26

## Bug A — build-prefix.sh / proot / Termux

1. **Убрать apt-get из proot-окружения**
   - Вырезать вызовы apt-get, т.к. в Termux rootfs нет /usr/bin/apt
   - Заменить на `pkg install` через proot или прямой вызов `pkg`

2. **Разделить: proot для chroot, pkg для пакетов**
   - proot — только для sandbox/target-path (--rootfs=$PREFIX_DIR)
   - Установка пакетов идёт ДО proot через `pkg install python git openssl`

3. **Альтернатива: --link2symlink (не рекомендую)**
   - Hack, ломает совместимость, дублирует менеджер пакетов

4. **Финал: pip install --target для Python-зависимостей**
   - После `pkg install python git`, зависимости через `pip install --target=$PREFIX_DIR/site-packages`

5. **Проверка:** python --version, pip --version, git --version, python -c "import ssl"

**Рекомендация: стратегия 1+4 (pkg вне proot + pip --target в proot)**

## Bug B — 10 PNG иконок

6. **Создать исходник 192px (xxxhdpi base)** — Pillow
7. **Сгенерировать 5 квадратных** (48, 72, 96, 144, 192 px)
8. **Сгенерировать 5 круглых** (маска круга для каждого размера)
9. **Проверить AndroidManifest.xml** (roundIcon уже прописан)
10. **Визуальная проверка** через file + vision_analyze

## План коммитов: 2 коммита

**Commit 1:** fix(termux): replace apt-get with pkg install in build-prefix.sh
**Commit 2:** feat(icons): add launcher icons (square + round) for 5 densities

**Почему 2:** Bug A — runtime/sandbox, нужен revert-изолятор. Bug B — ресурсы, revert не ломает сборку.
