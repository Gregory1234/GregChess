# GregChess
A Bukkit plugin and a WIP Fabricmc mod adding chess to minecraft written in Kotlin.

Works in Minecraft `1.19.2`.

## Gradle tasks
### Build
- Bukkit plugin jar: `:gregchess-bukkit:finalJar`
- Fabric mod jar: `:gregchess-fabric:finalJar`

(All jars are moved to `build/libs`)

### Run
- Paper server with the plugin: `:gregchess-bukkit:launchMinecraftServer`
- Fabric client with the mod: `:gregchess-fabric:runClient`
- Fabric server with the mod: `:gregchess-fabric:runServer`

### Documentation
- Dokka html: `:dokkaHtmlMultiModule`