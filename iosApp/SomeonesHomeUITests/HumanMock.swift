import XCTest

// =================================================================================================
// THE HUMAN-MOCK
// =================================================================================================
//
// **The only thing this layer fakes is the thumb.** Not the transport, not the house, not the
// clock, not the screen: a real build of the real app runs in a real process, and this bundle sits
// outside it pressing the controls a person presses. Everything the app does in answer is its own.
//
// That is the owner's ruling and it is the whole design of this target: *it should quite literally
// be a mock of the human using the app and nothing else should be mocked*. Where an in-process
// bench holds a `FlowModel` and calls the lambdas the buttons hold, this holds nothing — it can
// only see what a person could see and touch what a person could touch. The two are the same idiom
// at two altitudes, and the out-of-process one is the one that catches a button that was never
// wired to the lambda at all.
//
// WHAT THE SIMULATOR CANNOT DO IS STILL TRUE HERE. No BLE, no torch, no camera, no haptics. This
// target proves the walk up to the perimeter and the shapes of the controls; it can never sign off
// the game. `run-app.sh` says the same thing at more length and it has not stopped being true
// because the driving got automatic.
//
// =================================================================================================

/// What the thumb waits for. Long enough for a first frame on a cold simulator, short enough that a
/// genuine hang fails the run instead of hiding in it.
private let settle: TimeInterval = 20

extension XCUIApplication {
    /// An element by its `Modifier.testTag`.
    ///
    /// Compose Multiplatform publishes a `testTag` as the element's `accessibilityIdentifier` on
    /// iOS — verified against a running build, not assumed — so a tag written for a Compose test in
    /// `ui` is the same handle this bundle reaches for from outside the process. That is why the
    /// tag pass is a tag pass and not a second, parallel vocabulary of identifiers.
    func tagged(_ tag: String) -> XCUIElement {
        descendants(matching: .any).matching(identifier: tag).firstMatch
    }

    /// An element by the words on it, as a person would find it.
    func labelled(_ text: String) -> XCUIElement {
        descendants(matching: .any)
            .matching(NSPredicate(format: "label == %@", text))
            .firstMatch
    }

    /// An element whose label merely contains `text` — for the composed sentences the panel builds
    /// out of several values, where the whole string is a fact about the run rather than a constant.
    func containing(_ text: String) -> XCUIElement {
        descendants(matching: .any)
            .matching(NSPredicate(format: "label CONTAINS %@", text))
            .firstMatch
    }
}

// =================================================================================================
// ⚠️ THE FRAME CORRECTION — a platform finding, not a preference
// =================================================================================================
//
// **Compose Multiplatform 1.11.1 reports a clickable element's iOS accessibility frame 26 points
// above the region that actually answers a touch.** Measured twice, on two controls, with two
// independent drivers, and it came out the same number both times:
//
//   · the debug chip — reported top 832.67, first y that reached it 858.67. Offset 26.0.
//   · a picker row   — reported [206.3 … 226.3]; y=216 reaches the row ABOVE it and y=236 reaches
//                      the row itself, which puts its true band at [232 … 252]. Offset 25.7.
//
// A plain, non-clickable text element's frame is correct — which is the tell, and also the reason
// the problem stayed hidden through the first half of this spike: every tall button worked on the
// first attempt, because a 64-point control whose frame is 26 points out still overlaps itself.
// A control SHORTER than 26 points does not overlap itself at all, and is unreachable by frame
// from outside the process. The debug picker's 20-point rows are that case exactly.
//
// This is corrected in one place, by a named constant, rather than papered over per call site. If
// a later Compose release fixes the frames, `FRAME_LIFT` goes to zero and nothing else changes;
// the suite's own failures will say so loudly, because everything short will stop being reachable.
private let FRAME_LIFT: CGFloat = 26.0

extension XCUIElement {
    /// The point a finger has to actually land on to reach this control.
    ///
    /// **Measured down from the reported TOP, not out from the reported centre**, and the
    /// difference is not fussiness. The error is not a clean translation: the debug chip reports a
    /// 41-point box for a control that is really about 15 points tall, inflated upward from a
    /// bottom edge that is correct. Its reported centre plus the lift lands at y=879 on an
    /// 874-point screen — off the glass entirely — while the same arithmetic from the top lands
    /// squarely on the chip. Every control measured so far agrees on where its real top edge is
    /// (`reported top + FRAME_LIFT`) and disagrees about its height, so the top is what this aims
    /// from, six points in to clear the edge.
    private var truePress: XCUICoordinate {
        let f = frame
        return XCUIApplication().coordinate(withNormalizedOffset: .zero)
            .withOffset(CGVector(dx: f.midX, dy: f.minY + FRAME_LIFT + 6))
    }

    /// A tap where the control really is. See `FRAME_LIFT`.
    func humanTap() { truePress.tap() }

    /// A press of an exact duration, where the control really is.
    ///
    /// **The exact duration is the whole reason this target exists.** `HOLD_MILLIS` is 2000, and
    /// the half of that contract worth guarding is the negative one — a mis-tap in a dark pocket
    /// must reach nothing. Driving 1.0s and 2.6s at the same control, and demanding different
    /// answers, is a thing no flow-YAML driver in the bake-off could express at all.
    func humanPress(forDuration seconds: TimeInterval) {
        truePress.press(forDuration: seconds)
    }
}

extension XCUIElement {
    /// **Empties a field and does not believe it the first time.**
    ///
    /// Keystrokes from outside the process are delivered asynchronously and the field's published
    /// value lags them. Sending `count` deletes in one breath and typing immediately afterwards
    /// produced `MAMANUEL` on a field that held `MANUEL` — four of the six deletes had landed when
    /// the first real character arrived. So this reads the field back, deletes what is still
    /// there, and repeats until the field agrees it is empty.
    ///
    /// The same lag is why the assertion on what was typed is a poll rather than a bare read.
    func clearText(timeout: TimeInterval = 10) {
        let deadline = Date().addingTimeInterval(timeout)
        while Date() < deadline {
            let current = (value as? String) ?? ""
            if current.isEmpty { return }
            typeText(String(repeating: XCUIKeyboardKey.delete.rawValue, count: current.count))
            Thread.sleep(forTimeInterval: 0.2)
        }
    }

    /// Waits for the field to publish `expected`, and returns what it last read.
    @discardableResult
    func waitForValue(_ expected: String, timeout: TimeInterval = 10) -> String {
        let deadline = Date().addingTimeInterval(timeout)
        var last = ""
        while Date() < deadline {
            last = (value as? String) ?? ""
            if last == expected { return last }
            Thread.sleep(forTimeInterval: 0.2)
        }
        return last
    }
}

extension XCTestCase {
    /// Waits for an element and fails by name rather than by a bare false.
    @discardableResult
    func waitFor(
        _ element: XCUIElement,
        _ what: String,
        timeout: TimeInterval = settle,
        file: StaticString = #filePath,
        line: UInt = #line
    ) -> Bool {
        let found = element.waitForExistence(timeout: timeout)
        XCTAssertTrue(found, "waited \(Int(timeout))s and never saw \(what)", file: file, line: line)
        return found
    }
}

final class HumanMockTests: XCTestCase {

    override func setUp() {
        super.setUp()
        continueAfterFailure = false
    }

    /// A cold app, launched the way the runner launches it.
    ///
    /// **The launch environment is set here even though this flow needs none of it.** It is the
    /// seam the five-thumb round test hangs off: every switch this game has — the scripted evening,
    /// the arming host, the forgotten seat — is read with `getenv`, so the thing that gives five
    /// simulators five different jobs is this dictionary and nothing else. Proving it is reachable
    /// from the driver is half of why this target exists.
    private func coldApp(_ environment: [String: String] = [:]) -> XCUIApplication {
        let app = XCUIApplication()
        for (key, value) in environment { app.launchEnvironment[key] = value }
        return app
    }

    // ---------------------------------------------------------------------------------------
    // 1 · The cold start, and the gate that is supposed to refuse
    // ---------------------------------------------------------------------------------------

    /// **The permissions gate refuses on a simulator, and that is the assertion.**
    ///
    /// Not a workaround and not a skip: this phone has no Bluetooth, so a build that let it take a
    /// seat would be the bug. The test names the missing permission out of the app's own refusal
    /// sentence, so a gate that silently opened — or that refused for a different reason — fails
    /// here rather than being discovered five screens later.
    func testTheColdStartReachesTheGateAndTheGateRefuses() {
        let app = coldApp()
        app.launch()

        waitFor(app.labelled("PERMISSIONS"), "the permissions page")

        // The refusal names what is missing. BLUETOOTH is the one the Simulator can never supply.
        let refusal = app.containing("THIS PHONE CANNOT TAKE A SEAT YET")
        waitFor(refusal, "the refusal sentence")
        XCTAssertTrue(
            refusal.label.contains("BLUETOOTH"),
            "the gate refused without naming BLUETOOTH — refusal read: \(refusal.label)"
        )

        // And the primary control says what it does, which is ask rather than proceed.
        XCTAssertTrue(
            app.labelled("REQUEST WHAT IS MISSING").exists,
            "the gate is refusing but the button does not offer to ask"
        )
    }

    // ---------------------------------------------------------------------------------------
    // 2 · A name typed, and the back idiom that returns to it
    // ---------------------------------------------------------------------------------------

    /// **The browsing walk: a name typed, a page left, and the back arrow reaching it again.**
    ///
    /// The name is the thing under test at the end, not the screen: coming back has to find the
    /// field as the thumb left it, because a back arrow that returns to a wiped page is the bug the
    /// arrow was added to prevent.
    func testANameIsTypedAndTheBackArrowReturnsToIt() {
        let app = coldApp()
        app.launch()

        waitFor(app.labelled("PERMISSIONS"), "the permissions page")
        jumpToJoin(app)

        // The field publishes a name of its own now, so the thumb can find it without knowing
        // where on the page it sits.
        let field = app.tagged("resident-name")
        waitFor(field, "the resident name field")
        field.humanTap()

        // **The phone remembers the last name it was given, so the field is cleared first.**
        //
        // Found by this suite on its second run and worth keeping the reason for: the first five
        // repeats read back MANUELMANUEL and then MANUELMANUELMA, because `ResidentNameDocument`
        // writes the name to disk and a fresh launch is not a fresh phone. That is correct product
        // behaviour — re-typing a name in a dark hallway is the failure it was added to close
        // (E-PAY-7) — and it is exactly the kind of state an out-of-process suite has to take
        // seriously where an in-process one gets a new object every time. Cleared the way a person
        // clears it, with the delete key, rather than by reaching past the app into its container.
        field.clearText()
        field.typeText("MANUEL")

        XCTAssertEqual(
            field.waitForValue("MANUEL"), "MANUEL",
            "the field did not read back what was typed into it"
        )

        // Leave the page by a real control.
        app.labelled("HOST A HOME INSTEAD").humanTap()
        waitFor(app.labelled("SAVED HOMES"), "the saved homes page")

        // And come back by the arrow, which is the idiom under test.
        let back = app.tagged("back-chevron")
        waitFor(back, "the back arrow")
        back.humanTap()

        waitFor(app.labelled("JOIN A HOME"), "the join page again")
        XCTAssertEqual(
            app.tagged("resident-name").waitForValue("MANUEL"), "MANUEL",
            "the back arrow returned to the join page and the typed name was gone"
        )
    }

    // ---------------------------------------------------------------------------------------
    // 3 · The hold, in both directions
    // ---------------------------------------------------------------------------------------

    /// **Two seconds fires and one second does not — and the second half is the half that matters.**
    ///
    /// `HOLD_MILLIS` is 2000 and the whole point of a hold is that a mis-tap in a dark pocket
    /// reaches nothing. A suite that could only prove the confirm case would sit green over a build
    /// that had quietly become a tap, which is exactly the shape of silent instrument this project
    /// keeps finding. Driving an exact press duration from outside the process is the one thing
    /// this target can do that no in-process bench and no flow-YAML driver can.
    func testAShortPressDoesNotConfirmAndATwoSecondHoldDoes() {
        let app = coldApp()
        app.launch()

        waitFor(app.labelled("PERMISSIONS"), "the permissions page")
        jump(app, to: "STAIRSWARN")
        waitFor(app.labelled("UNREGISTER AND CONTINUE"), "the stairs warning")

        let hold = app.tagged("hold-block")
        waitFor(hold, "the hold block")

        // A press well short of the two seconds must reach nothing at all.
        hold.humanPress(forDuration: 1.0)
        XCTAssertTrue(
            app.labelled("UNREGISTER AND CONTINUE").waitForExistence(timeout: 2),
            "a one-second press went through — the hold has become a tap"
        )

        // And the real hold must go through, leaving the screen it was holding.
        hold.humanPress(forDuration: 2.6)
        XCTAssertTrue(
            app.labelled("UNREGISTER AND CONTINUE").waitForNonExistence(timeout: settle),
            "a two-and-a-half second hold did not confirm"
        )
    }

    // ---------------------------------------------------------------------------------------
    // Diagnostic — not part of the suite's claim, kept because it is how the offset was measured
    // ---------------------------------------------------------------------------------------

    /// Measures the gap between a control's reported frame and the region that answers a touch.
    ///
    /// This is the instrument the `FRAME_LIFT` constant came out of, kept so the number can be
    /// re-measured rather than trusted. It walks one point at a time down from the reported top of
    /// the debug chip and reports the first y that actually opens the picker — restoring the screen
    /// after the hit, because a probe that navigates away measures the next screen instead.
    ///
    /// Opt-in, and never part of the suite's own claim:
    /// `TEST_RUNNER_E2E_DIAGNOSE=1 … -only-testing:…/testDiagnoseTheGeometry`
    func testDiagnoseTheGeometry() throws {
        try XCTSkipUnless(
            ProcessInfo.processInfo.environment["E2E_DIAGNOSE"] == "1",
            "diagnostic; set TEST_RUNNER_E2E_DIAGNOSE=1 to run it"
        )
        let app = coldApp()
        app.launch()
        waitFor(app.labelled("PERMISSIONS"), "the permissions page")

        let chip = app.tagged("variant-marker")
        let picker = app.labelled("DEBUG — PICK A SCREEN")
        let f = chip.frame
        print("=== variant-marker reported frame: \(f)")

        var trueTop: CGFloat = -1
        var y = f.minY
        while y <= f.maxY {
            app.coordinate(withNormalizedOffset: .zero)
                .withOffset(CGVector(dx: f.midX, dy: y)).tap()
            if picker.waitForExistence(timeout: 2) {
                trueTop = y
                // Put the screen back before the next probe.
                app.staticTexts["DEBUG"].tap()
                _ = app.labelled("PERMISSIONS").waitForExistence(timeout: 3)
                break
            }
            y += 1
        }
        print("=== reported top \(f.minY)  true top \(trueTop)  OFFSET \(trueTop - f.minY)")
        print("=== FRAME_LIFT in use: \(FRAME_LIFT)")
    }

    // ---------------------------------------------------------------------------------------
    // The grant-free route past a gate the Simulator cannot open
    // ---------------------------------------------------------------------------------------

    /// **Past the permissions gate the only way a simulator can go: the debug variant's own picker.**
    ///
    /// Written down rather than hidden in a helper's name, because it is the one place this suite
    /// is not a person: a player reaches JOIN by granting five permissions, and this phone cannot
    /// grant two of them at all. Everything after the jump is real navigation on real controls —
    /// the jump buys entry to the walk, and nothing else about the walk is bought.
    private func jumpToJoin(_ app: XCUIApplication) {
        jump(app, to: "JOIN")
        waitFor(app.labelled("JOIN A HOME"), "the join page")
    }

    private func jump(_ app: XCUIApplication, to screen: String) {
        app.tagged("variant-marker").humanTap()
        waitFor(app.labelled("DEBUG — PICK A SCREEN"), "the screen picker")
        app.tagged("screen-pick-\(screen.lowercased())").humanTap()
    }
}
