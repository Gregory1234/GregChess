package gregc.gregchess.bukkitutils

import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import kotlin.reflect.KClass
import kotlin.reflect.safeCast

inline fun itemStack(material: Material, builder: ItemStack.() -> Unit) = ItemStack(material).apply(builder)

inline fun <reified T : ItemMeta> ItemStack.meta(builder: T.() -> Unit) {
    val curMeta = getOrCreateItemMeta(T::class)
    itemMeta = curMeta?.apply(builder)
}

@PublishedApi
internal fun <T : ItemMeta> ItemStack.getOrCreateItemMeta(cl: KClass<T>): T? =
    cl.safeCast(itemMeta) ?: cl.safeCast(Bukkit.getItemFactory().getItemMeta(type))

@JvmName("simpleMeta")
inline fun ItemStack.meta(builder: ItemMeta.() -> Unit) = meta<ItemMeta>(builder)

var ItemMeta.name: String?
    get() = if (hasDisplayName()) displayName else null
    set(v) = setDisplayName(v)