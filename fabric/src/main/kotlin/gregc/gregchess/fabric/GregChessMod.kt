package gregc.gregchess.fabric

import gregc.gregchess.fabric.block.*
import gregc.gregchess.fabric.client.*
import gregc.gregchess.fabric.match.ChessMatchManager
import gregc.gregchess.fabric.piece.item
import gregc.gregchess.piece.PieceType
import gregc.gregchess.piece.white
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.client.itemgroup.FabricItemGroupBuilder
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents
import net.fabricmc.fabric.api.item.v1.FabricItemSettings
import net.fabricmc.loader.impl.entrypoint.EntrypointUtils
import net.minecraft.block.AbstractBlock
import net.minecraft.block.Blocks
import net.minecraft.block.entity.BlockEntityType
import net.minecraft.datafixer.TypeReferences
import net.minecraft.item.BlockItem
import net.minecraft.item.ItemGroup
import net.minecraft.screen.ScreenHandlerType
import net.minecraft.util.Util
import net.minecraft.util.registry.Registry


object GregChessMod : ModInitializer {

    @JvmField
    val CHESS_GROUP: ItemGroup = FabricItemGroupBuilder.build(ident("chess")) {
        white(PieceType.PAWN).item.defaultStack
    }

    init {
        EntrypointUtils.invoke("chess", ChessInitializer::class.java, ChessInitializer::onInitializeChess)
    }

    @JvmField
    val CHESSBOARD_FLOOR_BLOCK = ChessboardFloorBlock(AbstractBlock.Settings.copy(Blocks.GLASS))
    @JvmField
    val CHESSBOARD_FLOOR_BLOCK_ITEM = BlockItem(CHESSBOARD_FLOOR_BLOCK, FabricItemSettings().group(CHESS_GROUP))
    @JvmField
    val CHESSBOARD_FLOOR_ENTITY_TYPE: BlockEntityType<ChessboardFloorBlockEntity> =
        BlockEntityType.Builder.create(::ChessboardFloorBlockEntity, CHESSBOARD_FLOOR_BLOCK)
            .build(Util.getChoiceType(TypeReferences.BLOCK_ENTITY, "chessboard_floor"))

    @JvmField
    val CHESS_CONTROLLER_BLOCK = ChessControllerBlock(AbstractBlock.Settings.copy(Blocks.GLASS))
    @JvmField
    val CHESS_CONTROLLER_ITEM = BlockItem(CHESS_CONTROLLER_BLOCK, FabricItemSettings().group(CHESS_GROUP))
    @JvmField
    val CHESS_CONTROLLER_ENTITY_TYPE: BlockEntityType<ChessControllerBlockEntity> =
        BlockEntityType.Builder.create(::ChessControllerBlockEntity, CHESS_CONTROLLER_BLOCK)
            .build(Util.getChoiceType(TypeReferences.BLOCK_ENTITY, "chess_controller"))
    @JvmField
    val CHESS_CONTROLLER_SCREEN_HANDLER_TYPE = ScreenHandlerType(::ChessControllerGuiDescription)

    @JvmField
    val PROMOTION_MENU_HANDLER_TYPE = ScreenHandlerType(::PromotionMenuGuiDescription)

    @JvmField
    val CHESS_WORKBENCH_BLOCK = ChessWorkbenchBlock(AbstractBlock.Settings.copy(Blocks.CRAFTING_TABLE))
    @JvmField
    val CHESS_WORKBENCH_ITEM = BlockItem(CHESS_WORKBENCH_BLOCK, FabricItemSettings().group(CHESS_GROUP))
    @JvmField
    val CHESS_WORKBENCH_SCREEN_HANDLER_TYPE = ScreenHandlerType(::ChessWorkbenchGuiDescription)

    override fun onInitialize() {

        // TODO: add crafting recipes for all of the blocks
        // TODO: add a chess clock
        Registry.register(Registry.BLOCK, ident("chessboard_floor"), CHESSBOARD_FLOOR_BLOCK)
        Registry.register(Registry.ITEM, ident("chessboard_floor"), CHESSBOARD_FLOOR_BLOCK_ITEM)
        Registry.register(Registry.BLOCK_ENTITY_TYPE, ident("chessboard_floor"), CHESSBOARD_FLOOR_ENTITY_TYPE)

        Registry.register(Registry.BLOCK, ident("chess_controller"), CHESS_CONTROLLER_BLOCK)
        Registry.register(Registry.ITEM, ident("chess_controller"), CHESS_CONTROLLER_ITEM)
        Registry.register(Registry.BLOCK_ENTITY_TYPE, ident("chess_controller"), CHESS_CONTROLLER_ENTITY_TYPE)
        Registry.register(Registry.SCREEN_HANDLER, ident("chess_controller"), CHESS_CONTROLLER_SCREEN_HANDLER_TYPE)

        Registry.register(Registry.SCREEN_HANDLER, ident("promotion_menu"), PROMOTION_MENU_HANDLER_TYPE)

        Registry.register(Registry.BLOCK, ident("chess_workbench"), CHESS_WORKBENCH_BLOCK)
        Registry.register(Registry.ITEM, ident("chess_workbench"), CHESS_WORKBENCH_ITEM)
        Registry.register(Registry.SCREEN_HANDLER, ident("chess_workbench"), CHESS_WORKBENCH_SCREEN_HANDLER_TYPE)

        ServerLifecycleEvents.SERVER_STARTING.register {
            ChessMatchManager.server = it
        }

        ServerLifecycleEvents.SERVER_STOPPING.register {
            ChessMatchManager.save()
            ChessMatchManager.clear()
        }

        ServerWorldEvents.LOAD.register { _, world ->
            ChessMatchManager.sync(world)
        }

    }
}