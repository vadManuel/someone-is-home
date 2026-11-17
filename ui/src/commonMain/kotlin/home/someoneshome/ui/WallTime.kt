package home.someoneshome.ui

/**
 * **The phone's own wall clock, read for display and for nothing else** (D-243, provisional: owner
 * to confirm).
 *
 * The status strip, the lantern and the moment each house message landed were the design's drawn
 * times, `21:07` and `SATURDAY 16 AUGUST` and `20:58`, on every phone all evening. On a phone with a
 * house attached they now read this; a phone with no house keeps the drawn times, so the render
 * harness still draws the mockup.
 *
 * **It decides nothing.** No screen moves on it, no clock in the round is measured with it, and
 * `core` never sees it (rule 4): the house's clock is the house's. It is handed down by the app root
 * the way the radio and the torch are, because `ui` cannot see `platform`; every render and every
 * test that does not hand one down gets a phone that has no time to show.
 */
data class WallTime(
    val hour: Int,
    val minute: Int,
    /** ISO: 1 is Monday, 7 is Sunday. */
    val weekday: Int,
    val day: Int,
    /** 1 is January. */
    val month: Int,
) {
    /** `21:07`: twenty-four hours, the form the design draws every time in. */
    val clock: String get() = "${pad(hour)}:${pad(minute)}"

    /** `SATURDAY 16 AUGUST`, the lantern's line. A field out of range words as nothing, never throws. */
    val date: String
        get() = listOf(WEEKDAYS.getOrNull(weekday - 1), day.toString(), MONTHS.getOrNull(month - 1))
            .filterNotNull().joinToString(" ")

    private fun pad(n: Int): String = n.toString().padStart(2, '0')

    private companion object {
        val WEEKDAYS = listOf("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY")
        val MONTHS = listOf(
            "JANUARY", "FEBRUARY", "MARCH", "APRIL", "MAY", "JUNE",
            "JULY", "AUGUST", "SEPTEMBER", "OCTOBER", "NOVEMBER", "DECEMBER",
        )
    }
}

/**
 * **A text from the house and the time it landed on this phone** (D-243). The house thread's rows,
 * on a phone with a house attached. The time is this phone's wall clock at the arrival, so it is
 * when the phone was told, not when the house wrote it; a message caught up after a dropped
 * connection carries the time it was caught up.
 */
data class HouseText(val at: String, val body: String)
