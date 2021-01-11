package gregc.gregchess.chess

import java.util.concurrent.TimeUnit

data class GameConfiguration(val initialTime: Long, val increment: Long, val relaxedInsufficientMaterial: Boolean) {
    companion object {
        val rapid10 = GameConfiguration(TimeUnit.MINUTES.toMillis(10), TimeUnit.SECONDS.toMillis(10), true)
    }
}
