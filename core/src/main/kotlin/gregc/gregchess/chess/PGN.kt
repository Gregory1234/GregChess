package gregc.gregchess.chess

import gregc.gregchess.chess.component.clock
import gregc.gregchess.chess.variant.ChessVariant
import gregc.gregchess.registry.name
import gregc.gregchess.util.snakeToPascal
import java.time.format.DateTimeFormatter

class PGN private constructor(private val tags: List<TagPair>, private val moves: MoveTree) {
    class GenerateEvent internal constructor(private val tags: MutableList<TagPair>) : ChessEvent {
        operator fun set(name: String, value: String) {
            require(tags.none { it.name == name }) { "Tag already used: $name" }
            tags += TagPair(name, value)
        }
    }

    internal class TagPair(val name: String, val value: String) {
        override fun toString() = "[$name \"$value\"]\n"
    }

    private class MoveTree(
        private val initial: Color,
        private val initialMove: Int,
        private val moves: List<String>,
        private val result: String?
    ) {
        override fun toString() = buildString {
            val indexShift = if (initial == Color.WHITE) (initialMove * 2 - 2) else (initialMove * 2 - 1)
            if (initial == Color.BLACK) {
                append(initialMove, ". ")
            }
            for ((index, moveData) in moves.withIndex()) {
                if (index % 2 == 0)
                    append((index.toInt() + indexShift).div(2) + 1, ". ")
                append(moveData, " ")
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
            tags += TagPair("Event", "Casual game") // TODO: add events
            tags += TagPair("Site", game.environment.pgnSite)
            val date = DateTimeFormatter.ofPattern("uuuu.MM.dd").format(game.zonedStartTime)
            tags += TagPair("Date", date)
            tags += TagPair("Round", "1") // TODO: add rematches
            tags += TagPair("White", game[Color.WHITE].name)
            tags += TagPair("Black", game[Color.BLACK].name)

            val result = game.results?.score?.pgn

            tags += TagPair("Result", result ?: "*")
            tags += TagPair("PlyCount", game.board.moveHistory.count { !it.isPhantomMove }.toString())
            val timeControl = game.clock?.timeControl?.getPGN() ?: "-"
            tags += TagPair("TimeControl", timeControl)
            val time = DateTimeFormatter.ofPattern("HH:mm:ss").format(game.zonedStartTime)
            tags += TagPair("Time", time)
            tags += TagPair("Termination", game.results?.endReason?.pgn ?: "unterminated")
            tags += TagPair("Mode", "ICS")

            if (!game.board.initialFEN.isInitial() || game.board.chess960) {
                tags += TagPair("SetUp", "1")
                tags += TagPair("FEN", game.board.initialFEN.toString())
            }
            val variant = buildList {
                if (game.variant != ChessVariant.Normal)
                    add(game.variant.name.snakeToPascal())
                if (game.board.chess960)
                    add("Chess960")
                addAll(game.board.getVariantOptionStrings())
            }.joinToString(" ")

            if (variant.isNotBlank())
                tags += TagPair("Variant", variant)

            game.callEvent(GenerateEvent(tags))

            val tree = MoveTree(
                game.board.initialFEN.currentTurn, game.board.initialFEN.fullmoveCounter,
                game.board.moveHistory.filter { !it.isPhantomMove }.map { game.variant.pgnMoveFormatter.format(it) }, result
            )

            return PGN(tags, tree)
        }
    }

}