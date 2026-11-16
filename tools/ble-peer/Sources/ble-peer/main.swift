// ble-peer — the Mac's half of the one-phone radio check. See tools/one-phone/README.md.
//
//   ble-peer sniff [--round-seed N] [--seats 8] [--quiet] [--seconds S]
//   ble-peer advertise --round-seed N --seat K [--seconds S]
//   ble-peer tokens --round-seed N --seat K [--window W]
//
// sniff      listens for the game's service UUID and prints what is on the air, flagging anything
//            beyond the two service UUIDs a phone may advertise (D-212, D-218). Exits 1 on a FAIL.
// advertise  advertises what seat K's phone would: 5348 plus this window's token, no name,
//            re-derived at every 15 s window boundary.
// tokens     prints seat K's tokens around now, with no radio, to compare against a phone's log.

import ContactDerivation
import CoreBluetooth
import Foundation

setvbuf(stdout, nil, _IOLBF, 0)

// MARK: - Arguments

func usage() -> Never {
    FileHandle.standardError.write(Data("""
    usage:
      ble-peer sniff [--round-seed N] [--seats 8] [--quiet] [--seconds S]
      ble-peer advertise --round-seed N --seat K [--seconds S]
      ble-peer tokens --round-seed N --seat K [--window W]

    """.utf8))
    exit(64)
}

let argv = Array(CommandLine.arguments.dropFirst())
guard let mode = argv.first else { usage() }
var options: [String: String] = [:]
var flags = Set<String>()
do {
    var i = 1
    while i < argv.count {
        let a = argv[i]
        guard a.hasPrefix("--") else { usage() }
        if a == "--quiet" { flags.insert(a); i += 1; continue }
        guard i + 1 < argv.count else { usage() }
        options[a] = argv[i + 1]
        i += 2
    }
}

func int64(_ key: String) -> Int64? {
    guard let raw = options[key] else { return nil }
    guard let v = Int64(raw) else { usage() }
    return v
}

let roundSeed = int64("--round-seed")
let seatArg = int64("--seat")
let seconds = int64("--seconds")
let seatCount = Int(int64("--seats") ?? 8)

let clockFormat: DateFormatter = {
    let f = DateFormatter()
    f.dateFormat = "HH:mm:ss.SSS"
    return f
}()

func stamp() -> String { clockFormat.string(from: Date()) }

let service = CBUUID(string: ContactDerivation.serviceUUID)

func stateName(_ s: CBManagerState) -> String {
    switch s {
    case .poweredOn: return "POWERED ON"
    case .poweredOff: return "POWERED OFF"
    case .unauthorized: return "UNAUTHORIZED"
    case .unsupported: return "UNSUPPORTED"
    case .resetting: return "RESETTING"
    default: return "UNKNOWN"
    }
}

/// Every state but on is reported; the two a person can fix are said in words and end the run.
func reportState(_ s: CBManagerState) {
    switch s {
    case .poweredOn: return
    case .unauthorized:
        print("BLUETOOTH IS NOT ALLOWED FOR THIS TERMINAL — System Settings → Privacy & Security → Bluetooth: allow the terminal app you ran this from, then run it again.")
        exit(2)
    case .poweredOff:
        print("BLUETOOTH IS OFF ON THIS MAC — switch it on and run again.")
        exit(2)
    case .unsupported:
        print("THIS MAC HAS NO BLUETOOTH LOW ENERGY RADIO CoreBluetooth can use.")
        exit(2)
    default:
        print("\(stamp())  BLUETOOTH \(stateName(s)) — WAITING")
    }
}

// MARK: - sniff

/// Keys CoreBluetooth adds on the receiving side. They describe the reception, not the payload.
let receiverKeys: Set<String> = [
    CBAdvertisementDataIsConnectable, "kCBAdvDataTimestamp", "kCBAdvDataRxPrimaryPHY", "kCBAdvDataRxSecondaryPHY",
    CBAdvertisementDataServiceUUIDsKey, CBAdvertisementDataTxPowerLevelKey,
]

/// Keys that carry payload a phone must never put on the air (D-212: no name, nothing but the UUIDs).
let forbiddenKeys: [(String, String)] = [
    (CBAdvertisementDataLocalNameKey, "LOCAL NAME"),
    (CBAdvertisementDataManufacturerDataKey, "MANUFACTURER DATA"),
    (CBAdvertisementDataServiceDataKey, "SERVICE DATA"),
    (CBAdvertisementDataOverflowServiceUUIDsKey, "OVERFLOW SERVICE UUIDS"),
    (CBAdvertisementDataSolicitedServiceUUIDsKey, "SOLICITED SERVICE UUIDS"),
]

final class Sniffer: NSObject, CBCentralManagerDelegate {
    private var central: CBCentralManager!
    private let quiet: Bool
    private let seeds: [[UInt8]]

    private var ads = 0
    private var fails = 0
    private var notes = Set<String>()
    private var seen = Set<String>()
    private var lastToken: String?
    private var lastTokenAt: Date?
    private var intervals: [Double] = []
    private var lastAddress: String?
    private var lastAddressAt: Date?
    private var resolved: [String: String] = [:]
    private var window: [Int] = []

    init(quiet: Bool) {
        self.quiet = quiet
        if let roundSeed {
            seeds = (0..<seatCount).map { ContactDerivation.seatSeed(roundSeed: roundSeed, seat: Int32($0)) }
        } else {
            seeds = []
        }
        super.init()
        central = CBCentralManager(delegate: self, queue: nil)
        if quiet {
            Timer.scheduledTimer(withTimeInterval: 2, repeats: true) { [weak self] _ in self?.rssiSummary() }
        }
    }

    func centralManagerDidUpdateState(_ c: CBCentralManager) {
        reportState(c.state)
        guard c.state == .poweredOn else { return }
        print("\(stamp())  SCANNING FOR SERVICE \(ContactDerivation.serviceUUID) (EVERY ADVERTISEMENT, DUPLICATES INCLUDED)" +
              (roundSeed != nil ? " · RESOLVING TOKENS AGAINST SEATS 0-\(seatCount - 1)" : ""))
        c.scanForPeripherals(withServices: [service], options: [CBCentralManagerScanOptionAllowDuplicatesKey: true])
    }

    func centralManager(_ c: CBCentralManager, didDiscover p: CBPeripheral, advertisementData ad: [String: Any], rssi: NSNumber) {
        ads += 1
        let now = Date()
        let uuids = ad[CBAdvertisementDataServiceUUIDsKey] as? [CBUUID] ?? []
        let tokens = uuids.filter { $0.data.count == ContactDerivation.tokenBytes }
        let others = uuids.filter { $0 != service && $0.data.count != ContactDerivation.tokenBytes }
        let address = String(p.identifier.uuidString.prefix(8))

        var problems: [String] = []
        for (key, label) in forbiddenKeys where present(ad[key]) { problems.append(label) }
        if !others.isEmpty { problems.append("EXTRA SERVICE UUID \(others.map(\.uuidString).joined(separator: ","))") }
        if tokens.count != 1 { problems.append("\(tokens.count) TOKEN UUIDS, NOT ONE") }
        if !uuids.contains(service) { problems.append("NO \(ContactDerivation.serviceUUID) IN THE LIST") }

        let token = tokens.first.map { ContactDerivation.hex([UInt8]($0.data)) }
        let flag = { (key: String) in self.present(ad[key]) ? "yes" : "no" }
        let tx = (ad[CBAdvertisementDataTxPowerLevelKey] as? NSNumber).map { "\($0)" } ?? "-"
        let conn = (ad[CBAdvertisementDataIsConnectable] as? NSNumber).map { $0.boolValue ? "yes" : "no" } ?? "-"

        if !quiet || !problems.isEmpty {
            print("\(stamp())  RSSI \(rssi)  UUIDS [\(uuids.map(\.uuidString).joined(separator: ", "))]" +
                  "  NAME \(flag(CBAdvertisementDataLocalNameKey)) · MFR \(flag(CBAdvertisementDataManufacturerDataKey))" +
                  " · SVCDATA \(flag(CBAdvertisementDataServiceDataKey)) · TX \(tx) · CONN \(conn)" +
                  "  TOKEN \(token ?? "-")  ADDR \(address)\(token.map(resolve) ?? "")")
        }
        for problem in problems {
            fails += 1
            print("\(stamp())  FAIL — \(problem) (ADDR \(address))")
        }
        for key in ad.keys where !receiverKeys.contains(key) && !forbiddenKeys.contains(where: { $0.0 == key }) {
            note("UNRECOGNISED ADVERTISEMENT KEY \(key) — not a FAIL by itself; report it")
        }
        if ad[CBAdvertisementDataTxPowerLevelKey] != nil {
            note("TX POWER LEVEL IS ON THE AIR (\(tx) dBm) — iOS adds it itself; not a FAIL here, unit 6 rules on it")
        }
        if let cached = p.name {
            note("THIS MAC HAS A CACHED GAP NAME FOR ADDR \(address) ('\(cached)') — from an earlier connection, not from this advertisement")
        }

        if let token, !seen.contains(token) {
            seen.insert(token)
            let edge = ContactDerivation.nowMillis() % ContactDerivation.windowMillis
            if let lastToken, let lastTokenAt {
                let gap = now.timeIntervalSince(lastTokenAt)
                intervals.append(gap)
                print("\(stamp())  TOKEN CHANGE \(lastToken.prefix(8)) → \(token.prefix(8)) AFTER \(String(format: "%.1f", gap)) S" +
                      " · WALL mod 15000 = \(edge) MS · RSSI \(rssi)\(resolve(token))")
            } else {
                print("\(stamp())  FIRST TOKEN \(token.prefix(8)) · WALL mod 15000 = \(edge) MS · RSSI \(rssi)\(resolve(token))")
            }
            lastToken = token
            lastTokenAt = now
        }
        if address != lastAddress {
            if let lastAddress {
                let sinceToken = lastTokenAt.map { String(format: "%.1f", now.timeIntervalSince($0)) } ?? "-"
                print("\(stamp())  ADDRESS CHANGE \(lastAddress) → \(address) · \(sinceToken) S AFTER THE LAST TOKEN CHANGE")
            }
            lastAddress = address
            lastAddressAt = now
        }
        window.append(rssi.intValue)
    }

    private func present(_ value: Any?) -> Bool {
        switch value {
        case nil: return false
        case let s as String: return !s.isEmpty
        case let d as Data: return !d.isEmpty
        case let a as [Any]: return !a.isEmpty
        case let m as [AnyHashable: Any]: return !m.isEmpty
        default: return true
        }
    }

    private func note(_ line: String) {
        if notes.insert(line).inserted { print("\(stamp())  NOTE — \(line)") }
    }

    /// Which seat and window a token is, when the round seed is known: the house's inversion.
    private func resolve(_ token: String) -> String {
        guard !seeds.isEmpty else { return "" }
        if let hit = resolved[token] { return hit }
        let now = ContactDerivation.window(containingMillis: ContactDerivation.nowMillis())
        var answer = " = NO SEAT 0-\(seatCount - 1) IN WINDOWS \(now - 1)…\(now + 1)"
        search: for (seat, seed) in seeds.enumerated() {
            for (w, label) in [(now, "NOW"), (now - 1, "PREVIOUS"), (now + 1, "NEXT")]
            where ContactDerivation.hex(ContactDerivation.token(seed: seed, window: w)) == token {
                answer = " = SEAT \(seat) · WINDOW \(w) (\(label))"
                break search
            }
        }
        resolved[token] = answer
        return answer
    }

    private func rssiSummary() {
        defer { window.removeAll() }
        guard !window.isEmpty else {
            print("\(stamp())  RSSI 2 S — NOTHING HEARD")
            return
        }
        let sorted = window.sorted()
        print("\(stamp())  RSSI 2 S — \(sorted.count) ADS · MEDIAN \(sorted[sorted.count / 2]) · BEST \(sorted.last!) · WORST \(sorted.first!)")
    }

    func finish() -> Never {
        central.stopScan()
        let mean = intervals.isEmpty ? "-" : String(format: "%.1f S", intervals.reduce(0, +) / Double(intervals.count))
        print("\(stamp())  SUMMARY — \(ads) ADVERTISEMENTS · \(seen.count) TOKENS · \(intervals.count) CHANGES (MEAN \(mean))" +
              " · \(fails) FAIL LINES · \(notes.count) NOTES")
        if ads == 0 { print("\(stamp())  HEARD NOTHING ON \(ContactDerivation.serviceUUID) — no phone was advertising, or it was out of range") }
        print(fails == 0 ? "RESULT: NO FAIL" : "RESULT: FAIL")
        exit(fails == 0 ? 0 : 1)
    }
}

// MARK: - advertise

final class Advertiser: NSObject, CBPeripheralManagerDelegate {
    private var manager: CBPeripheralManager!
    private let seed: [UInt8]
    private let seat: Int64
    private var scheduled = false

    init(roundSeed: Int64, seat: Int64) {
        self.seat = seat
        seed = ContactDerivation.seatSeed(roundSeed: roundSeed, seat: Int32(seat))
        super.init()
        manager = CBPeripheralManager(delegate: self, queue: nil)
    }

    func peripheralManagerDidUpdateState(_ m: CBPeripheralManager) {
        reportState(m.state)
        if m.state == .poweredOn { advertiseNow() }
    }

    func peripheralManagerDidStartAdvertising(_ m: CBPeripheralManager, error: Error?) {
        print("\(stamp())  " + (error == nil ? "ADVERTISING" : "ADVERTISING FAILED — \(error!.localizedDescription)"))
    }

    /// The phone's shape exactly: two service UUIDs, 5348 and the token, and no name key.
    private func advertiseNow() {
        let now = ContactDerivation.nowMillis()
        let w = ContactDerivation.window(containingMillis: now)
        let token = ContactDerivation.token(seed: seed, window: w)
        if manager.isAdvertising { manager.stopAdvertising() }
        manager.startAdvertising([CBAdvertisementDataServiceUUIDsKey: [service, CBUUID(data: Data(token))]])
        // The phone's own line, so the two logs read alike (BluetoothRadio.ios.kt).
        print("\(stamp())  ADVERTISE \(ContactDerivation.hex(token).prefix(8)) · WINDOW \(w) · WALL \(now)" +
              "  (SEAT \(seat), TOKEN \(ContactDerivation.hex(token)))")
        // A strict timer with a small leeway: `asyncAfter` lets GCD coalesce a 15 s wait by up to
        // a second, which put the Mac's token change 600 ms past the boundary in the first run.
        let next = (w + 1) * ContactDerivation.windowMillis
        let wait = max(1, next - ContactDerivation.nowMillis() + 5)
        let timer = DispatchSource.makeTimerSource(flags: .strict, queue: .main)
        timer.schedule(deadline: .now() + .milliseconds(Int(wait)), leeway: .milliseconds(2))
        timer.setEventHandler { [weak self] in
            self?.rotation?.cancel()
            guard let self, self.manager.state == .poweredOn else { return }
            self.advertiseNow()
        }
        rotation?.cancel()
        rotation = timer
        timer.resume()
    }

    private var rotation: DispatchSourceTimer?

    func finish() -> Never {
        manager.stopAdvertising()
        print("\(stamp())  STOPPED ADVERTISING")
        exit(0)
    }
}

// MARK: - main

func requireSeat() -> (Int64, Int64) {
    guard let roundSeed, let seatArg, seatArg >= 0 else { usage() }
    return (roundSeed, seatArg)
}

var finish: () -> Never

switch mode {
case "sniff":
    let sniffer = Sniffer(quiet: flags.contains("--quiet"))
    finish = { sniffer.finish() }
case "advertise":
    let (r, k) = requireSeat()
    print("\(stamp())  SEAT \(k) OF ROUND SEED \(r) · SERVICE \(ContactDerivation.serviceUUID) · NO NAME · ROTATING EVERY 15 S ON THE WALL CLOCK")
    let advertiser = Advertiser(roundSeed: r, seat: k)
    finish = { advertiser.finish() }
case "tokens":
    let (r, k) = requireSeat()
    let seed = ContactDerivation.seatSeed(roundSeed: r, seat: Int32(k))
    let now = ContactDerivation.window(containingMillis: ContactDerivation.nowMillis())
    print("SEAT \(k) SEED \(ContactDerivation.hex(seed))")
    let windows = int64("--window").map { [$0] } ?? [now - 1, now, now + 1]
    for w in windows {
        print("WINDOW \(w)\(w == now ? " (NOW)" : "")  TOKEN \(ContactDerivation.hex(ContactDerivation.token(seed: seed, window: w)))")
    }
    exit(0)
default:
    usage()
}

signal(SIGINT, SIG_IGN)
signal(SIGTERM, SIG_IGN)
let interrupts = [SIGINT, SIGTERM].map { sig -> DispatchSourceSignal in
    let source = DispatchSource.makeSignalSource(signal: sig, queue: .main)
    source.setEventHandler { finish() }
    source.resume()
    return source
}
if let seconds {
    DispatchQueue.main.asyncAfter(deadline: .now() + .seconds(Int(seconds))) { finish() }
}
RunLoop.main.run()
