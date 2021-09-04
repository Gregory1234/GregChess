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
    val PIECE_BLOCK = ConnectedRegistryType<PieceType, BySides<PieceBlock>>("piece_block", RegistryType.PIECE_TYPE)
    @JvmField
    val PIECE_ITEM = ConnectedRegistryType<PieceType, BySides<BlockItem>>("piece_item", RegistryType.PIECE_TYPE)
}

fun ChessModule.registerShort(t: PieceType) {
    val blocks = bySides { ShortPieceBlock(t.of(it), AbstractBlock.Settings.copy(Blocks.OAK_PLANKS)) }
    register(FabricRegistryTypes.PIECE_BLOCK, t, blocks)
    val items = bySides { BlockItem(blocks[it], FabricItemSettings().group(GregChess.CHESS_GROUP)) }
    register(FabricRegistryTypes.PIECE_ITEM, t, items)
    Side.forEach {
        Registry.register(Registry.BLOCK, t.of(it).id, blocks[it])
        Registry.register(Registry.ITEM, t.of(it).id, items[it])
    }
}

fun ChessModule.registerTall(t: PieceType, rarity: Rarity) {
    val blocks = bySides { TallPieceBlock(t.of(it), AbstractBlock.Settings.copy(Blocks.OAK_PLANKS)) }
    register(FabricRegistryTypes.PIECE_BLOCK, t, blocks)
    val items = bySides { BlockItem(blocks[it], FabricItemSettings().group(GregChess.CHESS_GROUP).rarity(rarity)) }
    register(FabricRegistryTypes.PIECE_ITEM, t, items)
    Side.forEach {
        Registry.register(Registry.BLOCK, t.of(it).id, blocks[it])
        Registry.register(Registry.ITEM, t.of(it).id, items[it])
    }
}

abstract class FabricChessModuleExtension(module: ChessModule) : ChessModuleExtension(module, FABRIC) {
    companion object {
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
    }

}