package gregc.gregchess.chess.component

import gregc.gregchess.DEFAULT_LANG
import gregc.gregchess.LocalizedString
import gregc.gregchess.chess.ComponentsConfig
import gregc.gregchess.chess.Side

interface ScoreboardManager : Component {
    operator fun plusAssign(p: GameProperty)
    operator fun plusAssign(p: PlayerProperty)
}

val ComponentsConfig.scoreboard by ComponentsConfig

inline fun ScoreboardManager.game(name: LocalizedString, crossinline block: () -> String) {
    this += object : GameProperty(name.get(DEFAULT_LANG)) {
        override fun invoke() = block()
    }
}

inline fun ScoreboardManager.player(name: LocalizedString, crossinline block: (Side) -> String) {
    this += object : PlayerProperty(name.get(DEFAULT_LANG)) {
        override fun invoke(s: Side) = block(s)
    }
}

abstract class PlayerProperty(val name: String) {
    abstract operator fun invoke(s: Side): String
}

abstract class GameProperty(val name: String) {
    abstract operator fun invoke(): String
}