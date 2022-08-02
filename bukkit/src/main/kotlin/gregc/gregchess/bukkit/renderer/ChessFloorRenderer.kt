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


fun interface ChessFloorRenderer {
    fun ChessMatch.getFloorMaterial(p: Pos): Material
}

private fun getFloor(name: String): Material = Material.valueOf(config.getString("Chess.Floor.$name")!!)

fun simpleFloorRenderer(specialSquares: Collection<Pos> = emptyList()) = ChessFloorRenderer { p ->
    val heldPiece = (currentSide as? BukkitChessSideFacade)?.held
    fun Move.getFloorMaterial(): Material {
        if (castlesTrait != null || promotionTrait != null)
            return getFloor("Special")
        captureTrait?.let {
            if (board[it.capture]?.piece != null)
                return getFloor("Capture")
        }
        return getFloor("Move")
    }
    when(p) {
        heldPiece?.pos -> getFloor("Nothing")
        in heldPiece?.getLegalMoves(board).orEmpty().map { it.display } ->
            heldPiece?.getLegalMoves(board).orEmpty().first { it.display == p }.getFloorMaterial()
        board.lastNormalMove?.origin -> getFloor("LastStart")
        board.lastNormalMove?.display -> getFloor("LastEnd")
        in specialSquares -> getFloor("Other")
        else -> if ((p.file + p.rank) % 2 == 0) getFloor("Dark") else getFloor("Light")
    }
}

val ChessVariant.floorRenderer: ChessFloorRenderer
    get() = BukkitRegistry.FLOOR_RENDERER[this]