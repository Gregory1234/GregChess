package gregc.gregchess.match

import gregc.gregchess.Color
import gregc.gregchess.clock.clock
import gregc.gregchess.event.ChessEvent
import java.time.format.DateTimeFormatter

class PGN private constructor(private val tags: List<TagPair>, private val moves: MoveTree) {
    // TODO: add a way to order the tags better
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
                    append((index + indexShift).div(2) + 1, ". ")
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
        fun generate(match: ChessMatch): PGN {
            val tags = mutableListOf<TagPair>()
            // TODO: move most tag generation to Components
            tags += TagPair("Event", match.info.pgnEventName)
            tags += TagPair("Site", match.info.pgnSite)
            val date = DateTimeFormatter.ofPattern("uuuu.MM.dd").format(match.time.zonedStartTime)
            tags += TagPair("Date", date)
            tags += TagPair("Round", match.info.pgnRound.toString())
            tags += TagPair("White", match.sides.white.name)
            tags += TagPair("Black", match.sides.black.name)

            val result = match.results?.score?.pgn

            tags += TagPair("Result", result ?: "*")
            tags += TagPair("PlyCount", match.board.moveHistory.count { !it.isPhantomMove }.toString())
            val timeControl = match.clock?.timeControl?.getPGN() ?: "-"
            tags += TagPair("TimeControl", timeControl)
            val time = DateTimeFormatter.ofPattern("HH:mm:ss").format(match.time.zonedStartTime)
            tags += TagPair("Time", time)
            tags += TagPair("Termination", match.results?.endReason?.pgn ?: "unterminated")
            tags += TagPair("Mode", "ICS")

            match.variant.addPGNTags(match, GenerateEvent(tags))

            match.callEvent(GenerateEvent(tags))

            val tree = MoveTree(
                match.board.initialFEN.currentColor, match.board.initialFEN.fullmoveCounter,
                match.board.moveHistory.filter { !it.isPhantomMove }.map { match.variant.pgnMoveFormatter.format(it) }, result
            )

            return PGN(tags, tree)
        }
    }

}