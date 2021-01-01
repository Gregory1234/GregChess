package gregc.gregchess.chess

import gregc.gregchess.between
import gregc.gregchess.rotationsOf
import gregc.gregchess.times
import gregc.gregchess.towards
import org.bukkit.Material
import gregc.gregchess.chess.ChessMoveScheme.Move.Finish.*
import java.lang.IllegalArgumentException

sealed class ChessMoveScheme {

    abstract fun genMoves(game: ChessGame, origin: ChessPosition): List<Move>

    data class Move(val origin: ChessPosition, val target: ChessPosition, val color: Material, val scheme: Generator, val game: ChessGame, val blocks: List<ChessPosition> = listOf(), val cont: List<ChessPosition> = listOf(), val elements: List<Element> = listOf(), val finish: Finish = END_TURN) {
        fun execute() {
            if (finish == DEFEND)
                throw IllegalArgumentException(this.toString())
            for (e in elements) e.execute(game.board)
            Element(origin, target).execute(game.board)
            when (finish) {
                END_TURN -> game.nextTurn()
                PROMOTE -> game.currentPlayer.promote(game.board[target]!!)
                DEFEND -> throw IllegalArgumentException()
            }
        }

        fun display() {
            target.fillFloor(game.arena.world, color)
        }

        data class Element(val origin: ChessPosition, val target: ChessPosition?) {
            fun execute(board: ChessBoard) {
                val piece = board[origin]
                if (target == null)
                    piece!!.capture()
                else
                    piece!!.pos = target
            }

            override fun toString() = "$origin -> $target"
        }

        enum class Finish {
            END_TURN, PROMOTE, DEFEND
        }

        override fun toString() = "Move(elements = ${elements+ Element(origin,target)}, color = $color, scheme = $scheme, blocks = $blocks)"
    }

    protected infix fun ChessPosition.into(target: ChessPosition?) = Move.Element(this, target)
    protected infix fun ChessPosition.by(target: Pair<Int, Int>) = Move.Element(this, this + target)

    object King : ChessMoveScheme() {
        private val schemes = listOf(Generator.Neighbours, Generator.Castle(0, ChessPiece.Type.ROOK), Generator.Castle(7, ChessPiece.Type.ROOK))

        override fun genMoves(game: ChessGame, origin: ChessPosition) = schemes.flatMap { it.genMoves(game, origin) }
    }

    object Queen : ChessMoveScheme() {
        private val schemes = (rotationsOf(1, 0) + rotationsOf(1, 1)).map { Generator.Direction(it) }

        override fun genMoves(game: ChessGame, origin: ChessPosition) = schemes.flatMap { it.genMoves(game, origin) }
    }

    object Rook : ChessMoveScheme() {
        private val schemes = rotationsOf(1, 0).map { Generator.Direction(it) }

        override fun genMoves(game: ChessGame, origin: ChessPosition) = schemes.flatMap { it.genMoves(game, origin) }
    }

    object Bishop : ChessMoveScheme() {
        private val schemes = rotationsOf(1, 1).map { Generator.Direction(it) }

        override fun genMoves(game: ChessGame, origin: ChessPosition) = schemes.flatMap { it.genMoves(game, origin) }
    }

    object Knight : ChessMoveScheme() {
        private val schemes = rotationsOf(2, 1).map { Generator.Jump(it) }

        override fun genMoves(game: ChessGame, origin: ChessPosition) = schemes.flatMap { it.genMoves(game, origin) }
    }

    object Pawn : ChessMoveScheme() {
        override fun genMoves(game: ChessGame, origin: ChessPosition): List<Move> {
            val ret = mutableListOf<Move>()
            val piece = game.board[origin]!!
            val dir = piece.side.direction
            ret.addAll(Generator.SingleMove(Pair(0, dir)).genMoves(game, origin))
            if (!piece.hasMoved)
                ret.addAll(Generator.DoubleMove(Pair(0, dir)).genMoves(game, origin))
            for (s in listOf(-1, 1)) {
                ret.addAll(Generator.DiagonalCapture(Pair(s, dir)).genMoves(game, origin))
                ret.addAll(Generator.EnPassant(dir, s).genMoves(game, origin))
            }

            return ret.map {
                if (it.target.rank in listOf(0, 7) && it.finish != DEFEND)
                    it.copy(color = Material.BLUE_CONCRETE, finish = PROMOTE)
                else
                    it
            }
        }
    }

    sealed class Generator(val canCapture: Boolean = true) : ChessMoveScheme() {

        fun makeMove(main: Move.Element, color: Material, game: ChessGame, blocks: List<ChessPosition> = emptyList(), cont: List<ChessPosition> = emptyList(), elements: List<Move.Element> = emptyList(), finish: Move.Finish = END_TURN) = Move(main.origin, main.target!!, color, this, game, blocks, cont, elements, finish)

        fun oneMove(main: Move.Element, color: Material, game: ChessGame, blocks: List<ChessPosition> = emptyList(), cont: List<ChessPosition> = emptyList(), elements: List<Move.Element> = emptyList(), finish: Move.Finish = END_TURN) = listOf(makeMove(main, color, game, blocks, cont, elements, finish))

        data class Direction(private val dir: Pair<Int, Int>) : Generator() {
            override fun genMoves(game: ChessGame, origin: ChessPosition): List<Move> {
                val ret = mutableListOf<Move>()
                val passed = mutableListOf<ChessPosition>()
                var o = origin + dir
                while (game.board.empty(o)) {
                    ret += makeMove(origin into o, Material.GREEN_CONCRETE, game, passed.toList())
                    passed += o
                    o += dir
                }
                var o2 = o + dir
                val cont = mutableListOf<ChessPosition>()
                while (game.board.empty(o2)) {
                    cont += o2
                    o2 += dir
                }
                cont += o2
                ret += if (game.board.sided(o, !game.board[origin]!!.side))
                    makeMove(origin into o, Material.RED_CONCRETE, game, passed, cont, listOf(o into null))
                else
                    makeMove(origin into o, Material.GRAY_CONCRETE, game, passed, cont, finish = DEFEND)
                return ret
            }

        }

        data class Jump(private val dir: Pair<Int, Int>) : Generator() {
            override fun genMoves(game: ChessGame, origin: ChessPosition): List<Move> {
                val target = origin + dir
                val side = game.board.sideOf(target)
                return if (target.isValid())
                    when (side) {
                        null -> oneMove(origin into target, Material.GREEN_CONCRETE, game)
                        game.board.sideOf(origin) -> oneMove(origin into target, Material.GRAY_CONCRETE, game, finish = DEFEND)
                        else -> oneMove(origin into target, Material.RED_CONCRETE, game, elements = listOf(target into null))
                    }
                else
                    emptyList()
            }

        }

        object Neighbours : Generator() {
            override fun genMoves(game: ChessGame, origin: ChessPosition): List<Move> {
                val originSide = game.board.sideOf(origin)
                return origin.neighbours().mapNotNull { target ->
                    val side = game.board.sideOf(target)
                    return@mapNotNull if (target.isValid())
                        when (side) {
                            null -> makeMove(origin into target, Material.GREEN_CONCRETE, game)
                            originSide -> makeMove(origin into target, Material.GRAY_CONCRETE, game, finish = DEFEND)
                            else -> makeMove(origin into target, Material.RED_CONCRETE, game, elements = listOf(target into null))
                        }
                    else
                        null
                }
            }

            override fun toString(): String {
                return "Neighbours"
            }
        }

        data class Castle(val file: Int, val partner: ChessPiece.Type) : Generator(false) {

            override fun genMoves(game: ChessGame, origin: ChessPosition): List<Move> {
                val piece = game.board[origin]!!
                if (piece.hasMoved) return emptyList()
                val rook = game.board[origin.copy(file = file)]
                if (rook == null || rook.hasMoved || rook.type != partner || rook.side != piece.side) return emptyList()
                for (c in between(file, origin.file))
                    if (!game.board.empty(origin.copy(file = c)))
                        return emptyList()
                val unsafe = game.board.getAttackedPositions(!piece.side)
                for (i in (0..2))
                    if (origin.copy(file = origin.file.towards(file, i)) in unsafe)
                        return emptyList()
                return oneMove(
                        origin into origin.copy(file = origin.file.towards(file, 2)),
                        Material.BLUE_CONCRETE, game, between(file, origin.file).map { ChessPosition(origin.rank, it) },
                        elements = listOf(rook.pos into origin.copy(file = origin.file.towards(file, 1))))
            }
        }

        data class SingleMove(private val dir: Pair<Int, Int>) : Generator(false) {
            override fun genMoves(game: ChessGame, origin: ChessPosition): List<Move> =
                    if (game.board.empty(origin + dir))
                        oneMove(origin by dir, Material.GREEN_CONCRETE, game)
                    else
                        emptyList()
        }

        data class DoubleMove(private val dir: Pair<Int, Int>) : Generator(false) {
            override fun genMoves(game: ChessGame, origin: ChessPosition): List<Move> =
                    if (game.board.empty(origin + dir) && game.board.empty(origin + (dir * 2)))
                        oneMove(origin by dir * 2, Material.GREEN_CONCRETE, game, listOf(origin + dir))
                    else
                        emptyList()
        }

        data class DiagonalCapture(private val dir: Pair<Int, Int>) : Generator() {
            override fun genMoves(game: ChessGame, origin: ChessPosition): List<Move> =
                    if (game.board.sided(origin + dir, !game.board.sideOf(origin)!!))
                        oneMove(origin by dir, Material.RED_CONCRETE, game, elements = listOf(origin + dir into null))
                    else
                        oneMove(origin by dir, Material.GRAY_CONCRETE, game, finish = DEFEND)
        }

        data class EnPassant(private val dir: Int, private val s: Int) : Generator() {
            override fun genMoves(game: ChessGame, origin: ChessPosition): List<Move> {
                val side = game.board.sideOf(origin)!!
                val lastOpponentMove = game[!side].lastMove ?: return emptyList()
                if (lastOpponentMove.scheme !is DoubleMove) return emptyList()
                if (lastOpponentMove.target != origin.plusF(s)) return emptyList()
                return oneMove(origin by Pair(s, dir), Material.RED_CONCRETE, game, elements = listOf(origin.plusF(s) into null))
            }
        }
    }

}

