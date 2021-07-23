package gregc.gregchess.chess

import org.junit.jupiter.api.*
import org.junit.jupiter.api.Test
import kotlin.test.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MovementTests {
    val humanA = TestHuman("a")
    val humanB = TestHuman("b")
    val game = ChessGame(TestTimeManager(), testSettings("basic")).addPlayers {
        human(humanA, Side.WHITE, false)
        human(humanB, Side.BLACK, false)
    }.start()
    fun clearBoard(vararg pieces: PieceInfo) {
        game.board.setFromFEN(FEN.parseFromString("${FEN.BoardState.fromPieces(pieces.associateBy { it.pos }).state} w - - 0 1"))
    }

    private infix fun Piece.at(p: Pos) = PieceInfo(p, this, false)
    private infix fun Piece.at(d: Dir) = d to this

    private data class MoveSpec(val target: Pos, val isCapture: Boolean = false, val promotion: Piece? = null, val negate: Boolean = false)

    private fun MoveCandidate.follows(spec: MoveSpec) =
        target.pos == spec.target && (captured != null) == spec.isCapture && promotion == spec.promotion

    private fun Collection<MoveCandidate>.assertFollows(strict: Boolean, e: String, vararg specs: MoveSpec) {
        val sc = specs.toMutableList()
        forEach { c ->
            val s = sc.firstOrNull { c.follows(it) }
            if (strict)
                assertNotNull(s, "$e, $c\n$sc\n${specs.toList()}")
            assertFalse(s?.negate == true)
            sc.remove(s)
        }
        assertTrue(sc.all { it.negate }, "$e, ${map { it.target.pos }}, $sc")
    }

    private fun movesFrom(p: Pos) = game.board[p]?.bakedLegalMoves.orEmpty()

    private inline fun forEachPosIn(start: Pos, end: Pos, fn: (Pos) -> Unit) {
        for (f in start.file..end.file)
            for (r in start.rank..end.rank)
                fn(Pos(f, r))
    }

    @Nested
    inner class Pawn {
        private fun setupPawn(pos: Pos, side: Side, hasMoved: Boolean, vararg added: Pair<Dir, Piece>): Collection<MoveCandidate> {
            val (f, r) = pos
            clearBoard(
                PieceType.PAWN.of(side) at pos,
                PieceType.KING.white at Pos((f + 2) % 8, r),
                PieceType.KING.black at Pos((f + 4) % 8, r),
                *added.map { (d, p) ->
                    p at Pos((f + d.first)%8, (r + d.second)%8)
                }.toTypedArray()
            )
            game.board[Pos(f, r)]?.piece?.force(hasMoved)
            game.board.updateMoves()
            return movesFrom(pos)
        }

        private fun promotionsMoves(p: Pos, s: Side, isCapture: Boolean = false) =
            listOf(PieceType.BISHOP, PieceType.KNIGHT, PieceType.ROOK, PieceType.QUEEN)
                .map { MoveSpec(p, isCapture = isCapture, promotion = it.of(s))}.toTypedArray()

        @Nested
        inner class IfHasMoved {
            @Test
            fun `can only move 1 space forward`() {
                forEachPosIn(Pos(0,0), Pos(7,5)) { pos ->
                    setupPawn(pos, Side.WHITE, true)
                        .assertFollows(true, pos.toString(), MoveSpec(pos.plusR(1)))
                }
                forEachPosIn(Pos(0,2), Pos(7,7)) { pos ->
                    setupPawn(pos, Side.BLACK, true)
                        .assertFollows(true, pos.toString(), MoveSpec(pos.plusR(-1)))
                }
            }
            @Test
            fun `can promote from 1 space away`() {
                forEachPosIn(Pos(0,6), Pos(7,6)) { pos ->
                    setupPawn(pos, Side.WHITE, true)
                        .assertFollows(true, pos.toString(), *promotionsMoves(pos.copy(rank=7), Side.WHITE))
                }
                forEachPosIn(Pos(0,1), Pos(7,1)) { pos ->
                    setupPawn(pos, Side.BLACK, true)
                        .assertFollows(true, pos.toString(), *promotionsMoves(pos.copy(rank=0), Side.BLACK))
                }
            }
            @Test
            fun `is blocked by pieces`() {
                Side.values().forEach { blockSide ->
                    forEachPosIn(Pos(0,0), Pos(7,6)) { pos ->
                        setupPawn(pos, Side.WHITE, true, PieceType.ROOK.of(blockSide) at Dir(0,1))
                            .assertFollows(true, pos.toString())
                    }
                    forEachPosIn(Pos(0,1), Pos(7,7)) { pos ->
                        setupPawn(pos, Side.BLACK, true, PieceType.ROOK.of(blockSide) at Dir(0,-1))
                            .assertFollows(true, pos.toString())
                    }
                }
            }
        }

        @Nested
        inner class IfHasNotMoved {
            @Test
            fun `can only move 1 or 2 spaces forward`() {
                forEachPosIn(Pos(0,0), Pos(7,4)) { pos ->
                    setupPawn(pos, Side.WHITE, false)
                        .assertFollows(true, pos.toString(), MoveSpec(pos.plusR(1)), MoveSpec(pos.plusR(2)))
                }
                forEachPosIn(Pos(0,3), Pos(7,7)) { pos ->
                    setupPawn(pos, Side.BLACK, false)
                        .assertFollows(true, pos.toString(), MoveSpec(pos.plusR(-1)), MoveSpec(pos.plusR(-2)))
                }
            }
            @Test
            fun `can promote from 1 space away`() {
                forEachPosIn(Pos(0,6), Pos(7,6)) { pos ->
                    setupPawn(pos, Side.WHITE, false)
                        .assertFollows(true, pos.toString(), *promotionsMoves(pos.copy(rank=7), Side.WHITE))
                }
                forEachPosIn(Pos(0,1), Pos(7,1)) { pos ->
                    setupPawn(pos, Side.BLACK, false)
                        .assertFollows(true, pos.toString(), *promotionsMoves(pos.copy(rank=0), Side.BLACK))
                }
            }
            @Test
            fun `can promote from 2 spaces away`() {
                forEachPosIn(Pos(0,5), Pos(7,5)) { pos ->
                    setupPawn(pos, Side.WHITE, false)
                        .assertFollows(true, pos.toString(), MoveSpec(pos.copy(rank=6)),
                            *promotionsMoves(pos.copy(rank=7), Side.WHITE))
                }
                forEachPosIn(Pos(0,2), Pos(7,2)) { pos ->
                    setupPawn(pos, Side.BLACK, false)
                        .assertFollows(true, pos.toString(), MoveSpec(pos.copy(rank=1)),
                            *promotionsMoves(pos.copy(rank=0), Side.BLACK))
                }
            }
            @Test
            fun `is blocked by pieces from 1 space away`() {
                Side.values().forEach { blockSide ->
                    forEachPosIn(Pos(0,0), Pos(7,6)) { pos ->
                        setupPawn(pos, Side.WHITE, false, PieceType.ROOK.of(blockSide) at Dir(0,1))
                            .assertFollows(true, pos.toString())
                    }
                    forEachPosIn(Pos(0,1), Pos(7,7)) { pos ->
                        setupPawn(pos, Side.BLACK, false, PieceType.ROOK.of(blockSide) at Dir(0,-1))
                            .assertFollows(true, pos.toString())
                    }
                }
            }
            @Test
            fun `is blocked by pieces from 2 spaces away`() {
                Side.values().forEach { blockSide ->
                    forEachPosIn(Pos(0, 0), Pos(7, 5)) { pos ->
                        setupPawn(pos, Side.WHITE, false, PieceType.ROOK.of(blockSide) at Dir(0, 2))
                            .assertFollows(true, pos.toString(), MoveSpec(pos.plusR(1)))
                    }
                    forEachPosIn(Pos(0, 2), Pos(7, 7)) { pos ->
                        setupPawn(pos, Side.BLACK, false, PieceType.ROOK.of(blockSide) at Dir(0, -2))
                            .assertFollows(true, pos.toString(), MoveSpec(pos.plusR(-1)))
                    }
                }
            }
        }

        @Test
        fun `can capture opposite colored pieces diagonally`() {
            forEachPosIn(Pos(0, 0), Pos(6, 5)) { pos ->
                setupPawn(pos, Side.WHITE, false, PieceType.ROOK.black at Dir(1, 1))
                    .assertFollows(false, pos.toString(), MoveSpec(pos + Dir(1, 1), true))
            }
            forEachPosIn(Pos(0, 2), Pos(6, 7)) { pos ->
                setupPawn(pos, Side.BLACK, false, PieceType.ROOK.white at Dir(1, -1))
                    .assertFollows(false, pos.toString(), MoveSpec(pos + Dir(1, -1), true))
            }

            forEachPosIn(Pos(1, 0), Pos(7, 5)) { pos ->
                setupPawn(pos, Side.WHITE, false, PieceType.ROOK.black at Dir(-1, 1))
                    .assertFollows(false, pos.toString(), MoveSpec(pos + Dir(-1, 1), true))
            }
            forEachPosIn(Pos(1, 2), Pos(7, 7)) { pos ->
                setupPawn(pos, Side.BLACK, false, PieceType.ROOK.white at Dir(-1, -1))
                    .assertFollows(false, pos.toString(), MoveSpec(pos + Dir(-1, -1), true))
            }
        }

        @Test
        fun `can promote when capturing opposite colored pieces diagonally`() {
            forEachPosIn(Pos(0, 6), Pos(6, 6)) { pos ->
                setupPawn(pos, Side.WHITE, true, PieceType.ROOK.black at Dir(1, 1))
                    .assertFollows(false, pos.toString(),
                        *promotionsMoves(pos + Dir(1, 1), Side.WHITE, true))
            }
            forEachPosIn(Pos(0, 1), Pos(6, 1)) { pos ->
                setupPawn(pos, Side.BLACK, true, PieceType.ROOK.white at Dir(1, -1))
                    .assertFollows(false, pos.toString(),
                        *promotionsMoves(pos + Dir(1, -1), Side.BLACK, true))
            }

            forEachPosIn(Pos(1, 6), Pos(7, 6)) { pos ->
                setupPawn(pos, Side.WHITE, true, PieceType.ROOK.black at Dir(-1, 1))
                    .assertFollows(false, pos.toString(),
                        *promotionsMoves(pos + Dir(-1, 1), Side.WHITE, true))
            }
            forEachPosIn(Pos(1, 1), Pos(7, 1)) { pos ->
                setupPawn(pos, Side.BLACK, true, PieceType.ROOK.white at Dir(-1, -1))
                    .assertFollows(false, pos.toString(),
                        *promotionsMoves(pos + Dir(-1, -1), Side.BLACK,true))
            }
        }

        @Test
        fun `cannot capture same colored pieces diagonally`() {
            forEachPosIn(Pos(0, 0), Pos(6, 5)) { pos ->
                setupPawn(pos, Side.WHITE, true, PieceType.ROOK.white at Dir(1, 1))
                    .assertFollows(false, pos.toString(), MoveSpec(pos + Dir(1, 1), true, negate = true))
            }
            forEachPosIn(Pos(0, 2), Pos(6, 7)) { pos ->
                setupPawn(pos, Side.BLACK, true, PieceType.ROOK.black at Dir(1, -1))
                    .assertFollows(false, pos.toString(), MoveSpec(pos + Dir(1, -1), true, negate = true))
            }

            forEachPosIn(Pos(1, 0), Pos(7, 5)) { pos ->
                setupPawn(pos, Side.WHITE, true, PieceType.ROOK.white at Dir(-1, 1))
                    .assertFollows(false, pos.toString(), MoveSpec(pos + Dir(-1, 1), true, negate = true))
            }
            forEachPosIn(Pos(1, 2), Pos(7, 7)) { pos ->
                setupPawn(pos, Side.BLACK, true, PieceType.ROOK.black at Dir(-1, -1))
                    .assertFollows(false, pos.toString(), MoveSpec(pos + Dir(-1, -1), true, negate = true))
            }
        }

        @Test
        fun `can en passant opposite colored pawns`() {
            forEachPosIn(Pos(0, 1), Pos(6, 5)) { pos ->
                setupPawn(pos, Side.WHITE, true, PieceType.PAWN.black at Dir(1, 2))
                game.board[pos + Dir(1, 2)]?.piece?.force(false)
                game.nextTurn()
                (game.players[Side.BLACK] as HumanChessPlayer).run {
                    pickUp(pos + Dir(1, 2))
                    makeMove(pos + Dir(1,0))
                }
                movesFrom(pos).assertFollows(false, pos.toString(), MoveSpec(pos + Dir(1, 1), true))
            }
            forEachPosIn(Pos(0, 2), Pos(6, 6)) { pos ->
                setupPawn(pos, Side.BLACK, true, PieceType.PAWN.white at Dir(1, -2))
                game.board[pos + Dir(1, -2)]?.piece?.force(false)
                game.board.updateMoves()
                (game.players[Side.WHITE] as HumanChessPlayer).run {
                    pickUp(pos + Dir(1, -2))
                    makeMove(pos + Dir(1,0))
                }
                println(game.board.getFEN())
                println(game.running)
                movesFrom(pos).assertFollows(false, pos.toString(), MoveSpec(pos + Dir(1, -1), true))
            }

            forEachPosIn(Pos(1, 1), Pos(7, 5)) { pos ->
                setupPawn(pos, Side.WHITE, true, PieceType.PAWN.black at Dir(-1, 2))
                game.board[pos + Dir(-1, 2)]?.piece?.force(false)
                game.nextTurn()
                (game.players[Side.BLACK] as HumanChessPlayer).run {
                    pickUp(pos + Dir(-1, 2))
                    makeMove(pos + Dir(-1,0))
                }
                movesFrom(pos).assertFollows(false, pos.toString(), MoveSpec(pos + Dir(-1, 1), true))
            }
            forEachPosIn(Pos(1, 2), Pos(7, 6)) { pos ->
                setupPawn(pos, Side.BLACK, true, PieceType.PAWN.white at Dir(-1, -2))
                game.board[pos + Dir(-1, -2)]?.piece?.force(false)
                game.board.updateMoves()
                (game.players[Side.WHITE] as HumanChessPlayer).run {
                    pickUp(pos + Dir(-1, -2))
                    makeMove(pos + Dir(-1,0))
                }
                println(game.board.getFEN())
                println(game.running)
                movesFrom(pos).assertFollows(false, pos.toString(), MoveSpec(pos + Dir(-1, -1), true))
            }
        }
    }
}