package gregc.gregchess.bukkit.chess.component

import gregc.gregchess.bukkit.Loc
import gregc.gregchess.bukkit.chess.*
import gregc.gregchess.bukkit.chess.player.PiecePlayerActionEvent
import gregc.gregchess.bukkit.toLocation
import gregc.gregchess.chess.*
import gregc.gregchess.chess.component.Component
import gregc.gregchess.chess.component.ComponentData
import gregc.gregchess.chess.piece.*
import gregc.gregchess.chess.variant.AtomicChess
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.bukkit.Material
import org.bukkit.World
import kotlin.math.floor
import kotlin.reflect.KClass

@Serializable
data class BukkitRendererSettings(
    @Transient val arena: Arena = Arena.nextArena(),
    val pieceRows: Map<PieceType, Int> = mapOf(PieceType.PAWN to 1)
) : ComponentData<BukkitRenderer> {
    override val componentClass: KClass<out BukkitRenderer> get() = BukkitRenderer::class

    override fun getComponent(game: ChessGame) = BukkitRenderer(game, this)
}

fun interface ChessFloorRenderer {
    fun ChessGame.getFloorMaterial(p: Pos): Material
}

class BukkitRenderer(game: ChessGame, override val data: BukkitRendererSettings) : Component(game) {
    companion object {

        private class FillVolume(val world: World, val mat: Material, val start: Loc, val stop: Loc) {
            constructor(world: World, mat: Material, loc: Loc) : this(world, mat, loc, loc)
        }

        private fun fill(vol: FillVolume) {
            for (i in vol.start.x..vol.stop.x)
                for (j in (vol.start.y..vol.stop.y).reversed())
                    for (k in vol.start.z..vol.stop.z)
                        vol.world.getBlockAt(i, j, k).type = vol.mat
        }
    }

    private val capturedPieces = mutableMapOf<CapturedPos, CapturedPiece>()
    private val rowSize = byColor { mutableListOf<Int>() }

    private data class CapturedPos(val row: Int, val pos: Int, val by: Color)

    val arena: Arena get() = data.arena
    private val tileSize get() = arena.tileSize
    private val highHalfTile get() = floor(tileSize.toDouble() / 2).toInt()
    private val lowHalfTile get() = floor((tileSize.toDouble() - 1) / 2).toInt()
    private val boardSize get() = 8 * tileSize
    private val boardStart get() = arena.boardStart
    private val capturedStart get() = arena.capturedStart

    fun getPos(loc: Loc) =
        Pos(file = 7 - (loc.x - boardStart.x).floorDiv(tileSize), rank = (loc.z - boardStart.z).floorDiv(tileSize))

    private val Pos.loc
        get() = Loc(boardSize - 2 - file * tileSize, 1, 1 + rank * tileSize) + boardStart

    private val CapturedPos.loc
        get() = capturedStart[by] + when (by) {
            Color.WHITE -> Loc(-2 * pos, 0, -2 * row)
            Color.BLACK -> Loc(2 * pos, 0, 2 * row)
        }

    private val world get() = arena.world

    private fun Piece.render(loc: Loc) {
        for ((i, m) in structure.withIndex())
            fill(FillVolume(world, m, loc.copy(y = loc.y + i)))
    }

    private fun BoardPiece.render() = piece.render(pos.loc)

    private fun clearPiece(loc: Loc) {
        for (i in 0..10) {
            fill(FillVolume(world, Material.AIR, loc.copy(y = loc.y + i)))
        }
    }

    private fun BoardPiece.clearRender() = clearPiece(pos.loc)

    private val Pos.location get() = loc.toLocation(world)

    private fun playPieceSound(pos: Pos, sound: String, type: PieceType) =
        world.playSound(pos.location, type.getSound(sound), 3.0f, 1.0f)

    private fun BoardPiece.playSound(sound: String) = playPieceSound(pos, sound, type)

    override fun handleEvent(e: ChessEvent) {
        arena.handleEvent(e)
        super.handleEvent(e)
    }

    @ChessEventHandler
    fun handleExplosion(e: AtomicChess.ExplosionEvent) {
        world.createExplosion(e.pos.location, 4.0f, false, false)
    }

    private fun Pos.fillFloor(material: Material) {
        val (x, y, z) = loc
        val mi = -lowHalfTile
        val ma = highHalfTile
        fill(FillVolume(world, material, Loc(x + mi, y - 1, z + mi), Loc(x + ma, y - 1, z + ma)))
    }

    override fun validate() {
        arena.game = game
    }

    private fun addCapturedPiece(piece: CapturedPiece) {
        val row = data.pieceRows.getOrDefault(piece.type, 0)
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
        val row = data.pieceRows.getOrDefault(piece.type, 0)
        if (row < 0) throw IndexOutOfBoundsException(row)

        val rows = rowSize[piece.capturedBy]
        if (rows.size <= row) throw NoSuchElementException(piece.toString())

        val pos = CapturedPos(row, rows[row] - 1, piece.capturedBy)
        if (capturedPieces[pos] != piece) throw NoSuchElementException(piece.toString())
        capturedPieces.remove(pos)
        clearPiece(pos.loc)
        rows[row]--
    }

    @ChessEventHandler
    fun onStart(e: GameStartStageEvent) {
        if (e == GameStartStageEvent.BEGIN) {
            game.board.data.capturedPieces.forEach {
                addCapturedPiece(it)
            }
            redrawFloor()
        }
    }

    @ChessEventHandler
    fun onTurnStart(e: TurnEvent) {
        if (e == TurnEvent.START || e == TurnEvent.UNDO) {
            redrawFloor()
        }
    }

    @ChessEventHandler
    fun onStop(e: GameStopStageEvent) {
        if (e == GameStopStageEvent.CLEAR || e == GameStopStageEvent.PANIC) {
            game.board.pieces.forEach {
                it.clearRender()
            }
            capturedPieces.keys.forEach {
                clearPiece(it.loc)
            }
        }
    }

    private fun redrawFloor() {
        for (file in 0..7) {
            for (rank in 0..7) {
                with (game.variant.floorRenderer) {
                    Pos(file, rank).fillFloor(game.getFloorMaterial(Pos(file, rank)))
                }
            }
        }
    }

    @ChessEventHandler
    fun onPiecePlayerAction(e: PiecePlayerActionEvent) = when (e.type) {
        PiecePlayerActionEvent.Type.PICK_UP -> {
            e.piece.clearRender()
            e.piece.playSound("PickUp")
            redrawFloor()
        }
        PiecePlayerActionEvent.Type.PLACE_DOWN -> {
            e.piece.render()
            e.piece.playSound("Move")
            redrawFloor()
        }
    }

    @ChessEventHandler
    fun handlePieceEvents(e: PieceEvent) {
        when (e) {
            is PieceEvent.Created -> e.piece.render()
            is PieceEvent.Cleared -> e.piece.clearRender()
            is PieceEvent.Moved -> {
                for ((o, _) in e.moves)
                    when (o) {
                        is BoardPiece -> o.clearRender()
                        is CapturedPiece -> removeCapturedPiece(o)
                    }
                for ((o, t) in e.moves)
                    when (t) {
                        is BoardPiece -> {
                            t.render()
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
    }
}

val ChessGame.arena get() = renderer.arena

val ChessGame.renderer get() = requireComponent<BukkitRenderer>()