package gregc.gregchess

import gregc.gregchess.chess.BySides
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
import kotlin.reflect.KProperty

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

inline fun cTry(p: CommandSender, err: (Exception) -> Unit = {}, f: () -> Unit) = try {
    f()
} catch (e: CommandException) {
    p.sendMessage(e.playerMsg)
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

val ErrorConfig.wrongArgumentsNumber by ErrorConfig
val ErrorConfig.wrongArgument by ErrorConfig
val ErrorConfig.noPermission by ErrorConfig
val ErrorConfig.notPlayer by ErrorConfig
val ErrorConfig.playerNotFound by ErrorConfig
val ErrorConfig.rendererNotFound by ErrorConfig
val ErrorConfig.clockNotFound by ErrorConfig
val ErrorConfig.engineNotFound by ErrorConfig
val ErrorConfig.pieceNotFound by ErrorConfig
val ErrorConfig.gameNotFound by ErrorConfig
val ErrorConfig.noArenas by ErrorConfig
val ErrorConfig.teleportFailed by ErrorConfig
val ErrorConfig.nothingToTakeback by ErrorConfig
val ErrorConfig.wrongDurationFormat by ErrorConfig
val ErrorConfig.youInGame get() = getError("InGame.You")
val ErrorConfig.opponentInGame get() = getError("InGame.Opponent")
val ErrorConfig.youNotInGame get() = getError("NotInGame.You")
val ErrorConfig.playerNotInGame get() = getError("NotInGame.Player")
val ErrorConfig.opponentNotHuman get() = getError("NotHuman.Opponent")

interface MessageConfig: ConfigBlock {
    companion object {
        operator fun getValue(owner: MessageConfig, property: KProperty<*>) = owner.getMessage(property.name.upperFirst())
    }

    fun getMessage(s: String): String
    fun getMessage1(s: String): (String) -> String
}

val Config.message: MessageConfig by Config

val MessageConfig.boardOpDone by MessageConfig
val MessageConfig.skippedTurn by MessageConfig
val MessageConfig.timeOpDone by MessageConfig
val MessageConfig.engineCommandSent by MessageConfig
val MessageConfig.loadedFEN by MessageConfig
val MessageConfig.configReloaded by MessageConfig
val MessageConfig.levelSet by MessageConfig
val MessageConfig.copyFEN by MessageConfig
val MessageConfig.copyPGN by MessageConfig
val MessageConfig.inCheck by MessageConfig
val MessageConfig.pawnPromotion by MessageConfig
val MessageConfig.chooseSettings by MessageConfig

val MessageConfig.youArePlayingAs get() = BySides { getMessage("YouArePlayingAs.${it.standardName}") }
val MessageConfig.gameFinished get() = BySides { getMessage1("GameFinished.${it.standardName}Won") }
val MessageConfig.gameFinishedDraw get() = getMessage1("GameFinished.ItWasADraw")

interface TitleConfig: ConfigBlock {
    companion object {
        operator fun getValue(owner: TitleConfig, property: KProperty<*>) = owner.getMessage(property.name.upperFirst())
    }

    fun getMessage(s: String): String
    fun getMessage1(s: String): (String) -> String
}

val Config.title: TitleConfig by Config

val TitleConfig.inCheck by TitleConfig
val TitleConfig.youWon get() = getMessage("Player.YouWon")
val TitleConfig.youDrew get() = getMessage("Player.YouDrew")
val TitleConfig.youLost get() = getMessage("Player.YouLost")
val TitleConfig.spectatorDraw get() = getMessage("Spectator.ItWasADraw")

val TitleConfig.spectator get() = BySides { getMessage("Spectator.${it.standardName}Won") }
val TitleConfig.youArePlayingAs get() = BySides { getMessage("YouArePlayingAs.${it.standardName}") }
val TitleConfig.yourTurn by TitleConfig

fun cArgs(args: Array<String>, min: Int = 0, max: Int = Int.MAX_VALUE) {
    cRequire(args.size in min..max, Config.error.wrongArgumentsNumber)
}

fun cPerms(p: CommandSender, perm: String) {
    cRequire(p.hasPermission(perm), Config.error.noPermission)
}

fun cPlayer(p: CommandSender) {
    contract {
        returns() implies (p is Player)
    }
    cRequire(p is Player, Config.error.notPlayer)
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

fun cServerPlayer(name: String) = cNotNull(Bukkit.getPlayer(name), Config.error.playerNotFound)

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
