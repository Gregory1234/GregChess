package gregc.gregchess.bukkit.renderer

import gregc.gregchess.*
import gregc.gregchess.bukkit.*
import gregc.gregchess.bukkit.component.BukkitComponentType
import gregc.gregchess.bukkit.piece.getSound
import gregc.gregchess.bukkit.piece.structure
import gregc.gregchess.bukkit.player.PiecePlayerActionEvent
import gregc.gregchess.event.*
import gregc.gregchess.match.ChessMatch
import gregc.gregchess.move.connector.PieceMoveEvent
import gregc.gregchess.piece.*
import gregc.gregchess.variant.AtomicChess
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.bukkit.Location
import org.bukkit.Material
import kotlin.math.floor

@Serializable
data class BukkitRenderer(val pieceRows: Map<PieceType, Int> = mapOf(PieceType.PAWN to 1)) : Renderer {
    override val type get() = BukkitComponentType.RENDERER

    @Transient
    private lateinit var arena: Arena

    override fun validate() {
        ArenaManager.validateFreeArenas()
    }

    override fun init(match: ChessMatch, events: EventListenerRegistry) {
        this.arena = ArenaManager.reserveArena(match)
        arena.registerEvents(events.subRegistry("arena"))
        events.registerR<AtomicChess.ExplosionEvent> {
            world.createExplosion(pos.location, 4.0f, false, false)
        }
        events.register<ChessBaseEvent> { handleBaseEvent(match, it) }
        events.register<TurnEvent> { handleTurnEvent(match, it) }
        events.registerR<PiecePlayerActionEvent> { handlePiecePlayerActionEvent(match) }
        events.register(::handlePieceMoveEvent)
    }

    @Transient private val capturedPieces = mutableMapOf<CapturedPos, CapturedPiece>()
    @Transient private val rowSize = byColor { mutableListOf<Int>() }

    private data class CapturedPos(val row: Int, val pos: Int, val by: Color)

    private val tileSize get() = arena.tileSize
    private val highHalfTile get() = floor(tileSize.toDouble() / 2).toInt()
    private val lowHalfTile get() = floor((tileSize.toDouble() - 1) / 2).toInt()
    private val boardSize get() = 8 * tileSize
    private val boardStart get() = arena.boardStart.toLoc()
    private val capturedStart get() = byColor { arena.capturedStart[it].toLoc() }

    override fun getPos(location: Location) = getPos(location.toLoc())

    private fun getPos(loc: Loc) =
        Pos(file = 7 - (loc.x - boardStart.x).floorDiv(tileSize), rank = (loc.z - boardStart.z).floorDiv(tileSize))

    private val Pos.loc
        get() = Loc(boardSize - 2 - file * tileSize, 1, 1 + rank * tileSize) + boardStart

    private val CapturedPos.loc
        get() = capturedStart[by] + when (by) {
            Color.WHITE -> Loc(-2 * pos, 0, -2 * row)
            Color.BLACK -> Loc(2 * pos, 0, 2 * row)
        }

    private val world get() = arena.boardStart.world!!

    private fun fill(mat: Material, start: Loc, stop: Loc = start) {
        for (i in start.x..stop.x)
            for (j in (start.y..stop.y).reversed())
                for (k in start.z..stop.z)
                    world.getBlockAt(i, j, k).type = mat
    }

    private fun Piece.render(loc: Loc) {
        for ((i, m) in structure.withIndex())
            fill(m, loc.copy(y = loc.y + i))
    }

    private fun BoardPiece.render() = piece.render(pos.loc)

    private fun clearPiece(loc: Loc) {
        for (i in 0..10) {
            fill(Material.AIR, loc.copy(y = loc.y + i))
        }
    }

    private fun BoardPiece.clearRender() = clearPiece(pos.loc)

    private val Pos.location get() = loc.toLocation(world)

    private fun playPieceSound(pos: Pos, sound: String, type: PieceType) =
        world.playSound(pos.location, type.getSound(sound), 3.0f, 1.0f)

    private fun BoardPiece.playSound(sound: String) = playPieceSound(pos, sound, type)

    private fun Pos.fillFloor(material: Material) {
        val (x, y, z) = loc
        val mi = -lowHalfTile
        val ma = highHalfTile
        fill(material, Loc(x + mi, y - 1, z + mi), Loc(x + ma, y - 1, z + ma))
    }

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

    private fun redrawFloor(match: ChessMatch) {
        for (file in 0..7) {
            for (rank in 0..7) {
                with (match.variant.floorRenderer) {
                    Pos(file, rank).fillFloor(match.getFloor(Pos(file, rank)).material)
                }
            }
        }
    }

    private fun PiecePlayerActionEvent.handlePiecePlayerActionEvent(match: ChessMatch) = when (action) {
        PiecePlayerActionEvent.Type.PICK_UP -> {
            piece.clearRender()
            piece.playSound("PickUp")
            redrawFloor(match)
        }
        PiecePlayerActionEvent.Type.PLACE_DOWN -> {
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