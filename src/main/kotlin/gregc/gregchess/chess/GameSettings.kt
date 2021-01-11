package gregc.gregchess.chess

import java.util.concurrent.TimeUnit

data class GameSettings(val initialTime: Long, val increment: Long, val relaxedInsufficientMaterial: Boolean) {
    companion object {
        val rapid10 = GameSettings(TimeUnit.MINUTES.toMillis(10), TimeUnit.SECONDS.toMillis(10), true)
    }
}
