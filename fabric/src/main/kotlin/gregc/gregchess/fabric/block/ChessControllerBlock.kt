package gregc.gregchess.fabric.block

import gregc.gregchess.Pos
import gregc.gregchess.fabric.GregChess
import gregc.gregchess.fabric.GregChessMod
import gregc.gregchess.fabric.client.ChessControllerGuiDescription
import gregc.gregchess.fabric.coroutines.FabricChessEnvironment
import gregc.gregchess.fabric.coroutines.uuid
import gregc.gregchess.fabric.match.ChessMatchManager
import gregc.gregchess.fabric.piece.PieceBlock
import gregc.gregchess.fabric.piece.item
import gregc.gregchess.fabric.renderer.FabricRenderer
import gregc.gregchess.match.ChessMatch
import gregc.gregchess.piece.Piece
import gregc.gregchess.player.ChessSideManager
import gregc.gregchess.results.drawBy
import gregc.gregchess.variant.ChessVariant
import io.github.cottonmc.cotton.gui.PropertyDelegateHolder
import net.minecraft.block.*
import net.minecraft.block.entity.BlockEntity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.entity.player.PlayerInventory
import net.minecraft.inventory.Inventories
import net.minecraft.inventory.SidedInventory
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NbtCompound
import net.minecraft.network.Packet
import net.minecraft.network.listener.ClientPlayPacketListener
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket
import net.minecraft.screen.*
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
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
    var currentMatchUUID: UUID? by BlockEntityDirtyDelegate(null)
    val currentMatch: ChessMatch? get() = currentMatchUUID?.takeIf { world?.isClient == false }?.let { ChessMatchManager[it] }
    var chessboardStartPos: BlockPos? by BlockEntityDirtyDelegate(null)
    private val selfRef = BlockReference(ChessControllerBlockEntity::class, { this.pos }, { world })

    override fun writeNbt(nbt: NbtCompound) {
        nbt.putLongOrNull("ChessboardStart", chessboardStartPos?.asLong())
        nbt.putUuidOrNull("MatchUUID", currentMatch?.uuid)
        Inventories.writeNbt(nbt, items)
    }

    override fun readNbt(nbt: NbtCompound) {
        chessboardStartPos = nbt.getLongOrNull("ChessboardStart")?.let(BlockPos::fromLong)
        currentMatchUUID = nbt.getUuidOrNull("MatchUUID")
        Inventories.readNbt(nbt, items)
    }

    override fun getDisplayName(): Text = Text.translatable(cachedState.block.translationKey)

    override fun createMenu(syncId: Int, inventory: PlayerInventory?, player: PlayerEntity?): ScreenHandler =
        ChessControllerGuiDescription(syncId, inventory, ScreenHandlerContext.create(world, pos))

    fun detectBoard(): Boolean {
        val dirs = mutableListOf(Direction.EAST, Direction.WEST, Direction.NORTH, Direction.SOUTH)
        fun BlockReference<ChessboardFloorBlockEntity>.isFloor(): Boolean {
            if (block !is ChessboardFloorBlock)
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
                        if (ent.directPiece.block is PieceBlock && ent.directPiece.pos != ent.pieceBlock.pos) {
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
            buildList {
                for (i in 0 until 8 * 3)
                    for (j in 0 until 8 * 3)
                        world?.getBlockEntity(st.offset(Direction.Axis.X, i).offset(Direction.Axis.Z, j))?.let {
                            if (it is ChessboardFloorBlockEntity)
                                add(it)
                        }
            }
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
        currentMatch?.stop(drawBy(GregChess.CHESSBOARD_BROKEN))
        currentMatchUUID = null
    }

    fun startMatch(whitePlayer: ServerPlayerEntity, blackPlayer: ServerPlayerEntity) {
        if (currentMatch != null) return
        currentMatchUUID = ChessMatch(
            FabricChessEnvironment(), ChessVariant.Normal,
            ChessMatchManager.settings(ChessVariant.Normal, 0, getBoardState(), FabricRenderer(this), ChessSideManager(whitePlayer, blackPlayer)), 0
        ).start().uuid
    }

    fun getBoardState(): Map<Pos, Piece> = buildMap {
        for (t in floorBlockEntities) {
            val p = t.directPiece.block
            if (p is PieceBlock)
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

    override fun toInitialChunkDataNbt(): NbtCompound = createNbt().ensureNotEmpty()

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

@Suppress("OVERRIDE_DEPRECATION")
class ChessControllerBlock(settings: Settings?) : BlockWithEntity(settings) {
    override fun createBlockEntity(pos: BlockPos?, state: BlockState?): BlockEntity =
        ChessControllerBlockEntity(pos, state)

    override fun getRenderType(state: BlockState?): BlockRenderType = BlockRenderType.MODEL

    override fun onUse(
        state: BlockState, world: World, pos: BlockPos?, player: PlayerEntity, hand: Hand?, hit: BlockHitResult?
    ): ActionResult = if (world.isClient) {
        ActionResult.SUCCESS
    } else {
        player.openHandledScreen(state.createScreenHandlerFactory(world, pos))
        ActionResult.CONSUME
    }

    @Suppress("DEPRECATION")
    override fun onStateReplaced(state: BlockState, world: World, pos: BlockPos, newState: BlockState, moved: Boolean) {
        val entity = world.getBlockEntity(pos) as? ChessControllerBlockEntity
        entity?.resetBoard()
        super.onStateReplaced(state, world, pos, newState, moved)
    }
}