package gregc.gregchess.bukkit

import gregc.gregchess.bukkitutils.*
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract


internal fun message(n: String) = Message(config, "Message.$n")
internal fun title(n: String) = Message(config, "Title.$n")
internal fun err(n: String) = Message(config, "Message.Error.$n")

@JvmField
internal val WRONG_ARGUMENTS_NUMBER = err("WrongArgumentsNumber")
@JvmField
internal val WRONG_ARGUMENT = err("WrongArgument")
@JvmField
internal val INTERNAL_ERROR = err("InternalError")
@JvmField
internal val NO_PERMISSION = err("NoPermission")
@JvmField
internal val NOT_PLAYER = err("NotPlayer")
@JvmField
internal val PLAYER_NOT_FOUND = err("PlayerNotFound")
@JvmField
internal val WRONG_DURATION_FORMAT = err("WrongDurationFormat")
@JvmField
internal val YOU_IN_MATCH = err("InMatch.You")
@JvmField
internal val OPPONENT_IN_MATCH = err("InMatch.Opponent")
@JvmField
internal val YOU_NOT_IN_MATCH = err("NotInMatch.You")
@JvmField
internal val PLAYER_NOT_IN_MATCH = err("NotInMatch.Player")
@JvmField
internal val OPPONENT_NOT_HUMAN = err("NotHuman.Opponent")
@JvmField
internal val NO_ARENAS = err("NoArenas")

@OptIn(ExperimentalContracts::class)
internal fun cRequire(e: Boolean, msg: Message) {
    contract {
        returns() implies e
    }
    if (!e) throw CommandException(msg)
}

internal fun <T> T?.cNotNull(msg: Message): T = this ?: throw CommandException(msg)

internal inline fun <reified T, reified R : T> T.cCast(msg: Message): R = (this as? R).cNotNull(msg)

internal inline fun <T> cWrongArgument(block: () -> T): T = try {
    block()
} catch (e: DurationFormatException) {
    throw CommandException(WRONG_DURATION_FORMAT, e)
} catch (e: IllegalArgumentException) {
    throw CommandException(WRONG_ARGUMENT, e)
}