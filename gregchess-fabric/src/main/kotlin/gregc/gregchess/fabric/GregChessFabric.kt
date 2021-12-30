package gregc.gregchess.fabric

import gregc.gregchess.*
import gregc.gregchess.chess.*
import gregc.gregchess.chess.piece.PieceType
import gregc.gregchess.chess.variant.KingOfTheHill
import gregc.gregchess.fabric.chess.component.*
import gregc.gregchess.fabric.chess.player.FabricPlayer
import net.minecraft.util.Rarity

internal object GregChessFabric : ChessExtension {
    @JvmField
    val CHESSBOARD_BROKEN = GregChess.register("chessboard_broken", DrawEndReason(EndReason.Type.EMERGENCY))
    @JvmField
    val ABORTED = GregChess.register("aborted", DrawEndReason(EndReason.Type.EMERGENCY))

    private fun registerItems() = with(GregChess) {
        registerShort(PieceType.PAWN)
        registerTall(PieceType.KNIGHT, Rarity.UNCOMMON)
        registerTall(PieceType.BISHOP, Rarity.UNCOMMON)
        registerTall(PieceType.ROOK, Rarity.RARE)
        registerTall(PieceType.QUEEN, Rarity.RARE)
        registerTall(PieceType.KING, Rarity.EPIC)
    }

    private fun registerComponents() = with(GregChess) {
        registerComponent<FabricRenderer, FabricRendererSettings>("fabric_renderer")
        registerSimpleComponent<GameController>("game_controller")
    }

    override fun load(): Unit = with(GregChess) {
        registerItems()
        registerComponents()
        register<FabricPlayer>("fabric")
        registerSimpleFloorRenderer(KingOfTheHill, (Pair(3, 3)..Pair(4, 4)).map { (x,y) -> Pos(x,y) })
        completeFloorRenderers()
    }

}