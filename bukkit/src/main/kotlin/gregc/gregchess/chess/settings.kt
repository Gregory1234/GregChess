package gregc.gregchess.chess

import gregc.gregchess.*
import gregc.gregchess.chess.component.*
import gregc.gregchess.chess.variant.ChessVariant
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import kotlin.reflect.KClass

object SettingsManager {

    val clockSettings: Map<String, ChessClock.Settings>
        get() = config["Settings.Clock"].childrenViews.orEmpty().mapValues { (_, it) ->
            val t = it.getEnum("Type", ChessClock.Type.INCREMENT, false)
            val initial = it.getDuration("Initial")
            val increment = if (t.usesIncrement) it.getDuration("increment") else 0.seconds
            ChessClock.Settings(t, initial, increment)
        }

    fun <T, R> chooseOrParse(opts: Map<T, R>, v: T?, parse: (T) -> R?): R? = opts[v] ?: v?.let(parse)

    private val componentParsers = mutableMapOf<KClass<out Component.Settings<*>>, (View) -> Component.Settings<*>?>()

    operator fun <T : Component.Settings<*>> set(cl: KClass<T>, f: (View) -> T?) {
        componentParsers[cl] = f
    }

    inline operator fun <reified T : Component.Settings<*>> plusAssign(noinline f: (View) -> T?) {
        this[T::class] = f
    }

    private val extraComponents = mutableListOf<KClass<out Component.Settings<*>>>(
        Arena::class, Chessboard.Settings::class, ChessClock.Settings::class,
        BukkitScoreboardManager.Settings::class, BukkitRenderer.Settings::class, BukkitEventRelay.Settings::class
    )

    operator fun <T : Component.Settings<*>> plusAssign(cl: KClass<T>) {
        extraComponents += cl
    }

    fun getSettings(): List<GameSettings> =
        config["Settings.Presets"].childrenViews.orEmpty().map { (key, child) ->
            val simpleCastling = child.getDefaultBoolean("SimpleCastling", false)
            val variant = ChessVariant[child.getOptionalString("Variant")]
            val components = (extraComponents + variant.requiredComponents + variant.requiredComponents)
                .mapNotNull { componentParsers[it] }.mapNotNull { it(child) }
            GameSettings(key, simpleCastling, variant, components)
        }

}

private val CHOOSE_SETTINGS = message("ChooseSettings")

suspend fun Player.openSettingsMenu() =
    openMenu(CHOOSE_SETTINGS, SettingsManager.getSettings().mapIndexed { index, s ->
        val item = ItemStack(Material.IRON_BLOCK)
        val meta = item.itemMeta!!
        meta.setDisplayName(s.name)
        item.itemMeta = meta
        ScreenOption(item, s, InventoryPosition.fromIndex(index))
    })