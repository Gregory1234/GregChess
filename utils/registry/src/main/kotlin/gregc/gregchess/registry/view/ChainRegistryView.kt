package gregc.gregchess.registry.view

import gregc.gregchess.registry.ChessModule

class ChainRegistryView<K, M, T>(val base: RegistryView<K, M>, val extension: RegistryView<M, T>) : RegistryView<K, T> {
    override fun getOrNull(module: ChessModule, key: K): T? =
        base.getOrNull(module, key)?.let { extension.getOrNull(module, it) }
}