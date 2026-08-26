// THE APP ICON, DRAWN RATHER THAN EXPORTED.
//
//   java tools/appicon/AppIcon.java <out-appiconset-dir> [--checks <dir>] [--raw-scanlines]
//
// Normally run through ./tools/appicon/icon.sh, which knows both paths.
//
// WHY A GENERATOR AND NOT NINE PNGs FROM A DESIGN TOOL. Nine hand-exported bitmaps are nine
// things that can disagree, and the one that disagrees is the one nobody looks at — the 58 on a
// home screen with forty other apps. Here every size is the SAME composition evaluated at that
// size, so the roof stroke is 3.4 units of that tile at 1024 and at 58 alike, and a change to the
// design is one edit followed by one command rather than nine round trips.
//
// EVERY SIZE IS RENDERED NATIVELY, never downsampled from the master. The line work is a 3.4-unit
// stroke on a 48-unit box: at 58px that is 4 device pixels, and 4 crisp pixels drawn at 58 are a
// house, while 4 pixels resampled down from 1024 are a smudge the shape of a house.
//
// The geometry and the colours are the owner's Icon Study, option 1f "THE LIT DOOR", transcribed
// exactly. The three places this file makes a judgement rather than a transcription are marked
// JUDGEMENT and explained where they are made.
//
// Zero dependencies on purpose: Java2D ships with the JDK the Gradle build already needs, so this
// runs on a clean clone with nothing installed that was not installed anyway.

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.MultipleGradientPaint;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;

public final class AppIcon {

    // ---------------------------------------------------------------- the design, as given

    /** The tile ground. */
    private static final Color GROUND = new Color(0x15, 0x10, 0x0A);

    /**
     * The frame: one step lighter than the ground, hugging the tile edge.
     *
     * The study's production note is the whole reason it exists — on a dark wallpaper a tile whose
     * ground is #15100A has no edge at all, and an icon with no edge is a hole. The frame is not
     * decoration; it is the only thing that says where the app stops.
     */
    private static final Color FRAME = new Color(0x24, 0x18, 0x06);

    /** The line work: roof, chimney, walls. One colour for all three. */
    private static final Color LINE = new Color(0xB3, 0x7F, 0x1F);

    /** The door. The single bright element on the tile, and the only one. */
    private static final Color DOOR = new Color(0xFF, 0xE7, 0xB0);

    /** The house is drawn in a 48-unit box, the study's SVG viewBox. */
    private static final double BOX = 48.0;

    /** ...and that box occupies this much of the tile's width, centred. */
    private static final double HOUSE_SCALE = 0.65;

    /** Stroke width for roof and walls, in box units. */
    private static final double LINE_WIDTH = 3.4;

    /**
     * The frame's thickness as a fraction of the tile.
     *
     * JUDGEMENT. The study gives the frame a colour and a job but not a width. This is a hairline
     * at every size that matters — 27px at 1024, and still a whole pixel at 40 — which is what the
     * job asks for: visible enough to bound the tile against a black wallpaper, thin enough that it
     * is never read as part of the drawing.
     */
    private static final double FRAME_INSET = 0.026;

    /**
     * iOS's own corner radius, as a fraction of the tile's width.
     *
     * The tile itself is filled square and opaque — iOS masks its own corners and an icon that
     * arrives pre-rounded gets rounded twice, which is a dark rind around the edge. This number is
     * used for ONE thing: making the inner ground concentric with the mask, so the frame reads as
     * an even band instead of a square whose corners the mask ate.
     */
    private static final double IOS_CORNER = 0.2237;

    // The glow. A radial gradient centred on the door, clipped to the house silhouette.
    //
    // ON TOP OF THE LINE WORK, not behind it. The clip is what settles this: a glow painted behind
    // an opaque house would be hidden BY that house, and clipping a hidden thing to the shape that
    // hides it is not a design decision anyone makes. The clip exists because the glow is an
    // overlay that would otherwise spill light onto the tile ground, which is the one thing a house
    // with a lit door must not do — the light comes out of the door, and the night stays night.
    private static final float[] GLOW_STOPS = {0f, 0.26f, 0.52f, 0.76f, 1f};
    private static final Color[] GLOW_COLORS = {
        new Color(255, 215, 130, 158), // rgba(255,215,130,.62)
        new Color(255, 199, 89, 82),   // rgba(255,199,89,.32)
        new Color(255, 183, 60, 31),   // rgba(255,183,60,.12)
        new Color(255, 183, 60, 0),    // transparent
        new Color(255, 183, 60, 0),
    };

    /**
     * The silhouette the glow is clipped to, in percent of the 48-unit box: apex, right eave,
     * bottom right, bottom left, left eave. The study's own polygon.
     */
    private static final double[][] SILHOUETTE = {
        {50, 17}, {79, 46.5}, {79, 84}, {21, 84}, {21, 46.5},
    };

    /** The scan lines: one dark unit in three, at the study's 96px reference. */
    private static final Color SCAN_INK = new Color(0, 0, 0, 140); // rgba(0,0,0,.55)
    private static final double SCAN_REFERENCE = 96.0;
    private static final double SCAN_PERIOD_AT_REFERENCE = 3.0;
    private static final double SCAN_DUTY = 1.0 / 3.0;

    /**
     * The smallest period, in device pixels, that is still allowed to carry scan lines.
     *
     * JUDGEMENT, and the one the study itself sets the bar for: its stated test of an icon is the
     * small-size read. The pitch scales with the tile, so at 58px one period is 1.8px and the dark
     * unit is 0.6px — a band narrower than the pixel it lands on. Nothing about that is a band any
     * more. It resolves to a flat 18% veil with a faint beat where the phase drifts against the
     * pixel grid, and the two things it takes 18% off are the two things the icon has to say at
     * 29pt: the door against the wall, and the frame against the ground. Below the threshold the
     * band is dropped outright rather than thinned — a veil that is not read as a band is a veil
     * that is only costing contrast.
     *
     * 2.4px is where the snapped period below stops being an honest rendering of the study's
     * density: 3px is the coarsest a period can be and still be a texture, and at a proportional
     * period under 2.4 the snap is stretching the design by more than a quarter to get there.
     * It puts the cut between the 76 and the 80 — so every shipped iPhone size from 40pt up carries
     * the band, and the settings and notification tiles do not.
     */
    private static final double SCAN_MIN_PERIOD_PX = 2.4;

    // ---------------------------------------------------------------- the set

    /**
     * What the asset catalog ships. TARGETED_DEVICE_FAMILY is 1 — iPhone — so the set is the iPhone
     * sizes and the App Store master, and nothing else. An iPad idiom in a catalog for an
     * iPhone-only target is a file Xcode compiles, ships and never shows anybody.
     */
    private record Slot(String idiom, String size, String scale, int px) {}

    private static final Slot[] SET = {
        new Slot("iphone", "20x20", "2x", 40),
        new Slot("iphone", "20x20", "3x", 60),
        new Slot("iphone", "29x29", "2x", 58),
        new Slot("iphone", "29x29", "3x", 87),
        new Slot("iphone", "40x40", "2x", 80),
        new Slot("iphone", "40x40", "3x", 120),
        new Slot("iphone", "60x60", "2x", 120),
        new Slot("iphone", "60x60", "3x", 180),
        new Slot("ios-marketing", "1024x1024", "1x", 1024),
    };

    /**
     * The sizes rendered for looking at, over and above the set.
     *
     * 29 and 76 are iPad points and are not shipped, but the unit was asked to look at the 29, and
     * "the icon is unreadable at 29" is a fact about the drawing rather than about the iPad.
     */
    private static final int[] EXTRA_CHECKS = {29, 76};

    // ---------------------------------------------------------------- entry

    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("usage: AppIcon <appiconset-dir> [--checks <dir>] [--raw-scanlines]");
            System.exit(2);
        }
        Path set = Path.of(args[0]);
        Path checks = null;
        boolean ruled = true;
        for (int i = 1; i < args.length; i++) {
            switch (args[i]) {
                case "--checks" -> checks = Path.of(args[++i]);
                case "--raw-scanlines" -> ruled = false;
                default -> {
                    System.err.println("unknown argument: " + args[i]);
                    System.exit(2);
                }
            }
        }

        int written = 0;
        // THE RAW RENDER NEVER TOUCHES THE CATALOG. It exists to be looked at next to the ruled one
        // and nothing else, and a comparison run that quietly leaves the rejected version in the
        // shipping set is a way to ship the rejected version — which this did once, before the
        // guard.
        if (ruled) {
            Files.createDirectories(set);
            List<Integer> done = new ArrayList<>();
            for (Slot slot : SET) {
                if (done.contains(slot.px())) continue; // 120 is both 40@3x and 60@2x
                done.add(slot.px());
                ImageIO.write(render(slot.px(), true), "png", set.resolve(name(slot.px())).toFile());
                written++;
            }
            Files.writeString(set.resolve("Contents.json"), contentsJson(), StandardCharsets.UTF_8);
            written++;
        }

        if (checks != null) {
            Files.createDirectories(checks);
            String suffix = ruled ? "" : "-raw";
            for (int px : allCheckSizes()) {
                Path p = checks.resolve("icon-" + px + suffix + ".png");
                ImageIO.write(render(px, ruled), "png", p.toFile());
                written++;
            }
            // A contact sheet of the small sizes on a black ground, at 8x, so the small-size read
            // can be argued about without anybody squinting at a 29px file in a viewer that has
            // already resampled it.
            Path sheet = checks.resolve("small-sizes" + suffix + ".png");
            ImageIO.write(contactSheet(ruled), "png", sheet.toFile());
            written++;
        }

        System.out.println("wrote " + written + " file(s); scan lines "
                + (ruled ? "snapped, dropped below a " + SCAN_MIN_PERIOD_PX + "px period"
                         : "RAW — proportional and unsnapped at every size"));
    }

    private static int[] allCheckSizes() {
        List<Integer> px = new ArrayList<>();
        for (Slot s : SET) if (!px.contains(s.px())) px.add(s.px());
        for (int s : EXTRA_CHECKS) if (!px.contains(s)) px.add(s);
        px.sort(Integer::compare);
        return px.stream().mapToInt(Integer::intValue).toArray();
    }

    private static String name(int px) {
        return "icon-" + px + ".png";
    }

    // ---------------------------------------------------------------- the drawing

    static BufferedImage render(int size, boolean ruledScanLines) {
        // TYPE_INT_RGB, not ARGB. The tile has no transparency anywhere and an opaque buffer is the
        // structural version of saying so: iOS rejects an app icon with an alpha channel outright.
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY);

        tile(g, size);
        house(g, size);
        scanLines(g, size, ruledScanLines);

        g.dispose();
        return img;
    }

    /** The ground and the frame that bounds it. */
    private static void tile(Graphics2D g, int size) {
        g.setColor(FRAME);
        g.fill(new Rectangle2D.Double(0, 0, size, size));

        double inset = FRAME_INSET * size;
        double radius = IOS_CORNER * size - inset;
        g.setColor(GROUND);
        g.fill(new RoundRectangle2D.Double(
                inset, inset, size - 2 * inset, size - 2 * inset, 2 * radius, 2 * radius));
    }

    /** Roof, chimney, walls, door, glow — in the study's own paint order. */
    private static void house(Graphics2D g, int size) {
        Graphics2D h = (Graphics2D) g.create();
        double scale = HOUSE_SCALE * size / BOX;
        double origin = (size - HOUSE_SCALE * size) / 2.0;
        h.translate(origin, origin);
        h.scale(scale, scale);

        h.setColor(LINE);
        h.setStroke(new BasicStroke(
                (float) LINE_WIDTH, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f));

        // M4 26 L24 7 L44 26
        Path2D.Double roof = new Path2D.Double();
        roof.moveTo(4, 26);
        roof.lineTo(24, 7);
        roof.lineTo(44, 26);
        h.draw(roof);

        // M31.6 9 h6.2 V20.4 L31.6 14.4 z — drawn after the roof, so its diagonal cut sits on the
        // roof line rather than being sliced by it.
        Path2D.Double chimney = new Path2D.Double();
        chimney.moveTo(31.6, 9);
        chimney.lineTo(37.8, 9);
        chimney.lineTo(37.8, 20.4);
        chimney.lineTo(31.6, 14.4);
        chimney.closePath();
        h.fill(chimney);

        // M9 20.6 v21.4 h30 V20.6 — an open path. Stroked, never closed: the top edge of the walls
        // is the roof's job, and a closed rectangle would draw a line across the gable.
        Path2D.Double walls = new Path2D.Double();
        walls.moveTo(9, 20.6);
        walls.lineTo(9, 42);
        walls.lineTo(39, 42);
        walls.lineTo(39, 20.6);
        h.draw(walls);

        h.setColor(DOOR);
        h.fill(new Rectangle2D.Double(20, 24, 8, 12));

        glow(h);
        h.dispose();
    }

    /** The light out of the door, clipped to the silhouette so none of it lands on the night. */
    private static void glow(Graphics2D g) {
        Path2D.Double silhouette = new Path2D.Double();
        for (int i = 0; i < SILHOUETTE.length; i++) {
            double x = SILHOUETTE[i][0] / 100.0 * BOX;
            double y = SILHOUETTE[i][1] / 100.0 * BOX;
            if (i == 0) silhouette.moveTo(x, y); else silhouette.lineTo(x, y);
        }
        silhouette.closePath();

        // Centred on the door: rect x=20 w=8 -> 24, y=24 h=12 -> 30.
        Point2D.Double centre = new Point2D.Double(24, 30);
        // CSS's default extent for `radial-gradient(circle, ...)` is farthest-corner, measured in
        // the box the gradient is painted into — which is the same 48-unit box the polygon above is
        // a percentage of.
        double radius = 0;
        for (double[] corner : new double[][] {{0, 0}, {BOX, 0}, {0, BOX}, {BOX, BOX}}) {
            radius = Math.max(radius, centre.distance(corner[0], corner[1]));
        }

        Graphics2D lit = (Graphics2D) g.create();
        lit.clip(silhouette);
        lit.setPaint(new RadialGradientPaint(
                centre, (float) radius, GLOW_STOPS, GLOW_COLORS,
                MultipleGradientPaint.CycleMethod.NO_CYCLE));
        lit.fill(silhouette);
        lit.dispose();
    }

    /**
     * The CRT band, over the whole tile, frame included.
     *
     * THE PITCH IS SNAPPED TO WHOLE PIXELS, and to a multiple of three so that the duty stays
     * exactly one-in-three. JUDGEMENT, and the one that changed how the icon looks at the size the
     * owner will actually see it at. The proportional period at 180 is 5.625px: every band lands on
     * a different sub-pixel phase, the rasteriser splits each one across two rows by a different
     * ratio, and what comes out is not a texture but a moiré — bands visibly heavier every third or
     * fourth row, marching. Snapping 5.625 to 6 gives 2 dark pixels in 6, every band identical,
     * every edge on a pixel boundary, and a density 7% off the study's. A 7% error nobody can see
     * bought the removal of a beat everybody can.
     *
     * `--raw-scanlines` skips both the snap and the threshold and renders the literal proportional
     * band at every size, which is the "before" of the pair beside the worklog.
     */
    private static void scanLines(Graphics2D g, int size, boolean ruled) {
        double ideal = size * SCAN_PERIOD_AT_REFERENCE / SCAN_REFERENCE;
        g.setColor(SCAN_INK);

        if (!ruled) {
            for (double y = 0; y < size; y += ideal) {
                g.fill(new Rectangle2D.Double(0, y, size, ideal * SCAN_DUTY));
            }
            return;
        }

        if (ideal < SCAN_MIN_PERIOD_PX) return;
        int period = (int) Math.max(3, Math.round(ideal / 3.0) * 3);
        int dark = (int) Math.round(period * SCAN_DUTY);
        for (int y = 0; y < size; y += period) {
            g.fill(new Rectangle2D.Double(0, y, size, dark));
        }
    }

    // ---------------------------------------------------------------- the contact sheet

    /**
     * Every shipped size laid out on black at 8x nearest-neighbour, next to itself at 1:1. The 8x
     * is the pixels as they are, magnified — not the icon re-rendered larger, which would answer a
     * different question.
     */
    private static BufferedImage contactSheet(boolean ruled) {
        int[] sizes = allCheckSizes();
        int zoom = 8;
        int pad = 24;
        int maxRow = 0;
        int height = pad;
        for (int px : sizes) {
            if (px > 180) continue;
            maxRow = Math.max(maxRow, px * zoom + pad + px);
            height += px * zoom + pad;
        }
        BufferedImage sheet = new BufferedImage(maxRow + 2 * pad, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = sheet.createGraphics();
        g.setColor(Color.BLACK);
        g.fill(new Rectangle2D.Double(0, 0, sheet.getWidth(), sheet.getHeight()));
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        int y = pad;
        for (int px : sizes) {
            if (px > 180) continue;
            BufferedImage one = render(px, ruled);
            g.drawImage(one, pad, y, px * zoom, px * zoom, null);
            g.drawImage(one, pad + px * zoom + pad, y, px, px, null);
            y += px * zoom + pad;
        }
        g.dispose();
        return sheet;
    }

    // ---------------------------------------------------------------- Contents.json

    private static String contentsJson() {
        StringBuilder b = new StringBuilder("{\n  \"images\" : [\n");
        for (int i = 0; i < SET.length; i++) {
            Slot s = SET[i];
            b.append("    {\n")
             .append("      \"filename\" : \"").append(name(s.px())).append("\",\n")
             .append("      \"idiom\" : \"").append(s.idiom()).append("\",\n")
             .append("      \"scale\" : \"").append(s.scale()).append("\",\n")
             .append("      \"size\" : \"").append(s.size()).append("\"\n")
             .append("    }").append(i == SET.length - 1 ? "\n" : ",\n");
        }
        b.append("  ],\n  \"info\" : {\n    \"author\" : \"xcode\",\n    \"version\" : 1\n  }\n}\n");
        return b.toString();
    }
}
