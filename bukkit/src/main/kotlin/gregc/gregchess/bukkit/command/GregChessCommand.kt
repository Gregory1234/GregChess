package gregc.gregchess.bukkit.command

import gregc.gregchess.Pos
import gregc.gregchess.board.FEN
import gregc.gregchess.bukkit.*
import gregc.gregchess.bukkit.player.*
import gregc.gregchess.bukkit.registry.toKey
import gregc.gregchess.bukkitutils.command.*
import gregc.gregchess.bukkitutils.player.BukkitCommandSender
import gregc.gregchess.player.HumanChessSideFacade
import gregc.gregchess.registry.FiniteRegistryView

internal fun playerArgument(name: String) = playerArgument(name, PLAYER_NOT_FOUND, BukkitPlayerProvider)

internal fun offlinePlayerArgument(name: String) = offlinePlayerArgument(name, PLAYER_NOT_FOUND, BukkitPlayerProvider)

internal fun CommandBuilder<*>.requirePermission(permission: String) = requirePermission(permission, NO_PERMISSION)

internal fun <S : BukkitCommandSender> CommandBuilder<S>.subcommand(name: String, builder: CommandBuilder<S>.() -> Unit) {
    literal(name) {
        requirePermission("gregchess.chess.$name")
        builder()
    }
}

internal fun CommandBuilder<BukkitCommandSender>.playerSubcommand(name: String, builder: CommandBuilder<BukkitPlayer>.() -> Unit) {
    literal(BukkitPlayer::class, name) {
        requirePermission("gregchess.chess.$name")
        builder()
    }
}

internal fun CommandBuilder<BukkitPlayer>.requireHumanOpponent(): ExecutionContext<BukkitPlayer>.() -> HumanChessSideFacade<*> {
    validate(OPPONENT_NOT_HUMAN) { sender.currentSide?.opponent is HumanChessSideFacade }
    return { sender.currentSide!!.opponent as HumanChessSideFacade }
}

internal fun CommandBuilder<BukkitPlayer>.requireMatch(): ExecutionContext<BukkitPlayer>.() -> BukkitChessSideFacade {
    validate(YOU_NOT_IN_MATCH) { sender.currentMatch != null }
    return { sender.currentSide!! }
}

internal fun CommandBuilder<*>.requireNoMatch() {
    validate(YOU_IN_MATCH) { sender !is BukkitPlayer || ((sender as BukkitPlayer).currentMatch == null && (sender as BukkitPlayer).spectatedMatch == null) }
}

internal fun posArgument(name: String) = SimpleArgument(name) { try { Pos.parseFromString(it) } catch (e : IllegalArgumentException) { null } }

internal fun <T : Any> registryArgument(name: String, registryView: FiniteRegistryView<String, T>, condition: ((T) -> Boolean)? = null) =
    SimpleArgument(
        name,
        defaults = { registryView.keys.filter { condition?.invoke(registryView[it]) ?: true }.map { it.toString() }.toSet() }
    ) {
        try {
            registryView[it.toKey()].also { v -> require(condition?.invoke(v) ?: true) }
        } catch (e: IllegalArgumentException) {
            null
        }
    }

internal class FENArgument(name: String) : CommandArgumentType<FEN>(name) {
    override fun tryParse(args: StringArguments): FEN? = try {
        FEN.parseFromString(args.nextMany().joinToString(" "))
    } catch (e: FEN.FENFormatException) {
        null
    }
}

internal fun durationArgument(name: String) = durationArgument(name, WRONG_DURATION_FORMAT)