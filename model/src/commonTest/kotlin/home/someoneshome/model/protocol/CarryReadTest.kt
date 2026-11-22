package home.someoneshome.model.protocol

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * **D-276: a `Carry` read without the general decoder is the `Carry` the general decoder reads.**
 *
 * `TransportWire.decodeOrNull` reads a `Carry` as `encode` writes one by hand, and leaves every other
 * text to the general decoder. Every answer here is compared with the general decoder's own, over
 * frames `encode` writes (bodies with escapes, control characters, non-ASCII, backslashes) and over
 * texts that look nearly like one and must not be decided by the shortcut.
 */
class CarryReadTest {

    private fun plain(text: String): TransportFrame? = try {
        Json.decodeFromString(TransportFrame.serializer(), text)
    } catch (_: IllegalArgumentException) {
        null
    }

    private val bodies = listOf(
        "",
        "x",
        RoundWire.encode(RoundBody.Sighted("0".repeat(SightingUpload.BODY_CHARS), SightingUpload.EMPTY_ASK, UploadProbe.NONE, RangingWire.NO_REPORT, MainViewField.OFF)),
        RoundWire.encode(RoundBody.UploadHeard.of("0123456789abcdef", UploadProbe.NONE, 0L, 0L, RangingWire.NO_PLAN, home.someoneshome.model.protocol.OccupancyWire.blank(ByteArray(home.someoneshome.model.protocol.OccupancyWire.FIELD_BYTES)))),
        RoundWire.encode(RoundBody.Push(9, RoundMessage.MeetingCalling, round = 1)),
        LobbyWire.encode(LobbyBody.Standing(joined = 6, linesIn = 4, names = listOf("ÉLODIE", "TOMÁS", "李"), insiders = 2)),
        LobbyWire.encode(LobbyBody.Handover("a line with \"quotes\", a back\\slash, a tab\t, a newline\n and \u0001")),
        "\\", "\"", "\\\"", "ends with a backslash \\", "/slash/ and \u007f and 😀",
        "line and paragraph separators, \u0080 and   and ﻿ and ￿", "<tag>&amp;'single'",
        "\u001f", "\u0000",
    )

    private val nearly = listOf(
        "", "{}", "{\"type\":\"carry\",\"body\":\"", "{\"type\":\"carry\",\"body\":\"}",
        "{\"type\":\"carry\",\"body\":\"\"}", "{\"type\":\"carry\",\"body\":\"abc\\\"}",
        "{\"type\":\"carry\",\"body\":\"a\"b\"}", "{\"type\":\"carry\",\"body\":\"a\\nb\"}",
        "{\"type\":\"carry\",\"body\":\"a\\u0041b\"}", "{\"type\":\"carry\",\"body\":\"a\\/b\"}",
        "{\"type\":\"carry\",\"body\":\"a\u0001b\"}", "{\"type\":\"carry\",\"body\":\"ab\"} ",
        "{\"type\":\"carry\", \"body\":\"ab\"}", "{\"body\":\"ab\",\"type\":\"carry\"}",
        "{\"type\":\"carry\",\"body\":\"ab\",\"x\":1}", "{\"type\":\"seated\",\"token\":\"tk-7\"}",
        "{\"type\":\"carry\",\"body\":\"ab\\\\\"}", "{\"type\":\"carry\",\"body\":\"\\\\\\\"\"}",
    )

    @Test
    fun `every carry reads as the general decoder reads it`() {
        for (body in bodies) {
            val text = TransportWire.encode(TransportFrame.Carry(body))
            assertEquals(TransportFrame.Carry(body), TransportWire.decodeOrNull(text), text)
            assertEquals(plain(text), TransportWire.decodeOrNull(text), text)
        }
        for (text in nearly) assertEquals(plain(text), TransportWire.decodeOrNull(text), text)
    }
}
