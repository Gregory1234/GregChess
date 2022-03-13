package gregc.gregchess.bukkitutils.command

import gregc.gregchess.bukkitutils.*
import org.bukkit.Bukkit
import java.util.*
import kotlin.math.min

class StringArguments(private val args: List<String>) : Iterator<String> {
    var index: Int = 0
    fun lookup() = args[index]
    fun lookupOrNull() = args.getOrNull(index)
    fun lookupMany(len: Int = args.size - index) = args.subList(index, min(len, args.size - index))
    override fun next() = args[index++]
    fun nextOrNull() = args.getOrNull(index++)
    fun nextMany(len: Int = args.size - index): List<String> {
        val ret = args.subList(index, min(index + len, args.size))
        index = min(index + len, args.size)
        return ret
    }
    override fun hasNext(): Boolean = index < args.size
    fun hasNext(len: Int): Boolean = index + len <= args.size

    fun <R : Any> tryUndo(block: StringArguments.() -> R?): R? {
        val startIndex = index
        val ret = block()
        index = startIndex
        return ret
    }

    fun <R : Any> transaction(block: StringArguments.() -> R?): R? {
        val startIndex = index
        val ret = block()
        if (ret == null)
            index = startIndex
        return ret
    }
}

abstract class CommandArgumentType<R>(val name: String, val failMessage: Message? = null) {
    abstract fun tryParse(args: StringArguments): R?
    open fun autocomplete(args: StringArguments, ctx: ExecutionContext<*>): Set<String>? =
        if (args.tryUndo { tryParse(this) } == null) emptySet() else null
}

class SimpleArgument<R : Any>(
    name: String,
    val defaults: () -> Set<String> = ::emptySet,
    failMessage: Message? = null,
    val simpleParser: (String) -> R?
) : CommandArgumentType<R>(name, failMessage) {

    override fun tryParse(args: StringArguments): R? = args.nextOrNull()?.let(simpleParser)
    override fun autocomplete(args: StringArguments, ctx: ExecutionContext<*>): Set<String>? =
        if (!args.hasNext()) defaults() else null

}

fun stringArgument(name: String) = SimpleArgument(name) { it }

fun intArgument(name: String) = SimpleArgument(name) { it.toIntOrNull() }

class GreedyStringArgument(name: String) : CommandArgumentType<String>(name) {
    override fun tryParse(args: StringArguments): String =
        args.nextMany().joinToString(" ")
}

fun uuidArgument(name: String) = SimpleArgument(name) { try { UUID.fromString(it) } catch (e : IllegalArgumentException) { null } }

fun literalArgument(value: String, name: String = value) = SimpleArgument(name, defaults = { setOf(value) }) { if (it == value) Unit else null }

fun <T : Any> enumArgument(values: Collection<T>, name: String) = SimpleArgument(name, defaults = { values.map(Any::toString).toSet() }) { values.firstOrNull { v -> v.toString() == it } }

inline fun <reified E : Enum<E>> enumArgument(name: String) = enumArgument(E::class.java.enumConstants.toList(), name)

fun playerArgument(name: String, msg: Message) = SimpleArgument(name, defaults = { Bukkit.getOnlinePlayers().map { it.name }.toSet() }, failMessage = msg) { Bukkit.getPlayer(it) }

fun offlinePlayerArgument(name: String, msg: Message) = SimpleArgument(name, defaults = { Bukkit.getOnlinePlayers().map { it.name }.toSet() }, failMessage = msg) { getOfflinePlayerByName(it) }

fun durationArgument(name: String, msg: Message) = SimpleArgument(name, failMessage = msg) { it.toDurationOrNull() }