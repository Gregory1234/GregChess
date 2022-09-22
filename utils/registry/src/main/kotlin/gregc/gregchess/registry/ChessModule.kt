package gregc.gregchess.registry

import org.slf4j.Logger
import org.slf4j.LoggerFactory

abstract class ChessModule(val name: String, val namespace: String) {
    val logger: Logger = LoggerFactory.getLogger(name)
    private var lockedBefore = true
    private var lockedAfter = false
    val locked: Boolean get() = lockedBefore || lockedAfter

    operator fun <K, T, V : T> Registry<K, T, *>.set(key: K, v: V) = set(this@ChessModule, key, v)

    protected abstract fun load()
    protected abstract fun postLoad()
    protected abstract fun validate()
    protected abstract fun finish()
    fun fullLoad() {
        require(lockedBefore && !lockedAfter) { "Module $this was already loaded!" }
        lockedBefore = false
        load()
        postLoad()
        logger.info("Loaded chess module $this")
        lockedAfter = true
        validate()
        logger.info("Validated chess module $this")
        finish()
    }

    final override fun toString() = "$namespace@${hashCode().toString(16)}"
}