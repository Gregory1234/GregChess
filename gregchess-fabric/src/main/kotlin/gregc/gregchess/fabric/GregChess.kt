package gregc.gregchess.fabric

import gregc.gregchess.chess.*
import gregc.gregchess.chess.piece.PieceType
import gregc.gregchess.chess.variant.KingOfTheHill
import gregc.gregchess.fabric.chess.component.FabricComponentType
import gregc.gregchess.fabric.chess.player.FabricPlayerType
import gregc.gregchess.rangeTo
import gregc.gregchess.registerGregChessCore
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
        PieceType.PAWN.registerShortPieceBlock()
        PieceType.KNIGHT.registerTallPieceBlock(Rarity.UNCOMMON)
        PieceType.BISHOP.registerTallPieceBlock(Rarity.UNCOMMON)
        PieceType.ROOK.registerTallPieceBlock(Rarity.RARE)
        PieceType.QUEEN.registerTallPieceBlock(Rarity.RARE)
        PieceType.KING.registerTallPieceBlock(Rarity.EPIC)
    }

    override fun load() {
        registerGregChessCore(this)
        AutoRegister(this, AutoRegister.basicTypes).apply {
            registerAll<FabricComponentType>()
            registerAll<GregChess>()
            registerAll<FabricPlayerType>()
        }
        registerPieceBlocks()
        KingOfTheHill.registerSimpleFloorRenderer((Pair(3, 3)..Pair(4, 4)).map { (x,y) -> Pos(x,y) })
    }

}