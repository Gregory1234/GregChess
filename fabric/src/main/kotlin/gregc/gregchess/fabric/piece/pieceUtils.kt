package gregc.gregchess.fabric.piece

import gregc.gregchess.ByColor
import gregc.gregchess.byColor
import gregc.gregchess.fabric.FabricRegistry
import gregc.gregchess.fabric.id
import gregc.gregchess.piece.*
import net.fabricmc.fabric.api.item.v1.FabricItemSettings
import net.minecraft.block.AbstractBlock
import net.minecraft.block.Blocks
import net.minecraft.item.BlockItem
import net.minecraft.item.Item
import net.minecraft.registry.Registries
import net.minecraft.registry.Registry
import net.minecraft.sound.SoundEvent
import net.minecraft.util.Identifier
import net.minecraft.util.Rarity

val Piece.block get() = FabricRegistry.PIECE_BLOCK[this]
val Piece.item: Item get() = block.asItem()

private fun PieceBlock.registerMinecraft(p: Piece, itemSettings: FabricItemSettings): PieceBlock = apply {
    Registry.register(Registries.BLOCK, p.id, this)
    Registry.register(Registries.ITEM, p.id, BlockItem(this, itemSettings))
}

private fun PieceType.registerSound(name: String): SoundEvent = SoundEvent.of(Identifier.of(id.namespace, "chess." + id.path + ".$name")).apply {
    Registry.register(Registries.SOUND_EVENT, this.id, this)
}

private fun PieceType.registerSounds() = ChessPieceSounds(registerSound("move"), registerSound("capture"), registerSound("pick_up"))

fun PieceType.shortPieceBlocks(): ByColor<PieceBlock> {
    val sounds = registerSounds()
    return byColor {
        ShortPieceBlock(of(it), sounds, AbstractBlock.Settings.copy(Blocks.OAK_PLANKS))
            .registerMinecraft(of(it), FabricItemSettings())
    }
}

fun PieceType.tallPieceBlocks(rarity: Rarity): ByColor<PieceBlock> {
    val sounds = registerSounds()
    return byColor {
        TallPieceBlock(of(it), sounds, AbstractBlock.Settings.copy(Blocks.OAK_PLANKS))
            .registerMinecraft(of(it), FabricItemSettings().rarity(rarity))
    }
}