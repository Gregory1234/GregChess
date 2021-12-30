package gregc.gregchess.fabric

import gregc.gregchess.ChessModule
import gregc.gregchess.chess.Color
import gregc.gregchess.chess.Pos
import gregc.gregchess.chess.piece.*
import gregc.gregchess.chess.variant.ChessVariant
import gregc.gregchess.fabric.chess.*
import gregc.gregchess.fabric.chess.component.ChessFloorRenderer
import gregc.gregchess.registry.ConnectedBiRegistry
import gregc.gregchess.registry.ConnectedRegistry
import net.fabricmc.fabric.api.item.v1.FabricItemSettings
import net.minecraft.block.AbstractBlock
import net.minecraft.block.Blocks
import net.minecraft.item.BlockItem
import net.minecraft.util.Rarity
import net.minecraft.util.registry.Registry

object FabricRegistry {
    @JvmField
    val PIECE_BLOCK = ConnectedBiRegistry<Piece, PieceBlock>("piece_block", PieceRegistryView)
    @JvmField
    val PIECE_ITEM = ConnectedBiRegistry<Piece, BlockItem>("piece_item", PieceRegistryView)
    @JvmField
    val VARIANT_FLOOR_RENDERER = ConnectedRegistry<ChessVariant, ChessFloorRenderer>(
        "variant_floor_renderer", gregc.gregchess.registry.Registry.VARIANT
    )
}

fun ChessModule.registerShort(t: PieceType) {
    Color.forEach {
        val p = t.of(it)
        val block = ShortPieceBlock(p, AbstractBlock.Settings.copy(Blocks.OAK_PLANKS))
        register(FabricRegistry.PIECE_BLOCK, p, block)
        val item = BlockItem(block, FabricItemSettings().group(GregChessMod.CHESS_GROUP))
        register(FabricRegistry.PIECE_ITEM, p, item)
        Registry.register(Registry.BLOCK, p.id, block)
        Registry.register(Registry.ITEM, p.id, item)
    }
}

fun ChessModule.registerTall(t: PieceType, rarity: Rarity) {
    Color.forEach {
        val p = t.of(it)
        val block = TallPieceBlock(p, AbstractBlock.Settings.copy(Blocks.OAK_PLANKS))
        register(FabricRegistry.PIECE_BLOCK, p, block)
        val item = BlockItem(block, FabricItemSettings().group(GregChessMod.CHESS_GROUP).rarity(rarity))
        register(FabricRegistry.PIECE_ITEM, p, item)
        Registry.register(Registry.BLOCK, p.id, block)
        Registry.register(Registry.ITEM, p.id, item)
    }
}

fun ChessModule.registerFloorRenderer(variant: ChessVariant, floorRenderer: ChessFloorRenderer) =
    register(FabricRegistry.VARIANT_FLOOR_RENDERER, variant, floorRenderer)

fun ChessModule.registerSimpleFloorRenderer(variant: ChessVariant, specialSquares: Collection<Pos>) =
    registerFloorRenderer(variant, simpleFloorRenderer(specialSquares))

fun ChessModule.completeFloorRenderers() =
    get(FabricRegistry.VARIANT_FLOOR_RENDERER).completeWith { simpleFloorRenderer() }
