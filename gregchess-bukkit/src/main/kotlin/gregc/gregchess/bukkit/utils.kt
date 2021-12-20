package gregc.gregchess.bukkit

import gregc.gregchess.GregLogger
import gregc.gregchess.Loc
import gregc.gregchess.bukkitutils.CommandException
import gregc.gregchess.bukkitutils.Message
import gregc.gregchess.chess.ChessEnvironment
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.modules.SerializersModule
import org.bukkit.*
import org.bukkit.block.Block
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.entity.Player
import org.bukkit.event.Listener
import java.util.*
import java.util.logging.Logger
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract
import kotlin.math.roundToLong
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

fun Loc.toLocation(w: World) = Location(w, x.toDouble(), y.toDouble(), z.toDouble())
fun Location.toLoc() = Loc(x.toInt(), y.toInt(), z.toInt())

val Block.loc: Loc
    get() = Loc(x, y, z)

@JvmField
val WRONG_ARGUMENTS_NUMBER = err("WrongArgumentsNumber")
@JvmField
val WRONG_ARGUMENT = err("WrongArgument")
@JvmField
val NO_PERMISSION = err("NoPermission")
@JvmField
val NOT_PLAYER = err("NotPlayer")
@JvmField
val PLAYER_NOT_FOUND = err("PlayerNotFound")
@JvmField
val WRONG_DURATION_FORMAT = err("WrongDurationFormat")
@JvmField
val YOU_IN_GAME = err("InGame.You")
@JvmField
val OPPONENT_IN_GAME = err("InGame.Opponent")
@JvmField
val YOU_NOT_IN_GAME = err("NotInGame.You")
@JvmField
val PLAYER_NOT_IN_GAME = err("NotInGame.Player")
@JvmField
val OPPONENT_NOT_HUMAN = err("NotHuman.Opponent")


fun message(n: String) = Message(config, "Message.$n")
fun title(n: String) = Message(config, "Title.$n")
fun err(n: String) = Message(config, "Message.Error.$n")



@OptIn(ExperimentalContracts::class)
fun cRequire(e: Boolean, msg: Message) {
    contract {
        returns() implies e
    }
    if (!e) throw CommandException(msg)
}

fun <T> T?.cNotNull(msg: Message): T = this ?: throw CommandException(msg)

inline fun <reified T, reified R : T> T.cCast(msg: Message): R = (this as? R).cNotNull(msg)

inline fun <T> cWrongArgument(block: () -> T): T = try {
    block()
} catch (e: DurationFormatException) {
    throw CommandException(WRONG_DURATION_FORMAT, e)
} catch (e: IllegalArgumentException) {
    throw CommandException(WRONG_ARGUMENT, e)
}

internal fun Listener.registerEvents() = Bukkit.getPluginManager().registerEvents(this, GregChessPlugin.plugin)

val Int.ticks: Duration get() = (this * 50).milliseconds
val Long.ticks: Duration get() = (this * 50).milliseconds

class DurationFormatException(message: String, cause: Throwable? = null) : IllegalArgumentException(message, cause)

fun String.toDuration(): Duration = toDurationOrNull() ?: throw DurationFormatException(this)

fun String.toDurationOrNull(): Duration? {
    val match1 = Regex("""^(-|\+|)(\d+(?:\.\d+)?)(s|ms|t|m)$""").find(this)
    if (match1 != null) {
        val amount =
            (match1.groupValues[2].toDoubleOrNull()
                ?: return null) * (if (match1.groupValues[1] == "-") -1 else 1)
        return when (match1.groupValues[3]) {
            "s" -> amount.seconds
            "ms" -> amount.milliseconds
            "t" -> amount.roundToLong().ticks
            "m" -> amount.minutes
            else -> null
        }
    }
    val match2 = Regex("""^(-)?(\d+):(\d{2,}(?:\.\d)?)$""").find(this)
    if (match2 != null) {
        val sign = (if (match2.groupValues[1] == "-") -1 else 1)
        val minutes = (match2.groupValues[2].toLongOrNull() ?: return null) * sign
        val seconds = (match2.groupValues[3].toDoubleOrNull() ?: return null) * sign
        return minutes.minutes + seconds.seconds
    }
    return null
}

fun Duration.format(formatString: String): String = toComponents { hours, minutes, seconds, nanoseconds ->
    formatString.replace(Regex("""HH|mm|ss|S|SS|SSS|\w|'(\w*)'|"""")) {
        when(it.value) {
            "HH" -> hours.toString()
            "mm" -> minutes.toString().padStart(2,'0')
            "ss" -> seconds.toString().padStart(2,'0')
            "SSS" -> nanoseconds.toString().take(3).replace(Regex("0+$"), "").ifEmpty { "0" }
            "SS" -> nanoseconds.toString().take(2).replace(Regex("0+$"), "").ifEmpty { "0" }
            "S" -> nanoseconds.toString().take(1).replace(Regex("0+$"), "").ifEmpty { "0" }
            "n" -> nanoseconds.toString()
            "t" -> (nanoseconds / 50000000).toString()
            "T" -> (inWholeNanoseconds / 50000000).toString()
            "\"" -> "'"
            else -> if (it.value.startsWith("'"))
                it.groupValues[0]
            else
                throw DurationFormatException(formatString)
        }
    }
}

internal val config: ConfigurationSection get() = GregChessPlugin.plugin.config

class JavaGregLogger(private val logger: Logger) : GregLogger {
    override fun info(msg: String) = logger.info(msg)
    override fun warn(msg: String) = logger.warning(msg)
    override fun err(msg: String) = logger.severe(msg)
}

object UUIDAsStringSerializer : KSerializer<UUID> {
    override val descriptor = PrimitiveSerialDescriptor("UUIDAsString", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: UUID) {
        encoder.encodeString(value.toString())
    }

    override fun deserialize(decoder: Decoder): UUID = UUID.fromString(decoder.decodeString())
}

@Suppress("UNCHECKED_CAST")
internal fun defaultModule() = SerializersModule {
    contextual(UUID::class, UUIDAsStringSerializer)
    contextual(ChessEnvironment::class, BukkitChessEnvironment.serializer() as KSerializer<ChessEnvironment>)
}

object PlayerSerializer : KSerializer<Player> {
    override val descriptor = PrimitiveSerialDescriptor("PlayerAsString", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Player) {
        encoder.encodeString(value.uniqueId.toString())
    }

    // TODO: what if the player is offline?
    override fun deserialize(decoder: Decoder): Player = Bukkit.getPlayer(UUID.fromString(decoder.decodeString()))!!
}