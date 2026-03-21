# IronKeep

Minecraft Paper 1.21.11 server with Geyser for Bedrock crossplay support.

## Prerequisites

- **Java 21+** — Install via `winget install Microsoft.OpenJDK.21` (Windows) or your package manager of choice.
  - After installing, make sure `java` is on your PATH. If `java --version` doesn't work, add the install directory to PATH manually (e.g., `C:\Program Files\Microsoft\jdk-21.0.10.7-hotspot\bin` on Windows).

## Project Structure

```
IronKeep/
├── plugins/
│   └── ironkeep-core/       # Custom server plugin (Gradle/Java 21)
│       ├── src/              # Plugin source code
│       ├── build.gradle.kts  # Build config — deploys JAR to server/plugins/
│       └── gradlew           # Gradle wrapper (no Gradle install needed)
├── server/                   # Server runtime (git-ignored)
│   ├── paper-1.21.11-127.jar
│   ├── server.properties
│   ├── plugins/              # Plugin JARs loaded by the server
│   └── ...
├── server.properties.example # Template config with secrets removed
└── .gitignore
```

The `server/` directory contains the full runtime and is **not tracked in git**. Each developer sets up their own local copy.

## Server Setup

1. Create a `server/` directory in the project root.

2. Download [Paper 1.21.11](https://papermc.io/downloads/paper) and place the JAR in `server/`.

3. Copy `server.properties.example` to `server/server.properties` and update any values (e.g., `management-server-secret`).

4. Download [Geyser-Spigot](https://geysermc.org/) and [Floodgate](https://wiki.geysermc.org/floodgate/) into `server/plugins/`.

5. Run the server once to generate config files and accept the EULA:
   ```
   cd server
   java -Xms2G -Xmx4G -jar paper-1.21.11-127.jar --nogui
   ```
   On first run, edit `server/eula.txt` and set `eula=true`, then start it again.

## Building the Plugin

From the project root:

```
cd plugins/ironkeep-core
./gradlew build
```

This compiles the plugin and automatically copies the JAR into `server/plugins/`. Restart the server to load the updated plugin.

## Running the Server

```
cd server
java -Xms2G -Xmx4G -jar paper-1.21.11-127.jar --nogui
```

Or use `./start.bat` if Java is on your PATH.

The server console accepts commands directly (without a `/` prefix), e.g., `op PlayerName`.

## Connecting

- **Java Edition:** Multiplayer → Add Server → address `localhost`
- **Bedrock Edition:** Servers → Add Server → address `localhost`, port `19132`
