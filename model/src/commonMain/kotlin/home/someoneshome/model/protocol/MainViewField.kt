package home.someoneshome.model.protocol

/**
 * **One character in every upload: whether this phone is on the main view** (D-307, the owner's
 * 2026-11-20 rulings).
 *
 * *In order to report a player you must not be in a minigame. You must just be in the main view
 * (that being first or second page).* The house drives the report (D-301), so it has to know where
 * the reader's phone is: on springboard page 1 or 2 (a banner over the page included) or on the
 * report screen that took one over, and in no Subroutine, scan, briefing, Egress widget, meeting
 * or lantern. The
 * phone answers that question once a beat, in its upload, and the house starts no hold and keeps
 * none running on an upload that says [OFF].
 *
 * ### One width, from every phone, every beat (D-217)
 *
 * [CHARS] characters, [ON] or [OFF], from every seat whatever its role, out seats included: the
 * upload is the same length and on the same cadence for every phone at every moment, and which of
 * the two it carries depends on the screen the person is looking at, never on who they are. An
 * Insider's fake Subroutine is the same screen as a Resident's real one (rule 8), and the Insider's
 * page 2 is the same page as a Resident's (D-142), so the character is the same for both at the same
 * screen. What it says is what the room can already see: whether that phone is lit as the
 * springboard or as some activity (D-106, D-112). The owner's 2026-11-20 ruling: what a phone knows
 * internally is not a leak; what matters is that nothing a person can see, hear or feel gives a role
 * away.
 *
 * ### It fails closed
 *
 * Anything but exactly [ON] reads as off the main view ([reads]): a field that did not read starts
 * no hold and cancels a running one. The failure is *my report did not start*, noticed in seconds,
 * never a report started from inside a Subroutine.
 *
 * Its own field beside the sightings, the request slot, the clock probe and the ranging report, so
 * none of them moved. Plain text on the Wi-Fi, as the request slot is (D-212: *length is the
 * guarantee; content is only as private as the transport*).
 */
object MainViewField {

    /** The field's width, always. */
    const val CHARS: Int = 1

    /** On the main view: page 1 or 2, or the report screen. */
    const val ON: String = "1"

    /** Anywhere else, and every caller that is not the phone's own uploader. */
    const val OFF: String = "0"

    /** The field for [on]. */
    fun of(on: Boolean): String = if (on) ON else OFF

    /** Whether [field] is a field a packer builds: [ON] or [OFF]. */
    fun isField(field: String): Boolean = field == ON || field == OFF

    /** **Whether [field] says the main view.** Only exactly [ON] does: anything else is off it. */
    fun reads(field: String): Boolean = field == ON
}
