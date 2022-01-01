package gregc.gregchess.bukkitutils.command

import gregc.gregchess.bukkitutils.*
import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import org.bukkit.entity.Player
import java.util.*
import kotlin.time.Duration

abstract class CommandArgumentType<R>(val name: String, val failMessage: Message? = null) {
    abstract fun tryParse(strings: List<String>): Pair<R, List<String>>?
    open fun autocomplete(ctx: ExecutionContext<*>, strings: List<String>): Set<String>? =
        if (tryParse(strings) == null) emptySet() else null
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

class PlayerArgument(name: String, msg: Message) : CommandArgumentType<Player>(name, msg) {
    override fun tryParse(strings: List<String>): Pair<Player, List<String>>? = strings.firstOrNull()?.let { n ->
        Bukkit.getPlayer(n)?.let {
            Pair(it, strings.drop(1))
        }
    }

    override fun autocomplete(ctx: ExecutionContext<*>, strings: List<String>): Set<String>? =
        if (strings.isEmpty()) Bukkit.getOnlinePlayers().map { it.name }.toSet() else null
}

class OfflinePlayerArgument(name: String, msg: Message) : CommandArgumentType<OfflinePlayer>(name, msg) {
    override fun tryParse(strings: List<String>): Pair<OfflinePlayer, List<String>>? = strings.firstOrNull()?.let { n ->
        getOfflinePlayerByName(n)?.let {
            Pair(it, strings.drop(1))
        }
    }

    override fun autocomplete(ctx: ExecutionContext<*>, strings: List<String>): Set<String>? =
        if (strings.isEmpty()) Bukkit.getOnlinePlayers().map { it.name }.toSet() else null
}

class DurationArgument(name: String, failMessage: Message) : CommandArgumentType<Duration>(name, failMessage) {
    override fun tryParse(strings: List<String>): Pair<Duration, List<String>>? =
        strings.firstOrNull()?.toDurationOrNull()?.to(strings.drop(1))
}