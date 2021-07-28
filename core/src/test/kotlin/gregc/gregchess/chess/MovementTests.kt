package gregc.gregchess.chess

import gregc.gregchess.chess.variant.CaptureAll
import gregc.gregchess.times
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Test
import kotlin.math.*
import kotlin.test.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MovementTests {
    val game = ChessGame(TestTimeManager(), testSettings("basic", variant = CaptureAll)).addPlayers {
        human(TestHuman("a"), Side.WHITE, false)
        human(TestHuman("b"), Side.BLACK, false)
    }.start()

    fun clearBoard(vararg pieces: PieceInfo) {
        game.board.setFromFEN(FEN.parseFromString("${FEN.BoardState.fromPieces(pieces.associateBy { it.pos }).state} w - - 0 1"))
    }

    private infix fun Piece.at(p: Pos) = PieceInfo(p, this, false)
    private infix fun Piece.at(d: Dir) = d to this

    private fun movesFrom(p: Pos) = game.board[p]?.bakedLegalMoves.orEmpty()

    private inline fun forEachPosIn(start: Pos = Pos(0,0), end: Pos = Pos(7,7), side: Side = Side.WHITE, u: Int = 0, fn: (Pos) -> Unit) {
        for (f in max(start.file+u, 0)..min(end.file+u, 7))
            when(side) {
                Side.WHITE -> {
                    for (r in start.rank..end.rank)
                        fn(Pos(f, r))
                }
                Side.BLACK -> {
                    for (r in 7-end.rank..7-start.rank)
                        fn(Pos(f, r))
                }
            }
    }

    private fun Collection<MoveCandidate>.assertSize(s: Int) = apply {
        assertSame(s, size)
    }

    private inline fun Collection<MoveCandidate>.assertMove(target: Pos, block: MoveCandidate.() -> Unit) = apply {
        val move = firstOrNull { it.target.pos == target }
        assertNotNull(move, target.toString()).block()
    }

    private fun Collection<MoveCandidate>.assertNoMove(target: Pos) = apply {
        val move = firstOrNull { it.target.pos == target }
        assertNull(move, target.toString())
    }

    private fun setup(piece: PieceInfo, vararg added: Pair<Dir, Piece>) {
        val (f, r) = piece.pos
        clearBoard(
            piece,
            *added.map { (d, p) ->
                p at Pos((f + d.first)%8, (r + d.second)%8)
            }.toTypedArray()
        )
    }

    private var Pos.hasMoved
        get() = game.board[this]?.piece?.hasMoved ?: false
        set(value) {
            game.board[this]?.piece?.force(value)
            game.board.updateMoves()
        }

    private fun MoveCandidate.assertNotCapture() = assertNull(captured)

    private fun MoveCandidate.assertCapture(target: Pos = this.target.pos) = assertEquals(captured?.pos, target)


    @Nested
    inner class Pawn {
        private fun setupPawn(pos: Pos, side: Side, hasMoved: Boolean, vararg added: Pair<Dir, Piece>): Collection<MoveCandidate> {
            setup(PieceType.PAWN.of(side) at pos, *added)
            pos.hasMoved = hasMoved
            return movesFrom(pos)
        }

        private fun promotions(s: Side) =
            listOf(PieceType.QUEEN, PieceType.ROOK, PieceType.BISHOP, PieceType.KNIGHT).map { it.of(s) }

        private fun MoveCandidate.assertNotPromoting() = assertNull(promotions)

        private fun MoveCandidate.assertPromoting() = assertContentEquals(promotions, promotions(piece.side))

        @Nested
        inner class IfHasMoved {
            @Test
            fun `can only move 1 space forward`() {
                Side.forEach { s ->
                    forEachPosIn(Pos(0,0), Pos(7,5), s) { pos ->
                        setupPawn(pos, s, true).assertSize(1)
                            .assertMove(pos + s.dir) { assertNotCapture(); assertNotPromoting() }
                    }
                }
            }
            @Test
            fun `can promote from 1 space away`() {
                Side.forEach { s ->
                    forEachPosIn(Pos(0, 6), Pos(7, 6), s) { pos ->
                        setupPawn(pos, s, true).assertSize(1)
                            .assertMove(pos + s.dir) { assertNotCapture(); assertPromoting() }
                    }
                }
            }
            @Test
            fun `is blocked by pieces`() {
                Side.forEach { s ->
                    Side.forEach { blockSide ->
                        forEachPosIn(Pos(0,0), Pos(7,6), s) { pos ->
                            setupPawn(pos, s, true, PieceType.ROOK.of(blockSide) at s.dir).assertSize(0)
                        }
                    }
                }
            }
        }

        @Nested
        inner class IfHasNotMoved {
            @Test
            fun `can only move 1 or 2 spaces forward`() {
                Side.forEach { s ->
                    forEachPosIn(Pos(0, 0), Pos(7, 4), s) { pos ->
                        setupPawn(pos, s, false).assertSize(2)
                            .assertMove(pos + s.dir) { assertNotCapture(); assertNotPromoting() }
                            .assertMove(pos + s.dir * 2) { assertNotCapture(); assertNotPromoting() }
                    }
                }
            }
            @Test
            fun `can promote from 1 space away`() {
                Side.forEach { s ->
                    forEachPosIn(Pos(0, 6), Pos(7, 6), s) { pos ->
                        setupPawn(pos, s, false).assertSize(1)
                            .assertMove(pos + s.dir) { assertNotCapture(); assertPromoting() }
                    }
                }
            }
            @Test
            fun `can promote from 2 spaces away`() {
                Side.forEach { s ->
                    forEachPosIn(Pos(0, 5), Pos(7, 5), s) { pos ->
                        setupPawn(pos, s, false).assertSize(2)
                            .assertMove(pos + s.dir) { assertNotCapture(); assertNotPromoting() }
                            .assertMove(pos + s.dir * 2) { assertNotCapture(); assertPromoting() }
                    }
                }
            }
            @Test
            fun `is blocked by pieces from 1 space away`() {
                Side.forEach { s ->
                    Side.forEach { blockSide ->
                        forEachPosIn(Pos(0, 0), Pos(7, 6), s) { pos ->
                            setupPawn(pos, s, false, PieceType.ROOK.of(blockSide) at s.dir).assertSize(0)
                        }
                    }
                }
            }
            @Test
            fun `is blocked by pieces from 2 spaces away`() {
                Side.forEach { s ->
                    Side.values().forEach { blockSide ->
                        forEachPosIn(Pos(0, 0), Pos(7, 5), s) { pos ->
                            setupPawn(pos, s, false, PieceType.ROOK.of(blockSide) at s.dir * 2).assertSize(1)
                                .assertMove(pos + s.dir) { assertNotCapture(); assertNotPromoting() }
                        }
                    }
                }
            }
        }


        @Test
        fun `can capture opposite colored pieces diagonally`() {
            Side.forEach { s ->
                listOf(1, -1).forEach { u ->
                    forEachPosIn(Pos(0, 0), Pos(7, 5), s, -u) { pos ->
                        val d = Dir(u, s.direction)
                        setupPawn(pos, s, false, PieceType.ROOK.of(!s) at d)
                            .assertMove(pos + d) { assertCapture(); assertNotPromoting() }
                    }
                }
            }
        }

        @Test
        fun `can promote when capturing opposite colored pieces diagonally`() {
            Side.forEach { s ->
                listOf(1, -1).forEach { u ->
                    forEachPosIn(Pos(0, 6), Pos(7, 6), s, -u) { pos ->
                        val d = Dir(u, s.direction)
                        setupPawn(pos, s, true, PieceType.ROOK.of(!s) at d)
                            .assertMove(pos + d) { assertCapture(); assertPromoting() }
                    }
                }
            }
        }

        @Test
        fun `cannot capture same colored pieces diagonally`() {
            Side.forEach { s ->
                listOf(1, -1).forEach { u ->
                    forEachPosIn(Pos(0, 0), Pos(7, 5), s, -u) { pos ->
                        val d = Dir(u, s.direction)
                        setupPawn(pos, s, true, PieceType.ROOK.of(s) at d)
                            .assertNoMove(pos + d)
                    }
                }
            }
        }

        @Test
        fun `can en passant opposite colored pawns`() {
            Side.forEach { s ->
                listOf(1, -1).forEach { u ->
                    forEachPosIn(Pos(0, 1), Pos(6, 5), s, -u) { pos ->
                        val d = Dir(u, s.direction)
                        setupPawn(pos, s, true, PieceType.PAWN.of(!s) at Dir(u, 0))
                        game.board[pos + d]!!.flags += ChessFlag(EN_PASSANT, 0)
                        game.board.updateMoves()
                        movesFrom(pos).assertMove(pos + d) { assertCapture(pos.plusF(u)); assertNotPromoting() }
                    }
                }
            }
        }
    }

    @Nested
    inner class Bishop {

        @Test
        fun `can only move on the diagonals`() {
            Side.forEach { s ->
                forEachPosIn { pos ->
                    setup(PieceType.BISHOP.of(s) at pos)
                    movesFrom(pos).apply {
                        var size = 0
                        (-7..7).forEach {
                            val t1 = pos + Dir(it, it)
                            if (t1.isValid() && t1 != pos) {
                                assertMove(t1) { assertNotCapture() }
                                size++
                            }
                            val t2 = pos + Dir(it, -it)
                            if (t2.isValid() && t2 != pos) {
                                assertMove(t2) { assertNotCapture() }
                                size++
                            }
                        }
                        assertSize(size)
                    }
                }
            }
        }

        @Test
        fun `is blocked by same colored pieces`() {
            Side.forEach { s ->
                forEachPosIn { pos ->
                    setup(PieceType.BISHOP.of(s) at pos,
                        PieceType.ROOK.of(s) at Dir(2, 2),
                        PieceType.ROOK.of(s) at Dir(2, -2),
                        PieceType.ROOK.of(s) at Dir(-2, 2),
                        PieceType.ROOK.of(s) at Dir(-2, -2))
                    movesFrom(pos).apply {
                        var size = 0
                        (-1..1).forEach {
                            val t1 = pos + Dir(it, it)
                            if (t1.isValid() && t1 != pos) {
                                assertMove(t1) { assertNotCapture() }
                                size++
                            }
                            val t2 = pos + Dir(it, -it)
                            if (t2.isValid() && t2 != pos) {
                                assertMove(t2) { assertNotCapture() }
                                size++
                            }
                        }
                        assertSize(size)
                    }
                }
            }
        }

        @Test
        fun `can capture opposite colored pieces`() {
            Side.forEach { s ->
                forEachPosIn { pos ->
                    setup(PieceType.BISHOP.of(s) at pos,
                        PieceType.ROOK.of(!s) at Dir(2, 2),
                        PieceType.ROOK.of(!s) at Dir(2, -2),
                        PieceType.ROOK.of(!s) at Dir(-2, 2),
                        PieceType.ROOK.of(!s) at Dir(-2, -2))
                    movesFrom(pos).apply {
                        var size = 0
                        (-2..2).forEach {
                            val t1 = pos + Dir(it, it)
                            if (t1.isValid() && t1 != pos) {
                                assertMove(t1) { if (it.absoluteValue == 2) assertCapture() else assertNotCapture() }
                                size++
                            }
                            val t2 = pos + Dir(it, -it)
                            if (t2.isValid() && t2 != pos) {
                                assertMove(t2) { if (it.absoluteValue == 2) assertCapture() else assertNotCapture() }
                                size++
                            }
                        }
                        assertSize(size)
                    }
                }
            }
        }
    }

    @Nested
    inner class Rook {

        @Test
        fun `can only move horizontally and vertically`() {
            Side.forEach { s ->
                forEachPosIn { pos ->
                    setup(PieceType.ROOK.of(s) at pos)
                    movesFrom(pos).apply {
                        var size = 0
                        (-7..7).forEach {
                            val t1 = pos + Dir(it, 0)
                            if (t1.isValid() && t1 != pos) {
                                assertMove(t1) { assertNotCapture() }
                                size++
                            }
                            val t2 = pos + Dir(0, it)
                            if (t2.isValid() && t2 != pos) {
                                assertMove(t2) { assertNotCapture() }
                                size++
                            }
                        }
                        assertSize(size)
                    }
                }
            }
        }

        @Test
        fun `is blocked by same colored pieces`() {
            Side.forEach { s ->
                forEachPosIn { pos ->
                    setup(PieceType.ROOK.of(s) at pos,
                        PieceType.BISHOP.of(s) at Dir(2, 0),
                        PieceType.BISHOP.of(s) at Dir(-2, 0),
                        PieceType.BISHOP.of(s) at Dir(0, 2),
                        PieceType.BISHOP.of(s) at Dir(0, -2))
                    movesFrom(pos).apply {
                        var size = 0
                        (-1..1).forEach {
                            val t1 = pos + Dir(it, 0)
                            if (t1.isValid() && t1 != pos) {
                                assertMove(t1) { assertNotCapture() }
                                size++
                            }
                            val t2 = pos + Dir(0, it)
                            if (t2.isValid() && t2 != pos) {
                                assertMove(t2) { assertNotCapture() }
                                size++
                            }
                        }
                        assertSize(size)
                    }
                }
            }
        }

        @Test
        fun `can capture opposite colored pieces`() {
            Side.forEach { s ->
                forEachPosIn { pos ->
                    setup(PieceType.ROOK.of(s) at pos,
                        PieceType.BISHOP.of(!s) at Dir(2, 0),
                        PieceType.BISHOP.of(!s) at Dir(-2, 0),
                        PieceType.BISHOP.of(!s) at Dir(0, 2),
                        PieceType.BISHOP.of(!s) at Dir(0, -2))
                    movesFrom(pos).apply {
                        var size = 0
                        (-2..2).forEach {
                            val t1 = pos + Dir(it, 0)
                            if (t1.isValid() && t1 != pos) {
                                assertMove(t1) { if (it.absoluteValue == 2) assertCapture() else assertNotCapture() }
                                size++
                            }
                            val t2 = pos + Dir(0, it)
                            if (t2.isValid() && t2 != pos) {
                                assertMove(t2) { if (it.absoluteValue == 2) assertCapture() else assertNotCapture() }
                                size++
                            }
                        }
                        assertSize(size)
                    }
                }
            }
        }
    }

    @Nested
    inner class Queen {

        @Test
        fun `can only move on the diagonals horizontally and vertically`() {
            Side.forEach { s ->
                forEachPosIn { pos ->
                    setup(PieceType.QUEEN.of(s) at pos)
                    movesFrom(pos).apply {
                        var size = 0
                        (-7..7).forEach {
                            val t1 = pos + Dir(it, it)
                            if (t1.isValid() && t1 != pos) {
                                assertMove(t1) { assertNotCapture() }
                                size++
                            }
                            val t2 = pos + Dir(it, -it)
                            if (t2.isValid() && t2 != pos) {
                                assertMove(t2) { assertNotCapture() }
                                size++
                            }
                            val t3 = pos + Dir(it, 0)
                            if (t3.isValid() && t3 != pos) {
                                assertMove(t3) { assertNotCapture() }
                                size++
                            }
                            val t4 = pos + Dir(0, it)
                            if (t4.isValid() && t4 != pos) {
                                assertMove(t4) { assertNotCapture() }
                                size++
                            }
                        }
                        assertSize(size)
                    }
                }
            }
        }

        @Test
        fun `is blocked by same colored pieces`() {
            Side.forEach { s ->
                forEachPosIn { pos ->
                    setup(PieceType.QUEEN.of(s) at pos,
                        PieceType.KING.of(s) at Dir(2, 2),
                        PieceType.KING.of(s) at Dir(2, -2),
                        PieceType.KING.of(s) at Dir(-2, 2),
                        PieceType.KING.of(s) at Dir(-2, -2),
                        PieceType.KING.of(s) at Dir(2, 0),
                        PieceType.KING.of(s) at Dir(-2, 0),
                        PieceType.KING.of(s) at Dir(0, 2),
                        PieceType.KING.of(s) at Dir(0, -2))
                    movesFrom(pos).apply {
                        var size = 0
                        (-1..1).forEach {
                            val t1 = pos + Dir(it, it)
                            if (t1.isValid() && t1 != pos) {
                                assertMove(t1) { assertNotCapture() }
                                size++
                            }
                            val t2 = pos + Dir(it, -it)
                            if (t2.isValid() && t2 != pos) {
                                assertMove(t2) { assertNotCapture() }
                                size++
                            }
                            val t3 = pos + Dir(it, 0)
                            if (t3.isValid() && t3 != pos) {
                                assertMove(t3) { assertNotCapture() }
                                size++
                            }
                            val t4 = pos + Dir(0, it)
                            if (t4.isValid() && t4 != pos) {
                                assertMove(t4) { assertNotCapture() }
                                size++
                            }
                        }
                        assertSize(size)
                    }
                }
            }
        }

        @Test
        fun `can capture opposite colored pieces`() {
            Side.forEach { s ->
                forEachPosIn { pos ->
                    setup(PieceType.QUEEN.of(s) at pos,
                        PieceType.KING.of(!s) at Dir(2, 2),
                        PieceType.KING.of(!s) at Dir(2, -2),
                        PieceType.KING.of(!s) at Dir(-2, 2),
                        PieceType.KING.of(!s) at Dir(-2, -2),
                        PieceType.KING.of(!s) at Dir(2, 0),
                        PieceType.KING.of(!s) at Dir(-2, 0),
                        PieceType.KING.of(!s) at Dir(0, 2),
                        PieceType.KING.of(!s) at Dir(0, -2))
                    movesFrom(pos).apply {
                        var size = 0
                        (-2..2).forEach {
                            val t1 = pos + Dir(it, it)
                            if (t1.isValid() && t1 != pos) {
                                assertMove(t1) { if (it.absoluteValue == 2) assertCapture() else assertNotCapture() }
                                size++
                            }
                            val t2 = pos + Dir(it, -it)
                            if (t2.isValid() && t2 != pos) {
                                assertMove(t2) { if (it.absoluteValue == 2) assertCapture() else assertNotCapture() }
                                size++
                            }
                            val t3 = pos + Dir(it, 0)
                            if (t3.isValid() && t3 != pos) {
                                assertMove(t3) { if (it.absoluteValue == 2) assertCapture() else assertNotCapture() }
                                size++
                            }
                            val t4 = pos + Dir(0, it)
                            if (t4.isValid() && t4 != pos) {
                                assertMove(t4) { if (it.absoluteValue == 2) assertCapture() else assertNotCapture() }
                                size++
                            }
                        }
                        assertSize(size)
                    }
                }
            }
        }
    }
}