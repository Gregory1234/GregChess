package gregc.gregchess

import gregc.gregchess.chess.BySides
import gregc.gregchess.chess.Side
import java.time.Duration
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.contracts.contract
import kotlin.math.*
import kotlin.reflect.KClass
import kotlin.reflect.KProperty

interface ErrorConfig: ConfigBlock {
    companion object {
        operator fun getValue(owner: ErrorConfig, property: KProperty<*>) = owner.getError(property.name.upperFirst())
    }

    fun getError(s: String): String
}

val Config.error: ErrorConfig by Config

val ErrorConfig.wrongArgumentsNumber by ErrorConfig
val ErrorConfig.wrongArgument by ErrorConfig
val ErrorConfig.rendererNotFound by ErrorConfig
val ErrorConfig.teleportFailed by ErrorConfig

interface MessageConfig : ConfigBlock {
    companion object {
        operator fun getValue(owner: MessageConfig, property: KProperty<*>) =
            owner.getMessage(property.name.upperFirst())
    }

    fun getMessage(s: String): String
    fun getMessage1(s: String): (String) -> String
}

val Config.message: MessageConfig by Config

val MessageConfig.youArePlayingAs get() = BySides { getMessage("YouArePlayingAs.${it.standardName}") }
val MessageConfig.gameFinished get() = BySides { getMessage1("GameFinished.${it.standardName}Won") }
val MessageConfig.gameFinishedDraw get() = getMessage1("GameFinished.ItWasADraw")
val MessageConfig.inCheck by MessageConfig


interface TitleConfig : ConfigBlock {
    companion object {
        operator fun getValue(owner: TitleConfig, property: KProperty<*>) = owner.getTitle(property.name.upperFirst())
    }

    fun getTitle(s: String): String
}

val Config.title: TitleConfig by Config


val TitleConfig.inCheck by TitleConfig
val TitleConfig.spectatorDraw get() = getTitle("Spectator.ItWasADraw")
val TitleConfig.spectatorWinner get() = BySides { getTitle("Spectator.${it.standardName}Won") }
fun TitleConfig.spectator(winner: Side?) = winner?.let(spectatorWinner::get) ?: spectatorDraw

val TitleConfig.youWon get() = getTitle("Player.YouWon")
val TitleConfig.youDrew get() = getTitle("Player.YouDrew")
val TitleConfig.youLost get() = getTitle("Player.YouLost")
fun TitleConfig.winner(you: Side, winner: Side?) = when (winner) {
    you -> youWon
    null -> youDrew
    else -> youLost
}

val TitleConfig.youArePlayingAs get() = BySides { getTitle("YouArePlayingAs.${it.standardName}") }
val TitleConfig.yourTurn by TitleConfig

class CommandException(val playerMsg: String) : Exception() {
    override val message: String
        get() = "Uncaught command error: $playerMsg"
}

fun cRequire(e: Boolean, msg: String) {
    contract {
        returns() implies e
    }
    if (!e) throw CommandException(msg)
}

fun cArgs(args: Array<String>, min: Int = 0, max: Int = Int.MAX_VALUE) {
    cRequire(args.size in min..max, Config.error.wrongArgumentsNumber)
}

inline fun <T> cWrongArgument(block: () -> T): T = try {
    block()
} catch (e: IllegalArgumentException) {
    e.printStackTrace()
    cWrongArgument()
}

fun cWrongArgument(): Nothing = throw CommandException(Config.error.wrongArgument)

fun <T> cNotNull(p: T?, msg: String): T = p ?: throw CommandException(msg)

inline fun <reified T, reified R : T> cCast(p: T, msg: String): R = cNotNull(p as? R, msg)

fun randomString(size: Int) =
    String(CharArray(size) { (('a'..'z') + ('A'..'Z') + ('0'..'9')).random() })

fun rotationsOf(x: Int, y: Int): List<Pair<Int, Int>> =
    listOf(x to y, x to -y, -x to y, -x to -y, y to x, -y to x, y to -x, -y to -x).distinct()

fun between(i: Int, j: Int): IntRange = if (i > j) (j + 1 until i) else (i + 1 until j)

fun <T : Enum<T>> enumValueOrNull(cl: KClass<T>): (String) -> T? = { s ->
    try {
        java.lang.Enum.valueOf(cl.java, s.uppercase())
    } catch (e: IllegalArgumentException) {
        null
    }
}

operator fun <E> List<E>.component6(): E = this[5]

fun isValidUUID(s: String) =
    Regex("""^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}${'$'}""")
        .matches(s)

operator fun Pair<Int, Int>.rangeTo(other: Pair<Int, Int>) = (first..other.first).flatMap { i ->
    (second..other.second).map { j -> Pair(i, j) }
}

operator fun Pair<Int, Int>.times(m: Int) = Pair(m * first, m * second)

fun String.upperFirst() = replaceFirstChar { it.uppercase() }
fun String.lowerFirst() = replaceFirstChar { it.lowercase() }

fun Duration.toTicks(): Long = toMillis() / 50

val Int.seconds: Duration
    get() = Duration.ofSeconds(toLong())
val Int.ticks: Duration
    get() = Duration.ofMillis(toLong() * 50)
val Long.minutes: Duration
    get() = Duration.ofMinutes(this)
val Long.ticks: Duration
    get() = Duration.ofMillis(this * 50)
val Double.seconds: Duration
    get() = Duration.ofNanos(floor(this * 1000000000L).toLong())
val Double.milliseconds: Duration
    get() = Duration.ofNanos(floor(this * 1000000L).toLong())
val Double.minutes: Duration
    get() = Duration.ofNanos(floor(this * 60000000000L).toLong())

fun parseDuration(s: String): Duration? {
    val match1 = Regex("""^(-|\+|)(\d+(?:\.\d+)?)(s|ms|t|m)$""").find(s)
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
    val match2 = Regex("""^(-)?(\d+):(\d{2,}(?:\.\d)?)$""").find(s)
    if (match2 != null) {
        val sign = (if (match2.groupValues[1] == "-") -1 else 1)
        val minutes = (match2.groupValues[2].toLongOrNull() ?: return null) * sign
        val seconds = (match2.groupValues[3].toDoubleOrNull() ?: return null) * sign
        return minutes.minutes + seconds.seconds
    }
    return null
}

fun Duration.toLocalTime(): LocalTime =
    LocalTime.ofNanoOfDay(max(ceil(toNanos().toDouble() / 1000000.0).toLong() * 1000000, 0))

@JvmInline
value class TimeFormat(private val formatter: DateTimeFormatter) {
    constructor(format: String) : this(DateTimeFormatter.ofPattern(format))

    operator fun invoke(time: LocalTime): String = time.format(formatter)
    operator fun invoke(time: Duration): String = time.toLocalTime().format(formatter)
}

fun String.snakeToPascal(): String {
    val snakeRegex = "_[a-zA-Z]".toRegex()
    return snakeRegex.replace(lowercase()) { it.value.replace("_", "").uppercase() }.upperFirst()
}

fun numberedFormatCheck(s: String, num: UInt): Boolean {
    return Regex("""\$(?:(\d+)|\{(\d+)})""").findAll(s).none {
        val i = it.groupValues[1].toIntOrNull() ?: it.groupValues.getOrNull(2)?.toIntOrNull()
        i == null || i < 1 || i.toUInt() > num
    }
}

fun numberedFormat(s: String, vararg args: Any?): String? {
    var retNull = false
    val ret = s.replace(Regex("""\$(?:(\d+)|\{(\d+)})""")) {
        val i = it.groupValues[1].toIntOrNull() ?: it.groupValues.getOrNull(2)?.toIntOrNull()
        if (i == null || i < 1 || i > args.size) {
            retNull = true
            ""
        } else
            args[i - 1].toString()
    }
    return if (retNull) null else ret
}

data class Loc(val x: Int, val y: Int, val z: Int) {
    operator fun plus(offset: Loc) = Loc(x + offset.x, y + offset.y, z + offset.z)
}

val glog = CombinedLogger()