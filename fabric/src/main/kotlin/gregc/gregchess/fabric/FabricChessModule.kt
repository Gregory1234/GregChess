package gregc.gregchess.fabric

import gregc.gregchess.*
import gregc.gregchess.fabric.chess.*
import gregc.gregchess.fabric.chess.component.ChessFloorRenderer
import gregc.gregchess.piece.*
import gregc.gregchess.registry.*
import gregc.gregchess.variant.ChessVariant
import net.fabricmc.fabric.api.item.v1.FabricItemSettings
import net.minecraft.block.AbstractBlock
import net.minecraft.block.Blocks
import net.minecraft.item.BlockItem
import net.minecraft.util.Rarity
import net.minecraft.util.registry.Registry as MinecraftRegistry

object FabricRegistry {
    @JvmField
    val PIECE_BLOCK = ConnectedBiRegistry<Piece, PieceBlock>("piece_block", PieceRegistryView)
    @JvmField
    val FLOOR_RENDERER = ConnectedRegistry<ChessVariant, ChessFloorRenderer>("floor_renderer", Registry.VARIANT)
}

operator fun <T> ConnectedBiRegistry<Piece, T>.set(key: PieceType, v: ByColor<T>) {
    Color.forEach {
        set(key.of(it), v[it])
    }
}

private fun PieceBlock.registerMinecraft(p: Piece, itemSettings: FabricItemSettings): PieceBlock = apply {
    MinecraftRegistry.register(MinecraftRegistry.BLOCK, p.id, this)
    MinecraftRegistry.register(MinecraftRegistry.ITEM, p.id, BlockItem(this, itemSettings))
}

fun PieceType.shortPieceBlocks() = byColor {
    ShortPieceBlock(of(it), AbstractBlock.Settings.copy(Blocks.OAK_PLANKS))
        .registerMinecraft(of(it), FabricItemSettings().group(GregChessMod.CHESS_GROUP))
}

fun PieceType.tallPieceBlocks(rarity: Rarity) = byColor {
    TallPieceBlock(of(it), AbstractBlock.Settings.copy(Blocks.OAK_PLANKS))
        .registerMinecraft(of(it), FabricItemSettings().group(GregChessMod.CHESS_GROUP).rarity(rarity))
}

abstract class FabricChessModule(name: String, namespace: String) : ChessModule(name, namespace) {
    companion object {
        internal val modules = mutableSetOf<ChessModule>()
        operator fun get(namespace: String) = modules.first { it.namespace == namespace }
    }

    final override fun postLoad() {
        FabricRegistry.FLOOR_RENDERER[this].completeWith { simpleFloorRenderer() }
    }

    final override fun finish() {
        modules += this
    }

    final override fun validate() {
        Registry.REGISTRIES.forEach { it[this].validate() }
    }
}

fun interface ChessInitializer {
    fun onInitializeChess()
}