package gregc.gregchess.move.trait

import gregc.gregchess.*
import gregc.gregchess.move.Move
import gregc.gregchess.move.MoveEnvironment
import gregc.gregchess.registry.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule

@Serializable(with = MoveTraitSerializer::class)
interface MoveTrait {
    val type: MoveTraitType<out @SelfType MoveTrait>
    val shouldComeFirst: Boolean get() = false
    val shouldComeLast: Boolean get() = false
    val shouldComeBefore: Set<MoveTraitType<*>> get() = emptySet()
    val shouldComeAfter: Set<MoveTraitType<*>> get() = emptySet()
    fun execute(env: MoveEnvironment, move: Move) {}
    fun undo(env: MoveEnvironment, move: Move) {}
}

@Serializable(with = MoveTraitType.Serializer::class)
class MoveTraitType<T : MoveTrait>(val serializer: KSerializer<T>): NameRegistered {
    object Serializer : NameRegisteredSerializer<MoveTraitType<*>>("MoveTraitType", CoreRegistry.MOVE_TRAIT_TYPE)

    override val key get() = CoreRegistry.MOVE_TRAIT_TYPE[this]

    override fun toString(): String = CoreRegistry.MOVE_TRAIT_TYPE.simpleElementToString(this)

    @RegisterAll(MoveTraitType::class)
    companion object {

        internal val AUTO_REGISTER = AutoRegisterType(MoveTraitType::class) { m, n, _ -> CoreRegistry.MOVE_TRAIT_TYPE[m, n] = this }

        @JvmField
        val HALFMOVE_CLOCK = MoveTraitType(DefaultHalfmoveClockTrait.serializer())
        @JvmField
        val CASTLES = MoveTraitType(CastlesTrait.serializer())
        @JvmField
        val PROMOTION = MoveTraitType(PromotionTrait.serializer())
        @JvmField
        val REQUIRE_FLAG = MoveTraitType(RequireFlagTrait.serializer())
        @JvmField
        val FLAG = MoveTraitType(FlagTrait.serializer())
        @JvmField
        val CHECK = MoveTraitType(CheckTrait.serializer())
        @JvmField
        val CAPTURE = MoveTraitType(CaptureTrait.serializer())
        @JvmField
        val TARGET = MoveTraitType(TargetTrait.serializer())
        @JvmField
        val SPAWN = MoveTraitType(SpawnTrait.serializer())
        @JvmField
        val CLEAR = MoveTraitType(ClearTrait.serializer())

        fun registerCore(module: ChessModule) = AutoRegister(module, listOf(AUTO_REGISTER)).registerAll<MoveTraitType<*>>()
    }
}

object MoveTraitSerializer : KeyRegisteredSerializer<MoveTraitType<*>, MoveTrait>("MoveTrait", MoveTraitType.Serializer) {

    @Suppress("UNCHECKED_CAST")
    override fun MoveTraitType<*>.valueSerializer(module: SerializersModule): KSerializer<MoveTrait> =
        serializer as KSerializer<MoveTrait>

    override val MoveTrait.key: MoveTraitType<*> get() = type

}