package gregc.gregchess.bukkit.renderer

import gregc.gregchess.Pos
import gregc.gregchess.bukkit.config
import gregc.gregchess.bukkit.player.BukkitChessSideFacade
import gregc.gregchess.bukkit.registry.BukkitRegistry
import gregc.gregchess.match.ChessMatch
import gregc.gregchess.move.Move
import gregc.gregchess.move.trait.*
import gregc.gregchess.variant.ChessVariant
import org.bukkit.Material

class Floor(val name: String) {
    companion object {
        @JvmField
        val LIGHT = Floor("Light")
        @JvmField
        val DARK = Floor("Dark")
        @JvmField
        val MOVE = Floor("Move")
        @JvmField
        val CAPTURE = Floor("Capture")
        @JvmField
        val SPECIAL = Floor("Special")
        @JvmField
        val NOTHING = Floor("Nothing")
        @JvmField
        val OTHER = Floor("Other")
        @JvmField
        val LAST_START = Floor("LastStart")
        @JvmField
        val LAST_END = Floor("LastEnd")
    }

    val material: Material get() = Material.valueOf(config.getString("Chess.Floor.$name")!!)
}

fun interface ChessFloorRenderer {
    fun ChessMatch.getFloor(p: Pos): Floor
}

fun simpleFloorRenderer(specialSquares: Collection<Pos> = emptyList()) = ChessFloorRenderer { p ->
    val heldPiece = (sides.current as? BukkitChessSideFacade)?.held
    fun Move.getFloorMaterial(): Floor {
        if (castlesTrait != null || promotionTrait != null)
            return Floor.SPECIAL
        captureTrait?.let {
            if (board[it.capture]?.piece != null)
                return Floor.CAPTURE
        }
        return Floor.MOVE
    }
    when(p) {
        heldPiece?.pos -> Floor.NOTHING
        in heldPiece?.getLegalMoves(board).orEmpty().map { it.display } ->
            heldPiece?.getLegalMoves(board).orEmpty().first { it.display == p }.getFloorMaterial()
        board.lastNormalMove?.origin -> Floor.LAST_START
        board.lastNormalMove?.display -> Floor.LAST_END
        in specialSquares -> Floor.OTHER
        else -> if ((p.file + p.rank) % 2 == 0) Floor.DARK else Floor.LIGHT
    }
}

val ChessVariant.floorRenderer: ChessFloorRenderer
    get() = BukkitRegistry.FLOOR_RENDERER[this]