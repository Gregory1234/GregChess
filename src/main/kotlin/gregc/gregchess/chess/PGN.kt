package gregc.gregchess.chess

import gregc.gregchess.chess.component.ChessClock
import java.time.format.DateTimeFormatter

class PGN internal constructor(private val tags: List<TagPair>, private val moves: MoveTree) {
    class TagPair internal constructor(val name: String, val value: String) {
        override fun toString() = "[$name \"$value\"]\n"
    }

    class MoveTree internal constructor(
        private val initial: ChessSide,
        private val initialMove: Int,
        private val moves: List<MoveData>,
        private val result: String?
    ) {
        override fun toString() = buildString {
            val indexShift =
                if (initial == ChessSide.WHITE) (initialMove * 2 - 2) else (initialMove * 2 - 1)
            if (initial == ChessSide.BLACK) {
                append(initialMove, ". ")
            }
            moves.forEachIndexed { index, moveData ->
                if (index % 2 == 0)
                    append((index + indexShift).div(2) + 1, ". ")
                append(moveData.standardName, " ")
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
            tags += TagPair("White", game[ChessSide.WHITE].name)
            tags += TagPair("Black", game[ChessSide.BLACK].name)

            val result = game.endReason?.let {
                when (it.winner) {
                    ChessSide.WHITE -> "1-0"
                    ChessSide.BLACK -> "0-1"
                    null -> "1/2-1/2"
                }
            }

            tags += TagPair("Result", result ?: "*")
            tags += TagPair("PlyCount", game.board.moveHistory.size.toString())
            val timeControl = game.getComponent(ChessClock::class)?.settings?.getPGN() ?: "-"
            tags += TagPair("TimeControl", timeControl)
            val time = DateTimeFormatter.ofPattern("HH:mm:ss").format(game.startTime)
            tags += TagPair("Time", time)
            tags += TagPair("Termination", game.endReason?.reasonPGN ?: "unterminated")
            tags += TagPair("Mode", "ICS")

            if (!game.board.initialFEN.isInitial()) {
                tags += TagPair("SetUp", "1")
                tags += TagPair("FEN", game.board.initialFEN.toString())
            }
            if (game.board.settings.chess960) {
                tags += TagPair("Variant", "Chess960")
            }

            return PGN(
                tags,
                MoveTree(
                    game.board.initialFEN.currentTurn,
                    game.board.initialFEN.fullmoveClock,
                    game.board.moveHistory.filter { it.standardName != "" },
                    result
                )
            )
        }
    }

}