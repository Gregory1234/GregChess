package gregc.gregchess.bukkitutils.command

import gregc.gregchess.bukkitutils.*
import kotlinx.coroutines.*
import org.bukkit.command.CommandSender
import org.bukkit.plugin.java.JavaPlugin
import kotlin.reflect.KClass

@Target(AnnotationTarget.CLASS)
@DslMarker
private annotation class CommandDsl

data class CommandEnvironment(
    val plugin: JavaPlugin,
    val coroutineScope: CoroutineScope,
    val wrongArgumentsNumberMessage: Message,
    val wrongArgumentMessage: Message,
    val unhandledExceptionMessage: Message
)

@CommandDsl
class CommandBuilder { // TODO: redesign this api
    private val onExecute = mutableListOf<suspend ExecutionContext<*>.() -> Unit>()
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

    fun executeSuspend(builder: suspend ExecutionContext<*>.() -> Unit) {
        canBeLast = true
        onExecute += builder
    }

    fun execute(builder: ExecutionContext<*>.() -> Unit) {
        executeSuspend(builder)
    }

    @Suppress("UNCHECKED_CAST")
    fun <S : CommandSender> executeSuspend(cl: KClass<S>, builder: suspend ExecutionContext<S>.() -> Unit) {
        executeSuspend {
            if (cl.isInstance(this.sender)) {
                (this as ExecutionContext<S>).builder()
            }
        }
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
    inline fun <reified S : CommandSender> executeSuspend(noinline builder: suspend ExecutionContext<S>.() -> Unit) {
        executeSuspend(S::class, builder)
    }

    @JvmName("executeSpecial")
    inline fun <reified S : CommandSender> execute(noinline builder: ExecutionContext<S>.() -> Unit) {
        execute(S::class, builder)
    }

    private fun executeOn(
        env: CommandEnvironment,
        strings: List<String>,
        toExec: MutableList<suspend ExecutionContext<*>.() -> Unit>,
        toValidate: MutableList<ExecutionContext<*>.() -> Message?>,
        context: MutableList<Any?>
    ) {
        var msg: Message? = null
        for ((arg, com) in onArgument) {
            val parse = arg.tryParse(strings)
            if (parse != null) {
                context += parse.first
                toValidate.addAll(validator)
                return com.executeOn(env, parse.second, toExec, toValidate, context)
            } else {
                msg = msg ?: arg.failMessage
            }
        }
        if (strings.isEmpty() && canBeLast) {
            toExec.addAll(onExecute)
            return
        }
        throw CommandException(
            if (strings.isEmpty() || onArgument.isEmpty()) env.wrongArgumentsNumberMessage else msg ?: env.wrongArgumentMessage
        )
    }

    suspend fun executeOn(env: CommandEnvironment, sender: CommandSender, strings: List<String>) {
        val toExec = mutableListOf<suspend ExecutionContext<*>.() -> Unit>()
        val toValidate = mutableListOf<ExecutionContext<*>.() -> Message?>()
        val ctx = mutableListOf<Any?>()
        executeOn(env, strings, toExec, toValidate, ctx)
        val ectx = ExecutionContext(sender, ctx)
        for (x in toValidate)
            ectx.x()?.let {
                throw CommandException(it)
            }
        for (x in toExec)
            ectx.x()
    }

    private fun autocompleteOn(
        env: CommandEnvironment,
        sender: CommandSender,
        strings: List<String>,
        ac: MutableSet<String>,
        context: MutableList<Any?>
    ) {
        outLoop@ for ((arg, com) in onArgument) {
            val ctx = ExecutionContext(sender, context)
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
                    com.autocompleteOn(env, sender, parse.second, ac, context)
                    context.removeLast()
                }
            }
        }
    }

    fun autocompleteOn(env: CommandEnvironment, sender: CommandSender, strings: List<String>): Set<String> {
        val ret = mutableSetOf<String>()
        autocompleteOn(env, sender, strings, ret, mutableListOf())
        return ret
    }
}

class CommandArgument<T>(val index: Int, val type: CommandArgumentType<T>)

@CommandDsl
class ExecutionContext<out S : CommandSender>(val sender: S, private val arguments: List<Any?>) {

    @Suppress("UNCHECKED_CAST")
    operator fun <T> CommandArgument<T>.invoke(): T = arguments[index] as T
}

fun CommandBuilder.requirePermission(permission: String, msg: Message) {
    validate(msg) { sender.hasPermission(permission) }
}

fun CommandEnvironment.addCommand(name: String, command: CommandBuilder.() -> Unit) {
    val com = CommandBuilder().apply { command() }
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
            com.executeOn(this@addCommand.copy(coroutineScope = commandScope), sender, args.toList())
        }
        true
    }
    plugin.getCommand(name)?.setTabCompleter { sender, _, _, args ->
        val last by lazy { args.last().lowercase() }
        com.autocompleteOn(this, sender, args.dropLast(1)).filter { args.isEmpty() || last in it.lowercase() }
    }
}