package com.codewalnut.ats.bank;

import java.util.List;
import java.util.Locale;

/**
 * Small, dependency-free SVG drawings for question pictures (ADR-0014). Every picture is drawn
 * from the same numbers the answer is computed from, so question, picture and answer always agree.
 * Output is shown to candidates through an &lt;img&gt; tag, never inlined into the page.
 */
public final class Svg {

    static final String[] COLORS = {"#4F5BD5", "#F59E0B", "#10B981", "#EF4444", "#8B5CF6", "#0EA5E9", "#EC4899"};
    private static final String FONT = "font-family=\"Arial, Helvetica, sans-serif\"";
    private static final String INK = "#1F2937";

    private Svg() {}

    static String open(int w, int h) {
        return "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 " + w + " " + h + "\" width=\"" + w + "\" height=\""
                + h + "\"><rect width=\"" + w + "\" height=\"" + h + "\" fill=\"#ffffff\"/>";
    }

    static String text(double x, double y, String s, int size, String anchor, String extra) {
        boolean ownFill = extra != null && (extra.startsWith("fill=") || extra.contains(" fill="));
        return "<text x=\"" + f(x) + "\" y=\"" + f(y) + "\" " + FONT + " font-size=\"" + size + "\""
                + (ownFill ? "" : " fill=\"" + INK + "\"") + " text-anchor=\"" + anchor + "\"" + (extra == null ? "" : " " + extra)
                + ">" + esc(s) + "</text>";
    }

    static String f(double v) {
        return v == Math.rint(v) ? Long.toString((long) v) : String.format(Locale.ROOT, "%.2f", v);
    }

    static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    // ---- charts ----

    /** Vertical bar chart with values printed on the bars. */
    public static String barChart(String title, List<String> labels, List<Integer> values, String unit) {
        int w = 520;
        int h = 320;
        int left = 60;
        int bottom = 270;
        int top = 50;
        int max = niceMax(values.stream().mapToInt(Integer::intValue).max().orElse(10));
        StringBuilder s = new StringBuilder(open(w, h));
        s.append(text(w / 2.0, 26, title, 16, "middle", "font-weight=\"bold\""));
        s.append(axes(left, top, bottom, w - 20, max, unit));
        double slot = (w - 20 - left) / (double) labels.size();
        double bw = slot * 0.55;
        for (int i = 0; i < labels.size(); i++) {
            double x = left + slot * i + (slot - bw) / 2;
            double bh = (bottom - top) * values.get(i) / (double) max;
            s.append("<rect x=\"").append(f(x)).append("\" y=\"").append(f(bottom - bh)).append("\" width=\"").append(f(bw))
                    .append("\" height=\"").append(f(bh)).append("\" fill=\"").append(COLORS[0]).append("\"/>");
            s.append(text(x + bw / 2, bottom - bh - 6, String.valueOf(values.get(i)), 12, "middle", "font-weight=\"bold\""));
            s.append(text(x + bw / 2, bottom + 18, labels.get(i), 12, "middle", null));
        }
        return s.append("</svg>").toString();
    }

    /** Line chart with points labelled. */
    public static String lineChart(String title, List<String> labels, List<Integer> values, String unit) {
        int w = 520;
        int h = 320;
        int left = 60;
        int bottom = 270;
        int top = 50;
        int max = niceMax(values.stream().mapToInt(Integer::intValue).max().orElse(10));
        StringBuilder s = new StringBuilder(open(w, h));
        s.append(text(w / 2.0, 26, title, 16, "middle", "font-weight=\"bold\""));
        s.append(axes(left, top, bottom, w - 20, max, unit));
        double slot = (w - 20 - left) / (double) labels.size();
        StringBuilder points = new StringBuilder();
        for (int i = 0; i < labels.size(); i++) {
            double x = left + slot * i + slot / 2;
            double y = bottom - (bottom - top) * values.get(i) / (double) max;
            points.append(f(x)).append(',').append(f(y)).append(' ');
            s.append(text(x, bottom + 18, labels.get(i), 12, "middle", null));
        }
        s.append("<polyline points=\"").append(points.toString().strip()).append("\" fill=\"none\" stroke=\"").append(COLORS[0])
                .append("\" stroke-width=\"3\"/>");
        for (int i = 0; i < labels.size(); i++) {
            double x = left + slot * i + slot / 2;
            double y = bottom - (bottom - top) * values.get(i) / (double) max;
            s.append("<circle cx=\"").append(f(x)).append("\" cy=\"").append(f(y)).append("\" r=\"5\" fill=\"").append(COLORS[1]).append("\"/>");
            s.append(text(x, y - 10, String.valueOf(values.get(i)), 12, "middle", "font-weight=\"bold\""));
        }
        return s.append("</svg>").toString();
    }

    /** Pie chart; percents must add up to 100. Shares are labelled in the legend. */
    public static String pieChart(String title, List<String> labels, List<Integer> percents) {
        int w = 520;
        int h = 320;
        double cx = 170;
        double cy = 180;
        double r = 115;
        StringBuilder s = new StringBuilder(open(w, h));
        s.append(text(w / 2.0, 26, title, 16, "middle", "font-weight=\"bold\""));
        double start = -Math.PI / 2;
        for (int i = 0; i < labels.size(); i++) {
            double sweep = 2 * Math.PI * percents.get(i) / 100.0;
            double end = start + sweep;
            double x1 = cx + r * Math.cos(start);
            double y1 = cy + r * Math.sin(start);
            double x2 = cx + r * Math.cos(end);
            double y2 = cy + r * Math.sin(end);
            s.append("<path d=\"M").append(f(cx)).append(',').append(f(cy)).append(" L").append(f(x1)).append(',').append(f(y1))
                    .append(" A").append(f(r)).append(',').append(f(r)).append(" 0 ").append(sweep > Math.PI ? 1 : 0).append(",1 ")
                    .append(f(x2)).append(',').append(f(y2)).append(" Z\" fill=\"").append(COLORS[i % COLORS.length])
                    .append("\" stroke=\"#ffffff\" stroke-width=\"2\"/>");
            double mid = start + sweep / 2;
            s.append(text(cx + r * 0.62 * Math.cos(mid), cy + r * 0.62 * Math.sin(mid) + 5, percents.get(i) + "%", 13, "middle",
                    "font-weight=\"bold\""));
            start = end;
            double ly = 90 + i * 30;
            s.append("<rect x=\"330\" y=\"").append(f(ly - 13)).append("\" width=\"16\" height=\"16\" fill=\"")
                    .append(COLORS[i % COLORS.length]).append("\"/>");
            s.append(text(354, ly, labels.get(i) + " (" + percents.get(i) + "%)", 13, "start", null));
        }
        return s.append("</svg>").toString();
    }

    /** A simple data table. */
    public static String table(String title, List<String> headers, List<List<String>> rows) {
        int colW = 104;
        int rowH = 32;
        int w = Math.max(360, headers.size() * colW + 40);
        int h = 60 + (rows.size() + 1) * rowH + 20;
        StringBuilder s = new StringBuilder(open(w, h));
        s.append(text(w / 2.0, 28, title, 16, "middle", "font-weight=\"bold\""));
        double x0 = (w - headers.size() * colW) / 2.0;
        double y0 = 46;
        s.append("<rect x=\"").append(f(x0)).append("\" y=\"").append(f(y0)).append("\" width=\"").append(headers.size() * colW)
                .append("\" height=\"").append(rowH).append("\" fill=\"#E0E7FF\"/>");
        for (int r = 0; r <= rows.size(); r++) {
            List<String> cells = r == 0 ? headers : rows.get(r - 1);
            for (int c = 0; c < cells.size(); c++) {
                s.append(text(x0 + c * colW + colW / 2.0, y0 + r * rowH + 21, cells.get(c), 13, "middle",
                        r == 0 ? "font-weight=\"bold\"" : null));
            }
        }
        for (int r = 0; r <= rows.size() + 1; r++) {
            s.append(line(x0, y0 + r * rowH, x0 + headers.size() * colW, y0 + r * rowH, "#94A3B8", 1));
        }
        for (int c = 0; c <= headers.size(); c++) {
            s.append(line(x0 + c * colW, y0, x0 + c * colW, y0 + (rows.size() + 1) * rowH, "#94A3B8", 1));
        }
        return s.append("</svg>").toString();
    }

    private static String axes(int left, int top, int bottom, int right, int max, String unit) {
        StringBuilder s = new StringBuilder();
        for (int i = 0; i <= 5; i++) {
            double y = bottom - (bottom - top) * i / 5.0;
            s.append(line(left, y, right, y, "#E5E7EB", 1));
            s.append(text(left - 8, y + 4, String.valueOf(max * i / 5), 11, "end", null));
        }
        s.append(line(left, top, left, bottom, INK, 1.5)).append(line(left, bottom, right, bottom, INK, 1.5));
        if (unit != null && !unit.isEmpty()) {
            s.append(text(14, (top + bottom) / 2.0, unit, 11, "middle",
                    "transform=\"rotate(-90 14 " + f((top + bottom) / 2.0) + ")\""));
        }
        return s.toString();
    }

    /** A round number at or above max, split evenly into five gridlines. */
    static int niceMax(int max) {
        int step = 5;
        for (int candidate : new int[] {5, 10, 20, 25, 50, 100, 200, 250, 500, 1000, 2000, 2500, 5000}) {
            if (candidate * 5 >= max) {
                step = candidate;
                break;
            }
        }
        return step * 5;
    }

    static String line(double x1, double y1, double x2, double y2, String color, double width) {
        return "<line x1=\"" + f(x1) + "\" y1=\"" + f(y1) + "\" x2=\"" + f(x2) + "\" y2=\"" + f(y2) + "\" stroke=\"" + color
                + "\" stroke-width=\"" + f(width) + "\"/>";
    }

    // ---- reasoning pictures ----

    /** An analogue clock showing h:m. */
    public static String clock(int h, int m) {
        int size = 240;
        double c = size / 2.0;
        double r = 100;
        StringBuilder s = new StringBuilder(open(size, size));
        s.append("<circle cx=\"").append(f(c)).append("\" cy=\"").append(f(c)).append("\" r=\"").append(f(r))
                .append("\" fill=\"#F8FAFC\" stroke=\"").append(INK).append("\" stroke-width=\"4\"/>");
        for (int i = 1; i <= 12; i++) {
            double a = Math.toRadians(i * 30 - 90);
            s.append(text(c + 80 * Math.cos(a), c + 80 * Math.sin(a) + 6, String.valueOf(i), 16, "middle", "font-weight=\"bold\""));
        }
        for (int i = 0; i < 60; i++) {
            double a = Math.toRadians(i * 6 - 90);
            double inner = i % 5 == 0 ? 90 : 95;
            s.append(line(c + inner * Math.cos(a), c + inner * Math.sin(a), c + r * Math.cos(a), c + r * Math.sin(a), INK, i % 5 == 0 ? 2 : 1));
        }
        double hourAngle = Math.toRadians((h % 12) * 30 + m * 0.5 - 90);
        double minuteAngle = Math.toRadians(m * 6 - 90);
        s.append(line(c, c, c + 52 * Math.cos(hourAngle), c + 52 * Math.sin(hourAngle), INK, 6));
        s.append(line(c, c, c + 78 * Math.cos(minuteAngle), c + 78 * Math.sin(minuteAngle), COLORS[0], 4));
        s.append("<circle cx=\"").append(f(c)).append("\" cy=\"").append(f(c)).append("\" r=\"6\" fill=\"").append(INK).append("\"/>");
        return s.append("</svg>").toString();
    }

    /** Two overlapping sets with the count in each region; neither is printed outside. */
    public static String venn2(String a, String b, int onlyA, int both, int onlyB, int neither) {
        int w = 460;
        int h = 280;
        StringBuilder s = new StringBuilder(open(w, h));
        s.append("<rect x=\"10\" y=\"10\" width=\"440\" height=\"260\" fill=\"none\" stroke=\"").append(INK).append("\" stroke-width=\"1.5\"/>");
        s.append("<circle cx=\"180\" cy=\"145\" r=\"95\" fill=\"").append(COLORS[0]).append("\" fill-opacity=\"0.25\" stroke=\"")
                .append(COLORS[0]).append("\" stroke-width=\"2\"/>");
        s.append("<circle cx=\"280\" cy=\"145\" r=\"95\" fill=\"").append(COLORS[1]).append("\" fill-opacity=\"0.25\" stroke=\"")
                .append(COLORS[1]).append("\" stroke-width=\"2\"/>");
        s.append(text(140, 40, a, 15, "middle", "font-weight=\"bold\"")).append(text(320, 40, b, 15, "middle", "font-weight=\"bold\""));
        s.append(text(135, 152, String.valueOf(onlyA), 20, "middle", "font-weight=\"bold\""));
        s.append(text(230, 152, String.valueOf(both), 20, "middle", "font-weight=\"bold\""));
        s.append(text(325, 152, String.valueOf(onlyB), 20, "middle", "font-weight=\"bold\""));
        s.append(text(420, 258, String.valueOf(neither), 18, "middle", "font-weight=\"bold\""));
        return s.append("</svg>").toString();
    }

    /** Three sets; regions = [onlyA, onlyB, onlyC, AB only, BC only, AC only, ABC]. */
    public static String venn3(String a, String b, String c, int[] regions) {
        int w = 460;
        int h = 380;
        StringBuilder s = new StringBuilder(open(w, h));
        double[][] centres = {{185, 150}, {275, 150}, {230, 235}};
        String[] names = {a, b, c};
        for (int i = 0; i < 3; i++) {
            s.append("<circle cx=\"").append(f(centres[i][0])).append("\" cy=\"").append(f(centres[i][1]))
                    .append("\" r=\"95\" fill=\"").append(COLORS[i]).append("\" fill-opacity=\"0.22\" stroke=\"").append(COLORS[i])
                    .append("\" stroke-width=\"2\"/>");
        }
        s.append(text(120, 40, names[0], 15, "middle", "font-weight=\"bold\""));
        s.append(text(340, 40, names[1], 15, "middle", "font-weight=\"bold\""));
        s.append(text(230, 365, names[2], 15, "middle", "font-weight=\"bold\""));
        double[][] spots = {{140, 125}, {320, 125}, {230, 295}, {230, 110}, {290, 215}, {170, 215}, {230, 180}};
        for (int i = 0; i < 7; i++) {
            s.append(text(spots[i][0], spots[i][1] + 7, String.valueOf(regions[i]), 19, "middle", "font-weight=\"bold\""));
        }
        return s.append("</svg>").toString();
    }

    /** A train passing a platform (or a pole when platform is 0), lengths labelled. */
    public static String train(int trainLength, int platformLength) {
        int w = 520;
        int h = 200;
        StringBuilder s = new StringBuilder(open(w, h));
        s.append(line(20, 150, 500, 150, INK, 3));
        s.append("<rect x=\"40\" y=\"92\" width=\"170\" height=\"46\" rx=\"8\" fill=\"").append(COLORS[0]).append("\"/>");
        for (int i = 0; i < 4; i++) {
            s.append("<rect x=\"").append(52 + i * 38).append("\" y=\"100\" width=\"26\" height=\"16\" fill=\"#E0E7FF\"/>");
        }
        s.append("<circle cx=\"75\" cy=\"142\" r=\"8\" fill=\"").append(INK).append("\"/><circle cx=\"175\" cy=\"142\" r=\"8\" fill=\"")
                .append(INK).append("\"/>");
        s.append(text(125, 80, "Train: " + trainLength + " m", 14, "middle", "font-weight=\"bold\""));
        s.append(text(232, 122, "→", 28, "middle", null));
        if (platformLength > 0) {
            s.append("<rect x=\"270\" y=\"150\" width=\"210\" height=\"16\" fill=\"").append(COLORS[1]).append("\"/>");
            s.append(text(375, 190, "Platform: " + platformLength + " m", 14, "middle", "font-weight=\"bold\""));
        } else {
            s.append(line(380, 70, 380, 150, INK, 4)).append("<circle cx=\"380\" cy=\"66\" r=\"7\" fill=\"").append(COLORS[3]).append("\"/>");
            s.append(text(380, 186, "Pole", 14, "middle", "font-weight=\"bold\""));
        }
        return s.append("</svg>").toString();
    }

    // ---- non-verbal figures ----

    /** An arrow with a dot near its tail, rotated by angle degrees (clockwise), centred in a cell. */
    static String arrow(double cx, double cy, double angle, double scale) {
        return "<g transform=\"translate(" + f(cx) + " " + f(cy) + ") rotate(" + f(angle) + ") scale(" + f(scale) + ")\">"
                + "<line x1=\"0\" y1=\"28\" x2=\"0\" y2=\"-22\" stroke=\"" + INK + "\" stroke-width=\"5\"/>"
                + "<polygon points=\"0,-34 -12,-16 12,-16\" fill=\"" + INK + "\"/>"
                + "<circle cx=\"10\" cy=\"26\" r=\"5\" fill=\"" + COLORS[3] + "\"/></g>";
    }

    /** A row of framed cells; null draws a "?" cell. */
    public static String series(List<String> cells) {
        int cell = 100;
        int gap = 14;
        int w = cells.size() * cell + (cells.size() + 1) * gap;
        int h = cell + 2 * gap;
        StringBuilder s = new StringBuilder(open(w, h));
        for (int i = 0; i < cells.size(); i++) {
            double x = gap + i * (cell + gap);
            s.append("<rect x=\"").append(f(x)).append("\" y=\"").append(gap).append("\" width=\"").append(cell).append("\" height=\"")
                    .append(cell).append("\" fill=\"#F8FAFC\" stroke=\"#94A3B8\" stroke-width=\"1.5\"/>");
            if (cells.get(i) == null) {
                s.append(text(x + cell / 2.0, gap + cell / 2.0 + 14, "?", 40, "middle", "font-weight=\"bold\" fill=\"" + COLORS[0] + "\""));
            } else {
                s.append("<g transform=\"translate(").append(f(x)).append(" ").append(gap).append(")\">").append(cells.get(i)).append("</g>");
            }
        }
        return s.append("</svg>").toString();
    }

    /** One 100×100 cell as its own picture (for answer options). */
    public static String cell(String content) {
        return open(100, 100) + "<rect x=\"1\" y=\"1\" width=\"98\" height=\"98\" fill=\"#F8FAFC\" stroke=\"#94A3B8\" stroke-width=\"1.5\"/>"
                + content + "</svg>";
    }

    static String arrowCell(double angle) {
        return arrow(50, 50, ((angle % 360) + 360) % 360, 1.15);
    }

    /** Text drawn plain, mirrored (left-right), as a water image (upside down) or turned 180°. */
    public static String word(String word, String mode) {
        int w = 260;
        int h = 90;
        String transform = switch (mode) {
            case "MIRROR" -> "translate(" + w + " 0) scale(-1 1)";
            case "WATER" -> "translate(0 " + h + ") scale(1 -1)";
            case "ROTATE" -> "rotate(180 " + w / 2 + " " + h / 2 + ")";
            default -> "";
        };
        return open(w, h) + "<g transform=\"" + transform + "\">" + text(w / 2.0, 62, word, 46, "middle", "font-weight=\"bold\" letter-spacing=\"4\"")
                + "</g></svg>";
    }

    /** n dots arranged in a small grid inside a 100×100 cell. */
    static String dots(int n) {
        StringBuilder s = new StringBuilder();
        int cols = n <= 4 ? 2 : 3;
        int rows = (int) Math.ceil(n / (double) cols);
        double step = 24;
        double x0 = 50 - (cols - 1) * step / 2;
        double y0 = 50 - (rows - 1) * step / 2;
        for (int i = 0; i < n; i++) {
            s.append("<circle cx=\"").append(f(x0 + (i % cols) * step)).append("\" cy=\"").append(f(y0 + (i / cols) * step))
                    .append("\" r=\"8\" fill=\"").append(COLORS[0]).append("\"/>");
        }
        return s.toString();
    }

    /** A 3×3 grid of dot counts; the bottom-right cell shows "?". */
    public static String dotMatrix(int[][] counts) {
        int cell = 90;
        int w = 3 * cell + 20;
        StringBuilder s = new StringBuilder(open(w, w));
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                double x = 10 + c * cell;
                double y = 10 + r * cell;
                s.append("<rect x=\"").append(f(x)).append("\" y=\"").append(f(y)).append("\" width=\"").append(cell).append("\" height=\"")
                        .append(cell).append("\" fill=\"#F8FAFC\" stroke=\"#94A3B8\" stroke-width=\"1.5\"/>");
                if (r == 2 && c == 2) {
                    s.append(text(x + cell / 2.0, y + cell / 2.0 + 14, "?", 40, "middle", "font-weight=\"bold\" fill=\"" + COLORS[0] + "\""));
                } else {
                    s.append("<g transform=\"translate(").append(f(x - 5)).append(" ").append(f(y - 5)).append(") scale(0.9)\">")
                            .append(dots(counts[r][c])).append("</g>");
                }
            }
        }
        return s.append("</svg>").toString();
    }

    /** A regular polygon with the given number of sides, inside a 100×100 cell. */
    static String polygon(int sides, double rotation, String fill) {
        StringBuilder pts = new StringBuilder();
        for (int i = 0; i < sides; i++) {
            double a = Math.toRadians(rotation + i * 360.0 / sides - 90);
            pts.append(f(50 + 34 * Math.cos(a))).append(',').append(f(50 + 34 * Math.sin(a))).append(' ');
        }
        return "<polygon points=\"" + pts.toString().strip() + "\" fill=\"" + fill + "\" stroke=\"" + INK + "\" stroke-width=\"2\"/>";
    }
}
