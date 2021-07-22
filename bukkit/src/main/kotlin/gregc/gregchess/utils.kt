package gregc.gregchess

import gregc.gregchess.chess.human
import net.md_5.bungee.api.chat.ClickEvent
import net.md_5.bungee.api.chat.TextComponent
import org.bukkit.*
import org.bukkit.block.Block
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.event.Listener
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.potion.PotionEffect
import org.bukkit.scoreboard.Scoreboard
import java.time.Duration
import kotlin.contracts.contract

const val DEFAULT_LANG = "en_US"

data class PlayerData(
    val location: Location? = null,
    val inventory: List<ItemStack?> = List(41) { null },
    val gameMode: GameMode = GameMode.SURVIVAL,
    val health: Double = 20.0,
    val foodLevel: Int = 20, val saturation: Float = 20.0F,
    val level: Int = 0, val exp: Float = 0.0F,
    val allowFlight: Boolean = false, val isFlying: Boolean = false,
    val effects: List<PotionEffect> = emptyList(),
    val scoreboard: Scoreboard = Bukkit.getScoreboardManager()!!.mainScoreboard
)

var Player.playerData: PlayerData
    get() = PlayerData(
        location.clone(),
        inventory.contents.toList(),
        gameMode,
        health,
        foodLevel, saturation,
        level, exp,
        allowFlight, isFlying,
        activePotionEffects.toList(),
        scoreboard
    )
    set(d) {
        inventory.contents = d.inventory.toTypedArray()
        gameMode = d.gameMode
        health = d.health
        foodLevel = d.foodLevel; saturation = d.saturation
        level = d.level; exp = d.exp
        allowFlight = d.allowFlight; isFlying = d.isFlying
        activePotionEffects.forEach { removePotionEffect(it.type) }
        d.effects.forEach(::addPotionEffect)
        d.location?.let(::teleport)
        scoreboard = d.scoreboard
    }

fun World.playSound(l: Location, s: Sound) = playSound(l, s, 3.0f, 1.0f)

fun <R> Loc.doIn(w: World, f: (World, Location) -> R) = f(w, toLocation(w))

fun Player.sendDefTitle(title: String, subtitle: String = "") = sendTitle(title, subtitle, 10, 70, 20)

class CommandArgs(val player: CommandSender, val args: Array<String>) {
    var index = 0

    fun nextArg(): String {
        cArgs(args, ++index)
        return args[index - 1]
    }

    operator fun get(n: Int): String {
        cArgs(args, index + n + 1)
        return args[index + n]
    }

    fun endArgs() {
        cArgs(args, max = index)
    }

    fun lastArg(): String {
        cArgs(args, ++index, index)
        return args[index - 1]
    }

    fun latestArg() = get(-1)

    fun rest() = args.drop(index)

    fun restString() = rest().joinToString(" ")

}

inline fun cTry(p: CommandSender, err: (Exception) -> Unit = {}, f: () -> Unit) = try {
    f()
} catch (e: CommandException) {
    p.sendMessage(e.error.msg.get(p.lang).chatColor())
    err(e)
}

inline fun JavaPlugin.addCommand(name: String, crossinline command: CommandArgs.() -> Unit) {
    getCommand(name)?.setExecutor { sender, _, _, args ->
        cTry(sender) {
            command(CommandArgs(sender, args))
        }
        true
    }
}

inline fun JavaPlugin.addCommandTab(name: String, crossinline tabCompleter: CommandArgs.() -> List<String>?) {
    getCommand(name)?.setTabCompleter { sender, _, _, args ->
        tabCompleter(CommandArgs(sender, args))?.toMutableList()
    }
}

fun Loc.toLocation(w: World) = Location(w, x.toDouble(), y.toDouble(), z.toDouble())
fun Location.toLoc() = Loc(x.toInt(), y.toInt(), z.toInt())

fun World.getBlockAt(l: Loc) = getBlockAt(l.x, l.y, l.z)
val Block.loc: Loc
    get() = Loc(x, y, z)

data class ErrorMsg(val standardName: String) {
    companion object {
        private val errors = mutableListOf<ErrorMsg>()
    }
    init {
        errors += this
    }

    val msg get() = config.getLocalizedString("Message.Error.$standardName")
}

@JvmField
val WRONG_ARGUMENTS_NUMBER = ErrorMsg("WrongArgumentsNumber")
@JvmField
val WRONG_ARGUMENT = ErrorMsg("WrongArgument")
@JvmField
val NO_PERMISSION = ErrorMsg("NoPermission")
@JvmField
val NOT_PLAYER = ErrorMsg("NotPlayer")
@JvmField
val PLAYER_NOT_FOUND = ErrorMsg("PlayerNotFound")
@JvmField
val WRONG_DURATION_FORMAT = ErrorMsg("WrongDurationFormat")
@JvmField
val YOU_IN_GAME = ErrorMsg("InGame.You")
@JvmField
val OPPONENT_IN_GAME = ErrorMsg("InGame.Opponent")
@JvmField
val YOU_NOT_IN_GAME = ErrorMsg("NotInGame.You")
@JvmField
val PLAYER_NOT_IN_GAME = ErrorMsg("NotInGame.Player")
@JvmField
val OPPONENT_NOT_HUMAN = ErrorMsg("NotHuman.Opponent")

fun message(n: String) = config.getLocalizedString( "Message.$n")
fun title(n: String) = config.getLocalizedString( "Title.$n")


class CommandException(val error: ErrorMsg, cause: Throwable? = null) : Exception(cause) {

    override val message: String
        get() = "Uncaught command error: ${error.standardName}"
}

fun cRequire(e: Boolean, msg: ErrorMsg) {
    contract {
        returns() implies e
    }
    if (!e) throw CommandException(msg)
}

fun <T> T?.cNotNull(msg: ErrorMsg): T = this ?: throw CommandException(msg)

inline fun <reified T, reified R : T> T.cCast(msg: ErrorMsg): R = (this as? R).cNotNull(msg)


fun cPerms(p: CommandSender, perm: String) {
    cRequire(p.hasPermission(perm), NO_PERMISSION)
}

fun cPlayer(p: CommandSender) {
    contract {
        returns() implies (p is Player)
    }
    cRequire(p is Player, NOT_PLAYER)
}

fun cServerPlayer(name: String) = Bukkit.getPlayer(name).cNotNull(PLAYER_NOT_FOUND)

fun cArgs(args: Array<String>, min: Int = 0, max: Int = Int.MAX_VALUE) {
    cRequire(args.size in min..max, WRONG_ARGUMENTS_NUMBER)
}

inline fun <T> cWrongArgument(block: () -> T): T = try {
    block()
} catch (e: IllegalArgumentException) {
    e.printStackTrace()
    cWrongArgument()
}

fun cWrongArgument(): Nothing = throw CommandException(WRONG_ARGUMENT)

fun String.chatColor(): String = ChatColor.translateAlternateColorCodes('&', this)


class BuildTextComponentScope {
    val returnValue = TextComponent()
    fun append(str: String) {
        returnValue.addExtra(str)
    }

    fun append(tc: TextComponent) {
        returnValue.addExtra(tc)
    }

    fun append(v: Any?, clickEvent: ClickEvent? = null) {
        val c = TextComponent(v.toString())
        if (clickEvent != null)
            c.clickEvent = clickEvent
        append(c)
    }

    fun appendCopy(v: Any?, copy: Any?) {
        append(v, ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, copy.toString()))
    }
}

inline fun buildTextComponent(f: BuildTextComponentScope.() -> Unit) =
    BuildTextComponentScope().apply(f).returnValue

val CommandSender.lang get() = (this as? Player)?.human?.lang ?: DEFAULT_LANG
fun CommandSender.sendMessage(s: LocalizedString) = sendMessage(s.get(lang).chatColor())
fun CommandSender.sendCommandMessage(msg: String, action: String, command: String) {
    spigot().sendMessage(buildTextComponent {
        append(msg.chatColor())
        append(" ")
        append(action.chatColor(), ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
    })
}
fun CommandSender.sendCommandMessage(msg: LocalizedString, action: LocalizedString, command: String) =
    sendCommandMessage(msg.get(lang), action.get(lang), command)

fun Listener.registerEvents() = Bukkit.getPluginManager().registerEvents(this, GregChess.plugin)

fun Duration.toTicks(): Long = toMillis() / 50

val Int.ticks: Duration
    get() = Duration.ofMillis(toLong() * 50)