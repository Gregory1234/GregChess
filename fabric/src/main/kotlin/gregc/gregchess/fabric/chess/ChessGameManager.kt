package gregc.gregchess.fabric.chess

import gregc.gregchess.Pos
import gregc.gregchess.board.Chessboard
import gregc.gregchess.board.FEN
import gregc.gregchess.fabric.GregChess
import gregc.gregchess.fabric.chess.component.*
import gregc.gregchess.fabric.defaultModule
import gregc.gregchess.fabric.mixin.WorldSavePathCreator
import gregc.gregchess.fabric.nbt.*
import gregc.gregchess.game.ChessGame
import gregc.gregchess.game.Component
import gregc.gregchess.piece.Piece
import gregc.gregchess.variant.ChessVariant
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

    fun settings(v: ChessVariant, boardState: Map<Pos, Piece>, r: FabricRenderer): Collection<Component> = buildList {
        this += Chessboard(v, FEN.fromPieces(boardState))
        this += GameController()
        this += r
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
            if (game.renderer.world == world) {
                game.sync()
            }
        }
    }

}