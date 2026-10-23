# HotShop

HotShop is a marketplace where you can buy and sell items, search listings,
exchange messages, make offers and arrange meetups. One account supports both
buying and selling. Optional AI features help buyers propose search filters and
sellers generate listing descriptions.

## Use the hosted app

Open **[HotShop on Render](https://cs3227-2610-mp3-ueno.onrender.com/)** in your
browser, create an account from the login page, then log in. Browser users need
an internet connection; no Java installation or local database setup is required.

The JavaFX application runs through JPro on Render. Marketplace records persist
in **Supabase PostgreSQL**, and listing/profile photos are stored in **private
Supabase Storage buckets**. The current application does not use SQLite or store
marketplace records in a local `.hotshop` database. The local working directory
is used for locking and temporary files, not as a replacement for remote storage.

## Run locally for development

Install **JDK 25** and clone this repository. Before launching, follow the
[database and private Storage setup guide](docs/developerGuide/AccountServiceAndLocalPersistence.md)
to configure the environment, provision the PostgreSQL schema and activate the
image-storage bindings. Local runs also require access to the configured services;
this is not an offline application. The app does not automatically load `.env.local`.

After setup, run from the project directory:

```powershell
# Windows
.\gradlew.bat run
```

```bash
# macOS/Linux
chmod +x gradlew
./gradlew run
```

The Gradle 9.1.0 Wrapper downloads dependencies as needed; no separate Gradle
installation is required. Local desktop runs require a graphical environment.
Automated database tests use disposable Docker PostgreSQL databases, never the
hosted application database. See the Developer Guide for test and Docker/JPro
packaging instructions.

## Guides

Visit the [product documentation website](https://cs3227-2610-mp3-hotshop.github.io/CS3227-2610-MP3/)
for the [User Guide](https://cs3227-2610-mp3-hotshop.github.io/CS3227-2610-MP3/docs/UserGuide.html)
and [Developer Guide](https://cs3227-2610-mp3-hotshop.github.io/CS3227-2610-MP3/docs/DeveloperGuide.html).
The documentation website and the Render application are separate services.
