package gregc.gregchess.fabric.match

import gregc.gregchess.Pos
import gregc.gregchess.board.Chessboard
import gregc.gregchess.board.FEN
import gregc.gregchess.fabric.GregChess
import gregc.gregchess.fabric.defaultModule
import gregc.gregchess.fabric.mixin.WorldSavePathCreator
import gregc.gregchess.fabric.nbt.*
import gregc.gregchess.fabric.renderer.FabricRenderer
import gregc.gregchess.fabric.renderer.renderer
import gregc.gregchess.match.ChessMatch
import gregc.gregchess.match.Component
import gregc.gregchess.piece.Piece
import gregc.gregchess.variant.ChessVariant
import net.minecraft.nbt.NbtCompound
import net.minecraft.nbt.NbtIo
import net.minecraft.server.MinecraftServer
import net.minecraft.server.world.ServerWorld
import java.util.*

object ChessMatchManager {

    private val gregchessPath = WorldSavePathCreator.create("gregchess")

    private val loadedMatches = mutableMapOf<UUID, ChessMatch>()

    internal lateinit var server: MinecraftServer

    private fun matchFile(uuid: UUID) = server.getSavePath(gregchessPath).resolve("$uuid.dat").toFile()

    operator fun get(uuid: UUID): ChessMatch? = loadedMatches.getOrPut(uuid) {
        GregChess.logger.info("loading match $uuid")
        val f = matchFile(uuid)
        if (f.exists()) {
            val nbt = Nbt(defaultModule(server))
            try {
                nbt.decodeFromNbtElement<ChessMatch>(NbtIo.readCompressed(f)).also {
                    it.sync()
                    GregChess.logger.info("loaded match $it")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                return null
            }
        } else return null
    }

    operator fun plusAssign(match: ChessMatch) {
        GregChess.logger.info("added match $match")
        loadedMatches[match.uuid] = match
    }

    operator fun minusAssign(match: ChessMatch) {
        GregChess.logger.info("removed match $match")
        loadedMatches.remove(match.uuid, match)
        val file = matchFile(match.uuid)
        if (file.exists()) {
            file.delete()
        }
    }

    fun clear() = loadedMatches.clear()

    fun settings(v: ChessVariant, boardState: Map<Pos, Piece>, r: FabricRenderer): Collection<Component> = buildList {
        this += Chessboard(v, FEN.fromPieces(boardState))
        this += MatchController()
        this += r
    }


    fun save() {
        val nbt = Nbt(defaultModule(server))
        try {
            for ((u, g) in loadedMatches) {
                val f = matchFile(u)
                f.parentFile.mkdirs()
                NbtIo.writeCompressed(nbt.encodeToNbtElement(g) as NbtCompound, f)
                GregChess.logger.info("saved match $g")
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun sync(world: ServerWorld) {
        for (match in loadedMatches.values) {
            if (match.renderer.world == world) {
                match.sync()
            }
        }
    }

}