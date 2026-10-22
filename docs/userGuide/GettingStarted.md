---
  layout: default.md
  title: "Getting Started"
  pageNav: 3
---

## Requirements

Install a **JDK 25** (Java Development Kit) to build and run HotShop using the
Gradle Wrapper below. A graphical desktop is required.

## Start HotShop

From the project directory on Windows:

```powershell
.\gradlew.bat run
```

On macOS/Linux, use `./gradlew run` (run `chmod +x gradlew` first if needed).
The first build downloads dependencies and requires internet access.

HotShop opens the **Log in** page. Pages and the sidebar scroll when needed;
listing grids wrap to fewer columns in narrower windows. Keyboard focus is
shown with an outline.

Before launch, ask the operator to configure HOTSHOP_DB_URL, HOTSHOP_DB_USER and
HOTSHOP_DB_PASSWORD and run the separate migrateDatabase setup command. See the
[PostgreSQL setup guide](../developerGuide/AccountServiceAndLocalPersistence.html#postgresql-operator-setup). The first
provisioning creates an empty marketplace; it does not import older SQLite files.
Startup validates the schema and never upgrades, seeds or resets it automatically.

For optional AI Search, the operator supplies SOCLAAS_BASE_URL as the API base
(normally ending in /v1), SOCLAAS_API_KEY and SOCLAAS_MODEL. For seller description
generation, set SOCLAAS_DESCRIPTION_URL to the complete HTTPS Responses endpoint;
the same API key and model are used. The app reads process settings and does not
automatically load `.env.local`. Follow the Developer Guide local AI Search setup
to load that file before launching. Ordinary search and manual listing descriptions
remain available if either AI feature is unavailable.

Marketplace records persist in PostgreSQL. Production profile and listing images
are private objects in the configured Supabase Storage buckets. The local
`.hotshop` directory contains the process lock; only one HotShop process may use
that directory at a time. Back up PostgreSQL and the matching private image storage
together. Deleting `.hotshop` does not reset database records or remote images.

If startup reports a database problem, check connection settings/network access and
ask the operator to run compatible provisioning. Storage setup and migration steps
are in the [private image storage guide](../developerGuide/AccountServiceAndLocalPersistence.html#private-image-storage-setup-and-import).
Directory errors require a writable data directory and no competing HotShop process.

## Run the packaged application

### Download a prebuilt JAR

Prebuilt JARs for 64-bit Windows and Linux are attached to the
[latest release](https://github.com/CS3227-2610-MP3-HotShop/CS3227-2610-MP3/releases/latest),
and are also in the repository's `release/` folder:

- `HotShop-windows.jar` for Windows
- `HotShop-linux.jar` for Linux

macOS JARs are available in the repository's `release/` folder:

- `HotShop-mac.jar` for an Intel (x86-64) Java runtime
- `HotShop-mac-aarch64.jar` for an Apple Silicon (AArch64) Java runtime

Older published JARs may use SQLite; build this branch to obtain the PostgreSQL version.
Each JAR includes JavaFX but not Java itself, so install **JDK 25** first.
Choose the JAR matching your Java runtime's architecture. The macOS packages
were built on Windows; native macOS launch verification is still required.
Start the downloaded JAR from the folder you saved it in, for example on Windows:

```powershell
java -jar HotShop-windows.jar
```

On an Apple Silicon Mac with an AArch64 JDK 25, run:

```bash
java -jar HotShop-mac-aarch64.jar
```

With an Intel JDK 25, use `java -jar HotShop-mac.jar` instead.



### Build the JAR yourself

Build on the operating system and architecture where the JAR will run:

```powershell
.\gradlew.bat shadowJar
java -jar release/HotShop.jar
```

The JAR includes JavaFX libraries for the build machine's platform, but does not
include Java itself. The CI artifact targets Linux.

To build a specific macOS package, run `./gradlew shadowJar -PjavafxPlatform=mac`
or `./gradlew shadowJar -PjavafxPlatform=mac-aarch64`. On Windows, replace
`./gradlew` with `.\gradlew.bat`. The output is `release/HotShop-mac.jar` or
`release/HotShop-mac-aarch64.jar`, respectively.

To use a separate data folder, put the Java system property before `-jar`, for
example on Windows:

```powershell
java "-Dhotshop.dataDir=C:\HotShop-test-data" -jar release/HotShop.jar
```

Replace the example path with your chosen folder. Passing `-Dhotshop.dataDir=...`
to `gradlew run` does not select the app's folder: the current Gradle run task
does not forward that property to the application. Use the JAR command above.

