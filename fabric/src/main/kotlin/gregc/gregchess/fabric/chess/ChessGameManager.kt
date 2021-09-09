package gregc.gregchess.fabric.chess

import gregc.gregchess.chess.ChessGame
import gregc.gregchess.chess.GameSettings
import gregc.gregchess.chess.component.ChessboardState
import gregc.gregchess.chess.variant.ChessVariant
import gregc.gregchess.fabric.GregChess
import gregc.gregchess.fabric.chess.component.FabricRendererSettings
import gregc.gregchess.fabric.chess.component.PlayerManagerData
import gregc.gregchess.fabric.mixin.WorldSavePathCreator
import kotlinx.serialization.json.Json
import net.minecraft.server.MinecraftServer
import java.util.*

object ChessGameManager {

    private val gregchessPath = WorldSavePathCreator.create("gregchess")

    private val loadedGames = mutableMapOf<UUID, ChessGame>()

    internal lateinit var server: MinecraftServer

    private fun gameFile(uuid: UUID) = server.getSavePath(gregchessPath).resolve(uuid.toString()).toFile()

    operator fun get(uuid: UUID): ChessGame? = loadedGames.getOrPut(uuid) {
        GregChess.logger.info("loading game $uuid")
        val f = gameFile(uuid)
        if (f.exists()) {
            val json = Json {
                serializersModule = defaultModule(server)
            }
            f.readText().recreateGameFromJson(json).also {
                GregChess.logger.info("loaded game $it")
            }
        } else return null
    }

    operator fun plusAssign(game: ChessGame) {
        GregChess.logger.info("added game $game")
        loadedGames[game.uuid] = game
    }

    operator fun minusAssign(game: ChessGame) {
        GregChess.logger.info("removed game $game")
        loadedGames.remove(game.uuid, game)
        val file = gameFile(game.uuid)
        if (file.exists()) {
            file.delete()
        }
    }

    fun clear() = loadedGames.clear()

    fun settings(v: ChessVariant, r: FabricRendererSettings): GameSettings {
        val components = buildList {
            this += ChessboardState[v, null]
            this += PlayerManagerData
            this += r
        }
        return GameSettings("", false, ChessVariant.Normal, components)
    }


    fun save() {
        val json = Json {
            serializersModule = defaultModule(server)
        }

        for ((u, g) in loadedGames) {
            val f = gameFile(u)
            f.parentFile.mkdirs()
            f.writeText(g.serializeToJson(json))
            GregChess.logger.info("saved game $g")
        }
    }

}