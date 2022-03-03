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
            fun `can move 1 square forward`() {
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
        @Nested
        inner class King {
            @Test
            fun `can move to neighbouring squares`() {
                val game = mkGame(fen {
                    set(Pos(2, 2), white(PieceType.KING))
                    set(Pos(5, 5), black(PieceType.KING))
                    set(Pos(0, 6), black(PieceType.PAWN))
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
                        captures(target)
                        afterExecution(game).isNamed("K$target")
                    }
                }

                assertThat(game).pieceAt(Pos(2, 2)).legalMoves(game).onlyGetTo(*Pos(2, 2).neighbours().toTypedArray())
                assertThat(game).pieceAt(Pos(5, 5)).legalMoves(game).onlyGetTo(*Pos(5, 5).neighbours().toTypedArray())
                check(Pos(2, 2), Pos(3, 3))
                check(Pos(5, 5), Pos(6, 6))
                game.board.setFromFEN(game.board.initialFEN)
                check(Pos(2, 2), Pos(3, 2))
                check(Pos(5, 5), Pos(6, 5))
            }

            @Test
            fun `can castle`() {
                val game = mkGame(fen {
                    set(Pos(4, 0), white(PieceType.KING))
                    set(Pos(4, 7), black(PieceType.KING))
                    set(Pos(0, 0), white(PieceType.ROOK))
                    set(Pos(0, 7), black(PieceType.ROOK))
                    set(Pos(7, 0), white(PieceType.ROOK))
                    set(Pos(7, 7), black(PieceType.ROOK))
                    for(i in 0..7) {
                        set(Pos(i, 1), white(PieceType.PAWN))
                        set(Pos(i, 6), black(PieceType.PAWN))
                    }
                    castlingRights = byColor(listOf(0, 7))
                })
                fun check(origin: Pos, mid: Pos, target: Pos, rookPos: Pos, side: BoardSide, vararg extraEmpty: Pos) {
                    assertThat(game).pieceAt(origin).legalMoves(game).getTo(target).isNotNull().all {
                        isNormalMove()
                        needsEmptyExactly(mid, target, *extraEmpty)
                        passesThroughExactly(origin, mid, target)
                        stopsBlockingExactly(origin, rookPos)
                        startsBlockingExactly(target, mid)
                        hasExtraTraitsExactly(MoveTraitType.CASTLES)
                        castles(side, target, rookPos, mid)
                        afterExecution(game).isNamed(side.castles)
                    }
                }

                assertThat(game).pieceAt(Pos(4, 0)).legalMoves(game).onlyGetTo(Pos(2, 0), Pos(3, 0), Pos(5, 0), Pos(6, 0))
                assertThat(game).pieceAt(Pos(4, 7)).legalMoves(game).onlyGetTo(Pos(2, 7), Pos(3, 7), Pos(5, 7), Pos(6, 7))
                check(Pos(4, 0), Pos(5, 0), Pos(6, 0), Pos(7, 0), BoardSide.KINGSIDE)
                check(Pos(4, 7), Pos(5, 7), Pos(6, 7), Pos(7, 7), BoardSide.KINGSIDE)
                game.board.setFromFEN(game.board.initialFEN)
                check(Pos(4, 0), Pos(3, 0), Pos(2, 0), Pos(0, 0), BoardSide.QUEENSIDE, Pos(1, 0))
                check(Pos(4, 7), Pos(3, 7), Pos(2, 7), Pos(0, 7), BoardSide.QUEENSIDE, Pos(1, 7))
            }
        }
    }
}