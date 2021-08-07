package gregc.gregchess.fabric

import gregc.gregchess.*
import gregc.gregchess.chess.*
import gregc.gregchess.fabric.chess.*
import net.fabricmc.fabric.api.item.v1.FabricItemSettings
import net.minecraft.block.AbstractBlock
import net.minecraft.block.Blocks
import net.minecraft.item.BlockItem
import net.minecraft.util.Rarity
import net.minecraft.util.registry.Registry

object FabricRegistryTypes {
    @JvmField
    val PIECE_BLOCK = RegistryType<PieceType, BySides<PieceBlock>>("piece_block", RegistryType.PIECE_TYPE)
    @JvmField
    val PIECE_ITEM = RegistryType<PieceType, BySides<BlockItem>>("piece_item", RegistryType.PIECE_TYPE)
}

val ChessModule.pieceBlocks get() = this[FabricRegistryTypes.PIECE_BLOCK]
val ChessModule.pieceItems get() = this[FabricRegistryTypes.PIECE_ITEM]

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

object FabricGregChessModule : ChessModuleExtension {
    @JvmField
    val CHESSBOARD_BROKEN =
        GregChessModule.register("chessboard_broken", DrawEndReason(EndReason.Type.EMERGENCY, true))

    private fun registerItems() {
        GregChessModule.apply {
            registerShort(PieceType.PAWN)
            registerTall(PieceType.KNIGHT, Rarity.UNCOMMON)
            registerTall(PieceType.BISHOP, Rarity.UNCOMMON)
            registerTall(PieceType.ROOK, Rarity.RARE)
            registerTall(PieceType.QUEEN, Rarity.RARE)
            registerTall(PieceType.KING, Rarity.EPIC)
        }
    }

    override fun load() {
        registerItems()
    }

}