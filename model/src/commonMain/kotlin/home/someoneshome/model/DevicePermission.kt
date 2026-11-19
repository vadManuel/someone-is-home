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
 * permission and leaving four to ambush the round is the same bug with a smaller blast radius.
 *
 * **The order is the order the rows are drawn in**, and it is deliberate: Local Network and Camera
 * first because they are the two that end an evening, Notifications last because it is the one a
 * host will be least surprised to be asked for. Nearby Devices sits under Bluetooth, the other half
 * of how the house sees who is near whom (D-303).
 */
enum class DevicePermission(
    /** The row's own words on the Perms screen. Screen copy, in the closed vocabulary. */
    val label: String,
    /** What the evening cannot do without it, said to the host in one line. */
    val need: String,
    /**
     * ⚠️ **Whether the OS will ever confirm this grant — and therefore what a gate may demand.**
     *
     * Five of the six answer for real: `AVCaptureDevice`, `CMMotionActivityManager`,
     * `CBManager.authorization` and `UNUserNotificationCenter` each report a status this app can
     * read back, and Nearby Interaction answers a short session run to ask (D-303). **Local Network reports nothing, on any version this project targets** — see
     * [Grant.Asked] — so [Grant.Granted] is a state that row can never reach and a gate that waits
     * for it is a gate that never opens. The owner met that gate holding two phones with the
     * permission switched on in Settings: *it still shows as REQUESTED and … it says this phone
     * cannot take a seat yet, local network.*
     *
     * A row that cannot be confirmed gates at what **is** knowable — the prompt was raised — and
     * the truth test moves to where D-179 and D-180 already put it: the home either goes on the
     * air or it does not, and that refusal names the grant and opens Settings.
     */
    val confirmable: Boolean = true,
) {

    LocalNetwork(
        "LOCAL NETWORK",
        "THE PHONES FIND THE HOUSE OVER THIS NETWORK",
        confirmable = false,
    ),

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

    Notifications(
        "NOTIFICATIONS",
        "A MEETING REACHES A PHONE THAT IS FACE DOWN",
    ),
}

/**
 * **What the OS has said about one permission.** Five answers, because four would be a lie.
 *
 * [Asked] is the one that needs defending. iOS exposes **no way to read the Local Network grant** —
 * there is no status call, and there has not been one for any version this project targets. What
 * an app can do is browse and see what happens, which distinguishes *refused* from *not refused*
 * and never distinguishes *granted* from *nobody has answered yet*. So the honest state after
 * raising that prompt is **ASKED**, and the real answer arrives where it always did: the home
 * either goes on the air or it does not, and the hosting refusal already names that cause and
 * carries a control that opens the settings page (D-173).
 *
 * Inventing a [Granted] there would be the app asserting a fact it does not have, on the one screen
 * whose whole job is to tell a host the truth about their phone before the lights go out.
 */
enum class Grant(
    /** The value drawn at the right of the row. */
    val label: String,
) {

    /** Nobody has asked the OS yet. The state every row starts in on a fresh install. */
    Unasked("TAP TO GRANT"),

    /**
     * The prompt was raised and the OS will not say what came back. See the class KDoc.
     *
     * ⚠️ **The value was REQUESTED and it read as *pending* to the one person it is written for.**
     * The owner granted Local Network on both phones, came back to a row still saying REQUESTED,
     * and reasonably read it as *the app is still waiting for something*. Nothing is waiting:
     * this is the end state of that row, forever, and REQUESTED is the wrong word for a terminus.
     * ASKED says what happened without promising an answer, and the Perms screen carries the rest of
     * the sentence — that the house finds out when the home goes on the air — as its own line
     * rather than crushing it into a two-word value column. ⚠️ Proposed copy, for the owner.
     */
    Asked("ASKED"),

    Granted("GRANTED"),

    /** Said no — at the prompt, or in Settings afterwards, or by a restriction on the device. */
    Refused("REFUSED"),

    /** This phone does not have the thing. No amount of tapping changes it. */
    Unavailable("NOT ON THIS PHONE");

    /**
     * **Whether a phone in this state may take a seat** (D-161's gate).
     *
     * [Unavailable] passes, and that is not a hole in the gate. A grant is a question about a
     * person's consent; a missing sensor is a question about hardware, and refusing to seat a
     * phone until its owner grows a motion coprocessor would be a gate nobody can ever open. It is
     * loud in the log where the authority reads it, silent to the party (rule 6).
     */
    val opensTheGate: Boolean get() = this == Granted || this == Asked || this == Unavailable
}

/**
 * **Whether this row, in this state, lets the phone take a seat** — the gate, one row at a time.
 *
 * [Grant.opensTheGate] is the rule for every row the OS will answer. For a row it will not
 * ([DevicePermission.confirmable]), the bar is the strongest thing that row can ever say:
 * **anything but [Grant.Unasked]**, and Unasked still blocks — so the one action the refusal asks
 * for is the one action that helps, which is tapping the row and raising the prompt.
 *
 * That admits [Grant.Refused] on an unconfirmable row, deliberately. On Local Network that value
 * cannot be trusted anyway: the only signal behind it is a browse that failed, and a browse fails
 * *while the system modal is still on the glass*, before the person standing there has answered.
 * A genuinely revoked grant is caught one screen later, at hosting, by the home that does not go
 * on the air.
 */
fun DevicePermission.opensTheGate(grant: Grant): Boolean =
    if (confirmable) grant.opensTheGate else grant != Grant.Unasked

/**
 * **The whole set, as one question**: what is still standing between this phone and a seat.
 *
 * In [DevicePermission] order, so the sentence a host reads names the rows top to bottom in the
 * order they are looking at them. Empty means setup is complete.
 */
fun grantsMissing(states: Map<DevicePermission, Grant>): List<DevicePermission> =
    DevicePermission.entries.filter { it.opensTheGate(states[it] ?: Grant.Unasked).not() }
