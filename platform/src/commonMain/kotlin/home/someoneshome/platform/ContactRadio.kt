package home.someoneshome.platform

import home.someoneshome.model.ContactToken

/**
 * **What this phone's radio heard: a token, and how loud** (D-212, D-217).
 *
 * No window: the window is the phone's own clock's, stamped by the uploader at the moment of the
 * upload, so the radio never has to know what a window is. No seat, and there is nowhere to put
 * one: a token is resolvable by the house alone (D7).
 */
data class RadioReading(val token: ContactToken, val rssi: Int)

/**
 * **The contact radio, as the app sees it** (D-217). A phone holds the CoreBluetooth one
 * ([bluetoothContactRadio], D-218), the bench a simulated one, and the tests [NoRadio].
 *
 * Four calls and nothing that could carry a role or an armed bit (D-212 ruling 5): the phone says
 * which token to advertise, whether to scan, takes what was heard, and goes quiet when the round is
 * over. **Every phone in a round advertises; a phone that is out advertises and never scans**
 * (D-008). The radio has no opinion on which phone it is in, and the uploader decides scanning off
 * what the house pushed this phone and nothing else.
 *
 * ### Allocation (rule 7)
 *
 * [drain] returns what was heard since the last call and is called once per upload, so the list is
 * one small allocation per second. An implementation should keep its own buffer bounded (the upload
 * carries at most `SightingUpload.SLOTS`), because a busy room heard at radio rate is the thread
 * rule 7 names.
 */
interface ContactRadio {

    /** Advertise [token] from now until told otherwise. Called whenever the window turns. */
    fun advertise(token: ContactToken)

    /** Scan, or stop. A Revoked or Restrained phone stops, and keeps advertising (D-008). */
    fun scan(on: Boolean)

    /** Everything heard since the last call, oldest first. Empty while not scanning. */
    fun drain(): List<RadioReading>

    /** The round is over: stop advertising and stop scanning. */
    fun silence()
}

/**
 * **No radio at all: advertises nothing and hears nothing** (D-217).
 *
 * What a test holds when the radio is not the question, and what every phone held until the
 * CoreBluetooth radio (D-218). It is not a fake of anything: the uploader still runs on the fixed
 * cadence and still sends a full-size upload of filler every second, and the house resolves nothing
 * from it.
 */
object NoRadio : ContactRadio {
    override fun advertise(token: ContactToken) = Unit
    override fun scan(on: Boolean) = Unit
    override fun drain(): List<RadioReading> = emptyList()
    override fun silence() = Unit
}

/**
 * **Wall-clock milliseconds since the epoch**, the clock contact windows are cut from (D-217).
 *
 * Not [monotonicNanos]: that one starts at each phone's own boot, so two phones' readings share no
 * origin and a window index cut from it would differ by hours between them. The window has to be
 * the same number on the advertiser, on the scanner and on the house, and the one clock three
 * phones already agree on to well inside a second is the wall clock, kept by the network. The
 * resolver accepts the house's window and the one before it (D-213), which absorbs a few hundred
 * milliseconds of disagreement at an edge.
 *
 * Nothing in the rules reads this: the window is an argument to them, and what is recorded is what
 * the house concluded.
 */
expect fun wallClockMillis(): Long
