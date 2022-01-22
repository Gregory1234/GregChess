package gregc.gregchess.fabric

import gregc.gregchess.ChessModule
import gregc.gregchess.chess.Color
import gregc.gregchess.chess.Pos
import gregc.gregchess.chess.piece.*
import gregc.gregchess.chess.variant.ChessVariant
import gregc.gregchess.fabric.chess.*
import gregc.gregchess.fabric.chess.component.ChessFloorRenderer
import gregc.gregchess.registry.*
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
    val PIECE_ITEM = ConnectedBiRegistry<Piece, BlockItem>("piece_item", PieceRegistryView)
    @JvmField
    val VARIANT_FLOOR_RENDERER = DefaultedConnectedRegistry(
        "variant_floor_renderer", Registry.VARIANT
    ) { simpleFloorRenderer() }
}

fun PieceType.registerShortPieceBlock() {
    Color.forEach {
        val p = of(it)
        val block = ShortPieceBlock(p, AbstractBlock.Settings.copy(Blocks.OAK_PLANKS))
        module.register(FabricRegistry.PIECE_BLOCK, p, block)
        val item = BlockItem(block, FabricItemSettings().group(GregChessMod.CHESS_GROUP))
        module.register(FabricRegistry.PIECE_ITEM, p, item)
        MinecraftRegistry.register(MinecraftRegistry.BLOCK, p.id, block)
        MinecraftRegistry.register(MinecraftRegistry.ITEM, p.id, item)
    }
}

fun PieceType.registerTallPieceBlock(rarity: Rarity) {
    Color.forEach {
        val p = of(it)
        val block = TallPieceBlock(p, AbstractBlock.Settings.copy(Blocks.OAK_PLANKS))
        module.register(FabricRegistry.PIECE_BLOCK, p, block)
        val item = BlockItem(block, FabricItemSettings().group(GregChessMod.CHESS_GROUP).rarity(rarity))
        module.register(FabricRegistry.PIECE_ITEM, p, item)
        MinecraftRegistry.register(MinecraftRegistry.BLOCK, p.id, block)
        MinecraftRegistry.register(MinecraftRegistry.ITEM, p.id, item)
    }
}

fun ChessVariant.registerFloorRenderer(floorRenderer: ChessFloorRenderer) =
    module.register(FabricRegistry.VARIANT_FLOOR_RENDERER, this, floorRenderer)

fun ChessVariant.registerSimpleFloorRenderer(specialSquares: Collection<Pos>) =
    registerFloorRenderer(simpleFloorRenderer(specialSquares))

abstract class FabricChessModule(name: String, namespace: String) : ChessModule(name, namespace) {
    companion object {
        internal val modules = mutableSetOf<ChessModule>()
        operator fun get(namespace: String) = modules.first { it.namespace == namespace }
    }

    final override fun postLoad() {
    }

    final override fun finish() {
        modules += this
    }

    final override fun validate() {
        Registry.REGISTRIES.forEach { it[this].validate() }
    }
}