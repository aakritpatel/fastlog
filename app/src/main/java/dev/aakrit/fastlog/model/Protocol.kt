package dev.aakrit.fastlog.model

/**
 * A fasting protocol. Every protocol here is available with no gating of any kind.
 */
data class Protocol(
    val id: String,
    val label: String,
    val subtitle: String,
    val fastHours: Double,
) {
    val eatHours: Double get() = 24.0 - fastHours

    companion object {
        const val CUSTOM_ID = "custom"

        val ALL: List<Protocol> = listOf(
            Protocol("12:12", "12:12", "Circadian rhythm", 12.0),
            Protocol("14:10", "14:10", "Gentle start", 14.0),
            Protocol("16:8", "16:8", "Leangains", 16.0),
            Protocol("18:6", "18:6", "Extended daily", 18.0),
            Protocol("20:4", "20:4", "Warrior", 20.0),
            Protocol("23:1", "OMAD", "One meal a day", 23.0),
            Protocol("24h", "24 hours", "Full day fast", 24.0),
            Protocol("36h", "36 hours", "Monk fast", 36.0),
            Protocol("48h", "48 hours", "Two day fast", 48.0),
            Protocol("72h", "72 hours", "Three day fast", 72.0),
        )

        fun byId(id: String): Protocol? =
            ALL.firstOrNull { it.id == id }

        /** Builds a protocol for an arbitrary user-chosen length. */
        fun custom(hours: Double): Protocol =
            Protocol(CUSTOM_ID, formatHours(hours), "Custom length", hours)

        /** Resolves a stored protocol id back to a Protocol, falling back to custom. */
        fun resolve(id: String, hours: Double): Protocol =
            byId(id) ?: custom(hours)

        fun formatHours(hours: Double): String {
            val whole = hours.toInt()
            val minutes = ((hours - whole) * 60).toInt()
            return if (minutes == 0) "${whole}h" else "${whole}h ${minutes}m"
        }
    }
}
