package gregc.gregchess.chess

import gregc.gregchess.chess.variant.CaptureAll
import gregc.gregchess.times
import io.kotest.core.spec.style.FreeSpec
import io.kotest.datatest.withData
import io.kotest.matchers.collections.*
import io.kotest.matchers.shouldNotHave
import io.kotest.property.Arb
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll

private infix fun Piece.at(p: Pos) = PieceInfo(p, this, true)
private infix fun Piece.notMovedAt(p: Pos) = PieceInfo(p, this, false)

private val arbPos = arbitrary { rs ->
    Pos(rs.random.nextInt(0, 7), rs.random.nextInt(0, 7))
}

private fun arbPawnPos(s: Side, n: Int = 1, b: BoardSide? = null) =
    arbPos.filter { (it + s.dir * n + (b?.dir ?: Dir(0, 0))).isValid() }

private fun arbPromotingPos(s: Side, n: Int = 1, b: BoardSide? = null) =
    arbPos.filter { (it + s.dir * n + (b?.dir ?: Dir(0, 0))).run { isValid() && rank in listOf(0, 7) } }

private val arbPieceType = PieceType.run { Arb.of(PAWN, KNIGHT, BISHOP, ROOK, QUEEN, KING) }

private val arbPiece = Arb.bind(arbPieceType, Arb.enum<Side>()) { type, s -> Piece(type, s) }

private val game = ChessGame(testSettings("basic", variant = CaptureAll), bySides("a".cpi, "b".cpi)).start()

private fun clearBoard(vararg pieces: PieceInfo) {
    game.board.setFromFEN(FEN.parseFromString("8/8/8/8/8/8/8/8 w - - 0 1"))
    for (p in pieces) {
        game.board += p
    }
    game.board.updateMoves()
}

private fun addFlags(vararg flags: Pair<ChessFlagType, Pos>) {
    for ((f, p) in flags) {
        game.board[p]!!.flags += ChessFlag(f, f.startTime.toInt())
    }
    game.board.updateMoves()
}

private val Pos.moves get() = game.board[this]?.bakedLegalMoves.orEmpty()

private infix fun Pos.canMoveTo(target: Pos) {
    moves shouldHaveSingleElement { it.target.pos == target && it.captured == null }
}

private infix fun Pos.canPromoteAt(target: Pos) {
    moves shouldHaveSingleElement { it.target.pos == target && it.promotions == promotions(it.piece.side) }
}

private infix fun Pos.canCaptureAt(target: Pos) {
    moves shouldHaveSingleElement { it.target.pos == target && it.captured?.pos == target }
}

private fun Pos.canCaptureAt(target: Pos, capture: Pos) {
    moves shouldHaveSingleElement { it.target.pos == target && it.captured?.pos == capture }
}

private infix fun Pos.cannotGoTo(target: Pos) {
    moves shouldNotHave singleElement { it.target.pos == target }
}

private fun promotions(s: Side) = PieceType.run { listOf(QUEEN, ROOK, BISHOP, KNIGHT) }.map { it.of(s) }

private const val ITERS = 50

class MovementTests : FreeSpec({

    "Pawn" - {
        "if has moved" - {
            "can only move 1 square forward" - {
                withData(Side.values().toList()) { s ->
                    checkAll(ITERS, arbPawnPos(s)) { pos ->
                        clearBoard(s.pawn at pos)
                        pos canMoveTo pos + s.dir
                        pos.moves shouldHaveSize 1
                    }
                }
            }
            "can promote from 1 square away" - {
                withData(Side.values().toList()) { s ->
                    checkAll(ITERS, arbPromotingPos(s)) { pos ->
                        clearBoard(s.pawn at pos)
                        pos canMoveTo pos + s.dir
                        pos canPromoteAt pos + s.dir
                        pos.moves shouldHaveSize 1
                    }
                }
            }
            "is blocked by pieces" - {
                withData(Side.values().toList()) { s ->
                    checkAll(ITERS, arbPawnPos(s), arbPiece) { pos, block ->
                        clearBoard(s.pawn at pos, block at pos + s.dir)
                        pos.moves shouldHaveSize 0
                    }
                }
            }
        }
        "if has not moved" - {
            "can only move 1 or 2 squares forward" - {
                withData(Side.values().toList()) { s ->
                    checkAll(ITERS, arbPawnPos(s, 2)) { pos ->
                        clearBoard(s.pawn notMovedAt pos)
                        pos canMoveTo pos + s.dir
                        pos canMoveTo pos + s.dir * 2
                        pos.moves shouldHaveSize 2
                    }
                }
            }
            "can promote from" - {
                "1 square away" - {
                    withData(Side.values().toList()) { s ->
                        checkAll(ITERS, arbPromotingPos(s)) { pos ->
                            clearBoard(s.pawn notMovedAt pos)
                            pos canMoveTo pos + s.dir
                            pos canPromoteAt pos + s.dir
                            pos.moves shouldHaveSize 1
                        }
                    }
                }
                "2 squares away" - {
                    withData(Side.values().toList()) { s ->
                        checkAll(ITERS, arbPromotingPos(s, 2)) { pos ->
                            clearBoard(s.pawn notMovedAt pos)
                            pos canMoveTo pos + s.dir
                            pos canMoveTo pos + s.dir * 2
                            pos canPromoteAt pos + s.dir * 2
                            pos.moves shouldHaveSize 2
                        }
                    }
                }
            }
            "is blocked by pieces from" - {
                "1 square away" - {
                    withData(Side.values().toList()) { s ->
                        checkAll(ITERS, arbPawnPos(s), arbPiece) { pos, block ->
                            clearBoard(s.pawn notMovedAt pos, block at pos + s.dir)
                            pos.moves shouldHaveSize 0
                        }
                    }
                }
                "2 squares away" - {
                    withData(Side.values().toList()) { s ->
                        checkAll(ITERS, arbPawnPos(s, 2), arbPiece) { pos, block ->
                            clearBoard(s.pawn notMovedAt pos, block at pos + s.dir * 2)
                            pos canMoveTo pos + s.dir
                            pos.moves shouldHaveSize 1
                        }
                    }
                }
            }
        }
        "can capture opposite colored pieces diagonally" - {
            withData(Side.values().toList()) { s ->
                withData(BoardSide.values().toList()) { bs ->
                    checkAll(ITERS, arbPawnPos(s, 1, bs), arbPieceType) { pos, block ->
                        clearBoard(s.pawn at pos, block.of(!s) at pos + s.dir + bs.dir)
                        pos canCaptureAt pos + s.dir + bs.dir
                    }
                }
            }
        }
        "cannot capture same colored pieces diagonally" - {
            withData(Side.values().toList()) { s ->
                withData(BoardSide.values().toList()) { bs ->
                    checkAll(ITERS, arbPawnPos(s, 1, bs), arbPieceType) { pos, block ->
                        clearBoard(s.pawn at pos, block.of(s) at pos + s.dir + bs.dir)
                        pos cannotGoTo pos + s.dir + bs.dir
                    }
                }
            }
        }
        "can promote when capturing opposite colored pieces diagonally" - {
            withData(Side.values().toList()) { s ->
                withData(BoardSide.values().toList()) { bs ->
                    checkAll(ITERS, arbPromotingPos(s, 1, bs), arbPieceType) { pos, block ->
                        clearBoard(s.pawn at pos, block.of(!s) at pos + s.dir + bs.dir)
                        pos canCaptureAt pos + s.dir + bs.dir
                        pos canPromoteAt pos + s.dir + bs.dir
                    }
                }
            }
        }
        "can en passant opposite colored pawns" - {
            withData(Side.values().toList()) { s ->
                withData(BoardSide.values().toList()) { bs ->
                    checkAll(ITERS, arbPawnPos(s, 1, bs)) { pos ->
                        clearBoard(s.pawn at pos, (!s).pawn at pos + bs.dir)
                        addFlags(PawnMovement.EN_PASSANT to pos + s.dir + bs.dir)
                        pos.canCaptureAt(pos + s.dir + bs.dir, pos + bs.dir)
                    }
                }
            }
        }
    }

})