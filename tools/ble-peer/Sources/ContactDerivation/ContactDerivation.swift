import CryptoKit
import Foundation

/// The contact handshake's derivation, in Swift, for the Mac's BLE peer.
///
/// It must be the same function the app runs, or the Mac advertises tokens the house cannot
/// resolve. The Kotlin originals:
/// - seat seed: `core/.../Arming.kt` `contactSeedFor` — HMAC-SHA-256 keyed by the round's seed as
///   eight big-endian bytes, over `someone-is-home/contact-seed/1` and the seat index as four
///   big-endian bytes, truncated to 16 bytes.
/// - token: `model/.../Contact.kt` `ContactTokens.of` — HMAC-SHA-256 keyed by the seat seed, over
///   the byte `T` and the window index as eight big-endian bytes, truncated to 16 bytes.
/// - window: `ContactWindow.containing` — wall-clock milliseconds floor-divided by 15 000.
///
/// `Tests/ContactDerivationTests` pins this to the values `OnePhoneVectorsTest` pins in Kotlin.
public enum ContactDerivation {

    /// Bytes in a seed and in a token (`ContactTokens.TOKEN_BYTES`).
    public static let tokenBytes = 16

    /// One window, in milliseconds (`ContactWindow.MILLIS`, D-032).
    public static let windowMillis: Int64 = 15_000

    /// The game's 16-bit service UUID (`ContactAdvertisement.SERVICE_UUID`). Playtest only.
    public static let serviceUUID = "5348"

    static let seedLabel = Array("someone-is-home/contact-seed/1".utf8)

    /// The seat's 16-byte contact seed for a round seed.
    public static func seatSeed(roundSeed: Int64, seat: Int32) -> [UInt8] {
        let key = bigEndian(UInt64(bitPattern: roundSeed), bytes: 8)
        let message = seedLabel + bigEndian(UInt64(UInt32(bitPattern: seat)), bytes: 4)
        return Array(hmac(key: key, message: message).prefix(tokenBytes))
    }

    /// The 16-byte token a seat seed advertises in a window.
    public static func token(seed: [UInt8], window: Int64) -> [UInt8] {
        let message = [UInt8(ascii: "T")] + bigEndian(UInt64(bitPattern: window), bytes: 8)
        return Array(hmac(key: seed, message: message).prefix(tokenBytes))
    }

    /// The window a wall-clock reading in milliseconds falls in. Floor division, as in Kotlin.
    public static func window(containingMillis millis: Int64) -> Int64 {
        let q = millis / windowMillis
        return (millis % windowMillis < 0) ? q - 1 : q
    }

    /// Wall-clock milliseconds now, as the phone reads them.
    public static func nowMillis() -> Int64 {
        Int64((Date().timeIntervalSince1970 * 1000).rounded(.down))
    }

    /// Lowercase hex, as the app prints tokens and seeds.
    public static func hex(_ bytes: [UInt8]) -> String {
        bytes.map { String(format: "%02x", $0) }.joined()
    }

    static func hmac(key: [UInt8], message: [UInt8]) -> [UInt8] {
        Array(HMAC<SHA256>.authenticationCode(for: message, using: SymmetricKey(data: key)))
    }

    static func bigEndian(_ value: UInt64, bytes: Int) -> [UInt8] {
        (0..<bytes).map { i in UInt8(truncatingIfNeeded: value >> (8 * UInt64(bytes - 1 - i))) }
    }
}
