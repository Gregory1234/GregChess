package gregc.gregchess.bukkit.renderer

import gregc.gregchess.*
import gregc.gregchess.bukkit.*
import gregc.gregchess.bukkit.component.BukkitComponentType
import gregc.gregchess.bukkit.player.*
import gregc.gregchess.bukkit.variant.floorRenderer
import gregc.gregchess.bukkitutils.CommandException
import gregc.gregchess.event.*
import gregc.gregchess.match.ChessMatch
import gregc.gregchess.move.connector.PieceMoveEvent
import gregc.gregchess.piece.*
import gregc.gregchess.variant.AtomicChess
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.bukkit.Material
import org.bukkit.block.BlockFace
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.event.player.PlayerInteractEvent
import kotlin.math.floor

@Serializable
class SimpleRenderer : Renderer {
    override val type get() = BukkitComponentType.SIMPLE_RENDERER
    private data class CapturedPos(val row: Int, val pos: Int, val by: Color)

    @Transient
    override lateinit var arena: SimpleArena
        private set

    override val style: SimpleRendererStyle get() = config.getFromRegistry(BukkitRegistry.RENDERER_STYLE, "Renderer.Style") as SimpleRendererStyle

    @Transient private val capturedPieces = mutableMapOf<CapturedPos, CapturedPiece>()
    @Transient private val rowSize = byColor { mutableListOf<Int>() }

    override fun init(match: ChessMatch, events: EventListenerRegistry) {
        arena = SimpleArena.reserveOrNull(match) ?: throw CommandException(NO_ARENAS)
        arena.registerEventListeners(events.subRegistry("arena"))
        events.registerR<ResetPlayerEvent> {
            player.entity?.inventory?.setItem(0, player.currentSide?.held?.piece?.let(style::pieceItem))
        }
        events.registerR<PiecePlayerActionEvent> { handlePiecePlayerActionEvent(match) }
        events.registerR<AtomicChess.ExplosionEvent> {
            world.createExplosion(pos.loc.toLocation(world), 4.0f, false, false)
        }
        events.register<ChessBaseEvent> { handleBaseEvent(match, it) }
        events.register<TurnEvent> { handleTurnEvent(match, it) }
        events.register(::handlePieceMoveEvent)
    }

    private val tileSize get() = arena.tileSize
    private val highHalfTile get() = floor(tileSize.toDouble() / 2).toInt()
    private val lowHalfTile get() = floor((tileSize.toDouble() - 1) / 2).toInt()
    private val capturedStart get() = arena.capturedStartLoc
    private val pieceRows get() = arena.pieceRows
    private val world get() = arena.boardStart.world!!

    private val Pos.loc get() = arena.getLoc(this)

    private val CapturedPos.loc
        get() = capturedStart[by] + when (by) {
            Color.WHITE -> Loc(-2 * pos, 0, -2 * row)
            Color.BLACK -> Loc(2 * pos, 0, 2 * row)
        }

    private fun Pos.fillFloor(material: Material) {
        val (x, y, z) = loc
        val mi = -lowHalfTile
        val ma = highHalfTile
        world.fill(material, Loc(x + mi, y - 1, z + mi), Loc(x + ma, y - 1, z + ma))
    }

    private fun redrawFloor(match: ChessMatch) {
        for (file in 0..7) {
            for (rank in 0..7) {
                with (match.variant.floorRenderer) {
                    Pos(file, rank).fillFloor(style.floorMaterial(match.getFloor(Pos(file, rank))))
                }
            }
        }
    }

    private fun Piece.render(loc: Loc) {
        for ((i, m) in style.pieceStructure(this).withIndex())
            world.fill(m, loc.copy(y = loc.y + i))
    }

    private fun BoardPiece.render() = piece.render(pos.loc)

    private fun clearPiece(loc: Loc) {
        for (i in 0..10) {
            world.fill(Material.AIR, loc.copy(y = loc.y + i))
        }
    }

    private fun BoardPiece.clearRender() = clearPiece(pos.loc)

    private fun playPieceSound(pos: Pos, sound: String, type: PieceType) =
        world.playSound(pos.loc.toLocation(world), style.pieceSound(type, sound), 3.0f, 1.0f)

    private fun BoardPiece.playSound(sound: String) = playPieceSound(pos, sound, type)

    private fun addCapturedPiece(piece: CapturedPiece) {
        val row = pieceRows.getOrDefault(piece.type, 0)
        if (row < 0) throw IndexOutOfBoundsException(row)

        val rows = rowSize[piece.capturedBy]
        if (rows.size <= row)
            rows.addAll(List(row - rows.size + 1) { 0 })

        val pos = CapturedPos(row, rows[row], piece.capturedBy)
        capturedPieces[pos] = piece
        piece.piece.render(pos.loc)
        rows[row]++
    }

    private fun removeCapturedPiece(piece: CapturedPiece) {
        val row = pieceRows.getOrDefault(piece.type, 0)
        if (row < 0) throw IndexOutOfBoundsException(row)

        val rows = rowSize[piece.capturedBy]
        if (rows.size <= row) throw NoSuchElementException(piece.toString())

        val pos = CapturedPos(row, rows[row] - 1, piece.capturedBy)
        if (capturedPieces[pos] != piece) throw NoSuchElementException(piece.toString())
        capturedPieces.remove(pos)
        clearPiece(pos.loc)
        rows[row]--
    }

    private fun handleBaseEvent(match: ChessMatch, e: ChessBaseEvent) {
        if (e == ChessBaseEvent.RUNNING || e == ChessBaseEvent.SYNC) {
            redrawFloor(match)
        }
        else if (e == ChessBaseEvent.CLEAR || e == ChessBaseEvent.PANIC) {
            match.board.pieces.forEach {
                it.clearRender()
            }
            capturedPieces.keys.forEach {
                clearPiece(it.loc)
            }
        }
    }

    private fun handleTurnEvent(match: ChessMatch, e: TurnEvent) {
        if (e == TurnEvent.START || e == TurnEvent.UNDO) {
            redrawFloor(match)
        }
    }

    private fun PiecePlayerActionEvent.handlePiecePlayerActionEvent(match: ChessMatch) = when (action) {
        PiecePlayerActionEvent.Type.PICK_UP -> {
            player.entity?.inventory?.setItem(0, style.pieceItem(piece.piece))
            piece.clearRender()
            piece.playSound("PickUp")
            redrawFloor(match)
        }
        PiecePlayerActionEvent.Type.PLACE_DOWN -> {
            player.entity?.inventory?.setItem(0, null)
            piece.render()
            piece.playSound("Move")
            redrawFloor(match)
        }
    }

    private fun handlePieceMoveEvent(e: PieceMoveEvent) = with(e) {
        for ((o, _) in moves)
            when (o) {
                is BoardPiece -> o.clearRender()
                is CapturedPiece -> removeCapturedPiece(o)
            }
        for ((o, t) in moves)
            when (t) {
                is BoardPiece -> {
                    t.render()
                    if (o is BoardPiece)
                        t.playSound("Move")
                }
                is CapturedPiece -> {
                    addCapturedPiece(t)
                    if (o is BoardPiece)
                        o.playSound("Capture")
                }
            }
    }
}

val ChessMatch.simpleRenderer get() = renderer as? SimpleRenderer

object SimpleRendererListener : Listener {
    fun start() {
        registerEvents()
    }

    @EventHandler
    fun onPlayerDeath(e: EntityDeathEvent) {
        val ent = e.gregchessPlayer ?: return
        val match = ent.currentMatch ?: return
        match.simpleRenderer ?: return
        match.callEvent(ResetPlayerEvent(ent))
    }

    @EventHandler
    fun onPlayerDamage(e: EntityDamageEvent) {
        val ent = e.gregchessPlayer ?: return
        val match = ent.currentMatch ?: return
        match.simpleRenderer ?: return
        match.callEvent(ResetPlayerEvent(ent))
        e.isCancelled = true
    }

    @EventHandler
    fun onBlockClick(e: PlayerInteractEvent) {
        val player = e.gregchessPlayer.currentSide ?: return
        val renderer = player.match.simpleRenderer ?: return
        e.isCancelled = true
        if (player.hasTurn && e.blockFace != BlockFace.DOWN) {
            val block = e.clickedBlock ?: return
            val pos = renderer.arena.getPos(block.location) ?: return
            if (e.action == Action.LEFT_CLICK_BLOCK && player.held == null) {
                player.pickUp(pos)
            } else if (e.action == Action.RIGHT_CLICK_BLOCK && player.held != null) {
                player.makeMove(pos)
            }
        }
    }

    @EventHandler
    fun onBlockBreak(e: BlockBreakEvent) {
        e.gregchessPlayer.currentMatch?.simpleRenderer ?: return
        e.isCancelled = true
    }

    @EventHandler
    fun onInventoryDrag(e: InventoryDragEvent) {
        e.gregchessPlayer?.currentMatch?.simpleRenderer ?: return
        e.isCancelled = true
    }

    @EventHandler
    fun onInventoryClick(e: InventoryClickEvent) {
        e.gregchessPlayer?.currentMatch?.simpleRenderer ?: return
        e.isCancelled = true
    }

    @EventHandler
    fun onItemDrop(e: PlayerDropItemEvent) {
        e.gregchessPlayer.currentMatch?.simpleRenderer ?: return
        e.isCancelled = true
    }
}