package home.someoneshome.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * **The two scan configurations, and the swap that shipped.**
 *
 * The registration screen said BACK CAMERA . POINT AT THE CODE over a capture session hard-coded to
 * the front lens, with no preview layer and a torch button that reached no hardware at all. Every
 * one of those is a field of [ScanUse] now, and every one of them is asserted here **by name**, so
 * that a diff which flips one fails a test whose message says which of the two scans it broke
 * rather than failing somewhere far away in a camera nobody can run under `./gradlew check`.
 *
 * This is the whole of what a headless target can certify about a lens. The session itself needs a
 * phone; what needs no phone is *which session the code would build*, and that is the half that
 * was wrong.
 */
class ScanUseTest {

    /**
     * **Registration is back, with a picture, with a torch.** In the light, at host setup.
     *
     * Each field is its own assertion with its own sentence, because these three fail
     * independently and a combined assertion would report "the registration row changed" for a
     * torch flag while the lens was fine.
     */
    @Test
    fun registrationIsTheBackCameraWithAPictureAndATorch() {
        assertEquals(
            CameraFacing.Back, ScanUse.Registration.facing,
            "registration is on the FRONT lens — the card is on the far side of the phone at host " +
                "setup, and the screen says BACK CAMERA . POINT AT THE CODE over it",
        )
        assertTrue(
            ScanUse.Registration.preview,
            "registration has no viewfinder — the host is aiming a lens they cannot see through",
        )
        assertTrue(
            ScanUse.Registration.torch,
            "registration cannot light the torch — the button on that screen reaches nothing",
        )
    }

    /**
     * **The in-round scan is front, blind, and dark** (D-159, rule 5).
     *
     * The preview's absence is the design and not an omission: the lit screen *is* the light the
     * card is read by, and it is facing the paper. The torch's absence is rule 5 — a light nobody
     * authored, in a dark house, on the phone of somebody trying not to be found.
     */
    @Test
    fun theInRoundScanIsTheFrontCameraWithNoPictureAndNoTorch() {
        assertEquals(
            CameraFacing.Front, ScanUse.InRound.facing,
            "the in-round scan moved to the BACK lens — the panel is held up to the marker, so the " +
                "lens that faces the card is the one on the screen's own face",
        )
        assertFalse(
            ScanUse.InRound.preview,
            "the in-round scan gained a preview — a picture nobody can look at, drawn on the one " +
                "screen whose brightness is a game value (D-159)",
        )
        assertFalse(
            ScanUse.InRound.torch,
            "the in-round scan may light a torch — an unauthored light in a dark house is a signal " +
                "nobody authored, which rule 5 forbids",
        )
    }

    /**
     * **The two rows are opposites on every field.** Stated as its own claim rather than left to be
     * inferred from the two above: if a future edit made both configurations agree about a lens,
     * both tests above could still pass while the split this type exists for had quietly closed.
     */
    @Test
    fun theTwoConfigurationsAgreeAboutNothing() {
        val registration = ScanUse.Registration
        val inRound = ScanUse.InRound
        assertTrue(
            registration.facing != inRound.facing &&
                registration.preview != inRound.preview &&
                registration.torch != inRound.torch,
            "the two scans have started to look alike: $registration and $inRound. They are " +
                "opposites — a lit room with the card on the far side of the phone, and a dark " +
                "house with the screen pressed to the paper.",
        )
        assertEquals(
            2, ScanUse.entries.size,
            "a third scan configuration appeared. There are two scans in this game; a third row " +
                "here is a design question rather than a parameter (see ScanUse's own KDoc)",
        )
    }

    /**
     * **A scanner carries its configuration and nothing can talk it out of it.**
     *
     * The deck stands in for the registration walk, so it reports that use — and a deck built for
     * the in-round scan refuses the torch by exactly the rule the camera refuses it by.
     */
    @Test
    fun aScannerBuiltForOneScanCannotBeUsedAsTheOther() {
        val deck = SeededCardScanner()
        assertEquals(ScanUse.Registration, deck.use, "the playtest deck stopped being the host's walk")

        deck.torch(true)
        assertTrue(deck.torchLit, "a registration scanner refused its own torch")

        val inRound = SeededCardScanner(use = ScanUse.InRound)
        inRound.torch(true)
        assertFalse(
            inRound.torchLit,
            "an in-round scanner lit its torch — the refusal in CardScanner.torch has been removed, " +
                "and with it the only thing standing between a dark house and a light nobody authored",
        )
    }
}
