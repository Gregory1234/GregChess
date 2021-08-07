package gregc.gregchess.chess.variant

import gregc.gregchess.GregChessModule
import gregc.gregchess.chess.*
import gregc.gregchess.chess.component.*
import gregc.gregchess.register
import kotlin.reflect.KClass

object ThreeChecks : ChessVariant("THREE_CHECKS") {

    class CheckCounter(private val game: ChessGame, private val limit: UInt) : Component {
        class Settings(private val limit: UInt) : Component.Settings<CheckCounter> {
            override fun getComponent(game: ChessGame) = CheckCounter(game, limit)
        }

        private val checks = mutableBySides(0u)

        @ChessEventHandler
        fun addProperties(e: AddPropertiesEvent) {
            e.player(CHECK_COUNTER) { checks[it] }
        }

        @ChessEventHandler
        fun endTurn(e: TurnEvent) {
            if (e == TurnEvent.END)
                if (game.variant.isInCheck(game, !game.currentTurn))
                    checks[!game.currentTurn]++
        }

        fun checkForGameEnd() {
            for ((s, c) in checks.toIndexedList())
                if (c >= limit)
                    game.stop(s.lostBy(CHECK_LIMIT, limit))
        }
    }

    @JvmField
    val CHECK_COUNTER = GregChessModule.register(PropertyType<UInt>("CHECK_COUNTER"))
    @JvmField
    val CHECK_LIMIT = GregChessModule.register(DetEndReason("CHECK_LIMIT", EndReason.Type.NORMAL))

    override fun start(game: ChessGame) {
        game.requireComponent<CheckCounter>()
    }

    override fun checkForGameEnd(game: ChessGame) {
        game.requireComponent<CheckCounter>().checkForGameEnd()
        Normal.checkForGameEnd(game)
    }

    override val requiredComponents: Collection<KClass<out Component.Settings<*>>>
        get() = listOf(CheckCounter.Settings::class)

}