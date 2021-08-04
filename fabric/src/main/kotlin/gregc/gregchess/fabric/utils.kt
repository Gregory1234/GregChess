package gregc.gregchess.fabric

import gregc.gregchess.chess.Piece
import gregc.gregchess.fabric.chess.id
import gregc.gregchess.fabric.chess.pieceOfId
import net.minecraft.block.entity.BlockEntity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.nbt.NbtCompound
import net.minecraft.util.Identifier
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

const val MOD_ID = "gregchess"
const val MOD_NAME = "GregChess"

fun ident(name: String) = Identifier(MOD_ID, name)

class BlockEntityDirtyDelegate<T>(var value: T) : ReadWriteProperty<BlockEntity, T> {
    override operator fun getValue(thisRef: BlockEntity, property: KProperty<*>): T = value

    override operator fun setValue(thisRef: BlockEntity, property: KProperty<*>, value: T) {
        this.value = value
        thisRef.markDirty()
    }
}

interface PlayerExtraInfo {
    var `gregchess$heldPiece`: Piece?
}

fun PlayerExtraInfo.writeExtraInfo(nbt: NbtCompound): NbtCompound {
    `gregchess$heldPiece`?.let {
        nbt.putString("HeldPiece", it.id.toString())
    }
    return nbt
}

fun PlayerExtraInfo.readExtraInfo(nbt: NbtCompound) {
    if (nbt.contains("HeldPiece")) {
        `gregchess$heldPiece` = pieceOfId(Identifier(nbt.getString("HeldPiece")))
    }
}

@Suppress("CAST_NEVER_SUCCEEDS")
var PlayerEntity.heldPiece
    get() = (this as PlayerExtraInfo).`gregchess$heldPiece`
    set(v) {
        (this as PlayerExtraInfo).`gregchess$heldPiece` = v
    }