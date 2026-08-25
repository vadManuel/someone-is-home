package home.someoneshome.platform

import home.someoneshome.model.DevicePermission
import home.someoneshome.model.Grant
import home.someoneshome.platform.transport.DISCOVERY_SERVICE_TYPE
import platform.AVFoundation.AVAuthorizationStatusAuthorized
import platform.AVFoundation.AVAuthorizationStatusDenied
import platform.AVFoundation.AVAuthorizationStatusNotDetermined
import platform.AVFoundation.AVAuthorizationStatusRestricted
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.authorizationStatusForMediaType
import platform.AVFoundation.requestAccessForMediaType
import platform.CoreBluetooth.CBCentralManager
import platform.CoreBluetooth.CBCentralManagerDelegateProtocol
import platform.CoreBluetooth.CBManagerAuthorizationAllowedAlways
import platform.CoreBluetooth.CBManagerAuthorizationDenied
import platform.CoreBluetooth.CBManagerAuthorizationNotDetermined
import platform.CoreBluetooth.CBManagerAuthorizationRestricted
import platform.CoreBluetooth.CBManagerStatePoweredOff
import platform.CoreBluetooth.CBManagerStatePoweredOn
import platform.CoreBluetooth.CBManagerStateResetting
import platform.CoreBluetooth.CBManagerStateUnauthorized
import platform.CoreBluetooth.CBManagerStateUnknown
import platform.CoreBluetooth.CBManagerStateUnsupported
import platform.CoreMotion.CMAuthorizationStatusAuthorized
import platform.CoreMotion.CMAuthorizationStatusDenied
import platform.CoreMotion.CMAuthorizationStatusNotDetermined
import platform.CoreMotion.CMAuthorizationStatusRestricted
import platform.CoreMotion.CMMotionActivityManager
import platform.Foundation.NSDate
import platform.Foundation.NSNetService
import platform.Foundation.NSNetServiceBrowser
import platform.Foundation.NSNetServiceBrowserDelegateProtocol
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSRunLoop
import platform.Foundation.NSRunLoopCommonModes
import platform.Foundation.NSUserDefaults
import platform.Foundation.dateWithTimeIntervalSinceNow
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNAuthorizationStatusAuthorized
import platform.UserNotifications.UNAuthorizationStatusDenied
import platform.UserNotifications.UNAuthorizationStatusEphemeral
import platform.UserNotifications.UNAuthorizationStatusNotDetermined
import platform.UserNotifications.UNAuthorizationStatusProvisional
import platform.UserNotifications.UNUserNotificationCenter
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

/**
 * **iOS: five different frameworks, five different ways of being asked, one seam.**
 *
 * There is no general permission API on this platform. Each grant lives in the framework that owns
 * the hardware and each has its own spelling of *not determined*, so the whole of this file is that
 * translation — into [Grant], which is the only vocabulary anything above here sees.
 *
 * ### Every string in Info.plist is load-bearing and its absence is a crash, not a refusal
 *
 * `NSCameraUsageDescription`, `NSLocalNetworkUsageDescription`, `NSMotionUsageDescription` and
 * `NSBluetoothAlwaysUsageDescription` are all present in `iosApp/iosApp/Info.plist`, each with the
 * sentence the prompt shows. **iOS stops an app that raises a prompt without one** rather than
 * refusing the request — so a missing key here is not a permission bug, it is a phone that fails at
 * the exact moment the host is standing in front of the party.
 *
 * ### The two that answer through a callback are cached, and the cache is the honest kind
 *
 * Notifications and Bluetooth cannot be read synchronously, and rows are drawn every frame. So the
 * last answer each of them gave is held here, seeded at [Grant.Unasked] and never at a plausible
 * guess: a row that has not been asked reads TAP TO GRANT, which is what has happened.
 */
actual fun devicePermissions(): Permissions = IosPermissions()

@Suppress("TooManyFunctions")
private class IosPermissions(
    /**
     * **stdout, not `NSLog`**, for [CameraCardScanner]'s reason exactly: `devicectl --console`
     * carries stdout and does not carry the unified log, and a device run with nobody holding the
     * phone has no other evidence at all. Every line here is grepped for by the setup sweep.
     *
     * ⚠️ **This was `NSLog("%@", it)` and it took the app down on the Perms screen.** `NSLog` is a
     * C variadic and `%@` wants an Objective-C object; a Kotlin `String` handed to it as a vararg
     * is undefined behaviour, and on the 13 Pro it was a trap — signal 5, no message, two and a
     * half seconds after launch, which is exactly when Boot falls through to Perms and the first
     * grant is logged. The whole app failed at the first line the permission seam ever wrote.
     */
    private val log: (String) -> Unit = { println(it) },
) : Permissions {

    /**
     * The two answers that only ever arrive late, plus the one iOS will not give at all.
     *
     * Seeded empty rather than seeded optimistic. [Grant.Unasked] is what an unread row means and
     * it is what the gate refuses on — the failure direction is *the host is asked again*, never
     * *the party goes into the dark on a phone that cannot scan*.
     */
    private val remembered = mutableMapOf<DevicePermission, Grant>()

    /**
     * The Bluetooth manager, built on the first ask and held afterwards.
     *
     * **Building one is the request.** CoreBluetooth has no `requestAuthorization`: the prompt is
     * raised when a central manager first comes up in an app that has never been granted it, and
     * the answer arrives as a state change on the delegate. Held rather than rebuilt because a
     * second manager would raise a second power alert on a phone whose Bluetooth is switched off.
     */
    private var central: CBCentralManager? = null

    /** The browse that raises the Local Network prompt. Held so it can be stopped. */
    private var browser: NSNetServiceBrowser? = null

    private val bluetoothDelegate = object : NSObject(), CBCentralManagerDelegateProtocol {
        override fun centralManagerDidUpdateState(central: CBCentralManager) {
            val grant = bluetoothGrantOf(central.state)
            remembered[DevicePermission.Bluetooth] = grant
            log("$TAG BLUETOOTH state=${central.state} → $grant")
        }
    }

    /**
     * The browse's own delegate, and **only the failure half is wired**.
     *
     * Finding a home is not what this browse is for — it is for the modal. What matters is
     * `didNotSearch`, which is the one signal iOS gives when Local Network is refused, and which
     * `HostDiscovery` learned the hard way is the difference between a refusal and a silence.
     */
    private val browseDelegate = object : NSObject(), NSNetServiceBrowserDelegateProtocol {
        override fun netServiceBrowser(browser: NSNetServiceBrowser, didNotSearch: Map<Any?, *>) {
            remembered[DevicePermission.LocalNetwork] = Grant.Refused
            log("$TAG LOCAL NETWORK refused the browse $didNotSearch")
        }

        override fun netServiceBrowser(
            browser: NSNetServiceBrowser,
            didFindService: NSNetService,
            moreComing: Boolean,
        ) {
            // A home answered, which is proof the browse is really running — the one moment iOS
            // ever tells an app the grant was given. Every other run stops at REQUESTED.
            remembered[DevicePermission.LocalNetwork] = Grant.Granted
            log("$TAG LOCAL NETWORK is up — '${didFindService.name}' answered a browse")
        }
    }

    /**
     * **Silent.** Every row on the Perms screen reads this on every frame it is drawn, so a line
     * here is five lines per frame — a log that drowns the transitions [answer] writes, which are
     * the only lines a device run has as evidence. Reading a status is not an event; being told a
     * new answer is, and that is where the seam speaks.
     *
     * ⚠️ **Since N6 this is also what a GATE reads**, at the moment a phone asks to host or to take
     * a seat — see `PermsModel.missingNow`. That raised the standard: a row answered out of a cache
     * nothing filled is a refusal about which screens happened to compose rather than about this
     * phone. So the three below now answer out of the OS wherever the OS has an answer to give.
     */
    override fun grant(of: DevicePermission): Grant = when (of) {
        DevicePermission.Camera -> cameraGrant()
        DevicePermission.Motion -> motionGrant()
        DevicePermission.Bluetooth -> bluetoothGrant()
        DevicePermission.LocalNetwork -> remembered[of] ?: localNetworkStanding()
        // The one that is genuinely callback-only: there is no synchronous notification status on
        // this platform. What is held is the last answer [refresh] heard, and [refresh] now has a
        // caller at the app root as well as on the Perms screen, so a phone that never opened that
        // screen still has a real answer here rather than a hole.
        DevicePermission.Notifications -> remembered[of] ?: Grant.Unasked
    }

    override fun refresh(then: () -> Unit) {
        log("$TAG refreshing")
        // Notifications is the one that genuinely changes behind the app's back: a host who walked
        // out to Settings comes back to a row that has to move. It is also the only read of the
        // three that raises nothing, which is what makes it safe to do on every screen open.
        UNUserNotificationCenter.currentNotificationCenter()
            .getNotificationSettingsWithCompletionHandler { settings ->
                val grant = notificationGrantOf(settings?.authorizationStatus)
                dispatch_async(dispatch_get_main_queue()) {
                    remembered[DevicePermission.Notifications] = grant
                    log("$TAG NOTIFICATIONS → $grant")
                    then()
                }
            }
        // The Bluetooth manager reports its own state changes into `remembered` for as long as it
        // exists; asking it again means asking the one it already has.
        central?.let { bluetoothDelegate.centralManagerDidUpdateState(it) }
    }

    override fun request(of: DevicePermission, then: (Grant) -> Unit) {
        log("$TAG ASKING FOR ${of.label}")
        when (of) {
            DevicePermission.Camera -> requestCamera(then)
            DevicePermission.Motion -> requestMotion(then)
            DevicePermission.Notifications -> requestNotifications(then)
            DevicePermission.Bluetooth -> requestBluetooth(then)
            DevicePermission.LocalNetwork -> requestLocalNetwork(then)
        }
    }

    // ---- Camera -------------------------------------------------------------------------------

    private fun cameraGrant(): Grant = when (
        val status = AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeVideo)
    ) {
        AVAuthorizationStatusAuthorized -> Grant.Granted
        AVAuthorizationStatusNotDetermined -> Grant.Unasked
        AVAuthorizationStatusDenied, AVAuthorizationStatusRestricted -> Grant.Refused
        else -> {
            log("$TAG CAMERA unknown authorization status $status")
            Grant.Refused
        }
    }

    private fun requestCamera(then: (Grant) -> Unit) {
        if (cameraGrant() != Grant.Unasked) {
            answer(DevicePermission.Camera, cameraGrant(), then)
            return
        }
        AVCaptureDevice.requestAccessForMediaType(AVMediaTypeVideo) { granted ->
            dispatch_async(dispatch_get_main_queue()) {
                answer(
                    DevicePermission.Camera,
                    if (granted) Grant.Granted else Grant.Refused,
                    then,
                )
            }
        }
    }

    // ---- Motion -------------------------------------------------------------------------------

    private fun motionGrant(): Grant {
        if (!CMMotionActivityManager.isActivityAvailable()) return Grant.Unavailable
        return when (val status = CMMotionActivityManager.authorizationStatus()) {
            CMAuthorizationStatusAuthorized -> Grant.Granted
            CMAuthorizationStatusNotDetermined -> Grant.Unasked
            CMAuthorizationStatusDenied, CMAuthorizationStatusRestricted -> Grant.Refused
            else -> {
                log("$TAG MOTION unknown authorization status $status")
                Grant.Refused
            }
        }
    }

    /**
     * **The prompt is a query, because CoreMotion has no other way to raise one.**
     *
     * There is no `requestAuthorization` on `CMMotionActivityManager`: asking for one second of
     * history that nothing reads is what puts the modal on the glass. The handler's own arguments
     * are discarded on purpose — what the seam wants is the authorization status afterwards, which
     * is the one thing this call reliably changes.
     */
    private fun requestMotion(then: (Grant) -> Unit) {
        val standing = motionGrant()
        if (standing != Grant.Unasked) {
            answer(DevicePermission.Motion, standing, then)
            return
        }
        val manager = CMMotionActivityManager()
        manager.queryActivityStartingFromDate(
            start = NSDate.dateWithTimeIntervalSinceNow(-MOTION_HISTORY_SECONDS),
            toDate = NSDate(),
            toQueue = NSOperationQueue.mainQueue,
        ) { _, _ ->
            manager.stopActivityUpdates()
            answer(DevicePermission.Motion, motionGrant(), then)
        }
    }

    // ---- Notifications ------------------------------------------------------------------------

    private fun notificationGrantOf(status: Long?): Grant = when (status) {
        null -> Grant.Unasked
        UNAuthorizationStatusAuthorized, UNAuthorizationStatusProvisional,
        UNAuthorizationStatusEphemeral,
        -> Grant.Granted

        UNAuthorizationStatusNotDetermined -> Grant.Unasked
        UNAuthorizationStatusDenied -> Grant.Refused
        else -> {
            log("$TAG NOTIFICATIONS unknown authorization status $status")
            Grant.Refused
        }
    }

    private fun requestNotifications(then: (Grant) -> Unit) {
        UNUserNotificationCenter.currentNotificationCenter().requestAuthorizationWithOptions(
            UNAuthorizationOptionAlert or UNAuthorizationOptionSound or UNAuthorizationOptionBadge,
        ) { granted, error ->
            if (error != null) log("$TAG NOTIFICATIONS refused with $error")
            dispatch_async(dispatch_get_main_queue()) {
                answer(
                    DevicePermission.Notifications,
                    if (granted) Grant.Granted else Grant.Refused,
                    then,
                )
            }
        }
    }

    // ---- Bluetooth ----------------------------------------------------------------------------

    /**
     * **Bluetooth, read synchronously and without raising anything** (N6).
     *
     * `CBManager.authorization` is a class property: it answers what the *user* has said about this
     * app's use of the radio, now, with no manager built and no prompt raised. That is the honest
     * synchronous affordance the gate needed — before this, the only way this seam knew anything
     * about Bluetooth was a delegate callback on a manager that only [requestBluetooth] ever built,
     * so a phone which granted Bluetooth weeks ago read `Unasked` on every launch that did not walk
     * the Perms screen, **and was refused a seat for a grant it had already given.**
     *
     * [remembered] is still consulted first, and it has to be: authorization cannot distinguish *no
     * Bluetooth on this device* from *nobody has answered yet*, and a simulator is the first case.
     * The delegate writes [Grant.Unavailable] there once a manager exists, which opens the gate for
     * the reason [Grant.opensTheGate] gives — a missing radio is hardware, not consent.
     */
    private fun bluetoothGrant(): Grant {
        remembered[DevicePermission.Bluetooth]?.let { return it }
        return when (val status = CBCentralManager.authorization) {
            CBManagerAuthorizationAllowedAlways -> Grant.Granted
            CBManagerAuthorizationNotDetermined -> Grant.Unasked
            CBManagerAuthorizationDenied, CBManagerAuthorizationRestricted -> Grant.Refused
            else -> {
                log("$TAG BLUETOOTH unknown authorization $status")
                Grant.Unasked
            }
        }
    }

    private fun bluetoothGrantOf(state: Long): Grant = when (state) {
        // Powered on, off and resetting all mean the same thing about the GRANT: the app is
        // allowed to use the radio. Whether the radio is switched on is a different question, and
        // a Perms row that answered it would be a row that says REFUSED because somebody is in
        // aeroplane mode.
        CBManagerStatePoweredOn, CBManagerStatePoweredOff, CBManagerStateResetting -> Grant.Granted
        CBManagerStateUnauthorized -> Grant.Refused
        CBManagerStateUnsupported -> Grant.Unavailable
        CBManagerStateUnknown -> Grant.Unasked
        else -> Grant.Unasked
    }

    private fun requestBluetooth(then: (Grant) -> Unit) {
        val existing = central
        if (existing != null) {
            answer(DevicePermission.Bluetooth, bluetoothGrantOf(existing.state), then)
            return
        }
        // Building it IS the request — see [central]. The state arrives on the delegate a moment
        // later; what is reported back now is whatever it already knows, and the row moves again
        // when the delegate fires.
        val manager = CBCentralManager(delegate = bluetoothDelegate, queue = dispatch_get_main_queue())
        central = manager
        answer(DevicePermission.Bluetooth, bluetoothGrantOf(manager.state), then)
    }

    // ---- Local network ------------------------------------------------------------------------

    /**
     * **Browse, so the modal appears — and then say REQUESTED, because that is all iOS will tell.**
     *
     * See [Grant.Asked]. The browse is left running for a moment rather than started and stopped in
     * the same breath: `didNotSearch` is the refusal and it arrives asynchronously, so a browser
     * torn down immediately would take the one signal there is down with it.
     */
    private fun requestLocalNetwork(then: (Grant) -> Unit) {
        if (browser == null) {
            val started = NSNetServiceBrowser()
            started.delegate = browseDelegate
            started.scheduleInRunLoop(NSRunLoop.mainRunLoop, NSRunLoopCommonModes)
            started.searchForServicesOfType(DISCOVERY_SERVICE_TYPE, inDomain = "local.")
            browser = started
        }
        // **Written down before the answer is given, and it is the raising that is recorded** —
        // see [localNetworkStanding]. The prompt has now been put on the glass on this install, and
        // that is a fact about the install rather than about this process.
        defaults.setBool(true, LOCAL_NETWORK_RAISED)
        val standing = remembered[DevicePermission.LocalNetwork]
        answer(
            DevicePermission.LocalNetwork,
            if (standing == Grant.Refused || standing == Grant.Granted) standing else Grant.Asked,
            then,
        )
    }

    /**
     * ⚠️ **The one answer that has to outlive the process, because iOS will never give it again.**
     *
     * Every other row is re-read from its framework on demand. Local Network cannot be: there is no
     * status call, there never has been, and [Grant.Asked] exists precisely because *the prompt was
     * raised and the OS will not say what came back* is the strongest thing this app can ever know.
     *
     * **Held only in memory, that knowledge failed with the process** — so a host who granted Local
     * Network at setup last week launched the app, went to host, and was refused a seat naming a
     * grant they had already given. That is a false refusal, and D-180's family rules that a
     * refusal name a cause a person can act on; naming one that is already done is worse than
     * silence, because the only thing it offers them to do is the one thing that cannot help.
     *
     * So the *raising* is recorded, and nothing else is. It is deliberately **not** a stored
     * `Granted`: this seam never reports a grant it did not hear (see the class KDoc), and a phone
     * whose owner switched the permission off in Settings afterwards would be a phone the app told
     * a comfortable lie about. `Asked` stays exactly as honest a year later as it was that evening
     * — and the real answer still arrives where D-179 and D-180 put it, at hosting, where the home
     * either goes on the air or it does not and the refusal names the grant and opens Settings.
     *
     * ⚠️ **Flagged for the owner's morning review.** It is the smallest honest way to stop the gate
     * refusing on a row the OS refuses to answer, and it is a ruling somebody should make on
     * purpose rather than inherit from this file.
     */
    private fun localNetworkStanding(): Grant =
        if (defaults.boolForKey(LOCAL_NETWORK_RAISED)) Grant.Asked else Grant.Unasked

    // ---- Shared -------------------------------------------------------------------------------

    /**
     * One place every answer goes through: remembered, logged, then handed back.
     *
     * The log line is the evidence a device unit has. A permission prompt is the second output in
     * this app a screenshot cannot prove — the modal is the OS's own window — so what a run at
     * three in the morning can show is the transition, in words, with the row named.
     */
    private fun answer(of: DevicePermission, grant: Grant, then: (Grant) -> Unit) {
        val before = remembered[of]
        remembered[of] = grant
        log("$TAG ${of.label} ${before ?: "—"} → $grant")
        then(grant)
    }

    /** Where the one unrepeatable answer is kept. See [localNetworkStanding]. */
    private val defaults = NSUserDefaults.standardUserDefaults

    private companion object {

        /** What the device log is grepped for. */
        const val TAG = "[perms]"

        /** *The Local Network prompt has been put on this install's glass.* Nothing more. */
        const val LOCAL_NETWORK_RAISED = "home.someoneshome.perms.localNetworkRaised"

        /** One second of activity history, read by nothing. The query is the prompt. */
        const val MOTION_HISTORY_SECONDS = 1.0
    }
}
