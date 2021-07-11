package gregc.gregchess.chess

import gregc.gregchess.*
import gregc.gregchess.chess.component.*
import gregc.gregchess.chess.variant.ChessVariant
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import kotlin.reflect.KClass

object SettingsManager {

    private val NO_ARENAS = ErrorMsg("NoArenas")

    private fun getClockSettings(): Map<String, ChessClock.Settings> =
        config["Settings.Clock"].childrenViews.orEmpty().mapValues { (_, it) ->
            val t = it.getEnum("Type", ChessClock.Type.INCREMENT, false)
            val initial = it.getDuration("Initial")
            val increment = if (t.usesIncrement) it.getDuration("increment") else 0.seconds
            ChessClock.Settings(t, initial, increment)
        }

    private fun <T,R> chooseOrParse(opts: Map<T,R>, v: T?, parse: (T) -> R?): R? = opts[v] ?: v?.let(parse)

    private fun <T> MutableCollection<T>.addMaybe(v: T?) {
        v?.let { this += it }
    }

    private val componentParsers = mutableMapOf<KClass<out Component.Settings<*>>, (View) -> Component.Settings<*>>()

    operator fun <T: Component.Settings<*>> set(cl: KClass<T>, f: (View) -> T) {
        componentParsers[cl] = f
    }

    inline operator fun <reified T: Component.Settings<*>> plusAssign(noinline f: (View) -> T) {
        this[T::class] = f
    }

    fun getSettings(): List<GameSettings> =
        config["Settings.Presets"].childrenViews.orEmpty().map { (key, child) ->
            val simpleCastling = child.getDefaultBoolean("SimpleCastling", false)
            val variant = ChessVariant[child.getOptionalString("Variant")]
            val components = buildList<Component.Settings<*>> {
                this += cNotNull(ArenaManager.freeAreas.firstOrNull(), NO_ARENAS)
                this += Chessboard.Settings[child.getOptionalString("Board")]
                addMaybe(chooseOrParse(getClockSettings(), child.getOptionalString("Clock"), ChessClock.Settings::parse))
                this += BukkitRenderer.Settings(child.getDefaultInt("TileSize", 3))
                this += BukkitScoreboardManager.Settings
                this += BukkitEventRelay.Settings
                (variant.requiredComponents + variant.requiredComponents).mapNotNull { componentParsers[it] }.forEach {
                    this += it(child)
                }
            }
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