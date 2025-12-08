package gregc.gregchess.match

import gregc.gregchess.OrderConstraint
import gregc.gregchess.component.*
import gregc.gregchess.event.ChessBaseEvent
import gregc.gregchess.event.EventListenerRegistry
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.format
import kotlinx.datetime.format.char
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.*
import kotlin.time.Duration
import kotlin.time.Instant


private val DATETIME_FORMAT = LocalDateTime.Format {
    date(LocalDate.Formats.ISO)
    char(' ')
    hour(); char(':'); minute(); char(':'); second()
}

@Serializable
class ChessTimeManager(
    @SerialName("startTime") private var startTime_: Instant? = null,
    @SerialName("endTime") private var endTime_: Instant? = null,
    @SerialName("duration") private var durationCounted: Duration = Duration.ZERO
) : Component {
    override val type get() = ComponentType.TIME


    private fun Instant.local(match: ChessMatch) = toLocalDateTime(match.environment.zone)

    val startTime get() = startTime_

    private fun setStartTime(match: ChessMatch, v: Instant?) {
        check(match.state == ChessMatch.State.RUNNING) { "Start time set when not running: ${match.state}" }
        check(startTime_ == null) {
            "Start time already set: ${startTime_?.local(match)?.format(DATETIME_FORMAT)}, ${v?.local(match)?.format(DATETIME_FORMAT)}"
        }
        startTime_ = v
    }

    fun getLocalStartTime(match: ChessMatch): LocalDateTime? = startTime?.local(match)

    val endTime get() = endTime_

    private fun setEndTime(match: ChessMatch, v: Instant?) {
        check(match.state == ChessMatch.State.STOPPED) { "End time set when not stopped: ${match.state}" }
        check(endTime_ == null) {
            "End time already set: ${endTime_?.local(match)?.format(DATETIME_FORMAT)}, ${v?.local(match)?.format(DATETIME_FORMAT)}"
        }
        endTime_ = v
    }

    fun getLocalEndTime(match: ChessMatch): LocalDateTime? = endTime?.local(match)

    @Transient
    private lateinit var durationTimeStart: Instant

    override fun init(match: ChessMatch, events: EventListenerRegistry) {
        durationTimeStart = match.environment.clock.now()
        require((match.state >= ChessMatch.State.RUNNING) == (startTime != null)) { "Start time bad" }
        require((match.state >= ChessMatch.State.STOPPED) == (endTime != null)) { "End time bad" }
        events.registerE(ChessBaseEvent.START) {
            durationTimeStart = match.environment.clock.now()
        }
        events.registerE(ChessBaseEvent.RUNNING, OrderConstraint(runBeforeAll = true)) {
            setStartTime(match, match.environment.clock.now())
        }
        events.registerE(ChessBaseEvent.UPDATE) {
            updateDuration(match)
        }
        events.registerE(ChessBaseEvent.STOP, OrderConstraint(runBeforeAll = true)) {
            setEndTime(match, match.environment.clock.now())
        }
    }

    fun getDuration(match: ChessMatch) = durationCounted + (durationTimeStart - match.environment.clock.now())

    private fun updateDuration(match: ChessMatch) {
        val now = match.environment.clock.now()
        durationCounted += durationTimeStart - now
        durationTimeStart = now
    }

    fun getFacade(match: ChessMatch) = ChessTimeManagerFacade(match, this)
}

class ChessTimeManagerFacade(match: ChessMatch, component: ChessTimeManager) : ComponentFacade<ChessTimeManager>(match, component) {
    val startTime get() = component.startTime
    val localStartTime get() = component.getLocalStartTime(match)
    val endTime get() = component.endTime
    val localEndTime get() = component.getLocalEndTime(match)
    val duration get() = component.getDuration(match)
}