package gregc.gregchess.bukkitutils

import org.bukkit.NamespacedKey
import org.bukkit.configuration.ConfigurationSection

class BadConfigurationException(message: String): Exception(message)

// TODO: add functions for getting bukkit registry data with good error messages

fun ConfigurationSection.getConfigurationSectionOrThrow(path: String): ConfigurationSection =
    getConfigurationSection(path) ?: throw BadConfigurationException("Configuration section not found: $path")

fun ConfigurationSection.getStringDef(path: String, def: String): String = getString(path, def) ?: def

fun ConfigurationSection.getStringOrThrow(path: String): String =
    getString(path) ?: throw BadConfigurationException("Configuration string not found: $path")

fun ConfigurationSection.getNamespacedKeyOrThrow(path: String): NamespacedKey =
    getStringOrThrow(path).let { NamespacedKey.fromString(it) ?: throw BadConfigurationException("Bad namespaced key \"$it\" in configuration at: $path") }