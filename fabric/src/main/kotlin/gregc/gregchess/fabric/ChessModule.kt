package gregc.gregchess.fabric

import gregc.gregchess.*
import gregc.gregchess.chess.*
import gregc.gregchess.fabric.chess.*
import gregc.gregchess.fabric.chess.component.*
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

abstract class FabricChessModuleExtension(module: ChessModule) : ChessModuleExtension(module, FABRIC) {
    companion object {
        @JvmField
        internal val FABRIC = ExtensionType("fabric")
    }
}

object FabricGregChessModule : FabricChessModuleExtension(GregChessModule) {
    @JvmField
    val CHESSBOARD_BROKEN =
        GregChessModule.register("chessboard_broken", DrawEndReason(EndReason.Type.EMERGENCY, true))

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
        registerComponent<PlayerManager, PlayerManagerData>("player_manager")
    }

    override fun load() {
        registerItems()
        registerComponents()
        GregChessModule.registerPlayerType<FabricPlayerInfo>("fabric")
    }

}