package gregc.gregchess.fabric.chess

import gregc.gregchess.chess.Floor
import gregc.gregchess.chess.Pos
import gregc.gregchess.fabric.*
import gregc.gregchess.fabric.chess.player.FabricPlayer
import io.github.cottonmc.cotton.gui.networking.NetworkSide
import io.github.cottonmc.cotton.gui.networking.ScreenNetworking
import net.minecraft.block.*
import net.minecraft.block.entity.BlockEntity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.entity.player.PlayerInventory
import net.minecraft.nbt.NbtCompound
import net.minecraft.screen.*
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.state.StateManager
import net.minecraft.state.property.EnumProperty
import net.minecraft.text.Text
import net.minecraft.text.TranslatableText
import net.minecraft.util.*
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import net.minecraft.world.World


// TODO: make NamedScreenHandlerFactory a separate thing
class ChessboardFloorBlockEntity(pos: BlockPos?, state: BlockState?) :
    BlockEntity(GregChess.CHESSBOARD_FLOOR_ENTITY_TYPE, pos, state), NamedScreenHandlerFactory {
    var chessControllerBlockPos: BlockPos? by BlockEntityDirtyDelegate(null)
    var boardPos: Pos? by BlockEntityDirtyDelegate(null)
    override fun writeNbt(nbt: NbtCompound): NbtCompound {
        super.writeNbt(nbt)
        chessControllerBlockPos?.let {
            nbt.putLong("Controller", it.asLong())
        }
        boardPos?.let {
            nbt.putLong("Pos", ((it.file.toLong() shl 32) or (it.rank.toLong() and 0xFFFFFFFFL)))
        }
        return nbt
    }

    override fun readNbt(nbt: NbtCompound) {
        super.readNbt(nbt)

        if (nbt.contains("Controller", 4)) {
            chessControllerBlockPos = BlockPos.fromLong(nbt.getLong("Controller"))
        }

        if (nbt.contains("Pos", 4)) {
            val v = nbt.getLong("Pos")
            boardPos = Pos((v shr 32).toInt(), v.toInt())
        }
    }

    fun updateFloor(floor: Floor? = boardPos?.let { if ((it.rank + it.file) % 2 == 0) Floor.DARK else Floor.LIGHT }) {
        if (world?.getBlockState(pos)?.block is ChessboardFloorBlock)
            world?.setBlockState(pos, world!!.getBlockState(pos).with(ChessboardFloorBlock.FLOOR, floor?.chess ?: ChessboardFloor.INACTIVE))
    }

    override fun markRemoved() {
        if (world?.isClient == false) {
            chessControllerBlock?.resetBoard()
        }
        super.markRemoved()
    }

    val tileBlocks: Collection<ChessboardFloorBlockEntity>
        get() {
            if (pos == null)
                return emptyList()
            fun findOffsets(d: Direction): Int {
                var off = 1
                while (true) {
                    val ent = world?.getBlockEntity(pos.offset(d, off)) as? ChessboardFloorBlockEntity
                    if (ent == null || ent.boardPos != boardPos)
                        return off - 1
                    else
                        off++
                }
            }
            val minx = findOffsets(Direction.WEST)
            val maxx = findOffsets(Direction.EAST)
            val minz = findOffsets(Direction.NORTH)
            val maxz = findOffsets(Direction.SOUTH)
            return buildList {
                for (x in minx..maxx)
                    for (z in minz..maxz)
                        (world?.getBlockEntity(pos.offset(Direction.Axis.X, x).offset(Direction.Axis.Z, z))
                                as? ChessboardFloorBlockEntity)?.let { add(it) }
            }
        }

    internal val directPiece: PieceBlockEntity?
        get() = world?.getBlockEntity(pos.offset(Direction.UP)) as? PieceBlockEntity

    val pieceBlock: PieceBlockEntity?
        get() = tileBlocks.firstNotNullOfOrNull { it.directPiece }

    val chessControllerBlock: ChessControllerBlockEntity?
        get() = chessControllerBlockPos?.let { world?.getBlockEntity(it) as? ChessControllerBlockEntity }

    override fun getDisplayName(): Text = TranslatableText("gui.gregchess.promotion_menu")

    override fun createMenu(syncId: Int, inv: PlayerInventory?, player: PlayerEntity?): ScreenHandler {
        return PromotionMenuGuiDescription(syncId, inv, ScreenHandlerContext.create(world, pos)).apply {
            ScreenNetworking.of(this, NetworkSide.SERVER).receive(ident("setup_request")) {
                ScreenNetworking.of(this, NetworkSide.SERVER).send(ident("setup")) {
                    it.writeCollection(chessControllerBlock!!.promotions) { buf, p ->
                        buf.writeString(p.key.toString())
                    }
                }
            }
        }
    }
}

enum class ChessboardFloor(val floor: Floor?) : StringIdentifiable {
    INACTIVE(null), LIGHT(Floor.LIGHT), DARK(Floor.DARK), MOVE(Floor.MOVE), CAPTURE(Floor.CAPTURE),
    SPECIAL(Floor.SPECIAL), NOTHING(Floor.NOTHING), OTHER(Floor.OTHER),
    LAST_START(Floor.LAST_START), LAST_END(Floor.LAST_END);

    override fun asString(): String = name.lowercase()
}

class ChessboardFloorBlock(settings: Settings?) : BlockWithEntity(settings) {
    companion object {
        @JvmField
        val FLOOR: EnumProperty<ChessboardFloor> = EnumProperty.of("floor", ChessboardFloor::class.java)
    }

    override fun getRenderType(state: BlockState?): BlockRenderType = BlockRenderType.MODEL

    override fun createBlockEntity(pos: BlockPos?, state: BlockState?): BlockEntity =
        ChessboardFloorBlockEntity(pos, state)

    init {
        defaultState = stateManager.defaultState.with(FLOOR, ChessboardFloor.INACTIVE)
    }

    override fun appendProperties(builder: StateManager.Builder<Block, BlockState>) {
        builder.add(FLOOR)
    }

    override fun onUse(
        state: BlockState,
        world: World?,
        pos: BlockPos?,
        player: PlayerEntity,
        hand: Hand,
        hit: BlockHitResult?
    ): ActionResult {
        if (world?.isClient == true) return ActionResult.PASS
        if (hand != Hand.MAIN_HAND) return ActionResult.PASS
        val floorEntity = (world?.getBlockEntity(pos) as? ChessboardFloorBlockEntity) ?: return ActionResult.PASS
        val game = floorEntity.chessControllerBlock?.currentGame ?: return ActionResult.PASS

        val cp = game.currentPlayer as? FabricPlayer ?: return ActionResult.PASS

        if (cp.held != null) {
            cp.makeMove(floorEntity.boardPos!!, floorEntity, player as ServerPlayerEntity, state)
            return ActionResult.SUCCESS
        }
        return ActionResult.PASS
    }
}