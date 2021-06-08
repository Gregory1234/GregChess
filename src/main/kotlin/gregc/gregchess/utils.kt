package gregc.gregchess

import net.md_5.bungee.api.chat.ClickEvent
import net.md_5.bungee.api.chat.TextComponent
import org.bukkit.*
import org.bukkit.block.Block
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.potion.PotionEffect
import org.bukkit.scoreboard.Scoreboard
import kotlin.contracts.contract

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
    val size = args.size

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

inline fun cTry(p: CommandSender, c: Configurator, err: (Exception) -> Unit = {}, f: () -> Unit) = try {
    f()
} catch (e: CommandException) {
    p.sendMessage(e.playerMsg.get(c))
    err(e)
}

inline fun JavaPlugin.addCommand(name: String, c: Configurator, crossinline command: CommandArgs.() -> Unit) {
    getCommand(name)?.setExecutor { sender, _, _, args ->
        cTry(sender, c){
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

data class Loc(val x: Int, val y: Int, val z: Int) {
    fun toLocation(w: World) = Location(w, x.toDouble(), y.toDouble(), z.toDouble())
    operator fun plus(offset: Loc) = Loc(x + offset.x, y + offset.y, z + offset.z)

    companion object {
        fun fromLocation(l: Location) = Loc(l.x.toInt(), l.y.toInt(), l.z.toInt())
    }
}

fun World.getBlockAt(l: Loc) = getBlockAt(l.x, l.y, l.z)
val Block.loc: Loc
    get() = Loc(x, y, z)


fun cArgs(args: Array<String>, min: Int = 0, max: Int = Int.MAX_VALUE) {
    cRequire(args.size in min..max, ErrorMsg.wrongArgumentsNumber)
}

fun cPerms(p: CommandSender, perm: String) {
    cRequire(p.hasPermission(perm), ErrorMsg.noPermission)
}

fun cPlayer(p: CommandSender) {
    contract {
        returns() implies (p is Player)
    }
    cRequire(p is Player, ErrorMsg.notPlayer)
}

inline fun <T> cWrongArgument(block: () -> T): T = try {
    block()
} catch (e: IllegalArgumentException) {
    e.printStackTrace()
    cWrongArgument()
}

fun cWrongArgument(): Nothing = throw CommandException(ErrorMsg.wrongArgument)

fun <T> cNotNull(p: T?, msg: ConfigPath<String>): T = p ?: throw CommandException(msg)

inline fun <reified T, reified R : T> cCast(p: T, msg: ConfigPath<String>): R = cNotNull(p as? R, msg)

fun cServerPlayer(name: String) = cNotNull(Bukkit.getPlayer(name), ErrorMsg.playerNotFound)

fun chatColor(s: String): String = ChatColor.translateAlternateColorCodes('&', s)


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

fun Configurator.getFormatString(path: String, vararg args: Any?) = get(path, "format string", path) {
    numberedFormat(it, *args)?.let(::chatColor)
}

class ConfigFullFormatString(path: String, private vararg val gotten: Any?): ConfigPath<String>(path) {
    override fun get(c: Configurator): String =
        c.getFormatString(path, *gotten.map { if (it is ConfigVal<*>) it.get(c) else it }.toTypedArray())
}

class Message(val format: String, vararg val args: ConfigVal<*>): ConfigVal<String> {
    constructor(p: ConfigVal<*>): this("$1", p)

    override fun get(c: Configurator): String = numberedFormat(format, *args.map { it.get(c) }.toTypedArray()) ?: run {
        glog.warn("Bad format ", "\"$format\"", *args.map { it.get(c) }.toTypedArray())
        format
    }
}

class MessageBuilder(
    private val formatBuilder: StringBuilder = StringBuilder(),
    private val args: MutableList<ConfigVal<*>> = mutableListOf<ConfigVal<Any?>>(),
    private var i: UInt = 1u
) {
    fun append(str: String) {
        formatBuilder.append(str)
    }
    fun append(i: Int) {
        formatBuilder.append(i)
    }
    fun append(c: Char) {
        formatBuilder.append(c)
    }
    fun append(lng: Long) {
        formatBuilder.append(lng)
    }
    fun append(f: Float) {
        formatBuilder.append(f)
    }
    fun append(d: Double) {
        formatBuilder.append(d)
    }
    fun append(v: ConfigVal<Any?>) {
        formatBuilder.append("\${$i}")
        args += v
        i++
    }
    fun append(v: Any?) {
        formatBuilder.append(v)
    }

    fun build() = Message(formatBuilder.toString(), *args.toTypedArray())
}

fun buildMessage(block: MessageBuilder.() -> Unit): Message = MessageBuilder().apply(block).build()