# Образ нужен ТОЛЬКО для сборки мода: на хосте не требуются ни JDK, ни Gradle.
# Сам мод в контейнере не запускается — это клиентский мод для реального клиента.
FROM eclipse-temurin:25-jdk

# Gradle отдельно не ставим: нужную версию (9.5.1) скачает Gradle Wrapper.
WORKDIR /app

# Сначала копируем только файлы сборки — слой переиспользуется,
# пока не менялись зависимости, и правка исходников не сбрасывает кэш.
COPY gradle/ gradle/
COPY gradlew settings.gradle gradle.properties build.gradle LICENSE ./
RUN chmod +x gradlew

# Прогреваем wrapper: скачиваем дистрибутив Gradle отдельным слоем.
RUN --mount=type=cache,target=/root/.gradle ./gradlew --no-daemon --version

# Затем исходники.
COPY src/ src/

# Сборка. Кэш Gradle вынесен в BuildKit-кэш, поэтому повторные сборки быстрее,
# но образ собирается и с полностью пустым кэшем.
RUN --mount=type=cache,target=/root/.gradle ./gradlew build --no-daemon --stacktrace

# По умолчанию контейнер просто выкладывает собранный jar в /out,
# который build.sh монтирует на build/libs хоста.
CMD ["sh", "-c", "cp -v /app/build/libs/*.jar /out/"]
