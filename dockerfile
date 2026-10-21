FROM eclipse-temurin:25-jre

# JavaFX on Linux needs system libraries. Take the exact package list
# from JPro's "Preparing Linux for JPro" chapter.
RUN apt-get update && apt-get install -y --no-install-recommends \
    unzip xorg libgtk-3-0 \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY build/distributions/HotShop-jpro.zip app.zip
RUN unzip app.zip && rm app.zip

# The zip unpacks into a folder; adjust this path to match yours
WORKDIR /app/HotShop-jpro
CMD ["./bin/start.sh"]
