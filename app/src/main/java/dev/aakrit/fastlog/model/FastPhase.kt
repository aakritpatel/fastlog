package dev.aakrit.fastlog.model

/**
 * Well-documented metabolic stages of a fast, keyed to hours elapsed.
 * These are general physiological descriptions, not medical advice, and the
 * exact timing varies a lot between people.
 */
data class FastPhase(
    val startHour: Double,
    val endHour: Double,
    val title: String,
    val description: String,
) {
    companion object {
        val ALL: List<FastPhase> = listOf(
            FastPhase(
                0.0, 4.0, "Fed state",
                "Your body is still absorbing your last meal. Blood sugar and insulin are elevated and energy comes straight from that food."
            ),
            FastPhase(
                4.0, 12.0, "Glycogen burning",
                "Absorption is finished and insulin is falling. Your body turns to glycogen, the sugar stored in your liver and muscles."
            ),
            FastPhase(
                12.0, 16.0, "Fat burning begins",
                "Liver glycogen is running low, so your body starts releasing stored fat for fuel in earnest."
            ),
            FastPhase(
                16.0, 24.0, "Ketosis",
                "Fat is now the main fuel. Your liver converts it into ketones, which many people notice as steadier energy and appetite."
            ),
            FastPhase(
                24.0, 36.0, "Autophagy rising",
                "Cellular housekeeping picks up. Cells begin recycling damaged components and worn-out proteins."
            ),
            FastPhase(
                36.0, 48.0, "Deep ketosis",
                "Ketone levels are high and growth hormone is elevated, which helps preserve lean muscle during a longer fast."
            ),
            FastPhase(
                48.0, Double.MAX_VALUE, "Extended fast",
                "Autophagy and immune cell turnover are at their peak. Fasts this long are worth discussing with a doctor first."
            ),
        )

        fun forHours(hours: Double): FastPhase =
            ALL.firstOrNull { hours >= it.startHour && hours < it.endHour } ?: ALL.last()

        /** Index of the current phase, for showing progress through the stages. */
        fun indexForHours(hours: Double): Int =
            ALL.indexOf(forHours(hours))
    }
}
