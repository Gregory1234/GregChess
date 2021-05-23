package gregc.gregchess

import org.bukkit.Material
import org.bukkit.World

data class FillVolume(val world: World, val mat: Material, val start: Loc, val stop: Loc, private var last: Loc? = start): Iterator<Loc> {
    constructor(world: World, mat: Material, loc: Loc): this(world, mat, loc, loc)

    override fun hasNext() = last != null

    override fun next(): Loc {
        val ret = last ?: stop
        last = when {
            ret.x != stop.x -> ret.copy(x = ret.x + 1)
            ret.z != stop.z -> ret.copy(x=start.x, z = ret.z + 1)
            ret.y != stop.y -> ret.copy(x=start.x, z = start.z, y = ret.y + 1)
            else -> null
        }
        return ret
    }
}

private val fillQueue = ArrayDeque<FillVolume>()

fun fill(vol: FillVolume) {
    fillQueue += vol
}

private const val MAX_FILL = 10000

fun startFill() {
    TimeManager.runTaskTimer(0.ticks, 1.ticks) {
        var done = 0
        var vol = fillQueue.firstOrNull()
        while (vol != null && done < MAX_FILL) {
            while(vol.hasNext() && done < MAX_FILL) {
                val next = vol.next()
                vol.world.getBlockAt(next).type = vol.mat
                done++
            }
            if (!vol.hasNext())
                fillQueue.removeFirst()
            vol = fillQueue.firstOrNull()
        }
    }
}