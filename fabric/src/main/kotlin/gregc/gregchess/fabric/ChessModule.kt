package gregc.gregchess.fabric

import gregc.gregchess.*
import gregc.gregchess.chess.*
import gregc.gregchess.chess.piece.*
import gregc.gregchess.chess.player.ChessPlayerType
import gregc.gregchess.fabric.chess.*
import gregc.gregchess.fabric.chess.component.*
import gregc.gregchess.fabric.chess.player.FabricPlayer
import gregc.gregchess.registry.ConnectedRegistryType
import net.fabricmc.fabric.api.item.v1.FabricItemSettings
import net.minecraft.block.AbstractBlock
import net.minecraft.block.Blocks
import net.minecraft.item.BlockItem
import net.minecraft.util.Rarity
import net.minecraft.util.registry.Registry

object FabricRegistryTypes {
    @JvmField
    val PIECE_BLOCK = ConnectedRegistryType<Piece, PieceBlock>("piece_block", PieceRegistryView)
    @JvmField
    val PIECE_ITEM = ConnectedRegistryType<Piece, BlockItem>("piece_item", PieceRegistryView)
}

fun ChessModule.registerShort(t: PieceType) {
    Color.forEach {
        val p = t.of(it)
        val block = ShortPieceBlock(p, AbstractBlock.Settings.copy(Blocks.OAK_PLANKS))
        register(FabricRegistryTypes.PIECE_BLOCK, p, block)
        val item = BlockItem(block, FabricItemSettings().group(GregChess.CHESS_GROUP))
        register(FabricRegistryTypes.PIECE_ITEM, p, item)
        Registry.register(Registry.BLOCK, p.id, block)
        Registry.register(Registry.ITEM, p.id, item)
    }
}

fun ChessModule.registerTall(t: PieceType, rarity: Rarity) {
    Color.forEach {
        val p = t.of(it)
        val block = TallPieceBlock(p, AbstractBlock.Settings.copy(Blocks.OAK_PLANKS))
        register(FabricRegistryTypes.PIECE_BLOCK, p, block)
        val item = BlockItem(block, FabricItemSettings().group(GregChess.CHESS_GROUP).rarity(rarity))
        register(FabricRegistryTypes.PIECE_ITEM, p, item)
        Registry.register(Registry.BLOCK, p.id, block)
        Registry.register(Registry.ITEM, p.id, item)
    }
}

abstract class FabricChessExtension(module: ChessModule) : ChessExtension(module, FABRIC) {
    companion object {
        @JvmField
        internal val FABRIC = ExtensionType("fabric")
    }
}

object FabricGregChessModule : FabricChessExtension(GregChessModule) {
    @JvmField
    val CHESSBOARD_BROKEN = GregChessModule.register("chessboard_broken", DrawEndReason(EndReason.Type.EMERGENCY))
    @JvmField
    val ABORTED = GregChessModule.register("aborted", DrawEndReason(EndReason.Type.EMERGENCY))

    private fun registerItems() = with(GregChessModule) {
        registerShort(PieceType.PAWN)
        registerTall(PieceType.KNIGHT, Rarity.UNCOMMON)
        registerTall(PieceType.BISHOP, Rarity.UNCOMMON)
        registerTall(PieceType.ROOK, Rarity.RARE)
        registerTall(PieceType.QUEEN, Rarity.RARE)
        registerTall(PieceType.KING, Rarity.EPIC)
    }

    private fun registerComponents() = with(GregChessModule) {
        registerComponent<FabricRenderer, FabricRendererSettings>("fabric_renderer")
        registerSimpleComponent<GameController>("game_controller")
    }

    override fun load() {
        registerItems()
        registerComponents()
        GregChessModule.register("fabric", ChessPlayerType(UUIDAsIntArraySerializer) { c,g -> FabricPlayer(this, c, g) })
    }

}