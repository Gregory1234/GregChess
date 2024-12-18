package gregc.gregchess.fabric

import gregc.gregchess.CoreAutoRegister
import gregc.gregchess.fabric.component.FabricComponentType
import gregc.gregchess.fabric.component.MatchController
import gregc.gregchess.fabric.piece.shortPieceBlocks
import gregc.gregchess.fabric.piece.tallPieceBlocks
import gregc.gregchess.fabric.player.FabricChessSideType
import gregc.gregchess.fabric.renderer.simpleFloorRenderer
import gregc.gregchess.piece.PieceType
import gregc.gregchess.registry.Register
import gregc.gregchess.results.DrawEndReason
import gregc.gregchess.results.EndReason
import gregc.gregchess.variant.KingOfTheHill
import net.minecraft.util.Rarity

internal object GregChess : FabricChessModule("GregChess", "gregchess") {
    @JvmField
    @Register
    val CHESSBOARD_BROKEN = DrawEndReason(EndReason.Type.EMERGENCY)
    @JvmField
    @Register
    val ABORTED = DrawEndReason(EndReason.Type.EMERGENCY)

    private fun registerPieceBlocks() {
        FabricRegistry.PIECE_BLOCK[PieceType.PAWN] = PieceType.PAWN.shortPieceBlocks()
        FabricRegistry.PIECE_BLOCK[PieceType.KNIGHT] = PieceType.KNIGHT.tallPieceBlocks(Rarity.UNCOMMON)
        FabricRegistry.PIECE_BLOCK[PieceType.BISHOP] = PieceType.BISHOP.tallPieceBlocks(Rarity.UNCOMMON)
        FabricRegistry.PIECE_BLOCK[PieceType.ROOK] = PieceType.ROOK.tallPieceBlocks(Rarity.RARE)
        FabricRegistry.PIECE_BLOCK[PieceType.QUEEN] = PieceType.QUEEN.tallPieceBlocks(Rarity.RARE)
        FabricRegistry.PIECE_BLOCK[PieceType.KING] = PieceType.KING.tallPieceBlocks(Rarity.EPIC)
    }

    override fun load() {
        CoreAutoRegister.registerAll(this)
        CoreAutoRegister(this).apply {
            registerAll<FabricComponentType>()
            registerAll<GregChess>()
            registerAll<FabricChessSideType>()
        }
        registerPieceBlocks()
        FabricRegistry.FLOOR_RENDERER[KingOfTheHill] = simpleFloorRenderer(KingOfTheHill.SPECIAL_SQUARES)
        FabricRegistry.IMPLIED_COMPONENTS["match_controller"] = { MatchController }
    }

}