package gregc.gregchess.bukkit.command

import gregc.gregchess.bukkit.*
import gregc.gregchess.bukkit.coroutines.BukkitContext
import gregc.gregchess.bukkit.coroutines.BukkitDispatcher
import kotlinx.coroutines.*
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import java.util.*
import kotlin.reflect.KClass

@Target(AnnotationTarget.CLASS)
@DslMarker
annotation class CommandDsl

@CommandDsl
class CommandBuilder {
    private val onExecutePartial = mutableListOf<ExecutionContext<*>.() -> Unit>()
    private val onExecute = mutableListOf<ExecutionContext<*>.() -> Unit>()
    private val onArgument = mutableListOf<Pair<CommandArgumentType<*>, CommandBuilder>>()
    private val validator = mutableListOf<ExecutionContext<*>.() -> Message?>()
    private var index: Int = 0

    var canBeLast: Boolean = false

    fun validate(msg: Message, f: ExecutionContext<*>.() -> Boolean) {
        validate { if (f()) null else msg }
    }

    fun validate(f: ExecutionContext<*>.() -> Message?) {
        validator += f
    }

    fun <T> argument(type: CommandArgumentType<T>, builder: CommandBuilder.(CommandArgument<T>) -> Unit) {
        onArgument += type to CommandBuilder().also {
            it.index = index + 1
            it.builder(CommandArgument(index, type))
        }
    }

    fun literal(name: String, builder: CommandBuilder.() -> Unit) {
        onArgument += LiteralArgument(name) to CommandBuilder().also {
            it.index = index + 1
            it.builder()
        }
    }

    fun execute(builder: ExecutionContext<*>.() -> Unit) {
        canBeLast = true
        onExecute += builder
    }

    @Suppress("UNCHECKED_CAST")
    fun <S : CommandSender> execute(cl: KClass<S>, builder: ExecutionContext<S>.() -> Unit) {
        execute {
            if (cl.isInstance(this.sender)) {
                (this as ExecutionContext<S>).builder()
            }
        }
    }

    @JvmName("executeSpecial")
    inline fun <reified S : CommandSender> execute(noinline builder: ExecutionContext<S>.() -> Unit) {
        execute(S::class, builder)
    }

    fun partialExecute(builder: ExecutionContext<*>.() -> Unit) {
        onExecutePartial += builder
    }

    @Suppress("UNCHECKED_CAST")
    fun <S : CommandSender> partialExecute(cl: KClass<S>, builder: ExecutionContext<S>.() -> Unit) {
        partialExecute {
            if (cl.isInstance(this.sender)) {
                (this as ExecutionContext<S>).builder()
            }
        }
    }

    @JvmName("partialExecuteSpecial")
    inline fun <reified S : CommandSender> partialExecute(noinline builder: ExecutionContext<S>.() -> Unit) {
        partialExecute(S::class, builder)
    }

    private fun executeOn(
        strings: List<String>,
        toExec: MutableList<ExecutionContext<*>.() -> Unit>,
        toValidate: MutableList<ExecutionContext<*>.() -> Message?>,
        context: MutableList<Any?>
    ) {
        toExec.addAll(onExecutePartial)
        var msg: Message? = null
        for ((arg, com) in onArgument) {
            val parse = arg.tryParse(strings)
            if (parse != null) {
                context += parse.first
                toValidate.addAll(validator)
                return com.executeOn(parse.second, toExec, toValidate, context)
            } else {
                msg = msg ?: arg.failMessage
            }
        }
        if (strings.isEmpty() && canBeLast) {
            toExec.addAll(onExecute)
            return
        }
        throw CommandException(
            if (strings.isEmpty() || onArgument.isEmpty()) WRONG_ARGUMENTS_NUMBER else msg ?: WRONG_ARGUMENT
        )
    }

    fun executeOn(sender: CommandSender, strings: List<String>) {
        val toExec = mutableListOf<ExecutionContext<*>.() -> Unit>()
        val toValidate = mutableListOf<ExecutionContext<*>.() -> Message?>()
        val ctx = mutableListOf<Any?>()
        executeOn(strings, toExec, toValidate, ctx)
        val ectx = ExecutionContext(sender, ctx, CoroutineScope(
        BukkitDispatcher(GregChessPlugin.plugin, BukkitContext.SYNC) +
                SupervisorJob(GregChessPlugin.coroutineScope.coroutineContext.job) +
                CoroutineExceptionHandler { _, e ->
                    if (e is CommandException)
                        sender.sendMessage(e.error.get())
                    else
                        e.printStackTrace()
                }
        ))
        for (x in toValidate)
            ectx.x()?.let {
                throw CommandException(it)
            }
        for (x in toExec)
            ectx.x()
    }

    private fun autocompleteOn(
        sender: CommandSender,
        strings: List<String>,
        ac: MutableSet<String>,
        context: MutableList<Any?>
    ) {
        outLoop@ for ((arg, com) in onArgument) {
            val ctx = ExecutionContext(sender, context, GregChessPlugin.coroutineScope)
            for (v in validator) {
                if (ctx.v() != null)
                    continue@outLoop
            }
            val new = arg.autocomplete(ctx, strings)?.toMutableList()
            if (new != null) {
                new.removeIf {
                    var ret = false
                    val parse = arg.tryParse(strings + listOf(it))
                    if (parse != null) {
                        context += parse.first
                        for (v in com.validator) {
                            if (ctx.v() != null) {
                                ret = true
                                break
                            }
                        }
                        context.removeLast()
                    }
                    ret
                }
                ac.addAll(new)
            } else {
                val parse = arg.tryParse(strings)
                if (parse != null) {
                    context += parse.first
                    com.autocompleteOn(sender, parse.second, ac, context)
                    context.removeLast()
                }
            }
        }
    }

    fun autocompleteOn(sender: CommandSender, strings: List<String>): Set<String> {
        val ret = mutableSetOf<String>()
        autocompleteOn(sender, strings, ret, mutableListOf())
        return ret
    }
}

class CommandArgument<T>(val index: Int, val type: CommandArgumentType<T>)

abstract class CommandArgumentType<R>(val name: String, val failMessage: Message? = null) {
    abstract fun tryParse(strings: List<String>): Pair<R, List<String>>?
    open fun autocomplete(ctx: ExecutionContext<*>, strings: List<String>): Set<String>? =
        if (tryParse(strings) == null) emptySet() else null
}

@CommandDsl
class ExecutionContext<out S : CommandSender>(val sender: S, private val arguments: List<Any?>, val coroutineScope: CoroutineScope) {

    @Suppress("UNCHECKED_CAST")
    operator fun <T> CommandArgument<T>.invoke(): T = arguments[index] as T
}

class StringArgument(name: String) : CommandArgumentType<String>(name) {
    override fun tryParse(strings: List<String>): Pair<String, List<String>>? =
        if (strings.isEmpty()) null else Pair(strings.first(), strings.drop(1))
}

class GreedyStringArgument(name: String) : CommandArgumentType<String>(name) {
    override fun tryParse(strings: List<String>): Pair<String, List<String>> =
        Pair(strings.joinToString(" "), emptyList())
}

class UUIDArgument(name: String) : CommandArgumentType<UUID>(name) {
    override fun tryParse(strings: List<String>): Pair<UUID, List<String>>? = try {
        if (strings.isEmpty()) null else Pair(UUID.fromString(strings.first()), strings.drop(1))
    } catch (e: IllegalArgumentException) {
        null
    }
}

class LiteralArgument(val value: String, name: String = value) : CommandArgumentType<Unit>(name) {
    override fun tryParse(strings: List<String>): Pair<Unit, List<String>>? =
        if (strings.firstOrNull() == value) Pair(Unit, strings.drop(1)) else null

    override fun autocomplete(ctx: ExecutionContext<*>, strings: List<String>): Set<String>? =
        if (strings.isEmpty()) setOf(value) else null
}

class EnumArgument<T>(val values: Collection<T>, name: String) : CommandArgumentType<T>(name) {
    override fun tryParse(strings: List<String>): Pair<T, List<String>>? =
        values.firstOrNull { it.toString() == strings.firstOrNull() }?.to(strings.drop(1))

    override fun autocomplete(ctx: ExecutionContext<*>, strings: List<String>): Set<String>? =
        if (strings.isEmpty()) values.map { it.toString() }.toSet() else null
}

inline fun <reified E : Enum<E>> enumArgument(name: String) = EnumArgument(E::class.java.enumConstants.toList(), name)

class PlayerArgument(name: String) : CommandArgumentType<Player>(name, PLAYER_NOT_FOUND) {
    override fun tryParse(strings: List<String>): Pair<Player, List<String>>? = strings.firstOrNull()?.let { n ->
        Bukkit.getPlayer(n)?.let {
            Pair(it, strings.drop(1))
        }
    }

    override fun autocomplete(ctx: ExecutionContext<*>, strings: List<String>): Set<String>? =
        if (strings.isEmpty()) Bukkit.getOnlinePlayers().map { it.name }.toSet() else null
}

fun CommandBuilder.requirePermission(permission: String) {
    validate(NO_PERMISSION) { sender.hasPermission(permission) }
}

fun JavaPlugin.addCommand(name: String, command: CommandBuilder.() -> Unit) {
    val com = CommandBuilder().apply { command() }
    getCommand(name)?.setExecutor { sender, _, _, args ->
        cTry(sender) {
            com.executeOn(sender, args.toList())
        }
        true
    }
    getCommand(name)?.setTabCompleter { sender, _, _, args ->
        val last by lazy { args.last().lowercase() }
        com.autocompleteOn(sender, args.dropLast(1)).filter { args.isEmpty() || last in it.lowercase() }
    }
}