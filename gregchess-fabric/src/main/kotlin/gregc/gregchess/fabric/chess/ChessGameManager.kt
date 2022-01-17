package gregc.gregchess.fabric.chess

import gregc.gregchess.GregChess
import gregc.gregchess.chess.*
import gregc.gregchess.chess.component.ChessboardState
import gregc.gregchess.chess.component.SimpleComponentData
import gregc.gregchess.chess.piece.Piece
import gregc.gregchess.chess.variant.ChessVariant
import gregc.gregchess.fabric.chess.component.*
import gregc.gregchess.fabric.defaultModule
import gregc.gregchess.fabric.mixin.WorldSavePathCreator
import gregc.gregchess.fabric.nbt.*
import net.minecraft.nbt.NbtCompound
import net.minecraft.nbt.NbtIo
import net.minecraft.server.MinecraftServer
import net.minecraft.server.world.ServerWorld
import java.util.*

object ChessGameManager {

    private val gregchessPath = WorldSavePathCreator.create("gregchess")

    private val loadedGames = mutableMapOf<UUID, ChessGame>()

    internal lateinit var server: MinecraftServer

    private fun gameFile(uuid: UUID) = server.getSavePath(gregchessPath).resolve("$uuid.dat").toFile()

    operator fun get(uuid: UUID): ChessGame? = loadedGames.getOrPut(uuid) {
        GregChess.logger.info("loading game $uuid")
        val f = gameFile(uuid)
        if (f.exists()) {
            val nbt = Nbt(defaultModule(server))
            try {
                nbt.decodeFromNbtElement<ChessGame>(NbtIo.readCompressed(f)).also {
                    it.sync()
                    GregChess.logger.info("loaded game $it")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                return null
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

    fun settings(v: ChessVariant, boardState: Map<Pos, Piece>, r: FabricRendererSettings): GameSettings {
        val components = buildList {
            this += ChessboardState(v, FEN.fromPieces(boardState))
            this += SimpleComponentData(GameController::class)
            this += r
        }
        return GameSettings("", ChessVariant.Normal, components)
    }


    fun save() {
        val nbt = Nbt(defaultModule(server))
        try {
            for ((u, g) in loadedGames) {
                val f = gameFile(u)
                f.parentFile.mkdirs()
                NbtIo.writeCompressed(nbt.encodeToNbtElement(g) as NbtCompound, f)
                GregChess.logger.info("saved game $g")
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun sync(world: ServerWorld) {
        for (game in loadedGames.values) {
            if (game.renderer.data.world == world) {
                game.sync()
            }
        }
    }

}