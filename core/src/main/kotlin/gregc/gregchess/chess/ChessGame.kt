package gregc.gregchess.chess

import gregc.gregchess.chess.component.*
import gregc.gregchess.chess.variant.ChessVariant
import kotlinx.serialization.*
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*
import java.time.LocalDateTime
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
class ChessGame(
    val settings: GameSettings,
    val playerinfo: ByColor<ChessPlayerInfo>,
    val uuid: UUID = UUID.randomUUID()
) : ChessEventCaller {
    // TODO: make the serializer more complete
    @OptIn(ExperimentalSerializationApi::class, InternalSerializationApi::class)
    object Serializer: KSerializer<ChessGame> {
        override val descriptor = buildClassSerialDescriptor("ChessGame") {
            element("uuid", buildSerialDescriptor("ChessGameUUID", SerialKind.CONTEXTUAL))
            element("players", ByColor.serializer(ChessPlayerInfoSerializer).descriptor)
            element<String>("preset")
            element<ChessVariant>("variant")
            element<Boolean>("simpleCastling")
            element("components", ListSerializer(ComponentDataSerializer).descriptor)
        }

        override fun serialize(encoder: Encoder, value: ChessGame) = encoder.encodeStructure(descriptor) {
            encodeSerializableElement(descriptor, 0, encoder.serializersModule.serializer(), value.uuid)
            encodeSerializableElement(descriptor, 1, ByColor.serializer(ChessPlayerInfoSerializer), value.playerinfo)
            encodeStringElement(descriptor, 2, value.settings.name)
            encodeSerializableElement(descriptor, 3, ChessVariant.serializer(), value.variant)
            encodeBooleanElement(descriptor, 4, value.settings.simpleCastling)
            encodeSerializableElement(descriptor, 5, ListSerializer(ComponentDataSerializer), value.components.map { it.data })
        }

        override fun deserialize(decoder: Decoder): ChessGame = decoder.decodeStructure(descriptor) {
            var uuid: UUID? = null
            var players: ByColor<ChessPlayerInfo>? = null
            var preset: String? = null
            var variant: ChessVariant? = null
            var simpleCastling: Boolean? = null
            var components: List<ComponentData<*>>? = null
            if (decodeSequentially()) { // sequential decoding protocol
                uuid = decodeSerializableElement(descriptor, 0, decoder.serializersModule.serializer())
                players = decodeSerializableElement(descriptor, 1, ByColor.serializer(ChessPlayerInfoSerializer))
                preset = decodeStringElement(descriptor, 2)
                variant = decodeSerializableElement(descriptor, 3, ChessVariant.serializer())
                simpleCastling = decodeBooleanElement(descriptor, 4)
                components = decodeSerializableElement(descriptor, 5, ListSerializer(ComponentDataSerializer))
            } else {
                while (true) {
                    when (val index = decodeElementIndex(descriptor)) {
                        0 -> uuid = decodeSerializableElement(descriptor, index, decoder.serializersModule.serializer())
                        1 -> players =
                            decodeSerializableElement(descriptor, index, ByColor.serializer(ChessPlayerInfoSerializer))
                        2 -> preset = decodeStringElement(descriptor, index)
                        3 -> variant = decodeSerializableElement(descriptor, index, ChessVariant.serializer())
                        4 -> simpleCastling = decodeBooleanElement(descriptor, index)
                        5 -> components =
                            decodeSerializableElement(descriptor, index, ListSerializer(ComponentDataSerializer))
                        CompositeDecoder.DECODE_DONE -> break
                        else -> error("Unexpected index: $index")
                    }
                }
            }
            ChessGame(GameSettings(preset!!, simpleCastling!!, variant!!, components!!), players!!, uuid!!)
        }
    }

    override fun toString() = "ChessGame(uuid=$uuid)"

    val variant = settings.variant

    val components = settings.components.map { it.getComponent(this) }

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

    val players = byColor { playerinfo[it].getPlayer(it, this) }

    private var state: GameState = GameState.Initial

    private inline fun <reified T> require(): T = (state as? T) ?: run {
        val e = WrongStateException(state, T::class.java)
        if (state !is GameState.Stopped && state !is GameState.Running) {
            panic(e)
        } else {
            stop(drawBy(EndReason.ERROR))
        }
        throw e
    }

    private fun requireInitial() = require<GameState.Initial>()

    private fun requireRunning() = require<GameState.Running>()

    private fun requireStopped() = require<GameState.Stopped>()

    override fun callEvent(e: ChessEvent) = components.forEach { it.handleEvent(e) }

    var currentTurn: Color = board.initialFEN.currentTurn

    val currentPlayer: ChessPlayer get() = players[currentTurn]

    val currentOpponent: ChessPlayer get() = players[!currentTurn]

    val startTime: LocalDateTime
        get() = require<GameState.WithStartTime>().startTime

    val running: Boolean
        get() = state.running

    fun nextTurn() {
        requireRunning()
        callEvent(TurnEvent.END)
        variant.checkForGameEnd(this)
        if (running) {
            currentTurn++
            startTurn()
        }
    }

    fun previousTurn() {
        requireRunning()
        callEvent(TurnEvent.UNDO)
        currentTurn++
        startPreviousTurn()
    }

    fun start(): ChessGame {
        requireInitial()
        callEvent(GameBaseEvent.START)
        state = GameState.Running()
        callEvent(GameBaseEvent.RUNNING)
        startTurn()
        return this
    }

    fun update() {
        requireRunning()
        callEvent(GameBaseEvent.UPDATE)
    }

    private fun startTurn() {
        requireRunning()
        callEvent(TurnEvent.START)
        currentPlayer.startTurn()
    }

    private fun startPreviousTurn() {
        requireRunning()
        callEvent(TurnEvent.START)
        currentPlayer.startTurn()
    }

    val results: GameResults?
        get() = (state as? GameState.Ended)?.results

    fun stop(results: GameResults) {
        state = GameState.Stopped(state as? GameState.Running ?: run { requireStopped(); return }, results)
        callEvent(GameBaseEvent.STOP)
    }

    private fun panic(e: Exception) {
        e.printStackTrace()
        callEvent(GameBaseEvent.PANIC)
        state = GameState.Error(state, e)
    }

    operator fun get(color: Color): ChessPlayer = players[color]

    fun finishMove(move: Move) {
        requireRunning()
        move.execute(this)
        board.lastMove?.hideDone(board)
        board.lastMove = move
        board.lastMove?.showDone(board)
        nextTurn()
    }

}

fun <E> ChessGame.tryOrStopNull(expr: E?): E = try {
    expr!!
} catch (e: NullPointerException) {
    stop(drawBy(EndReason.ERROR))
    throw e
}