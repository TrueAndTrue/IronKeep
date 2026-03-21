# IronKeep

Minecraft Paper server with Geyser (Bedrock crossplay support).

## Project Structure

```
plugins/ironkeep-core/   # Custom server plugin (Gradle/Java 21)
server.properties.example # Server config template (copy to server/)
```

## Server Setup

1. Create a `server/` directory
2. Download [Paper 1.21.11](https://papermc.io/downloads/paper) into `server/`
3. Copy `server.properties.example` to `server/server.properties` and fill in secrets
4. Download [Geyser-Spigot](https://geysermc.org/) and [Floodgate](https://wiki.geysermc.org/floodgate/) into `server/plugins/`
5. Start the server:
   ```
   cd server
   java -Xms2G -Xmx4G -jar paper-1.21.11-127.jar --nogui
   ```

## Building the Plugin

```
cd plugins/ironkeep-core
./gradlew build
```

This builds the JAR and copies it to `server/plugins/` automatically.
