package gregc.gregchess.chess.component

import gregc.gregchess.RegistryType
import gregc.gregchess.chess.ChessGame
import kotlin.reflect.KClass

interface ComponentData<out T : Component> {
    fun getComponent(game: ChessGame): T
}
abstract class Component(protected val game: ChessGame) {
    abstract val data: ComponentData<*>

    open fun validate() {}
}

val KClass<out Component>.componentDataClass get() = RegistryType.COMPONENT_CLASS[this]
val KClass<out Component>.componentModule get() = RegistryType.COMPONENT_CLASS.getModule(this)
val KClass<out Component>.componentName get() = RegistryType.COMPONENT_CLASS[this]

val KClass<out ComponentData<*>>.componentClass get() = RegistryType.COMPONENT_DATA_CLASS[this]
@get:JvmName("getComponentDataModule")
val KClass<out ComponentData<*>>.componentModule get() = RegistryType.COMPONENT_DATA_CLASS.getModule(this)
@get:JvmName("getComponentDataName")
val KClass<out ComponentData<*>>.componentName get() = componentClass.componentName

class ComponentNotFoundException(cl: KClass<out Component>) : Exception(cl.toString())
class ComponentDataNotFoundException(cl: KClass<out ComponentData<*>>) : Exception(cl.toString())