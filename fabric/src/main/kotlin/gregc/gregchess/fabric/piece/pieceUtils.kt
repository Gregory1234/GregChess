package gregc.gregchess.fabric.piece

import gregc.gregchess.byColor
import gregc.gregchess.fabric.GregChessMod
import gregc.gregchess.fabric.id
import gregc.gregchess.fabric.registry.FabricRegistry
import gregc.gregchess.piece.*
import net.fabricmc.fabric.api.item.v1.FabricItemSettings
import net.minecraft.block.AbstractBlock
import net.minecraft.block.Blocks
import net.minecraft.item.BlockItem
import net.minecraft.item.Item
import net.minecraft.util.Rarity
import net.minecraft.util.registry.Registry

val Piece.block get() = FabricRegistry.PIECE_BLOCK[this]
val Piece.item: Item get() = block.asItem()

private fun PieceBlock.registerMinecraft(p: Piece, itemSettings: FabricItemSettings): PieceBlock = apply {
    Registry.register(Registry.BLOCK, p.id, this)
    Registry.register(Registry.ITEM, p.id, BlockItem(this, itemSettings))
}

fun PieceType.shortPieceBlocks() = byColor {
    ShortPieceBlock(of(it), AbstractBlock.Settings.copy(Blocks.OAK_PLANKS))
        .registerMinecraft(of(it), FabricItemSettings().group(GregChessMod.CHESS_GROUP))
}

fun PieceType.tallPieceBlocks(rarity: Rarity) = byColor {
    TallPieceBlock(of(it), AbstractBlock.Settings.copy(Blocks.OAK_PLANKS))
        .registerMinecraft(of(it), FabricItemSettings().group(GregChessMod.CHESS_GROUP).rarity(rarity))
}