package gregc.gregchess.fabric.chess

import gregc.gregchess.chess.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.LiteralText
import java.util.*

fun ServerPlayerEntity.showGameResults(side: Side, results: GameResults) {
    sendMessage(LiteralText(results.endReason.name), false)
}

@Serializable(with = FabricPlayerInfo.Serializer::class)
data class FabricPlayerInfo(val uuid: UUID): ChessPlayerInfo {
    object Serializer: KSerializer<FabricPlayerInfo> {
        override val descriptor = PrimitiveSerialDescriptor("FabricPlayerInfo", PrimitiveKind.STRING)

        override fun serialize(encoder: Encoder, value: FabricPlayerInfo) = encoder.encodeString(value.uuid.toString())

        override fun deserialize(decoder: Decoder) =
            FabricPlayerInfo(UUID.fromString(decoder.decodeString()))
    }

    override val name: String get() = "fabric player"
    override fun getPlayer(side: Side, game: ChessGame) = FabricPlayer(this, side, game)
}

class FabricPlayer(info: FabricPlayerInfo, side: Side, game: ChessGame) :
    ChessPlayer(info, side, game) {
    val uuid = info.uuid

    override fun init() {
        //player.sendMessage(LiteralText(side.name),false)
    }
}

val ServerPlayerEntity.cpi get() = FabricPlayerInfo(uuid)

inline fun BySides<ChessPlayer>.forEachReal(block: (UUID) -> Unit) {
    toList().filterIsInstance<FabricPlayer>().map { it.uuid }.distinct().forEach(block)
}

inline fun BySides<ChessPlayer>.forEachUnique(block: (FabricPlayer) -> Unit) {
    val players = toList().filterIsInstance<FabricPlayer>()
    if (players.size == 2 && players.all {it.info == players[0].info})
        players.filter { it.hasTurn }.forEach(block)
    else
        players.forEach(block)
}