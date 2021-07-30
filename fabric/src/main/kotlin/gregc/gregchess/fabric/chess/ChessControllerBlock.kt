package gregc.gregchess.fabric.chess

import gregc.gregchess.chess.Pos
import gregc.gregchess.fabric.*
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
import net.minecraft.text.Text
import net.minecraft.text.TranslatableText
import net.minecraft.util.ActionResult
import net.minecraft.util.Hand
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import net.minecraft.world.World
import kotlin.math.max
import kotlin.math.min


class ChessControllerBlockEntity(pos: BlockPos?, state: BlockState?) :
    BlockEntity(GregChess.CHESS_CONTROLLER_ENTITY_TYPE, pos, state), NamedScreenHandlerFactory, PropertyDelegateHolder {
    //var currentGame: ChessGame? = null
    var chessboardStart: BlockPos? by BlockEntityDirtyDelegate(null)
    var chessboardEnd: BlockPos? by BlockEntityDirtyDelegate(null)

    override fun writeNbt(nbt: NbtCompound): NbtCompound {
        super.writeNbt(nbt)

        chessboardStart?.let {
            nbt.putLong("ChessboardStart", it.asLong())
        }
        chessboardEnd?.let {
            nbt.putLong("ChessboardEnd", it.asLong())
        }

        return nbt
    }

    override fun readNbt(nbt: NbtCompound) {
        super.readNbt(nbt)

        if(nbt.contains("ChessboardStart", 4) && nbt.contains("ChessboardEnd", 4)) {
            chessboardStart = BlockPos.fromLong(nbt.getLong("ChessboardStart"))
            chessboardEnd = BlockPos.fromLong(nbt.getLong("ChessboardEnd"))
        }
    }

    override fun getDisplayName(): Text = TranslatableText(cachedState.block.translationKey)

    override fun createMenu(syncId: Int, inventory: PlayerInventory?, player: PlayerEntity?): ScreenHandler =
        ChessControllerGuiDescription(syncId, inventory, ScreenHandlerContext.create(world, pos))

    fun detectBoard(): Boolean {
        val dirs = mutableListOf(Direction.EAST, Direction.WEST, Direction.NORTH, Direction.SOUTH)
        fun BlockPos.isFloor() = world?.getBlockState(this)?.block.let { it != null && it is ChessboardFloorBlock }
                && (world?.getBlockEntity(this) as? ChessboardFloorBlockEntity)
            .let { it != null && (it.chessControllerBlockPos == null || it.chessControllerBlockPos == pos)}
        dirs.removeIf { d ->
            (1..8*3).any { i ->
                !pos.offset(d, i).isFloor()
            }
        }
        dirs.forEach { d ->
            val o = listOf(d.rotateYClockwise(), d.rotateYCounterclockwise())
            o.forEach { d2 ->
                if ((1..8*3).all { i -> (0 until 8*3).all { j -> pos.offset(d, i).offset(d2, j).isFloor() } }) {
                    val v1 = pos.offset(d)
                    val v2 = pos.offset(d, 8*3).offset(d2, 8*3-1)
                    chessboardStart = BlockPos(min(v1.x, v2.x), pos.y, min(v1.z, v2.z))
                    chessboardEnd = BlockPos(max(v1.x, v2.x), pos.y, max(v1.z, v2.z))
                    (1..8*3).forEach { i -> (0 until 8*3).forEach { j ->
                        (world?.getBlockEntity(pos.offset(d, i).offset(d2, j)) as? ChessboardFloorBlockEntity)?.let {
                            it.chessControllerBlockPos = pos
                            if (d2 == d.rotateYClockwise())
                                it.boardPos = Pos(j/3, (i-1)/3)
                            else
                                it.boardPos = Pos(7-j/3, (i-1)/3)
                            it.updateFloor()
                        }
                    } }
                    return true
                }
            }
        }
        resetBoard()
        return false
    }

    private val propertyDelegate = object : PropertyDelegate {
        override fun get(index: Int): Int = when (index) {
            0 -> if (chessboardStart != null && chessboardEnd != null) 1 else 0
            else -> -1
        }

        override fun set(index: Int, value: Int) {}

        override fun size(): Int = 1
    }

    override fun getPropertyDelegate(): PropertyDelegate = propertyDelegate

    fun resetBoard() {
        chessboardStart?.let { s ->
            chessboardEnd?.let { e ->
                for (x in s.x..e.x) {
                    for (y in s.y..e.y) {
                        for (z in s.z..e.z) {
                            val block = world?.getBlockEntity(BlockPos(x,y,z)) as? ChessboardFloorBlockEntity
                            if (block != null) {
                                block.chessControllerBlockPos = null
                                block.boardPos = null
                                block.updateFloor()
                            }
                        }
                    }
                }
            }
        }
        chessboardStart = null
        chessboardEnd = null
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
        root.validate(this)
    }

}

class ChessControllerBlockScreen(gui: ChessControllerGuiDescription?, player: PlayerEntity?, title: Text?) :
    CottonInventoryScreen<ChessControllerGuiDescription?>(gui, player, title)


class ChessControllerBlock(settings: Settings?) : BlockWithEntity(settings) {
    override fun createBlockEntity(pos: BlockPos?, state: BlockState?): BlockEntity =
        ChessControllerBlockEntity(pos, state)

    override fun getRenderType(state: BlockState?): BlockRenderType = BlockRenderType.MODEL

    override fun onBreak(world: World?, pos: BlockPos?, state: BlockState?, player: PlayerEntity?) {
        if (world?.isClient == false) {
            (world.getBlockEntity(pos) as? ChessControllerBlockEntity)?.resetBoard()
        }
        super.onBreak(world, pos, state, player)
    }

    override fun onUse(state: BlockState, world: World?, pos: BlockPos?, player: PlayerEntity, hand: Hand?, hit: BlockHitResult?): ActionResult {
        player.openHandledScreen(state.createScreenHandlerFactory(world, pos))
        return ActionResult.SUCCESS
    }
}