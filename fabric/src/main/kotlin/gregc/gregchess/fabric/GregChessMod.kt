package gregc.gregchess.fabric

import gregc.gregchess.ExtensionType
import gregc.gregchess.chess.piece.PieceType
import gregc.gregchess.chess.piece.white
import gregc.gregchess.fabric.chess.*
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.client.itemgroup.FabricItemGroupBuilder
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents
import net.fabricmc.fabric.api.item.v1.FabricItemSettings
import net.fabricmc.fabric.api.screenhandler.v1.ScreenHandlerRegistry
import net.fabricmc.loader.impl.entrypoint.EntrypointUtils
import net.minecraft.block.*
import net.minecraft.block.entity.BlockEntityType
import net.minecraft.datafixer.TypeReferences
import net.minecraft.item.*
import net.minecraft.screen.ScreenHandlerType
import net.minecraft.util.Util
import net.minecraft.util.registry.Registry
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger


object GregChessMod : ModInitializer {

    val logger: Logger = LogManager.getFormatterLogger(MOD_NAME)

    @JvmField
    val CHESS_GROUP: ItemGroup = FabricItemGroupBuilder.build(ident("chess")) {
        white(PieceType.PAWN).item.defaultStack
    }

    init {
        ExtensionType.extensionTypes += FabricChessExtension.FABRIC

        EntrypointUtils.invoke("chess", ChessInitializer::class.java, ChessInitializer::onInitializeChess)
    }

    @JvmField
    val PIECE_ENTITY_TYPE: BlockEntityType<*> = BlockEntityType.Builder.create(
        ::PieceBlockEntity, *FabricRegistry.PIECE_BLOCK.values.toTypedArray()
    ).build(Util.getChoiceType(TypeReferences.BLOCK_ENTITY, "piece"))

    @JvmField
    val CHESSBOARD_FLOOR_BLOCK: Block = ChessboardFloorBlock(AbstractBlock.Settings.copy(Blocks.GLASS))
    @JvmField
    val CHESSBOARD_FLOOR_BLOCK_ITEM: Item = BlockItem(CHESSBOARD_FLOOR_BLOCK, FabricItemSettings().group(CHESS_GROUP))

    @JvmField
    val CHESSBOARD_FLOOR_ENTITY_TYPE: BlockEntityType<*> =
        BlockEntityType.Builder.create(::ChessboardFloorBlockEntity, CHESSBOARD_FLOOR_BLOCK)
            .build(Util.getChoiceType(TypeReferences.BLOCK_ENTITY, "chessboard_floor"))

    @JvmField
    val CHESS_CONTROLLER_BLOCK: Block = ChessControllerBlock(AbstractBlock.Settings.copy(Blocks.GLASS))
    @JvmField
    val CHESS_CONTROLLER_ITEM: Item = BlockItem(CHESS_CONTROLLER_BLOCK, FabricItemSettings().group(CHESS_GROUP))

    @JvmField
    val CHESS_CONTROLLER_ENTITY_TYPE: BlockEntityType<*> =
        BlockEntityType.Builder.create(::ChessControllerBlockEntity, CHESS_CONTROLLER_BLOCK)
            .build(Util.getChoiceType(TypeReferences.BLOCK_ENTITY, "chess_controller"))

    @JvmField
    val CHESS_CONTROLLER_SCREEN_HANDLER_TYPE: ScreenHandlerType<ChessControllerGuiDescription> =
        ScreenHandlerRegistry.registerSimple(ident("chess_controller"), ::ChessControllerGuiDescription)

    @JvmField
    val PROMOTION_MENU_HANDLER_TYPE: ScreenHandlerType<PromotionMenuGuiDescription> =
        ScreenHandlerRegistry.registerSimple(ident("promotion_menu"), ::PromotionMenuGuiDescription)

    override fun onInitialize() {

        Registry.register(Registry.BLOCK_ENTITY_TYPE, ident("piece"), PIECE_ENTITY_TYPE)
        Registry.register(Registry.BLOCK, ident("chessboard_floor"), CHESSBOARD_FLOOR_BLOCK)
        Registry.register(Registry.ITEM, ident("chessboard_floor"), CHESSBOARD_FLOOR_BLOCK_ITEM)
        Registry.register(Registry.BLOCK_ENTITY_TYPE, ident("chessboard_floor"), CHESSBOARD_FLOOR_ENTITY_TYPE)
        Registry.register(Registry.BLOCK, ident("chess_controller"), CHESS_CONTROLLER_BLOCK)
        Registry.register(Registry.ITEM, ident("chess_controller"), CHESS_CONTROLLER_ITEM)
        Registry.register(Registry.BLOCK_ENTITY_TYPE, ident("chess_controller"), CHESS_CONTROLLER_ENTITY_TYPE)

        ServerLifecycleEvents.SERVER_STARTING.register {
            ChessGameManager.server = it
        }

        ServerLifecycleEvents.SERVER_STOPPING.register {
            ChessGameManager.save()
            ChessGameManager.clear()
        }

        ServerWorldEvents.LOAD.register { _, world ->
            ChessGameManager.sync(world)
        }

    }
}