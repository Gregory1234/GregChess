package gregc.gregchess.bukkitutils.command

import gregc.gregchess.bukkitutils.*
import gregc.gregchess.bukkitutils.player.*
import kotlinx.coroutines.*
import org.bukkit.command.CommandSender
import org.bukkit.command.ConsoleCommandSender
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import kotlin.reflect.KClass

@Target(AnnotationTarget.CLASS)
@DslMarker
private annotation class CommandDsl

data class CommandEnvironment(
    val plugin: JavaPlugin,
    val playerProvider: BukkitHumanProvider<*, *>,
    val coroutineScope: CoroutineScope,
    val wrongArgumentsNumberMessage: Message,
    val wrongArgumentMessage: Message,
    val unhandledExceptionMessage: Message,
    val notPlayer: Message
)

@CommandDsl
class CommandBuilder<S : BukkitCommandSender>(val senderClass: KClass<S>) {
    private val onExecute = mutableListOf<suspend ExecutionContext<S>.() -> Unit>()
    private val onArgument = mutableListOf<Pair<CommandArgumentType<*>, CommandBuilder<out S>>>()
    private val validator = mutableListOf<ExecutionContext<S>.() -> Message?>()
    private var index: Int = 0

    var canBeLast: Boolean = false

    fun validate(msg: Message, f: ExecutionContext<S>.() -> Boolean) {
        validate { if (f()) null else msg }
    }

    fun validate(f: ExecutionContext<S>.() -> Message?) {
        validator += f
    }

    fun <T, U : S> argument(senderClass: KClass<U>, type: CommandArgumentType<T>, builder: CommandBuilder<U>.(CommandArgument<T>) -> Unit) {
        onArgument += type to CommandBuilder(senderClass).also {
            it.index = index + 1
            it.builder(CommandArgument(index, type))
        }
    }

    fun <T> argument(type: CommandArgumentType<T>, builder: CommandBuilder<S>.(CommandArgument<T>) -> Unit) =
        argument(senderClass, type, builder)

    fun <U : S> literal(senderClass: KClass<U>, name: String, builder: CommandBuilder<U>.() -> Unit) {
        onArgument += literalArgument(name) to CommandBuilder(senderClass).also {
            it.index = index + 1
            it.builder()
        }
    }

    fun literal(name: String, builder: CommandBuilder<S>.() -> Unit) =
        literal(senderClass, name, builder)

    fun executeSuspend(builder: suspend ExecutionContext<S>.() -> Unit) {
        canBeLast = true
        onExecute += builder
    }

    fun execute(builder: ExecutionContext<S>.() -> Unit) {
        executeSuspend(builder)
    }

    @Suppress("UNCHECKED_CAST")
    private fun executeOn(
        sender: S,
        env: CommandEnvironment,
        strings: StringArguments,
        toExec: MutableList<suspend ExecutionContext<S>.() -> Unit>,
        toValidate: MutableList<ExecutionContext<S>.() -> Message?>,
        context: MutableList<Any?>
    ) {
        var msg: Message? = null
        for ((arg, com) in (onArgument as MutableList<Pair<CommandArgumentType<*>, CommandBuilder<S>>>)) {
            if (!com.senderClass.isInstance(sender)) {
                msg = msg ?: env.notPlayer
                continue
            }
            val parse = strings.transaction { arg.tryParse(this) }
            if (parse != null) {
                context += parse
                toValidate.addAll(validator)
                return com.executeOn(sender, env, strings, toExec, toValidate, context)
            } else {
                msg = msg ?: arg.failMessage
            }
        }
        if (!strings.hasNext() && canBeLast) {
            toExec.addAll(onExecute)
            toValidate.addAll(validator)
            return
        }
        throw CommandException(
            if (!strings.hasNext() || onArgument.isEmpty()) env.wrongArgumentsNumberMessage else msg ?: env.wrongArgumentMessage
        )
    }

    suspend fun executeOn(env: CommandEnvironment, sender: S, strings: List<String>) {
        val toExec = mutableListOf<suspend ExecutionContext<S>.() -> Unit>()
        val toValidate = mutableListOf<ExecutionContext<S>.() -> Message?>()
        val ctx = mutableListOf<Any?>()
        val arguments = StringArguments(strings)
        executeOn(sender, env, arguments, toExec, toValidate, ctx)
        val ectx = ExecutionContext(sender, ctx)
        for (x in toValidate)
            ectx.x()?.let {
                throw CommandException(it)
            }
        for (x in toExec)
            ectx.x()
    }

    @Suppress("UNCHECKED_CAST")
    private fun autocompleteOn(
        env: CommandEnvironment,
        sender: S,
        strings: StringArguments,
        setLast: (String?) -> Unit,
        ac: MutableSet<String>,
        context: MutableList<Any?>
    ) {
        outLoop@ for ((arg, com) in (onArgument as MutableList<Pair<CommandArgumentType<*>, CommandBuilder<S>>>)) {
            if (!com.senderClass.isInstance(sender))
                continue
            val ctx = ExecutionContext(sender, context)
            for (v in validator) {
                if (ctx.v() != null)
                    continue@outLoop
            }
            val new = strings.transaction { arg.autocomplete(this, ctx)?.toMutableList() }
            if (new != null) {
                new.removeIf {
                    var ret = false
                    setLast(it)
                    val parse = strings.tryUndo { arg.tryParse(this) }
                    if (parse != null) {
                        context += parse
                        for (v in com.validator) {
                            if (ctx.v() != null) {
                                ret = true
                                break
                            }
                        }
                        context.removeLast()
                    }
                    setLast(null)
                    ret
                }
                ac.addAll(new)
            } else strings.tryUndo {
                val parse = arg.tryParse(this)
                if (parse != null) {
                    context += parse
                    com.autocompleteOn(env, sender, this, setLast, ac, context)
                    context.removeLast()
                }
            }
        }
    }

    fun autocompleteOn(env: CommandEnvironment, sender: S, strings: List<String>): Set<String> {
        val ret = mutableSetOf<String>()
        val mutStrings = strings.toMutableList()
        val arguments = StringArguments(mutStrings)
        autocompleteOn(env, sender, arguments, { if (it == null) mutStrings.removeAt(strings.size) else mutStrings.add(strings.size, it) }, ret, mutableListOf())
        return ret
    }
}

class CommandArgument<T>(val index: Int, val type: CommandArgumentType<T>)

@CommandDsl
class ExecutionContext<out S : BukkitCommandSender>(val sender: S, private val arguments: List<Any?>) {

    @Suppress("UNCHECKED_CAST")
    operator fun <T> CommandArgument<T>.invoke(): T = arguments[index] as T
}

fun CommandBuilder<*>.requirePermission(permission: String, msg: Message) {
    validate(msg) { sender.hasPermission(permission) }
}

fun CommandEnvironment.addCommand(name: String, command: CommandBuilder<BukkitCommandSender>.() -> Unit) {
    val com = CommandBuilder(BukkitCommandSender::class).apply { command() }
    fun fromCommandSender(sender: CommandSender): BukkitCommandSender = when(sender) {
        is Player -> playerProvider.getOnlinePlayer(sender.uniqueId)!!
        is ConsoleCommandSender -> ConsoleBukkitCommandSender
        else -> throw IllegalArgumentException(sender.toString())
    }

    plugin.getCommand(name)?.setExecutor { sender, _, trueName, args ->
        val commandScope = CoroutineScope(coroutineScope.coroutineContext +
                SupervisorJob(coroutineScope.coroutineContext.job) +
                CoroutineName("Command $trueName") +
                CoroutineExceptionHandler { _, e ->
                    if (e is CommandException)
                        sender.sendMessage(e.error)
                    else {
                        sender.sendMessage(unhandledExceptionMessage)
                        e.printStackTrace()
                    }
                })
        commandScope.launch {
            com.executeOn(this@addCommand.copy(coroutineScope = commandScope), fromCommandSender(sender), args.toList())
        }
        true
    }
    plugin.getCommand(name)?.setTabCompleter { sender, _, _, args ->
        val last by lazy { args.last().lowercase() }
        com.autocompleteOn(this, fromCommandSender(sender), args.dropLast(1)).filter { args.isEmpty() || last in it.lowercase() }
    }
}