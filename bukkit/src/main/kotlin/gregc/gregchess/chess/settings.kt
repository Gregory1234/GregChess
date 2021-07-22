package gregc.gregchess.chess

import gregc.gregchess.*
import gregc.gregchess.chess.component.*
import gregc.gregchess.chess.variant.*
import org.bukkit.Material
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import kotlin.reflect.KClass

object SettingsManager {

    private val clockSettings: Map<String, ChessClock.Settings>
        get() = config.getConfigurationSection("Settings.Clock")?.getKeys(false).orEmpty().associateWith {
            val section = config.getConfigurationSection("Settings.Clock.$it")!!
            val t = ChessClock.Type.valueOf(section.getString("Type", ChessClock.Type.INCREMENT.toString())!!)
            val initial = section.getString("Initial")?.asDurationOrNull()!!
            val increment = if (t.usesIncrement) section.getString("Increment")?.asDurationOrNull()!! else 0.seconds
            ChessClock.Settings(t, initial, increment)
        }

    fun <T, R> chooseOrParse(opts: Map<T, R>, v: T?, parse: (T) -> R?): R? = opts[v] ?: v?.let(parse)

    private val componentParsers = mutableMapOf<KClass<out Component.Settings<*>>, (ConfigurationSection) -> Component.Settings<*>?>()

    private val NO_ARENAS = ErrorMsg("NoArenas")

    fun start() {
        this += { ArenaManager.freeAreas.firstOrNull().cNotNull(NO_ARENAS) }
        this += { Chessboard.Settings[it.getString("Board")] }
        this += { chooseOrParse(clockSettings, it.getString("Clock"), ChessClock.Settings::parse) }
        this += { BukkitRenderer.Settings(it.getInt("TileSize", 3)) }
        this += { ScoreboardManager.Settings }
        this += { BukkitEventRelay.Settings }
        this += { ThreeChecks.CheckCounter.Settings(it.getInt("CheckLimit", 3).toUInt()) }
        this += { AtomicChess.ExplosionManager.Settings }
        this += { SpectatorManager.Settings }
    }

    operator fun <T : Component.Settings<*>> set(cl: KClass<T>, f: (ConfigurationSection) -> T?) {
        componentParsers[cl] = f
    }

    inline operator fun <reified T : Component.Settings<*>> plusAssign(noinline f: (ConfigurationSection) -> T?) {
        this[T::class] = f
    }

    private val extraComponents = mutableListOf<KClass<out Component.Settings<*>>>(
        Arena::class, Chessboard.Settings::class, ChessClock.Settings::class, SpectatorManager.Settings::class,
        ScoreboardManager.Settings::class, BukkitRenderer.Settings::class, BukkitEventRelay.Settings::class
    )

    operator fun <T : Component.Settings<*>> plusAssign(cl: KClass<T>) {
        extraComponents += cl
    }

    fun getSettings(): List<GameSettings> =
        config.getConfigurationSection("Settings.Presets")?.getKeys(false).orEmpty().map { name ->
            val section = config.getConfigurationSection("Settings.Presets.$name")!!
            val simpleCastling = section.getBoolean("SimpleCastling", false)
            val variant = ChessVariant[section.getString("Variant")?.asIdent()]
            val components = (extraComponents + variant.requiredComponents + variant.requiredComponents)
                .mapNotNull { componentParsers[it] }.mapNotNull { it(section) }
            GameSettings(name, simpleCastling, variant, components)
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