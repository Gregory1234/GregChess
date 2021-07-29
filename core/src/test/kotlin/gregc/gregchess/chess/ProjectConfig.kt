package gregc.gregchess.chess

import io.kotest.core.config.AbstractProjectConfig

class ProjectConfig : AbstractProjectConfig() {
    override val parallelism: Int = 4
    override val concurrentSpecs: Int = 2
}