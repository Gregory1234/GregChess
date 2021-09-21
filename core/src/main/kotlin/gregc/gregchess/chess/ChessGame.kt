package gregc.gregchess.chess

import gregc.gregchess.chess.component.*
import gregc.gregchess.chess.variant.ChessVariant
import kotlinx.serialization.*
import kotlinx.serialization.builtins.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.reflect.KClass
import kotlin.reflect.safeCast


class GameSettings(
    val name: String,
    val simpleCastling: Boolean,
    val variant: ChessVariant,
    val components: Collection<ComponentData<*>>
) {
    inline fun <reified T : ComponentData<*>> getComponent(): T? = components.filterIsInstance<T>().firstOrNull()
}

enum class TurnEvent(val ending: Boolean) : ChessEvent {
    START(false), END(true), UNDO(true)
}

enum class GameBaseEvent : ChessEvent {
    START,
    RUNNING,
    UPDATE,
    STOP,
    PANIC
}

@Serializable(with = ChessGame.Serializer::class)
class ChessGame private constructor(
    val settings: GameSettings,
    val playerInfo: ByColor<ChessPlayerInfo>,
    val uuid: UUID,
    initialState: State,
    startTime: LocalDateTime?,
    results: GameResults?
) : ChessEventCaller {
    constructor(settings: GameSettings, playerInfo: ByColor<ChessPlayerInfo>)
            : this(settings, playerInfo, UUID.randomUUID(), State.INITIAL, null, null)

    @OptIn(ExperimentalSerializationApi::class, InternalSerializationApi::class)
    object Serializer : KSerializer<ChessGame> {
        override val descriptor = buildClassSerialDescriptor("ChessGame") {
            element("uuid", buildSerialDescriptor("ChessGameUUID", SerialKind.CONTEXTUAL))
            element("players", ByColor.serializer(ChessPlayerInfoSerializer).descriptor)
            element<String>("preset")
            element<ChessVariant>("variant")
            element<Boolean>("simpleCastling")
            element<State>("state")
            element<String?>("startTime")
            element("results", GameResultsSerializer.descriptor.nullable)
            element("components", ListSerializer(ComponentDataSerializer).descriptor)
        }

        override fun serialize(encoder: Encoder, value: ChessGame) = encoder.encodeStructure(descriptor) {
            encodeSerializableElement(descriptor, 0, encoder.serializersModule.serializer(), value.uuid)
            encodeSerializableElement(descriptor, 1, ByColor.serializer(ChessPlayerInfoSerializer), value.playerInfo)
            encodeStringElement(descriptor, 2, value.settings.name)
            encodeSerializableElement(descriptor, 3, ChessVariant.serializer(), value.variant)
            encodeBooleanElement(descriptor, 4, value.settings.simpleCastling)
            encodeSerializableElement(descriptor, 5, State.serializer(), value.state)
            encodeNullableSerializableElement(descriptor, 6, String.serializer().nullable, value.startTime?.toString())
            encodeNullableSerializableElement(descriptor, 7, GameResultsSerializer.nullable, value.results)
            encodeSerializableElement(descriptor, 8, ListSerializer(ComponentDataSerializer), value.componentData)
        }

        override fun deserialize(decoder: Decoder): ChessGame = decoder.decodeStructure(descriptor) {
            var uuid: UUID? = null
            var players: ByColor<ChessPlayerInfo>? = null
            var preset: String? = null
            var variant: ChessVariant? = null
            var simpleCastling: Boolean? = null
            var state: State? = null
            var startTime: LocalDateTime? = null
            var results: GameResults? = null
            var components: List<ComponentData<*>>? = null
            if (decodeSequentially()) { // sequential decoding protocol
                uuid = decodeSerializableElement(descriptor, 0, decoder.serializersModule.serializer())
                players = decodeSerializableElement(descriptor, 1, ByColor.serializer(ChessPlayerInfoSerializer))
                preset = decodeStringElement(descriptor, 2)
                variant = decodeSerializableElement(descriptor, 3, ChessVariant.serializer())
                simpleCastling = decodeBooleanElement(descriptor, 4)
                state = decodeSerializableElement(descriptor, 5, State.serializer())
                startTime = decodeNullableSerializableElement(descriptor, 6, String.serializer().nullable)?.let { LocalDateTime.parse(it) }
                results = decodeNullableSerializableElement(descriptor, 7, GameResultsSerializer.nullable)
                components = decodeSerializableElement(descriptor, 8, ListSerializer(ComponentDataSerializer))
            } else {
                while (true) {
                    when (val index = decodeElementIndex(descriptor)) {
                        0 -> uuid = decodeSerializableElement(descriptor, index, decoder.serializersModule.serializer())
                        1 -> players =
                            decodeSerializableElement(descriptor, index, ByColor.serializer(ChessPlayerInfoSerializer))
                        2 -> preset = decodeStringElement(descriptor, index)
                        3 -> variant = decodeSerializableElement(descriptor, index, ChessVariant.serializer())
                        4 -> simpleCastling = decodeBooleanElement(descriptor, index)
                        5 -> state = decodeSerializableElement(descriptor, index, State.serializer())
                        6 -> startTime = decodeNullableSerializableElement(descriptor, 6, String.serializer().nullable)?.let { LocalDateTime.parse(it) }
                        7 -> results = decodeNullableSerializableElement(descriptor, 7, GameResultsSerializer.nullable)
                        8 -> components =
                            decodeSerializableElement(descriptor, index, ListSerializer(ComponentDataSerializer))
                        CompositeDecoder.DECODE_DONE -> break
                        else -> error("Unexpected index: $index")
                    }
                }
            }
            ChessGame(
                GameSettings(preset!!, simpleCastling!!, variant!!, components!!),
                players!!, uuid!!, state!!, startTime, results
            )
        }
    }

    override fun toString() = "ChessGame(uuid=$uuid)"

    val variant = settings.variant

    val components = settings.components.map { it.getComponent(this) }

    val componentData get() = components.map { it.data }

    init {
        try {
            requireComponent<Chessboard>()
            for (it in variant.requiredComponents) {
                components.filterIsInstance(it.java).firstOrNull() ?: throw ComponentNotFoundException(it)
            }
            components.forEach { it.validate() }
        } catch (e: Exception) {
            panic(e)
            throw e
        }
    }

    val board get() = requireComponent<Chessboard>()

    val clock get() = getComponent<ChessClock>()

    fun <T : Component> getComponent(cl: KClass<T>): T? =
        components.mapNotNull { cl.safeCast(it) }.firstOrNull()

    fun <T : Component> requireComponent(cl: KClass<T>): T = getComponent(cl) ?: throw ComponentNotFoundException(cl)

    inline fun <reified T : Component> getComponent(): T? = getComponent(T::class)

    inline fun <reified T : Component> requireComponent(): T = requireComponent(T::class)

    val players = byColor { playerInfo[it].getPlayer(it, this) }

    private fun requireState(s: State) = check(state == s)

    override fun callEvent(e: ChessEvent) = components.forEach { it.handleEvent(e) }

    var currentTurn: Color = board.initialFEN.currentTurn

    val currentPlayer: ChessPlayer get() = players[currentTurn]

    val currentOpponent: ChessPlayer get() = players[!currentTurn]

    var startTime: LocalDateTime? = startTime
        private set(v) {
            check(state == State.RUNNING) { "Start time set when not running: $state" }
            check(field == null) {
                val formatter = DateTimeFormatter.ofPattern("uuuu.MM.dd HH:mm:ss")
                "Start time already set: ${formatter.format(field)}, ${formatter.format(v)}"
            }
            field = v
        }

    @Serializable
    private enum class State {
        INITIAL, RUNNING, STOPPED, ERROR
    }

    private var state: State = initialState
        set(v) {
            check(v > field) { "Changed state backwards: from $field to $v" }
            field = v
        }

    val running get() = state == State.RUNNING

    fun nextTurn() {
        requireState(State.RUNNING)
        callEvent(TurnEvent.END)
        variant.checkForGameEnd(this)
        if (running) {
            currentTurn++
            startTurn()
        }
    }

    fun previousTurn() {
        requireState(State.RUNNING)
        callEvent(TurnEvent.UNDO)
        currentTurn++
        startPreviousTurn()
    }

    fun start(): ChessGame {
        requireState(State.INITIAL)
        callEvent(GameBaseEvent.START)
        state = State.RUNNING
        startTime = LocalDateTime.now()
        callEvent(GameBaseEvent.RUNNING)
        startTurn()
        return this
    }

    fun update() {
        requireState(State.RUNNING)
        callEvent(GameBaseEvent.UPDATE)
    }

    private fun startTurn() {
        requireState(State.RUNNING)
        callEvent(TurnEvent.START)
        currentPlayer.startTurn()
    }

    private fun startPreviousTurn() {
        requireState(State.RUNNING)
        callEvent(TurnEvent.START)
        currentPlayer.startTurn()
    }

    var results: GameResults? = results
        private set(v) {
            check(state >= State.STOPPED) { "Results set when not stopped: $state" }
            check(field == null) { "Results already set: $field, $v" }
            field = v
        }

    fun stop(results: GameResults) {
        requireState(State.RUNNING)
        state = State.STOPPED
        this.results = results
        callEvent(GameBaseEvent.STOP)
    }

    private fun panic(e: Exception) {
        e.printStackTrace()
        callEvent(GameBaseEvent.PANIC)
        state = State.ERROR
        results = drawBy(EndReason.ERROR, e.toString())
    }

    fun <E> tryOrStopNull(expr: E?): E = try {
        expr!!
    } catch (e: NullPointerException) {
        panic(e)
        throw e
    }

    operator fun get(color: Color): ChessPlayer = players[color]

    fun finishMove(move: Move) {
        requireState(State.RUNNING)
        move.execute(this)
        board.lastMove?.hideDone(board)
        board.lastMove = move
        board.lastMove?.showDone(board)
        nextTurn()
    }

}