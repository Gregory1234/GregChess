package gregc.gregchess.chess.component

import gregc.gregchess.chess.Side

interface ScoreboardManager : Component {
    operator fun plusAssign(p: GameProperty)
    operator fun plusAssign(p: PlayerProperty)
}

inline fun ScoreboardManager.game(name: String, crossinline block: () -> String) {
    this += object : GameProperty(name) {
        override fun invoke() = block()
    }
}

inline fun ScoreboardManager.player(name: String, crossinline block: (Side) -> String) {
    this += object : PlayerProperty(name) {
        override fun invoke(s: Side) = block(s)
    }
}

abstract class PlayerProperty(val name: String) {
    abstract operator fun invoke(s: Side): String
}

abstract class GameProperty(val name: String) {
    abstract operator fun invoke(): String
}