package gregc.gregchess.chess

import gregc.gregchess.chess.component.ChessClock
import gregc.gregchess.chess.variant.ChessVariant
import gregc.gregchess.snakeToPascal
import java.time.format.DateTimeFormatter

class PGN internal constructor(private val tags: List<TagPair>, private val moves: MoveTree) {
    class TagPair internal constructor(val name: String, val value: String) {
        override fun toString() = "[$name \"$value\"]\n"
    }

    class MoveTree internal constructor(
        private val initial: Side,
        private val initialMove: UInt,
        private val moves: List<MoveData>,
        private val result: String?
    ) {
        override fun toString() = buildString {
            val indexShift = if (initial == white) (initialMove * 2u - 2u) else (initialMove * 2u - 1u)
            if (initial == black) {
                append(initialMove, ". ")
            }
            for ((index, moveData) in moves.withIndex()) {
                if (index % 2 == 0)
                    append((index.toUInt() + indexShift).div(2u) + 1u, ". ")
                append(moveData.name.pgn, " ")
                if (index % 2 == 1)
                    append("\n")
            }
            if (result != null)
                append(result)
        }
    }

    override fun toString() = buildString {
        tags.forEach(::append)
        append("\n\n", moves)
    }

    operator fun get(name: String) = tags.firstOrNull { it.name == name }?.value

    companion object {
        fun generate(game: ChessGame): PGN {
            val tags = mutableListOf<TagPair>()
            tags += TagPair("Event", "Casual game")
            tags += TagPair("Site", "GregChess plugin")
            val date = DateTimeFormatter.ofPattern("uuuu.MM.dd").format(game.startTime)
            tags += TagPair("Date", date)
            tags += TagPair("Round", "1")
            tags += TagPair("White", game[white].name)
            tags += TagPair("Black", game[black].name)

            val result = game.results?.score?.pgn

            tags += TagPair("Result", result ?: "*")
            tags += TagPair("PlyCount", game.board.moveHistory.size.toString())
            val timeControl = game.settings.getComponent<ChessClock.Settings>()?.getPGN() ?: "-"
            tags += TagPair("TimeControl", timeControl)
            val time = DateTimeFormatter.ofPattern("HH:mm:ss").format(game.startTime)
            tags += TagPair("Time", time)
            tags += TagPair("Termination", game.results?.endReason?.pgn ?: "unterminated")
            tags += TagPair("Mode", "ICS")

            if (!game.board.initialFEN.isInitial() || game.board.chess960) {
                tags += TagPair("SetUp", "1")
                tags += TagPair("FEN", game.board.initialFEN.toString())
            }
            val variant = buildList {
                if (game.variant != ChessVariant.Normal)
                    this += game.variant.name.snakeToPascal()
                if (game.board.chess960)
                    this += "Chess960"
                if (game.settings.simpleCastling)
                    this += "SimpleCastling"
            }.joinToString(" ")

            if (variant.isNotBlank())
                tags += TagPair("Variant", variant)

            val tree = MoveTree(
                game.board.initialFEN.currentTurn, game.board.initialFEN.fullmoveClock,
                game.board.moveHistory.filter { it.name.isNotEmpty() }, result
            )

            return PGN(tags, tree)
        }
    }

}