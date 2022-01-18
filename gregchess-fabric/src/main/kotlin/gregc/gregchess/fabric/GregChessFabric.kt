package gregc.gregchess.fabric

import gregc.gregchess.*
import gregc.gregchess.chess.*
import gregc.gregchess.chess.piece.PieceType
import gregc.gregchess.chess.variant.KingOfTheHill
import gregc.gregchess.fabric.chess.component.*
import gregc.gregchess.fabric.chess.player.FabricPlayer
import gregc.gregchess.registry.AutoRegister
import gregc.gregchess.registry.Register
import net.minecraft.util.Rarity

internal object GregChessFabric : ChessExtension {
    @JvmField
    @Register
    val CHESSBOARD_BROKEN = DrawEndReason(EndReason.Type.EMERGENCY)
    @JvmField
    @Register
    val ABORTED = DrawEndReason(EndReason.Type.EMERGENCY)

    private fun registerPieceBlocks() = with(GregChess) {
        registerShortPieceBlock(PieceType.PAWN)
        registerTallPieceBlock(PieceType.KNIGHT, Rarity.UNCOMMON)
        registerTallPieceBlock(PieceType.BISHOP, Rarity.UNCOMMON)
        registerTallPieceBlock(PieceType.ROOK, Rarity.RARE)
        registerTallPieceBlock(PieceType.QUEEN, Rarity.RARE)
        registerTallPieceBlock(PieceType.KING, Rarity.EPIC)
    }

    private fun registerComponents() = with(GregChess) {
        registerComponent<FabricRenderer>("fabric_renderer")
        registerComponent<GameController>("game_controller")
    }

    override fun load(): Unit = with(GregChess) {
        registerPieceBlocks()
        registerComponents()
        AutoRegister(this, AutoRegister.basicTypes).registerAll<GregChessFabric>()
        registerPlayerClass<FabricPlayer>("fabric")
        registerSimpleFloorRenderer(KingOfTheHill, (Pair(3, 3)..Pair(4, 4)).map { (x,y) -> Pos(x,y) })
        completeFloorRenderers()
    }

}