#!/usr/bin/env bash
# Сборка мода целиком в Docker. На хосте нужен только Docker —
# ни JDK, ни Gradle ставить не требуется.
set -euo pipefail

cd "$(dirname "$0")"

IMAGE="afk-notifier-build"

# В Git Bash / MSYS на Windows пути для bind-mount нужно отдавать Docker'у
# в Windows-формате (E:/...), а автоподстановку путей MSYS — отключать.
export MSYS_NO_PATHCONV=1
if command -v cygpath >/dev/null 2>&1; then
	PROJECT_DIR="$(cygpath -m "$PWD")"
else
	PROJECT_DIR="$PWD"
fi

# --- Шаг 0: одноразовый bootstrap Gradle Wrapper ---------------------------
# gradle-wrapper.jar и скрипт gradlew — бинарные артефакты, их нельзя написать
# руками. Если их нет (свежий клон), генерируем во временном контейнере с
# официальным образом Gradle. Генерация идёт в пустой директории внутри
# контейнера: иначе `gradle wrapper` попытается сконфигурировать loom-билд,
# а для этого ещё нет самого wrapper'а. Дальше сборка идёт только через ./gradlew.
if [ ! -f gradle/wrapper/gradle-wrapper.jar ] || [ ! -f gradlew ]; then
	echo "==> Gradle Wrapper не найден, генерирую его в контейнере..."
	docker run --rm -v "$PROJECT_DIR:/w" gradle:9.5.1-jdk25 sh -c '
		set -e
		mkdir -p /tmp/bs && cd /tmp/bs
		touch settings.gradle
		gradle wrapper --gradle-version 9.5.1 --no-daemon -q
		cp gradlew gradlew.bat /w/
		mkdir -p /w/gradle/wrapper
		cp gradle/wrapper/gradle-wrapper.jar /w/gradle/wrapper/
	'
fi

# --- Шаг 1: сборка образа (внутри выполняется ./gradlew build) -------------
echo "==> Собираю образ $IMAGE..."
docker build -t "$IMAGE" .

# --- Шаг 2: выкладываем готовый jar на хост через bind-mount ---------------
echo "==> Копирую jar на хост в build/libs/..."
mkdir -p build/libs
docker run --rm -v "$PROJECT_DIR/build/libs:/out" "$IMAGE"

echo
echo "==> Готово. Артефакты:"
ls -1 build/libs/
