package gregc.gregchess.fabric.mixin

import gregc.gregchess.chess.Piece
import gregc.gregchess.fabric.*
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.entity.EntityType
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.nbt.NbtCompound
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.world.World
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Unique
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo


@Mixin(PlayerEntity::class)
abstract class PlayerMixin(entityType: EntityType<out LivingEntity>?, world: World?) :
    PlayerExtraInfo, LivingEntity(entityType, world) {

    @Unique
    private var gregchessDirty: Boolean = false

    @get:Unique
    @set:Unique
    @Unique
    override var `gregchess$heldPiece`: Piece? = null
        set(v) {
            if (field != v)
                gregchessDirty = true
            field = v
        }

    @Inject(method = ["writeCustomDataToNbt(Lnet/minecraft/nbt/NbtCompound;)V"], at = [At("TAIL")])
    @Suppress("UNUSED_PARAMETER")
    fun writeGregchessInfo(nbt: NbtCompound, info: CallbackInfo) {
        nbt.put("GregChess", writeExtraInfo(NbtCompound()))
    }

    @Inject(method = ["readCustomDataFromNbt(Lnet/minecraft/nbt/NbtCompound;)V"], at = [At("TAIL")])
    @Suppress("UNUSED_PARAMETER")
    fun readGregchessInfo(nbt: NbtCompound, info: CallbackInfo) {
        readExtraInfo(nbt.getCompound("GregChess"))
    }

    @Inject(method = ["tick()V"], at = [At("TAIL")])
    @Suppress("UNUSED_PARAMETER", "CAST_NEVER_SUCCEEDS")
    fun syncGregchessInfo(info: CallbackInfo) {
        if (world?.isClient == false) {
            if (gregchessDirty) {
                val b = PacketByteBufs.create()
                b.writeNbt(writeExtraInfo(NbtCompound()))
                ServerPlayNetworking.send(this as ServerPlayerEntity, GregChess.PLAYER_EXTRA_INFO_SYNC, b)
            }
        }
    }


}