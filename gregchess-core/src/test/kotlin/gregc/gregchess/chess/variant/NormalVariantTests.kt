package gregc.gregchess.chess.variant

import assertk.all
import assertk.assertThat
import assertk.assertions.isNotNull
import gregc.gregchess.chess.*
import gregc.gregchess.chess.move.MoveTraitType
import gregc.gregchess.chess.piece.*
import org.junit.jupiter.api.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NormalVariantTests : VariantTests(ChessVariant.Normal) {
    @BeforeAll
    fun setup() {
        setupRegistry()
    }

    @Nested
    inner class Moves {
        @Nested
        inner class Pawn {

            @Test
            fun `single forward move is correct`() {
                val game = mkGame(fen {
                    set(Pos(0, 0), white(PieceType.KING))
                    set(Pos(0, 2), black(PieceType.KING))
                    set(Pos(3, 4), white(PieceType.PAWN))
                    set(Pos(5, 4), black(PieceType.PAWN))
                })
                fun check(origin: Pos, target: Pos) {
                    assertThat(game).pieceAt(origin).legalMoves(game).onlyGetTo(target)
                    assertThat(game).pieceAt(origin).legalMoves(game).getTo(target).isNotNull().all {
                        isNormalMove()
                        needsEmptyExactly(target)
                        passesThroughExactly(target)
                        stopsBlockingExactly(origin)
                        startsBlockingExactly(target)
                        hasExtraTraitsExactly(MoveTraitType.TARGET)
                        targets(target)
                        afterExecution(game).isNamed(target.toString())
                    }
                }

                check(Pos(3, 4), Pos(3, 5))
                check(Pos(5, 4), Pos(5, 3))
            }

            @Test
            fun `can move 2 squares forward when has not moved`() {
                val game = mkGame(fen {
                    set(Pos(0, 0), white(PieceType.KING))
                    set(Pos(0, 2), black(PieceType.KING))
                    set(Pos(3, 4), white(PieceType.PAWN))
                    set(Pos(5, 4), black(PieceType.PAWN))
                })
                fun check(origin: Pos, mid: Pos, target: Pos) {
                    assertThat(game).pieceAt(origin).legalMoves(game).onlyGetTo(mid, target)
                    assertThat(game).pieceAt(origin).legalMoves(game).getTo(target).isNotNull().all {
                        isNormalMove()
                        needsEmptyExactly(mid, target)
                        passesThroughExactly(mid, target)
                        stopsBlockingExactly(origin)
                        startsBlockingExactly(target)
                        hasExtraTraitsExactly(MoveTraitType.TARGET, MoveTraitType.FLAG)
                        targets(target)
                        createsFlagsExactly(mid to (ChessFlag.EN_PASSANT to 0u))
                        afterExecution(game).isNamed(target.toString())
                    }
                }

                game.board.setNotMoved(Pos(3, 4))
                game.board.setNotMoved(Pos(5, 4))
                check(Pos(3, 4), Pos(3, 5), Pos(3, 6))
                check(Pos(5, 4), Pos(5, 3), Pos(5, 2))
            }

            @Test
            fun `can promote on the last rank`() {
                val game = mkGame(fen {
                    set(Pos(0, 0), white(PieceType.KING))
                    set(Pos(0, 2), black(PieceType.KING))
                    set(Pos(3, 6), white(PieceType.PAWN))
                    set(Pos(5, 1), black(PieceType.PAWN))
                })
                val promotions = listOf(PieceType.QUEEN, PieceType.ROOK, PieceType.BISHOP, PieceType.KNIGHT)

                fun check(origin: Pos, target: Pos, color: Color) {
                    assertThat(game).pieceAt(origin).legalMoves(game).getTo(target).isNotNull().all {
                        isNormalMove()
                        needsEmptyExactly(target)
                        passesThroughExactly(target)
                        stopsBlockingExactly(origin)
                        startsBlockingExactly(target)
                        hasExtraTraitsExactly(MoveTraitType.TARGET, MoveTraitType.PROMOTION)
                        targets(target)
                        promotesTo(*promotions.map { Piece(it, color) }.toTypedArray())
                        afterExecution(game, Piece(PieceType.BISHOP, color)).isNamed("$target=B")
                    }
                }

                check(Pos(3, 6), Pos(3, 7), Color.WHITE)
                check(Pos(5, 1), Pos(5, 0), Color.BLACK)
            }

            @Test
            fun `can capture enemy diagonally`() {
                val game = mkGame(fen {
                    set(Pos(0, 0), white(PieceType.KING))
                    set(Pos(0, 2), black(PieceType.KING))
                    set(Pos(2, 5), black(PieceType.ROOK))
                    set(Pos(4, 5), black(PieceType.ROOK))
                    set(Pos(3, 4), white(PieceType.PAWN))
                    set(Pos(4, 3), white(PieceType.ROOK))
                    set(Pos(6, 3), white(PieceType.ROOK))
                    set(Pos(5, 4), black(PieceType.PAWN))
                })
                fun check(origin: Pos, target: Pos) {
                    assertThat(game).pieceAt(origin).legalMoves(game).getTo(target).isNotNull().all {
                        isNormalMove()
                        needsEmptyExactly()
                        passesThroughExactly(target)
                        stopsBlockingExactly(origin)
                        startsBlockingExactly(target)
                        hasExtraTraitsExactly(MoveTraitType.TARGET, MoveTraitType.CAPTURE)
                        targets(target)
                        captures(target, required = true)
                        afterExecution(game).isNamed("${origin.fileStr}x$target")
                    }
                }

                check(Pos(3, 4), Pos(2, 5))
                check(Pos(5, 4), Pos(4, 3))
                game.board.setFromFEN(game.board.initialFEN)
                check(Pos(3, 4), Pos(4, 5))
                check(Pos(5, 4), Pos(6, 3))
            }

            @Test
            fun `can capture enemy en passant`() {
                val game = mkGame(fen {
                    set(Pos(0, 0), white(PieceType.KING))
                    set(Pos(0, 2), black(PieceType.KING))
                    set(Pos(3, 4), white(PieceType.PAWN))
                    set(Pos(2, 4), black(PieceType.PAWN))
                    set(Pos(5, 4), black(PieceType.PAWN))
                    set(Pos(4, 4), white(PieceType.PAWN))
                })
                fun check(origin: Pos, target: Pos, capture: Pos) {
                    assertThat(game).pieceAt(origin).legalMoves(game).getTo(target).isNotNull().all {
                        isNormalMove()
                        needsEmptyExactly()
                        passesThroughExactly(target)
                        stopsBlockingExactly(origin, capture)
                        startsBlockingExactly(target)
                        hasExtraTraitsExactly(MoveTraitType.TARGET, MoveTraitType.CAPTURE, MoveTraitType.REQUIRE_FLAG)
                        targets(target)
                        captures(capture, required = true)
                        requiresFlagsExactly(target to ChessFlag.EN_PASSANT)
                        afterExecution(game).isNamed("${origin.fileStr}x$target")
                    }
                }

                game.board.addEnPassantFlag(Pos(2, 5))
                assertThat(game).pieceAt(Pos(3, 4)).legalMoves(game).onlyGetTo(Pos(3, 5), Pos(2, 5))
                check(Pos(3, 4), Pos(2, 5), Pos(2, 4))
                game.board.addEnPassantFlag(Pos(4, 3))
                assertThat(game).pieceAt(Pos(5, 4)).legalMoves(game).onlyGetTo(Pos(5, 3), Pos(4, 3))
                check(Pos(5, 4), Pos(4, 3), Pos(4, 4))
            }

        }
    }
}