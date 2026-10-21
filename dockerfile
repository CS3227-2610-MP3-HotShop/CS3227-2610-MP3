FROM eclipse-temurin:25-jdk AS build

WORKDIR /workspace
COPY gradlew build.gradle settings.gradle ./
COPY gradle ./gradle
COPY config ./config
COPY src ./src
RUN sed -i 's/\r$//' gradlew && chmod +x ./gradlew \
    && ./gradlew --no-daemon jproRelease -PjavafxPlatform=linux

FROM eclipse-temurin:25-jre

# JavaFX on Linux needs system libraries. Take the exact package list
# from JPro's "Preparing Linux for JPro" chapter.
RUN apt-get update && apt-get install -y --no-install-recommends \
    unzip xorg libgtk-3-0 \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY --from=build /workspace/build/distributions/HotShop-jpro.zip app.zip
RUN unzip app.zip && rm app.zip

WORKDIR /app/HotShop-jpro
CMD ["./bin/start.sh"]
