package gregc.gregchess.fabric.player

import com.mojang.authlib.GameProfile
import gregc.gregchess.Color
import gregc.gregchess.fabric.GameProfileSerializer
import gregc.gregchess.fabric.results.text
import gregc.gregchess.player.ChessPlayer
import gregc.gregchess.results.MatchResults
import gregc.gregchess.results.MatchScore
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import net.minecraft.screen.NamedScreenHandlerFactory
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import java.util.*
import kotlin.coroutines.*

// TODO: store current games
@Serializable
class FabricPlayer(
    @Contextual val server: MinecraftServer,
    @Serializable(with = GameProfileSerializer::class) val gameProfile: GameProfile
) : ChessPlayer<FabricChessSide> {
    constructor(entity: ServerPlayerEntity) : this(entity.server, entity.gameProfile)

    override fun createChessSide(color: Color): FabricChessSide = FabricChessSide(this, color)

    val name: String get() = gameProfile.name
    val uuid: UUID get() = gameProfile.id

    private inline fun <T : Any> onNull(value: T?, callback: T.() -> Unit) {
        value?.callback()
    }

    val entity: ServerPlayerEntity? get() = server.playerManager.getPlayer(gameProfile.id)
    fun sendMessage(msg: Text) = onNull(entity) { sendMessage(msg, false) }
    suspend fun <T> openHandledScreen(default: T, factory: (Continuation<T>) -> NamedScreenHandlerFactory): T =
        suspendCoroutine { entity?.openHandledScreen(factory(it)) ?: it.resume(default) }
    suspend fun <T : Any> openHandledScreen(factory: (Continuation<T?>) -> NamedScreenHandlerFactory): T? =
        openHandledScreen(null, factory)

    fun showMatchResults(color: Color, results: MatchResults) {
        val wonlostdraw = when(results.score) {
            MatchScore.Draw -> "draw_by"
            MatchScore.Victory(color) -> "you_won_by"
            else -> "you_lost_by"
        }
        sendMessage(Text.translatable("chess.gregchess.$wonlostdraw", results.text))
    }
}