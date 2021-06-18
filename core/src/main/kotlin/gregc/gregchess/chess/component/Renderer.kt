package gregc.gregchess.chess.component

import gregc.gregchess.Loc
import gregc.gregchess.chess.*
import kotlin.math.floor

interface Renderer<in T> : Component {
    interface Settings<in T> : Component.Settings<Renderer<T>>

    fun getPos(loc: T): Pos
    fun renderPiece(pos: Pos, piece: Piece)
    fun clearPiece(pos: Pos)
    fun renderCapturedPiece(pos: CapturedPos, piece: Piece)
    fun clearCapturedPiece(pos: CapturedPos)
    fun playPieceSound(pos: Pos, sound: PieceSound, type: PieceType)
    fun explosionAt(pos: Pos)
    fun fillFloor(pos: Pos, floor: Floor)
    fun renderBoardBase()
    fun removeBoard()
}

abstract class MinecraftRenderer(protected val game: ChessGame, protected val settings: Settings) : Renderer<Loc> {
    abstract class Settings(val tileSize: Int, val offset: Loc = Loc(0, 0, 0)) : Renderer.Settings<Loc> {
        val highHalfTile get() = floor(tileSize.toDouble() / 2).toInt()
        val lowHalfTile get() = floor((tileSize.toDouble() - 1) / 2).toInt()
        val boardSize get() = 8 * tileSize
    }

    protected companion object {
        val defaultSpawnLocation = Loc(4, 101, 4)
    }

    val spawnLocation
        get() = defaultSpawnLocation + settings.offset

    override fun getPos(loc: Loc) =
        Pos(
            file = (8 + settings.boardSize - 1 - loc.x + settings.offset.x).floorDiv(settings.tileSize),
            rank = (loc.z - settings.offset.z - 8).floorDiv(settings.tileSize)
        )

    protected fun getPieceLoc(pos: Pos) =
        Loc(
            x = 8 + settings.boardSize - 1 - settings.highHalfTile - pos.file * settings.tileSize,
            y = 102,
            z = pos.rank * settings.tileSize + 8 + settings.lowHalfTile
        ) + settings.offset

    protected fun getCapturedLoc(pos: CapturedPos): Loc {
        val p = pos.pos
        return when (pos.by) {
            Side.WHITE -> Loc(8 + settings.boardSize - 1 - 2 * p.first, 101, 8 - 3 - 2 * p.second)
            Side.BLACK -> Loc(8 + 2 * p.first, 101, 8 + settings.boardSize + 2 + 2 * p.second)
        } + settings.offset
    }

    protected abstract fun renderPiece(loc: Loc, piece: Piece)

    protected abstract fun clearPiece(loc: Loc)

    override fun renderPiece(pos: Pos, piece: Piece) = renderPiece(getPieceLoc(pos), piece)

    override fun clearPiece(pos: Pos) = clearPiece(getPieceLoc(pos))

    override fun renderCapturedPiece(pos: CapturedPos, piece: Piece) = renderPiece(getCapturedLoc(pos), piece)

    override fun clearCapturedPiece(pos: CapturedPos) = clearPiece(getCapturedLoc(pos))
}
