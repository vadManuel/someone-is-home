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
 * host will be least surprised to be asked for.
 */
enum class DevicePermission(
    /** The row's own words on the Perms screen. Screen copy, in the closed vocabulary. */
    val label: String,
    /** What the evening cannot do without it, said to the host in one line. */
    val need: String,
) {

    LocalNetwork(
        "LOCAL NETWORK",
        "THE PHONES FIND THE HOUSE OVER THIS NETWORK",
    ),

    Camera(
        "CAMERA",
        "THE PRINTED CARDS ARE READ THROUGH THE LENS",
    ),

    Bluetooth(
        "BLUETOOTH",
        "THE HOUSE SEES WHICH PHONES ARE NEAR EACH OTHER",
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
 * raising that prompt is **REQUESTED**, and the real answer arrives where it always did: the home
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

    /** The prompt was raised and the OS will not say what came back. See the class KDoc. */
    Asked("REQUESTED"),

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
 * **The whole set, as one question**: what is still standing between this phone and a seat.
 *
 * In [DevicePermission] order, so the sentence a host reads names the rows top to bottom in the
 * order they are looking at them. Empty means setup is complete.
 */
fun grantsMissing(states: Map<DevicePermission, Grant>): List<DevicePermission> =
    DevicePermission.entries.filter { (states[it] ?: Grant.Unasked).opensTheGate.not() }
