package gregc.gregchess.fabric.chess

import gregc.gregchess.chess.*
import gregc.gregchess.fabric.*
import gregc.gregchess.fabric.chess.component.FabricRenderer
import gregc.gregchess.rangeTo
import io.github.cottonmc.cotton.gui.PropertyDelegateHolder
import io.github.cottonmc.cotton.gui.SyncedGuiDescription
import io.github.cottonmc.cotton.gui.client.CottonInventoryScreen
import io.github.cottonmc.cotton.gui.networking.NetworkSide
import io.github.cottonmc.cotton.gui.networking.ScreenNetworking
import io.github.cottonmc.cotton.gui.widget.*
import io.github.cottonmc.cotton.gui.widget.data.Insets
import net.minecraft.block.*
import net.minecraft.block.entity.BlockEntity
import net.minecraft.client.resource.language.I18n
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.entity.player.PlayerInventory
import net.minecraft.nbt.NbtCompound
import net.minecraft.screen.*
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import net.minecraft.text.TranslatableText
import net.minecraft.util.ActionResult
import net.minecraft.util.Hand
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import net.minecraft.world.World
import kotlin.math.abs
import kotlin.math.min


class ChessControllerBlockEntity(pos: BlockPos?, state: BlockState?) :
    BlockEntity(GregChess.CHESS_CONTROLLER_ENTITY_TYPE, pos, state), NamedScreenHandlerFactory, PropertyDelegateHolder {
    var currentGame: ChessGame? = null
    var chessboardStart: BlockPos? by BlockEntityDirtyDelegate(null)

    override fun writeNbt(nbt: NbtCompound): NbtCompound {
        super.writeNbt(nbt)

        chessboardStart?.let {
            nbt.putLong("ChessboardStart", it.asLong())
        }
        currentGame?.let {
            nbt.putUuid("GameUUID", it.uuid)
        }

        return nbt
    }

    override fun readNbt(nbt: NbtCompound) {
        super.readNbt(nbt)

        if (nbt.contains("ChessboardStart", 4)) {
            chessboardStart = BlockPos.fromLong(nbt.getLong("ChessboardStart"))
        }
        if (nbt.containsUuid("GameUUID")) {
            currentGame = ChessGameManager[nbt.getUuid("GameUUID")]
        }
    }

    override fun getDisplayName(): Text = TranslatableText(cachedState.block.translationKey)

    override fun createMenu(syncId: Int, inventory: PlayerInventory?, player: PlayerEntity?): ScreenHandler =
        ChessControllerGuiDescription(syncId, inventory, ScreenHandlerContext.create(world, pos))

    fun detectBoard(): Boolean {
        val dirs = mutableListOf(Direction.EAST, Direction.WEST, Direction.NORTH, Direction.SOUTH)
        fun BlockPos.isFloor(): Boolean {
            val block = world?.getBlockState(this)?.block
            if (block == null || block !is ChessboardFloorBlock)
                return false

            val blockEntity = world?.getBlockEntity(this)
            if (blockEntity == null || blockEntity !is ChessboardFloorBlockEntity)
                return false
            return blockEntity.chessControllerBlockPos == null || blockEntity.chessControllerBlockPos == pos
        }

        for (d in dirs) {
            if ((1..8 * 3).any { i -> !pos.offset(d, i).isFloor() })
                continue
            val o = listOf(d.rotateYClockwise(), d.rotateYCounterclockwise())
            for (d2 in o) {
                if ((1..8 * 3).all { i -> (0 until 8 * 3).all { j -> pos.offset(d, i).offset(d2, j).isFloor() } }) {
                    val v1 = pos.offset(d)
                    val v2 = pos.offset(d, 8 * 3).offset(d2, 8 * 3 - 1)
                    val st = BlockPos(min(v1.x, v2.x), pos.y, min(v1.z, v2.z))
                    chessboardStart = st
                    for (ent in floorBlockEntities) {
                        ent.chessControllerBlockPos = pos
                        val eposx = abs(ent.pos.getComponentAlongAxis(d2.axis) - v1.getComponentAlongAxis(d2.axis))
                        val realposx = if (d2 == d.rotateYClockwise()) eposx / 3 else 7 - (eposx / 3)
                        val eposy = abs(ent.pos.getComponentAlongAxis(d.axis) - v1.getComponentAlongAxis(d.axis))
                        ent.boardPos = Pos(realposx, eposy / 3)
                        ent.updateFloor()
                    }
                    return true
                }
            }
        }
        resetBoard()
        return false
    }

    val floorBlockEntities
        get() = chessboardStart?.let { st ->
            (Pair(0, 0)..Pair(8 * 3 - 1, 8 * 3 - 1)).mapNotNull { (i, j) ->
                world?.getBlockEntity(st.offset(Direction.Axis.X, i).offset(Direction.Axis.Z, j))
            }.filterIsInstance<ChessboardFloorBlockEntity>()
        }.orEmpty()

    private val propertyDelegate = object : PropertyDelegate {
        override fun get(index: Int): Int = when (index) {
            0 -> if (chessboardStart != null) 1 else 0
            else -> -1
        }

        override fun set(index: Int, value: Int) {}

        override fun size(): Int = 1
    }

    override fun getPropertyDelegate(): PropertyDelegate = propertyDelegate

    fun resetBoard() {
        for (block in floorBlockEntities) {
            block.chessControllerBlockPos = null
            block.boardPos = null
            block.updateFloor()
        }
        chessboardStart = null
        currentGame?.stop(drawBy(FabricGregChessModule.CHESSBOARD_BROKEN))
        currentGame = null
    }

    override fun markRemoved() {
        if (world?.isClient == false) {
            resetBoard()
        }
    }

    fun startGame(whitePlayer: ServerPlayerEntity, blackPlayer: ServerPlayerEntity) {
        currentGame = ChessGame(ChessGameManager.settings(FabricRenderer.Settings(this))).addPlayers {
            fabric(whitePlayer, white)
            fabric(blackPlayer, black)
        }.start()
    }

}

class ChessControllerGuiDescription(syncId: Int, playerInventory: PlayerInventory?, context: ScreenHandlerContext) :
    SyncedGuiDescription(
        GregChess.CHESS_CONTROLLER_SCREEN_HANDLER_TYPE,
        syncId,
        playerInventory,
        null,
        getBlockPropertyDelegate(context, 1)
    ) {

    init {
        val root = WGridPanel()
        setRootPanel(root)
        root.setSize(300, 200)
        root.insets = Insets.ROOT_PANEL

        val startGameButton = WButton(TranslatableText("gui.gregchess.start_game"))
        startGameButton.onClick = Runnable {
            ScreenNetworking.of(this, NetworkSide.CLIENT).send(ident("start_game")) {
                it.writeUuid(playerInventory?.player?.uuid)
            }
        }
        root.add(startGameButton, 0, 5, 5, 1)

        val detectBoardButton = WButton(TranslatableText("gui.gregchess.detect_board"))
        detectBoardButton.onClick = Runnable {
            ScreenNetworking.of(this, NetworkSide.CLIENT).send(ident("detect_board")) {}
        }
        root.add(detectBoardButton, 0, 1, 5, 1)

        val boardStatusLabel = WDynamicLabel {
            I18n.translate(if (propertyDelegate.get(0) == 0) "gui.gregchess.no_chessboard" else "gui.gregchess.has_chessboard")
        }
        root.add(boardStatusLabel, 0, 3, 5, 1)

        ScreenNetworking.of(this, NetworkSide.SERVER).receive(ident("detect_board")) {
            context.run { world, pos ->
                val entity = world.getBlockEntity(pos)
                if (entity is ChessControllerBlockEntity) {
                    entity.detectBoard()
                }
            }
        }

        ScreenNetworking.of(this, NetworkSide.SERVER).receive(ident("start_game")) {
            context.run { world, pos ->
                val entity = world.getBlockEntity(pos)
                if (entity is ChessControllerBlockEntity && entity.chessboardStart != null && entity.currentGame == null) {
                    val player = world.getPlayerByUuid(it.readUuid()) as ServerPlayerEntity
                    entity.startGame(player, player)
                }
            }
        }
        root.validate(this)
    }

}

class ChessControllerBlockScreen(gui: ChessControllerGuiDescription?, player: PlayerEntity?, title: Text?) :
    CottonInventoryScreen<ChessControllerGuiDescription?>(gui, player, title)


class ChessControllerBlock(settings: Settings?) : BlockWithEntity(settings) {
    override fun createBlockEntity(pos: BlockPos?, state: BlockState?): BlockEntity =
        ChessControllerBlockEntity(pos, state)

    override fun getRenderType(state: BlockState?): BlockRenderType = BlockRenderType.MODEL

    override fun onUse(
        state: BlockState,
        world: World?,
        pos: BlockPos?,
        player: PlayerEntity,
        hand: Hand?,
        hit: BlockHitResult?
    ): ActionResult {
        player.openHandledScreen(state.createScreenHandlerFactory(world, pos))
        return ActionResult.SUCCESS
    }
}