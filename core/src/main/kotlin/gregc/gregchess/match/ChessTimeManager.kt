@file:UseSerializers(InstantSerializer::class)

package gregc.gregchess.match

import gregc.gregchess.OrderConstraint
import gregc.gregchess.component.*
import gregc.gregchess.event.ChessBaseEvent
import gregc.gregchess.event.EventListenerRegistry
import gregc.gregchess.utils.InstantSerializer
import gregc.gregchess.utils.between
import kotlinx.serialization.*
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlin.time.Duration

@Serializable
class ChessTimeManager(
    @SerialName("startTime") private var startTime_: Instant? = null,
    @SerialName("endTime") private var endTime_: Instant? = null,
    @SerialName("duration") private var durationCounted: Duration = Duration.ZERO
) : Component {
    override val type get() = ComponentType.TIME


    private fun Instant.zoned(match: ChessMatch) = atZone(match.environment.clock.zone)

    val startTime get() = startTime_

    private fun setStartTime(match: ChessMatch, v: Instant?) {
        check(match.state == ChessMatch.State.RUNNING) { "Start time set when not running: ${match.state}" }
        check(startTime_ == null) {
            val formatter = DateTimeFormatter.ofPattern("uuuu.MM.dd HH:mm:ss z")
            "Start time already set: ${formatter.format(startTime_?.zoned(match))}, ${formatter.format(v?.zoned(match))}"
        }
        startTime_ = v
    }

    fun getZonedStartTime(match: ChessMatch): ZonedDateTime? = startTime?.zoned(match)

    val endTime get() = endTime_

    private fun setEndTime(match: ChessMatch, v: Instant?) {
        check(match.state == ChessMatch.State.STOPPED) { "End time set when not stopped: ${match.state}" }
        check(endTime_ == null) {
            val formatter = DateTimeFormatter.ofPattern("uuuu.MM.dd HH:mm:ss z")
            "End time already set: ${formatter.format(endTime_?.zoned(match))}, ${formatter.format(v?.zoned(match))}"
        }
        endTime_ = v
    }

    fun getZonedEndTime(match: ChessMatch): ZonedDateTime? = endTime?.zoned(match)

    @Transient
    private lateinit var durationTimeStart: Instant

    override fun init(match: ChessMatch, events: EventListenerRegistry) {
        durationTimeStart = match.environment.clock.instant()
        require((match.state >= ChessMatch.State.RUNNING) == (startTime != null)) { "Start time bad" }
        require((match.state >= ChessMatch.State.STOPPED) == (endTime != null)) { "End time bad" }
        events.registerE(ChessBaseEvent.START) {
            durationTimeStart = match.environment.clock.instant()
        }
        events.registerE(ChessBaseEvent.RUNNING, OrderConstraint(runBeforeAll = true)) {
            setStartTime(match, match.environment.clock.instant())
        }
        events.registerE(ChessBaseEvent.UPDATE) {
            updateDuration(match)
        }
        events.registerE(ChessBaseEvent.STOP, OrderConstraint(runBeforeAll = true)) {
            setEndTime(match, match.environment.clock.instant())
        }
    }

    fun getDuration(match: ChessMatch) = durationCounted + Duration.between(durationTimeStart, match.environment.clock.instant())

    private fun updateDuration(match: ChessMatch) {
        val now = match.environment.clock.instant()
        durationCounted += Duration.between(durationTimeStart, now)
        durationTimeStart = now
    }

    fun getFacade(match: ChessMatch) = ChessTimeManagerFacade(match, this)
}

class ChessTimeManagerFacade(match: ChessMatch, component: ChessTimeManager) : ComponentFacade<ChessTimeManager>(match, component) {
    val startTime get() = component.startTime
    val zonedStartTime get() = component.getZonedStartTime(match)
    val endTime get() = component.endTime
    val zonedEndTime get() = component.getZonedEndTime(match)
    val duration get() = component.getDuration(match)
}