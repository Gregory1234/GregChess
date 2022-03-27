package gregc.gregchess.fabric

import gregc.gregchess.GregChessCore
import gregc.gregchess.chess.DrawEndReason
import gregc.gregchess.chess.EndReason
import gregc.gregchess.chess.piece.PieceType
import gregc.gregchess.chess.variant.KingOfTheHill
import gregc.gregchess.fabric.chess.component.FabricComponentType
import gregc.gregchess.fabric.chess.player.FabricPlayerType
import gregc.gregchess.fabric.chess.simpleFloorRenderer
import gregc.gregchess.registry.AutoRegister
import gregc.gregchess.registry.Register
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
        GregChessCore.registerAll(this)
        AutoRegister(this, AutoRegister.basicTypes).apply {
            registerAll<FabricComponentType>()
            registerAll<GregChess>()
            registerAll<FabricPlayerType>()
        }
        registerPieceBlocks()
        FabricRegistry.FLOOR_RENDERER[KingOfTheHill] = simpleFloorRenderer(KingOfTheHill.SPECIAL_SQUARES)
    }

}