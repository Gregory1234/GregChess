package gregc.gregchess.fabric

import gregc.gregchess.ChessModule
import gregc.gregchess.ExtensionType
import gregc.gregchess.chess.pawn
import gregc.gregchess.chess.white
import gregc.gregchess.fabric.chess.*
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.client.itemgroup.FabricItemGroupBuilder
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.item.v1.FabricItemSettings
import net.fabricmc.fabric.api.screenhandler.v1.ScreenHandlerRegistry
import net.fabricmc.loader.entrypoint.minecraft.hooks.EntrypointUtils
import net.minecraft.block.*
import net.minecraft.block.entity.BlockEntityType
import net.minecraft.datafixer.TypeReferences
import net.minecraft.item.*
import net.minecraft.screen.ScreenHandlerContext
import net.minecraft.screen.ScreenHandlerType
import net.minecraft.util.Util
import net.minecraft.util.registry.Registry
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger


object GregChess : ModInitializer {

    val logger: Logger = LogManager.getFormatterLogger(MOD_NAME)

    val PLAYER_EXTRA_INFO_SYNC = ident("player_extra_info_sync")

    val CHESS_GROUP: ItemGroup = FabricItemGroupBuilder.build(ident("chess")) {
        white.pawn.item.defaultStack
    }

    lateinit var PIECE_ENTITY_TYPE: BlockEntityType<*>
        private set

    val CHESSBOARD_FLOOR_BLOCK: Block = ChessboardFloorBlock(AbstractBlock.Settings.copy(Blocks.GLASS))
    val CHESSBOARD_FLOOR_BLOCK_ITEM: Item = BlockItem(CHESSBOARD_FLOOR_BLOCK, FabricItemSettings().group(CHESS_GROUP))

    val CHESSBOARD_FLOOR_ENTITY_TYPE: BlockEntityType<*> =
        BlockEntityType.Builder.create({ a, b -> ChessboardFloorBlockEntity(a, b) }, CHESSBOARD_FLOOR_BLOCK).build(
            Util.getChoiceType(TypeReferences.BLOCK_ENTITY, "chessboard_floor")
        )

    val CHESS_CONTROLLER_BLOCK: Block = ChessControllerBlock(AbstractBlock.Settings.copy(Blocks.GLASS))
    val CHESS_CONTROLLER_ITEM: Item = BlockItem(CHESS_CONTROLLER_BLOCK, FabricItemSettings().group(CHESS_GROUP))

    val CHESS_CONTROLLER_ENTITY_TYPE: BlockEntityType<*> =
        BlockEntityType.Builder.create({ a, b -> ChessControllerBlockEntity(a, b) }, CHESS_CONTROLLER_BLOCK).build(
            Util.getChoiceType(TypeReferences.BLOCK_ENTITY, "chess_controller")
        )

    val CHESS_CONTROLLER_SCREEN_HANDLER_TYPE: ScreenHandlerType<ChessControllerGuiDescription> =
        ScreenHandlerRegistry.registerSimple(ident("chess_controller")) { syncId, inventory ->
            ChessControllerGuiDescription(syncId, inventory, ScreenHandlerContext.EMPTY)
        }

    override fun onInitialize() {

        ExtensionType.extensionTypes += FabricChessModuleExtension.FABRIC

        EntrypointUtils.invoke("chess", ChessInitializer::class.java, ChessInitializer::onInitializeChess)

        PIECE_ENTITY_TYPE = BlockEntityType.Builder.create(
            { a, b -> PieceBlockEntity(a, b) },
            *ChessModule.modules.flatMap { m -> m[FabricRegistryTypes.PIECE_BLOCK].values.flatMap { it.toList() } }.toTypedArray()
        ).build(Util.getChoiceType(TypeReferences.BLOCK_ENTITY, "piece"))

        Registry.register(Registry.BLOCK_ENTITY_TYPE, ident("piece"), PIECE_ENTITY_TYPE)
        Registry.register(Registry.BLOCK, ident("chessboard_floor"), CHESSBOARD_FLOOR_BLOCK)
        Registry.register(Registry.ITEM, ident("chessboard_floor"), CHESSBOARD_FLOOR_BLOCK_ITEM)
        Registry.register(Registry.BLOCK_ENTITY_TYPE, ident("chessboard_floor"), CHESSBOARD_FLOOR_ENTITY_TYPE)
        Registry.register(Registry.BLOCK, ident("chess_controller"), CHESS_CONTROLLER_BLOCK)
        Registry.register(Registry.ITEM, ident("chess_controller"), CHESS_CONTROLLER_ITEM)
        Registry.register(Registry.BLOCK_ENTITY_TYPE, ident("chess_controller"), CHESS_CONTROLLER_ENTITY_TYPE)

        ClientPlayNetworking.registerGlobalReceiver(PLAYER_EXTRA_INFO_SYNC) {client, _, buf, _ ->
            val nbt = buf.readNbt()
            if (nbt != null && client.player != null)
                (client.player as PlayerExtraInfo).readExtraInfo(nbt)
        }

        ServerLifecycleEvents.SERVER_STOPPING.register {
            ChessGameManager.clear()
        }

    }
}