package gregc.gregchess.fabric.piece

import gregc.gregchess.ByColor
import gregc.gregchess.byColor
import gregc.gregchess.fabric.*
import gregc.gregchess.piece.*
import net.fabricmc.fabric.api.item.v1.FabricItemSettings
import net.minecraft.block.AbstractBlock
import net.minecraft.block.Blocks
import net.minecraft.item.BlockItem
import net.minecraft.item.Item
import net.minecraft.sound.SoundEvent
import net.minecraft.util.Identifier
import net.minecraft.util.Rarity
import net.minecraft.util.registry.Registry

val Piece.block get() = FabricRegistry.PIECE_BLOCK[this]
val Piece.item: Item get() = block.asItem()

private fun PieceBlock.registerMinecraft(p: Piece, itemSettings: FabricItemSettings): PieceBlock = apply {
    Registry.register(Registry.BLOCK, p.id, this)
    Registry.register(Registry.ITEM, p.id, BlockItem(this, itemSettings))
}

private fun PieceType.registerSound(name: String): SoundEvent = SoundEvent(Identifier.of(id.namespace, "chess." + id.path + ".$name")).apply {
    Registry.register(Registry.SOUND_EVENT, this.id, this)
}

private fun PieceType.registerSounds() = ChessPieceSounds(registerSound("move"), registerSound("capture"), registerSound("pick_up"))

fun PieceType.shortPieceBlocks(): ByColor<PieceBlock> {
    val sounds = registerSounds()
    return byColor {
        ShortPieceBlock(of(it), sounds, AbstractBlock.Settings.copy(Blocks.OAK_PLANKS))
            .registerMinecraft(of(it), FabricItemSettings().group(GregChessMod.CHESS_GROUP))
    }
}

fun PieceType.tallPieceBlocks(rarity: Rarity): ByColor<PieceBlock> {
    val sounds = registerSounds()
    return byColor {
        TallPieceBlock(of(it), sounds, AbstractBlock.Settings.copy(Blocks.OAK_PLANKS))
            .registerMinecraft(of(it), FabricItemSettings().group(GregChessMod.CHESS_GROUP).rarity(rarity))
    }
}