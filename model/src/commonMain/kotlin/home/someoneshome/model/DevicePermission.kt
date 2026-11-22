package home.someoneshome.model

/**
 * **The grants an evening needs, and what the OS has said about each** (D-161).
 *
 * ### Why this is in `model` rather than in `platform`
 *
 * Two modules have to agree on it and neither may see the other: `platform` asks the OS, `ui`
 * draws the answer, and the boundary table has always forbidden the edge between them. `model` is
 * the one module both of them already depend on, so the vocabulary lives here the way `Haptic` and
 * `Refusal` do — a word both ends use, with no behaviour attached to it.
 *
 * ### Every one of them is asked at setup, in the light, and none of them is asked again
 *
 * D-161: *a player cannot join a lobby until device setup is complete, and complete means the whole
 * Perms set, camera included.* The argument is that **iOS asks once, ever, on whichever screen
 * first needs the thing** — and the screen that first needs the camera is a player alone in a dark
 * corridor, where a system modal is both a light nobody authored (rule 5) and a tell. Warming one
 * permission and leaving three to ambush the round is the same bug with a smaller blast radius.
 *
 * ### Every one is required to play, and each must read GRANTED (D-309)
 *
 * The owner, 2026-11-20: *every request we make in the game is required for one to play.* No row
 * opens the gate on anything short of [Grant.Granted] (see [Grant.opensTheGate]).
 *
 * **Local Network is not a row.** iOS gives no way to read that grant back, and it raises the
 * prompt by itself the first time the phone browses for a home or advertises one; the walk asked
 * for it and could only ever say ASKED. Whether it was given is found out where it always was: the
 * home goes on the air or it does not.
 *
 * **Notifications is not a row** (the owner, 2026-11-22, D-326: *"we don't use it"*). The app posts
 * no iOS notification and has no background mode; every notification in the game is drawn in-app.
 *
 * **The order is the order the rows are drawn in**, and it is deliberate: Camera first because it
 * is the one that ends an evening. Nearby Devices sits under Bluetooth, the other half of how the
 * house sees who is near whom (D-303).
 */
enum class DevicePermission(
    /** The row's own words on the Perms screen. Screen copy, in the closed vocabulary. */
    val label: String,
    /** What the evening cannot do without it, said to the host in one line. */
    val need: String,
) {

    Camera(
        "CAMERA",
        "THE PRINTED CARDS ARE READ THROUGH THE LENS",
    ),

    Bluetooth(
        "BLUETOOTH",
        "THE HOUSE SEES WHICH PHONES ARE NEAR EACH OTHER",
    ),

    /**
     * **Nearby Interaction: UWB distance** (D-294, D-303). Beside Bluetooth, not instead of it (U6:
     * Bluetooth picks who is near, UWB measures them). The label is the owner's, the name iOS uses
     * for the same permission in Settings. [need] is drawn nowhere, as no row's is; it paraphrases
     * the owner's approved prompt (D-294).
     */
    NearbyDevices(
        "NEARBY DEVICES",
        "THE HOUSE MEASURES THE DISTANCE BETWEEN PHONES",
    ),

    Motion(
        "MOTION & FITNESS",
        "THE HOUSE KNOWS WHEN A PHONE IS BEING CARRIED",
    ),
}

/**
 * **What the OS has said about one permission.** Five answers, because four would be a lie.
 *
 * [Asked] is NEARBY DEVICES' alone (D-303). Nearby Interaction has no status to read, so its probe
 * runs a short session, and a session that neither runs nor is refused within two seconds of an
 * active app says only that the prompt was raised. That is not an answer, so it keeps the phone at
 * PERMISSIONS (D-309), and a tap on the row, REQUEST WHAT IS MISSING or a refresh probes again.
 */
enum class Grant(
    /** The value drawn at the right of the row. */
    val label: String,
) {

    /** Nobody has asked the OS yet. The state every row starts in on a fresh install. */
    Unasked("TAP TO GRANT"),

    /** The prompt was raised and the probe has not heard what came back. See the class KDoc. */
    Asked("ASKED"),

    Granted("GRANTED"),

    /** Said no — at the prompt, or in Settings afterwards, or by a restriction on the device. */
    Refused("REFUSED"),

    /** This phone does not have the thing. No amount of tapping changes it. */
    Unavailable("NOT ON THIS PHONE");

    /**
     * **Whether a phone in this state may take a seat** (D-161's gate; D-309: every row must read
     * GRANTED).
     *
     * [Asked] does not pass. On NEARBY DEVICES it is a probe with no clear answer, and a phone
     * seated on it could not be measured all round (D-308: it can neither Revoke nor be Revoked,
     * report nor be reported).
     *
     * [Unavailable] passes, and that is not a hole in the gate. A grant is a question about a
     * person's consent; a missing sensor is a question about hardware, and refusing to seat a
     * phone until its owner grows a motion coprocessor would be a gate nobody can ever open. It is
     * loud in the log where the authority reads it, silent to the party (rule 6). On NEARBY DEVICES
     * it does not arise at this gate: D-292's launch gate refuses a phone without Ultra Wideband
     * first.
     */
    val opensTheGate: Boolean get() = this == Granted || this == Unavailable
}

/**
 * **The whole set, as one question**: what is still standing between this phone and a seat.
 *
 * In [DevicePermission] order, so the sentence a host reads names the rows top to bottom in the
 * order they are looking at them. Empty means setup is complete.
 */
fun grantsMissing(states: Map<DevicePermission, Grant>): List<DevicePermission> =
    DevicePermission.entries.filter { (states[it] ?: Grant.Unasked).opensTheGate.not() }
