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
    val CHESSBOARD_BROKEN = GregChess.registerEndReason("chessboard_broken", DrawEndReason(EndReason.Type.EMERGENCY))
    @JvmField
    val ABORTED = GregChess.registerEndReason("aborted", DrawEndReason(EndReason.Type.EMERGENCY))

    private fun registerPieceBlocks() = with(GregChess) {
        registerShortPieceBlock(PieceType.PAWN)
        registerTallPieceBlock(PieceType.KNIGHT, Rarity.UNCOMMON)
        registerTallPieceBlock(PieceType.BISHOP, Rarity.UNCOMMON)
        registerTallPieceBlock(PieceType.ROOK, Rarity.RARE)
        registerTallPieceBlock(PieceType.QUEEN, Rarity.RARE)
        registerTallPieceBlock(PieceType.KING, Rarity.EPIC)
    }

    private fun registerComponents() = with(GregChess) {
        registerComponent<FabricRenderer, FabricRendererSettings>("fabric_renderer")
        registerSimpleComponent<GameController>("game_controller")
    }

    override fun load(): Unit = with(GregChess) {
        registerPieceBlocks()
        registerComponents()
        registerPlayerClass<FabricPlayer>("fabric")
        registerSimpleFloorRenderer(KingOfTheHill, (Pair(3, 3)..Pair(4, 4)).map { (x,y) -> Pos(x,y) })
        completeFloorRenderers()
    }

}