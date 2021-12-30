package gregc.gregchess.chess

import gregc.gregchess.MultiExceptionContext
import gregc.gregchess.chess.component.*
import gregc.gregchess.chess.move.Move
import gregc.gregchess.chess.player.*
import gregc.gregchess.chess.variant.ChessVariant
import kotlinx.coroutines.*
import kotlinx.serialization.*
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer
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
    SYNC,
    RUNNING,
    UPDATE,
    STOP,
    PANIC
}

class ChessEventException(val event: ChessEvent, cause: Throwable? = null) : RuntimeException(event.toString(), cause)

@Serializable(with = ChessGame.Serializer::class)
class ChessGame private constructor(
    val environment: ChessEnvironment,
    val settings: GameSettings,
    val playerData: ByColor<ChessPlayer>,
    val uuid: UUID,
    initialState: State,
    startTime: LocalDateTime?,
    results: GameResults?
) : ChessEventCaller {
    constructor(environment: ChessEnvironment, settings: GameSettings, playerInfo: ByColor<ChessPlayer>)
            : this(environment, settings, playerInfo, UUID.randomUUID(), State.INITIAL, null, null)

    @OptIn(ExperimentalSerializationApi::class, InternalSerializationApi::class)
    object Serializer : KSerializer<ChessGame> {
        override val descriptor = buildClassSerialDescriptor("ChessGame") {
            element("uuid", buildSerialDescriptor("ChessGameUUID", SerialKind.CONTEXTUAL))
            element("players", ByColor.serializer(ChessPlayerSerializer).descriptor)
            element<String>("preset")
            element<ChessVariant>("variant")
            element<Boolean>("simpleCastling")
            element<State>("state")
            element<String?>("startTime")
            element("results", GameResultsSerializer.descriptor.nullable)
            element("components", ComponentDataMapSerializer.descriptor)
            element("environment", buildSerialDescriptor("ChessEnvironment", SerialKind.CONTEXTUAL))
        }

        override fun serialize(encoder: Encoder, value: ChessGame) = encoder.encodeStructure(descriptor) {
            encodeSerializableElement(descriptor, 0, encoder.serializersModule.serializer(), value.uuid)
            encodeSerializableElement(descriptor, 1, ByColor.serializer(ChessPlayerSerializer), value.playerData)
            encodeStringElement(descriptor, 2, value.settings.name)
            encodeSerializableElement(descriptor, 3, ChessVariant.serializer(), value.variant)
            encodeBooleanElement(descriptor, 4, value.settings.simpleCastling)
            encodeSerializableElement(descriptor, 5, encoder.serializersModule.serializer(), value.state)
            encodeNullableSerializableElement(descriptor, 6, String.serializer().nullable, value.startTime?.toString())
            encodeNullableSerializableElement(descriptor, 7, GameResultsSerializer.nullable, value.results)
            encodeSerializableElement(descriptor, 8, ComponentDataMapSerializer, value.componentData.associateBy { it.componentClass.componentKey })
            encodeSerializableElement(descriptor, 9, encoder.serializersModule.getContextual(ChessEnvironment::class)!!, value.environment)
        }

        override fun deserialize(decoder: Decoder): ChessGame = decoder.decodeStructure(descriptor) {
            var uuid: UUID? = null
            var players: ByColor<ChessPlayer>? = null
            var preset: String? = null
            var variant: ChessVariant? = null
            var simpleCastling: Boolean? = null
            var state: State? = null
            var startTime: LocalDateTime? = null
            var results: GameResults? = null
            var components: List<ComponentData<*>>? = null
            var environment: ChessEnvironment? = null
            if (decodeSequentially()) { // sequential decoding protocol
                uuid = decodeSerializableElement(descriptor, 0, decoder.serializersModule.serializer())
                players = decodeSerializableElement(descriptor, 1, ByColor.serializer(ChessPlayerSerializer))
                preset = decodeStringElement(descriptor, 2)
                variant = decodeSerializableElement(descriptor, 3, ChessVariant.serializer())
                simpleCastling = decodeBooleanElement(descriptor, 4)
                state = decodeSerializableElement(descriptor, 5, decoder.serializersModule.serializer())
                startTime = decodeNullableSerializableElement(descriptor, 6, String.serializer().nullable)?.let { LocalDateTime.parse(it) }
                results = decodeNullableSerializableElement(descriptor, 7, GameResultsSerializer.nullable)
                components = decodeSerializableElement(descriptor, 8, ComponentDataMapSerializer).values.toList()
                environment = decodeSerializableElement(descriptor, 9, decoder.serializersModule.getContextual(ChessEnvironment::class)!!)
            } else {
                while (true) {
                    when (val index = decodeElementIndex(descriptor)) {
                        0 -> uuid = decodeSerializableElement(descriptor, index, decoder.serializersModule.serializer())
                        1 -> players =
                            decodeSerializableElement(descriptor, index, ByColor.serializer(ChessPlayerSerializer))
                        2 -> preset = decodeStringElement(descriptor, index)
                        3 -> variant = decodeSerializableElement(descriptor, index, ChessVariant.serializer())
                        4 -> simpleCastling = decodeBooleanElement(descriptor, index)
                        5 -> state = decodeSerializableElement(descriptor, index, decoder.serializersModule.serializer())
                        6 -> startTime = decodeNullableSerializableElement(descriptor, 6, String.serializer().nullable)?.let { LocalDateTime.parse(it) }
                        7 -> results = decodeNullableSerializableElement(descriptor, 7, GameResultsSerializer.nullable)
                        8 -> components =
                            decodeSerializableElement(descriptor, index, ComponentDataMapSerializer).values.toList()
                        9 -> environment =
                            decodeSerializableElement(descriptor, index, decoder.serializersModule.getContextual(ChessEnvironment::class)!!)
                        CompositeDecoder.DECODE_DONE -> break
                        else -> error("Unexpected index: $index")
                    }
                }
            }
            ChessGame(
                environment!!, GameSettings(preset!!, simpleCastling!!, variant!!, components!!),
                players!!, uuid!!, state!!, startTime, results
            )
        }
    }

    override fun toString() = "ChessGame(uuid=$uuid)"

    val variant = settings.variant

    val components = settings.components.map { it.getComponent(this) }

    val componentData get() = components.map { it.data }

    init {
        require((initialState >= State.RUNNING) == (startTime != null)) { "Start time bad" }
        require((initialState >= State.STOPPED) == (results != null)) { "Results bad" }
        try {
            requireComponent<Chessboard>()
            for (it in variant.requiredComponents) {
                components.filterIsInstance(it.java).firstOrNull() ?: throw ComponentNotFoundException(it)
            }
            components.forEach { it.validate() }
        } catch (e: Exception) {
            panic(e)
        }
    }

    val board get() = requireComponent<Chessboard>()

    val clock get() = getComponent<ChessClock>()

    // TODO: consider making component and player functions suspended
    val coroutineScope by lazy {
        CoroutineScope(
            environment.coroutineDispatcher +
            SupervisorJob() +
            CoroutineName("Game $uuid") +
            CoroutineExceptionHandler { _, e ->
                e.printStackTrace()
            }
        )
    }

    fun <T : Component> getComponent(cl: KClass<T>): T? =
        components.mapNotNull { cl.safeCast(it) }.firstOrNull()

    fun <T : Component> requireComponent(cl: KClass<T>): T = getComponent(cl) ?: throw ComponentNotFoundException(cl)

    inline fun <reified T : Component> getComponent(): T? = getComponent(T::class)

    inline fun <reified T : Component> requireComponent(): T = requireComponent(T::class)

    @Suppress("UNCHECKED_CAST")
    val sides: ByColor<ChessSide<*>> = byColor { playerData[it].initSide(it, this@ChessGame) }

    private fun requireState(s: State) = check(state == s)

    override fun callEvent(e: ChessEvent) = with(MultiExceptionContext()) {
        components.forEach {
            exec {
                it.handleEvent(e)
            }
        }
        rethrow { ChessEventException(e, it) }
    }

    var currentTurn: Color = board.initialFEN.currentTurn

    val currentSide: ChessSide<*> get() = sides[currentTurn]

    val currentOpponent: ChessSide<*> get() = sides[!currentTurn]

    var startTime: LocalDateTime? = startTime
        private set(v) {
            check(state == State.RUNNING) { "Start time set when not running: $state" }
            check(field == null) {
                val formatter = DateTimeFormatter.ofPattern("uuuu.MM.dd HH:mm:ss")
                "Start time already set: ${formatter.format(field)}, ${formatter.format(v)}"
            }
            field = v
        }

    enum class State {
        INITIAL, RUNNING, STOPPED, ERROR
    }

    var state: State = initialState
        private set(v) {
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

    fun sync() = apply {
        callEvent(GameBaseEvent.SYNC)
    }

    fun start() = apply {
        requireState(State.INITIAL)
        callEvent(GameBaseEvent.START)
        state = State.RUNNING
        startTime = LocalDateTime.now()
        callEvent(GameBaseEvent.RUNNING)
        startTurn()
    }

    fun update() = try {
        requireState(State.RUNNING)
        callEvent(GameBaseEvent.UPDATE)
    } catch (e: Exception) {
        panic(e)
    }

    private fun startTurn() {
        requireState(State.RUNNING)
        callEvent(TurnEvent.START)
        currentSide.startTurn()
    }

    private fun startPreviousTurn() {
        requireState(State.RUNNING)
        callEvent(TurnEvent.START)
        currentSide.startTurn()
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

    private fun panic(e: Exception): Nothing {
        state = State.ERROR
        results = drawBy(EndReason.ERROR, e.toString())
        try {
            callEvent(GameBaseEvent.PANIC)
        } catch (ex: Exception) {
            e.addSuppressed(ex)
        }
        throw e
    }

    fun <E> tryOrStopNull(expr: E?): E = try {
        expr!!
    } catch (e: NullPointerException) {
        panic(e)
    }

    operator fun get(color: Color): ChessSide<*> = sides[color]

    fun finishMove(move: Move) {
        requireState(State.RUNNING)
        move.execute(this)
        board.lastMove = move
        nextTurn()
    }

}