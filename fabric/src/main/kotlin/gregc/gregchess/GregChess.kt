package gregc.gregchess

import gregc.gregchess.chess.*
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.client.itemgroup.FabricItemGroupBuilder
import net.fabricmc.fabric.api.item.v1.FabricItemSettings
import net.fabricmc.fabric.api.screenhandler.v1.ScreenHandlerRegistry
import net.minecraft.block.*
import net.minecraft.block.entity.BlockEntityType
import net.minecraft.datafixer.TypeReferences
import net.minecraft.item.*
import net.minecraft.screen.ScreenHandlerContext
import net.minecraft.screen.ScreenHandlerType
import net.minecraft.util.Rarity
import net.minecraft.util.Util
import net.minecraft.util.registry.Registry
import java.util.logging.Logger


object GregChess : ModInitializer {
    val CHESS_GROUP: ItemGroup = FabricItemGroupBuilder.build(ident("chess")) {
        PieceType.PAWN.white.item.defaultStack
    }

    val PIECE_BLOCKS = PieceType.values().flatMap { t -> Side.values().map { s ->
        val piece = t.of(s)
        val block =
            if (t == PieceType.PAWN) PawnBlock(piece, AbstractBlock.Settings.copy(Blocks.OAK_PLANKS))
            else TallPieceBlock(piece, AbstractBlock.Settings.copy(Blocks.OAK_PLANKS))
        piece to block
    } }.toMap()

    val PIECE_ITEMS = PieceType.values().flatMap { t -> Side.values().map { s ->
        val piece = t.of(s)
        val block = piece.block
        val item =
            if (t == PieceType.PAWN) PawnItem(block, FabricItemSettings().group(CHESS_GROUP))
            else TallPieceItem(block, FabricItemSettings().group(CHESS_GROUP).rarity(when {
                t.minor -> Rarity.UNCOMMON
                t == PieceType.KING -> Rarity.EPIC
                else -> Rarity.RARE
            }))
        piece to item
    } }.toMap()

    val PIECE_ENTITY_TYPE: BlockEntityType<*> =
        BlockEntityType.Builder.create({ a, b -> PieceBlockEntity(a, b) }, *PIECE_BLOCKS.values.toTypedArray()).build(
            Util.getChoiceType(TypeReferences.BLOCK_ENTITY, "piece"))

    val CHESSBOARD_FLOOR_BLOCK: Block = ChessboardFloorBlock(AbstractBlock.Settings.copy(Blocks.GLASS))
    val CHESSBOARD_FLOOR_BLOCK_ITEM: Item = BlockItem(CHESSBOARD_FLOOR_BLOCK, FabricItemSettings().group(CHESS_GROUP))

    val CHESSBOARD_FLOOR_ENTITY_TYPE: BlockEntityType<*> =
        BlockEntityType.Builder.create({ a, b -> ChessboardFloorBlockEntity(a, b) }, CHESSBOARD_FLOOR_BLOCK).build(
            Util.getChoiceType(TypeReferences.BLOCK_ENTITY, "chessboard_floor"))

    val CHESS_CONTROLLER_BLOCK: Block = ChessControllerBlock(AbstractBlock.Settings.copy(Blocks.GLASS))
    val CHESS_CONTROLLER_ITEM: Item = BlockItem(CHESS_CONTROLLER_BLOCK, FabricItemSettings().group(CHESS_GROUP))

    val CHESS_CONTROLLER_ENTITY_TYPE: BlockEntityType<*> =
        BlockEntityType.Builder.create({ a, b -> ChessControllerBlockEntity(a, b) }, CHESS_CONTROLLER_BLOCK).build(
            Util.getChoiceType(TypeReferences.BLOCK_ENTITY, "chess_controller"))

    val CHESS_CONTROLLER_SCREEN_HANDLER_TYPE: ScreenHandlerType<ChessControllerGuiDescription> =
        ScreenHandlerRegistry.registerSimple(ident("chess_controller")) {
            syncId, inventory -> ChessControllerGuiDescription(syncId, inventory, ScreenHandlerContext.EMPTY) }

    override fun onInitialize() {
        glog = GregLogger(Logger.getLogger(MOD_NAME))
        PieceType.values().forEach { t ->
            Side.values().forEach { s ->
                val piece = t.of(s)
                val block = piece.block
                Registry.register(Registry.BLOCK, piece.id.fabric, block)
                val item = piece.item
                Registry.register(Registry.ITEM, piece.id.fabric, item)
            }
        }
        Registry.register(Registry.BLOCK_ENTITY_TYPE, ident("piece"), PIECE_ENTITY_TYPE)
        Registry.register(Registry.BLOCK, ident("chessboard_floor"), CHESSBOARD_FLOOR_BLOCK)
        Registry.register(Registry.ITEM, ident("chessboard_floor"), CHESSBOARD_FLOOR_BLOCK_ITEM)
        Registry.register(Registry.BLOCK_ENTITY_TYPE, ident("chessboard_floor"), CHESSBOARD_FLOOR_ENTITY_TYPE)
        Registry.register(Registry.BLOCK, ident("chess_controller"), CHESS_CONTROLLER_BLOCK)
        Registry.register(Registry.ITEM, ident("chess_controller"), CHESS_CONTROLLER_ITEM)
        Registry.register(Registry.BLOCK_ENTITY_TYPE, ident("chess_controller"), CHESS_CONTROLLER_ENTITY_TYPE)

    }
}