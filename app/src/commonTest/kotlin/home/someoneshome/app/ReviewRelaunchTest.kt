package home.someoneshome.app

import home.someoneshome.model.ContactSeed
import home.someoneshome.model.ContactToken
import home.someoneshome.model.ContactWindow
import home.someoneshome.model.RadioCause
import home.someoneshome.model.protocol.RoundBody
import home.someoneshome.model.protocol.RoundMessage
import home.someoneshome.platform.ContactRadio
import home.someoneshome.platform.RadioReading
import kotlin.test.Test
import kotlin.test.assertEquals

class ReviewRelaunchTest {
    private class QuietRadio : ContactRadio {
        override fun advertise(token: ContactToken) = Unit
        override fun scan(on: Boolean) = Unit
        override fun drain(): List<RadioReading> = emptyList()
        override fun silence() = Unit
        override fun faultBegan(): RadioCause? = null
    }

    private val seed = ContactSeed("0123456789abcdef0123456789abcdef")

    private fun uploader(grants: () -> Int, kept: SeatStore): Pair<ContactUploader, MutableList<RoundBody.RadioFaulted>> {
        val reports = mutableListOf<RoundBody.RadioFaulted>()
        return ContactUploader(QuietRadio(), { ContactWindow(10) }, faulted = { reports += it }, kept = kept, refusedGrants = grants, send = { }) to reports
    }

    /**
     * Camera switched off once (reported, answered). The grant stays off. Later the person only
     * leaves the app and the process is ended while suspended (or force-quit and reopened): the
     * relaunch must send that leave as a person's, never as a second permission.
     */
    @Test
    fun aLeaveAfterAnAlreadyReportedGrantIsNotASecondPermissionAfterARelaunch() {
        val store = MemorySeat()
        var off = 0
        val (first, firstReports) = uploader({ off }, store)
        first.heard(RoundMessage.ContactSeedIssued(seed))
        first.beat()
        off = 1 shl 1 // a grant switched off mid-round
        first.beat()
        assertEquals(listOf(RadioCause.Permission), firstReports.map { it.cause })
        first.acknowledged(firstReports.single().onset)
        repeat(3) { first.beat() }
        assertEquals(1, firstReports.size, "a grant still off was reported twice in one process")
        // Only a leave now; iOS ends the suspended process; the person reopens the app.
        first.leftTheForeground()
        val (second, secondReports) = uploader({ off }, store)
        second.heard(RoundMessage.ContactSeedIssued(seed))
        repeat(2) { second.beat() }
        assertEquals(
            listOf(RadioCause.Person), secondReports.map { it.cause },
            "a leave after a relaunch went up as a second permission switched off",
        )
    }
}
