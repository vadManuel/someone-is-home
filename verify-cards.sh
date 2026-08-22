#!/usr/bin/env bash
# Reads the printed sheet back, one card at a time, with a decoder nobody here wrote.
#
#   ./verify-cards.sh [run-tag] [dpi]
#
# `./gradlew check` proves the deck is right and that every symbol round-trips through the same
# library that produced it. That is one library agreeing with itself. What it cannot say is whether
# the thing that comes out of a printer is readable, because between the encoder and the paper sit
# a page layout, a coordinate flip, a quiet zone, a module size and a PDF written by hand.
#
# So this rasterises the finished PDF the way a printer would and decodes it with CIDetector --
# APPLE'S QR DECODER, the family the phone's camera uses.
#
# CARD BY CARD, not page by page. A whole page handed to the detector at once comes back with seven
# or eight of its twelve symbols and no indication which: a QR detector is built to find A code in a
# scene, and a scene containing twelve of them is not what it optimises for. Cropping to the cell
# the generator says it drew into turns one ambiguous answer into forty-four unambiguous ones, and
# it checks the position as well as the payload -- a card printed in the wrong slot fails here.
#
# WHAT IT STILL DOES NOT PROVE. Not one photon of this passes through a lens. Ink on paper at an
# angle, in a corridor, by lamplight, at whatever distance an arm is long -- that is a person
# holding a card up to a phone, and nothing on a Mac replaces it.
set -euo pipefail
cd "$(dirname "$0")"
export PATH="$HOME/.local/share/mise/shims:$PATH"

RUN="${1:-VRFY}"
DPI="${2:-300}"
OUT="cards/build/deck"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

echo "generating run ${RUN}..."
./gradlew -q :cards:sheet -Prun="$RUN" >/dev/null
PDF="$OUT/someone-is-home-cards-$RUN.pdf"
MANIFEST="$OUT/someone-is-home-cards-$RUN.cards.txt"
[ -f "$PDF" ] && [ -f "$MANIFEST" ] || { echo "the generator produced no sheet" >&2; exit 1; }

cat > "$WORK/read.swift" <<'SWIFT'
import Foundation
import CoreGraphics
import CoreImage

// Arguments: the PDF, the manifest, the resolution. The manifest is the generator's own account of
// where it put each card -- payload, page, and the cell in PDF points, origin bottom left.
let pdfPath = CommandLine.arguments[1]
let manifestPath = CommandLine.arguments[2]
let dpi = Double(CommandLine.arguments[3])!
let scale = dpi / 72.0

guard let doc = CGPDFDocument(URL(fileURLWithPath: pdfPath) as CFURL) else {
    FileHandle.standardError.write("not a PDF: \(pdfPath)\n".data(using: .utf8)!)
    exit(2)
}
let detector = CIDetector(
    ofType: CIDetectorTypeQRCode,
    context: CIContext(),
    options: [CIDetectorAccuracy: CIDetectorAccuracyHigh]
)!

// Each page rasterised once and kept, because forty-four crops out of four pages is four
// rasterisations rather than forty-four.
var pages: [Int: CGImage] = [:]
func raster(_ number: Int) -> CGImage? {
    if let cached = pages[number] { return cached }
    guard let page = doc.page(at: number) else { return nil }
    let box = page.getBoxRect(.mediaBox)
    let width = Int(box.width * scale), height = Int(box.height * scale)
    guard let bitmap = CGContext(
        data: nil, width: width, height: height, bitsPerComponent: 8, bytesPerRow: width,
        space: CGColorSpaceCreateDeviceGray(), bitmapInfo: CGImageAlphaInfo.none.rawValue
    ) else { return nil }
    // White first. A PDF draws no background, and an uninitialised buffer is black paper.
    bitmap.setFillColor(gray: 1, alpha: 1)
    bitmap.fill(CGRect(x: 0, y: 0, width: width, height: height))
    bitmap.scaleBy(x: scale, y: scale)
    bitmap.drawPDFPage(page)
    let image = bitmap.makeImage()
    pages[number] = image
    return image
}

var failures = 0
for line in try String(contentsOfFile: manifestPath, encoding: .utf8).split(separator: "\n") {
    let parts = line.split(separator: " ")
    guard parts.count == 6, let page = Int(parts[1]),
          let x = Double(parts[2]), let y = Double(parts[3]),
          let w = Double(parts[4]), let h = Double(parts[5]) else { continue }
    let expected = String(parts[0])
    guard let image = raster(page) else {
        print("NO PAGE  \(expected)  page \(page)"); failures += 1; continue
    }
    // CGImage's y counts DOWN from the top; the manifest's counts up from the bottom.
    let pageHeight = Double(image.height) / scale
    let crop = CGRect(
        x: x * scale, y: (pageHeight - y - h) * scale, width: w * scale, height: h * scale
    )
    guard let cell = image.cropping(to: crop) else {
        print("NO CROP  \(expected)"); failures += 1; continue
    }
    let found = detector.features(in: CIImage(cgImage: cell)).compactMap {
        ($0 as? CIQRCodeFeature)?.messageString
    }
    if found == [expected] {
        print("ok       \(expected)  page \(page)")
    } else {
        print("MISMATCH \(expected)  page \(page)  read \(found)")
        failures += 1
    }
}
FileHandle.standardError.write("\(failures) card(s) did not read back\n".data(using: .utf8)!)
exit(failures == 0 ? 0 : 1)
SWIFT

echo "compiling the reader..."
swiftc -O -o "$WORK/read" "$WORK/read.swift"

echo "reading $PDF at ${DPI}dpi, card by card..."
if "$WORK/read" "$PDF" "$MANIFEST" "$DPI" > "$WORK/found.txt" 2>"$WORK/err.txt"; then
    cards="$(grep -c '^ok' "$WORK/found.txt")"
    echo "PASS — $cards of $cards cards read back as the card they were printed from,"
    echo "       each one out of the cell the generator says it drew it into."
    echo "       (Ink, paper, an angle and a dark corridor are still a person's job.)"
else
    echo "FAIL — the sheet and the generator disagree:" >&2
    grep -v '^ok' "$WORK/found.txt" >&2 || true
    cat "$WORK/err.txt" >&2
    exit 1
fi
