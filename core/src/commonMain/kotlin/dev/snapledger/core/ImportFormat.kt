package dev.snapledger.core

/** Versions of the import format (see docs/03-data-format.md) this build understands. */
object ImportFormat {
    const val CURRENT_VERSION = "1.0"

    fun isSupported(version: String): Boolean = version.substringBefore('.') == CURRENT_VERSION.substringBefore('.')
}
