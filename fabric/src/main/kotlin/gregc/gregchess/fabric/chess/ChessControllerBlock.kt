package gregc.gregchess.fabric.chess

import gregc.gregchess.chess.*
import gregc.gregchess.chess.piece.*
import gregc.gregchess.chess.variant.ChessVariant
import gregc.gregchess.fabric.*
import gregc.gregchess.fabric.chess.component.FabricRenderer
import gregc.gregchess.fabric.chess.player.gregchess
import gregc.gregchess.fabric.coroutines.FabricChessEnvironment
import gregc.gregchess.rangeTo
import gregc.gregchess.registry.Registry
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
import net.minecraft.inventory.Inventories
import net.minecraft.inventory.SidedInventory
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.nbt.NbtCompound
import net.minecraft.network.Packet
import net.minecraft.network.listener.ClientPlayPacketListener
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket
import net.minecraft.screen.*
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import net.minecraft.text.TranslatableText
import net.minecraft.util.ActionResult
import net.minecraft.util.Hand
import net.minecraft.util.collection.DefaultedList
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import net.minecraft.world.World
import java.util.*
import kotlin.math.abs
import kotlin.math.min


class ChessControllerBlockEntity(pos: BlockPos?, state: BlockState?) :
    BlockEntity(GregChessMod.CHESS_CONTROLLER_ENTITY_TYPE, pos, state), NamedScreenHandlerFactory, PropertyDelegateHolder, ChessControllerInventory {
    var currentGameUUID: UUID? by BlockEntityDirtyDelegate(null)
    var currentGame: ChessGame? = null
        set(v) {
            field = v
            currentGameUUID = v?.uuid
        }
    var chessboardStartPos: BlockPos? by BlockEntityDirtyDelegate(null)
    private val selfRef = BlockReference(ChessControllerBlockEntity::class, { this.pos }, { world })

    override fun writeNbt(nbt: NbtCompound) {
        super.writeNbt(nbt)

        chessboardStartPos?.let {
            nbt.putLong("ChessboardStart", it.asLong())
        }
        currentGame?.let {
            nbt.putUuid("GameUUID", it.uuid)
        }
        Inventories.writeNbt(nbt, items)
    }

    override fun readNbt(nbt: NbtCompound) {
        super.readNbt(nbt)

        if (nbt.contains("ChessboardStart", 4)) {
            chessboardStartPos = BlockPos.fromLong(nbt.getLong("ChessboardStart"))
        }
        if (nbt.containsUuid("GameUUID")) {
            currentGame = ChessGameManager[nbt.getUuid("GameUUID")]
        }
        Inventories.readNbt(nbt, items)
    }

    override fun getDisplayName(): Text = TranslatableText(cachedState.block.translationKey)

    override fun createMenu(syncId: Int, inventory: PlayerInventory?, player: PlayerEntity?): ScreenHandler =
        ChessControllerGuiDescription(syncId, inventory, ScreenHandlerContext.create(world, pos))

    fun detectBoard(): Boolean {
        val dirs = mutableListOf(Direction.EAST, Direction.WEST, Direction.NORTH, Direction.SOUTH)
        fun BlockReference<ChessboardFloorBlockEntity>.isFloor(): Boolean {
            if (block == null || block !is ChessboardFloorBlock)
                return false
            if (entity == null)
                return false
            return entity?.chessControllerBlock?.pos == null || entity?.chessControllerBlock?.pos == this@ChessControllerBlockEntity.pos
        }

        val ref = selfRef.ofEntity(ChessboardFloorBlockEntity::class)

        for (d in dirs) {
            if ((1..8 * 3).any { i -> !ref.offset(BlockPos.ORIGIN.offset(d, i)).isFloor() })
                continue
            val o = listOf(d.rotateYClockwise(), d.rotateYCounterclockwise())
            for (d2 in o) {
                if ((1..8 * 3).all { i -> (0 until 8 * 3).all { j -> ref.offset(BlockPos.ORIGIN.offset(d, i).offset(d2, j)).isFloor() } }) {
                    val v1 = pos.offset(d)
                    val v2 = pos.offset(d, 8 * 3).offset(d2, 8 * 3 - 1)
                    val st = BlockPos(min(v1.x, v2.x), pos.y, min(v1.z, v2.z))
                    chessboardStartPos = st
                    for (ent in floorBlockEntities) {
                        val eposx = abs(ent.pos.getComponentAlongAxis(d2.axis) - v1.getComponentAlongAxis(d2.axis))
                        val realposx = if (d2 == d.rotateYClockwise()) eposx / 3 else 7 - (eposx / 3)
                        val eposy = abs(ent.pos.getComponentAlongAxis(d.axis) - v1.getComponentAlongAxis(d.axis))
                        ent.register(pos, Pos(realposx, eposy / 3))
                    }
                    for (ent in floorBlockEntities) {
                        if (ent.directPiece.entity != null && ent.directPiece.entity != ent.pieceBlock.entity) {
                            resetBoard()
                            return false
                        }
                    }
                    return true
                }
            }
        }
        resetBoard()
        return false
    }

    val floorBlockEntities
        get() = chessboardStartPos?.let { st ->
            (Pair(0, 0)..Pair(8 * 3 - 1, 8 * 3 - 1)).mapNotNull { (i, j) ->
                world?.getBlockEntity(st.offset(Direction.Axis.X, i).offset(Direction.Axis.Z, j))
            }.filterIsInstance<ChessboardFloorBlockEntity>()
        }.orEmpty()

    private val propertyDelegate = object : PropertyDelegate {
        override fun get(index: Int): Int = when (index) {
            0 -> if (chessboardStartPos != null) 1 else 0
            else -> -1
        }

        override fun set(index: Int, value: Int) {}

        override fun size(): Int = 1
    }

    override fun getPropertyDelegate(): PropertyDelegate = propertyDelegate

    fun resetBoard() {
        for (block in floorBlockEntities) {
            block.reset()
        }
        chessboardStartPos = null
        currentGame?.stop(drawBy(GregChess.CHESSBOARD_BROKEN))
        currentGame = null
    }

    override fun markRemoved() {
        resetBoard()
        super.markRemoved()
    }

    fun startGame(whitePlayer: ServerPlayerEntity, blackPlayer: ServerPlayerEntity) {
        if (currentGame != null) return
        currentGame = ChessGame(
            FabricChessEnvironment, ChessVariant.Normal,
            ChessGameManager.settings(ChessVariant.Normal, getBoardState(), FabricRenderer(this)),
            byColor(whitePlayer.gregchess, blackPlayer.gregchess)
        ).start()
    }

    fun getBoardState(): Map<Pos, Piece> = buildMap {
        for (t in floorBlockEntities) {
            val p = t.directPiece.entity
            if (p != null)
                put(t.boardPos!!, p.piece)
        }
    }

    private val items: DefaultedList<ItemStack?> =
        DefaultedList.ofSize(ChessControllerGuiDescription.INVENTORY_SIZE, ItemStack.EMPTY)

    override fun markDirty() = super<BlockEntity>.markDirty()

    override fun getItems(): DefaultedList<ItemStack?> = items

    fun addPiece(p: Piece): Boolean {
        val slot = ChessControllerGuiDescription.slotOf(p)
        return if (items[slot].isEmpty) {
            items[slot] = p.item.defaultStack
            true
        } else if (items[slot].item == p.item && items[slot].count < p.item.maxCount) {
            items[slot].count++
            true
        } else
            false
    }

    fun removePiece(p: Piece): Boolean {
        val slot = ChessControllerGuiDescription.slotOf(p)
        return if (items[slot].item == p.item && items[slot].count == 1) {
            items[slot] = ItemStack.EMPTY
            true
        } else if (items[slot].item == p.item) {
            items[slot].count--
            true
        } else
            false
    }

    fun hasPiece(p: Piece): Boolean {
        val slot = ChessControllerGuiDescription.slotOf(p)
        return items[slot].item == p.item && items[slot].count >= 1
    }

    override fun toInitialChunkDataNbt(): NbtCompound = createNbt()

    override fun toUpdatePacket(): Packet<ClientPlayPacketListener> = BlockEntityUpdateS2CPacket.create(this)

}

fun interface ChessControllerInventory : SidedInventory {

    fun getItems(): DefaultedList<ItemStack?>

    override fun size(): Int {
        return getItems().size
    }

    override fun isEmpty(): Boolean {
        for (i in 0 until size()) {
            val stack = getStack(i)
            if (!stack.isEmpty) {
                return false
            }
        }
        return true
    }

    override fun getStack(slot: Int): ItemStack {
        return getItems()[slot]
    }

    override fun removeStack(slot: Int, count: Int): ItemStack? {
        val result = Inventories.splitStack(getItems(), slot, count)
        if (!result.isEmpty) {
            markDirty()
        }
        return result
    }

    override fun removeStack(slot: Int): ItemStack? {
        return Inventories.removeStack(getItems(), slot)
    }

    override fun setStack(slot: Int, stack: ItemStack) {
        getItems()[slot] = stack
        if (stack.count > maxCountPerStack) {
            stack.count = maxCountPerStack
        }
    }

    override fun clear() {
        getItems().clear()
    }

    override fun markDirty() {
    }

    override fun canPlayerUse(player: PlayerEntity?): Boolean = true

    override fun canExtract(slot: Int, stack: ItemStack?, dir: Direction?): Boolean = false

    override fun canInsert(slot: Int, stack: ItemStack?, dir: Direction?): Boolean = false

    override fun getAvailableSlots(side: Direction?): IntArray = intArrayOf()

}

class ChessControllerGuiDescription(
    syncId: Int, playerInventory: PlayerInventory?, context: ScreenHandlerContext = ScreenHandlerContext.EMPTY
) : SyncedGuiDescription(
    GregChessMod.CHESS_CONTROLLER_SCREEN_HANDLER_TYPE, syncId, playerInventory,
    getBlockInventory(context, INVENTORY_SIZE), getBlockPropertyDelegate(context, 1)
) {

    companion object {
        private val pieces: List<Piece> = PieceRegistryView.values.toList()

        @JvmField
        val INVENTORY_SIZE = pieces.size

        fun slotOf(p: Piece) = pieces.indexOf(p)
    }

    init {
        val root = WGridPanel()
        setRootPanel(root)
        root.setSize(300, 300)
        root.insets = Insets.ROOT_PANEL

        val startGameButton = WButton(TranslatableText("gui.gregchess.start_game"))
        startGameButton.onClick = Runnable {
            ScreenNetworking.of(this, NetworkSide.CLIENT).send(ident("start_game")) {
                it.writeUuid(playerInventory?.player?.uuid)
            }
        }
        root.add(startGameButton, 0, 5, 5, 1)

        val abortGameButton = WButton(TranslatableText("gui.gregchess.abort_game"))
        abortGameButton.onClick = Runnable {
            ScreenNetworking.of(this, NetworkSide.CLIENT).send(ident("abort_game")) {}
        }
        root.add(abortGameButton, 0, 7, 5, 1)

        val detectBoardButton = WButton(TranslatableText("gui.gregchess.detect_board"))
        detectBoardButton.onClick = Runnable {
            ScreenNetworking.of(this, NetworkSide.CLIENT).send(ident("detect_board")) {}
        }
        root.add(detectBoardButton, 0, 1, 5, 1)

        val boardStatusLabel = WDynamicLabel {
            I18n.translate(if (propertyDelegate.get(0) == 0) "gui.gregchess.no_chessboard" else "gui.gregchess.has_chessboard")
        }
        root.add(boardStatusLabel, 0, 3, 5, 1)

        val whiteIcon = WItem(Items.WHITE_DYE.defaultStack)
        root.add(whiteIcon, 0, 9)
        val blackIcon = WItem(Items.BLACK_DYE.defaultStack)
        root.add(blackIcon, 0, 10)

        for ((i, piece) in Registry.PIECE_TYPE.values.withIndex()) {
            val icon = WItem(white(piece).item.defaultStack)
            root.add(icon, i+1, 8)
            val itemSlotWhite = WItemSlot.of(blockInventory, slotOf(white(piece)))
            itemSlotWhite.setFilter { it.item == white(piece).item }
            root.add(itemSlotWhite, i+1, 9)
            val itemSlotBlack = WItemSlot.of(blockInventory, slotOf(black(piece)))
            itemSlotBlack.setFilter { it.item == black(piece).item }
            root.add(itemSlotBlack, i+1, 10)
        }

        root.add(this.createPlayerInventoryPanel(), 0, 12)

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
                if (entity is ChessControllerBlockEntity && entity.chessboardStartPos != null && entity.currentGame == null) {
                    val player = world.getPlayerByUuid(it.readUuid()) as ServerPlayerEntity
                    entity.startGame(player, player)
                }
            }
        }

        ScreenNetworking.of(this, NetworkSide.SERVER).receive(ident("abort_game")) {
            context.run { world, pos ->
                val entity = world.getBlockEntity(pos)
                if (entity is ChessControllerBlockEntity && entity.chessboardStartPos != null) {
                    entity.currentGame?.stop(drawBy(GregChess.ABORTED))
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