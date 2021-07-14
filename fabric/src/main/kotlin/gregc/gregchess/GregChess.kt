package gregc.gregchess

import gregc.gregchess.chess.*
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.client.itemgroup.FabricItemGroupBuilder
import net.fabricmc.fabric.api.item.v1.FabricItemSettings
import net.minecraft.block.AbstractBlock
import net.minecraft.block.Blocks
import net.minecraft.block.entity.BlockEntityType
import net.minecraft.datafixer.TypeReferences
import net.minecraft.item.*
import net.minecraft.util.Rarity
import net.minecraft.util.Util
import net.minecraft.util.registry.Registry
import java.util.logging.Logger


object GregChess : ModInitializer {
    val PIECES_GROUP: ItemGroup = FabricItemGroupBuilder.build(ident("pieces")) {
        PIECE_ITEMS[Piece(PieceType.PAWN, Side.WHITE)]?.defaultStack
    }

    val PIECE_BLOCKS = PieceType.values().flatMap { t -> Side.values().map { s ->
        val piece = Piece(t, s)
        val block =
            if (t == PieceType.PAWN) PawnBlock(piece, AbstractBlock.Settings.copy(Blocks.OAK_PLANKS))
            else TallPieceBlock(piece, AbstractBlock.Settings.copy(Blocks.OAK_PLANKS))
        piece to block
    } }.toMap()

    val PIECE_ITEMS = PieceType.values().flatMap { t -> Side.values().map { s ->
        val piece = Piece(t, s)
        val block = piece.block
        val item =
            if (t == PieceType.PAWN) BlockItem(block, FabricItemSettings().group(PIECES_GROUP))
            else TallBlockItem(block, FabricItemSettings().group(PIECES_GROUP).rarity(when {
                t.minor -> Rarity.UNCOMMON
                t == PieceType.KING -> Rarity.EPIC
                else -> Rarity.RARE
            }))
        piece to item
    } }.toMap()

    val PIECE_ENTITY_TYPE: BlockEntityType<*> =
        BlockEntityType.Builder.create({ a, b -> PieceBlockEntity(a, b) }, *PIECE_BLOCKS.values.toTypedArray()).build(
            Util.getChoiceType(TypeReferences.BLOCK_ENTITY, "piece"))

    override fun onInitialize() {
        glog = GregLogger(Logger.getLogger(MOD_NAME))
        PieceType.values().forEach { t ->
            Side.values().forEach { s ->
                val piece = Piece(t, s)
                val block = piece.block
                Registry.register(Registry.BLOCK, piece.id, block)
                val item = piece.item
                Registry.register(Registry.ITEM, piece.id, item)
            }
        }
    }
}