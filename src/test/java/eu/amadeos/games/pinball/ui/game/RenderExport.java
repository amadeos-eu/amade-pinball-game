package eu.amadeos.games.pinball.ui.game;

import eu.amadeos.gameengine.pinball.*;
import eu.amadeos.games.pinball.domain.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.lang.reflect.Method;
import java.util.*;

/**
 * What Blender needs to build a table (tools/render): {@code <id>-print.png}, the printed playfield seen from
 * above with nothing standing on it, and {@code <id>.json}, every part, flipper, wall and lamp with its place.
 * {@code ./gradlew renderExport -Ptable=<id> -Pout=<folder>}.
 */
public final class RenderExport {
    public static void main(String[] a) throws Exception {
        String id = a[0]; File dir = new File(a[1]); double ppu = a.length > 2 ? Double.parseDouble(a[2]) : 8;
        dir.mkdirs();
        Table table = new Table(TableFile.load(id));
        TableSpec spec = table.spec();
        TableArt art = new TableArt(table, 2);
        // the print: the floor and the parts drawn onto it, nothing standing up
        double L = 0, T = 0, R = Table.WIDTH, B = Table.LENGTH;
        Relief low = new Relief(L, T, R, B, ppu);
        Relief junk = new Relief(L, T, R, B, 1);
        Relief junkHigh = junk.above();
        Method floor = TableArt.class.getDeclaredMethod("floor", Graphics2D.class, TableSpec.class);
        floor.setAccessible(true);
        floor.invoke(art, low.floor(), spec);
        Method draw = null;
        for (Method m : TableArt.class.getDeclaredMethods()) if (m.getName().equals("draw") && m.getParameterCount() == 8) draw = m;
        draw.setAccessible(true);
        Art.Shadows none = Art.Shadows.NONE;
        for (Part part : spec.parts()) {
            if (part.layer() != 0) continue;
            switch (part.kind()) {
                case "insert", "round", "fan", "modering", "label", "rollover", "kickback", "saucer", "lanes", "magnet", "captive" ->
                        draw.invoke(art, part, table, low.floor(), junk.parts(), none, junkHigh, none, spec.palette());
                default -> { }
            }
        }
        BufferedImage print = new BufferedImage((int) (R * ppu), (int) (B * ppu), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = print.createGraphics();
        Field f = null;
        java.lang.reflect.Field fl = Relief.class.getDeclaredField("floor"); fl.setAccessible(true);
        g.drawImage((BufferedImage) fl.get(low), 0, 0, null);
        g.dispose();
        javax.imageio.ImageIO.write(print, "png", new File(dir, id + "-print.png"));

        StringBuilder js = new StringBuilder("{\"id\":\"" + id + "\",\"width\":" + Table.WIDTH + ",\"length\":" + Table.LENGTH + ",\"palette\":[");
        Color[] p = spec.palette();
        for (int i = 0; i < p.length; i++) js.append(i > 0 ? "," : "").append(hex(p[i]));
        js.append("],\"parts\":[\n");
        boolean first = true;
        for (Part part : spec.parts()) {
            if (!first) js.append(",\n"); first = false;
            js.append("{\"kind\":\"").append(part.kind()).append("\",\"layer\":").append(part.layer()).append(",\"words\":[");
            for (int i = 0; i < part.words().size(); i++) js.append(i > 0 ? "," : "").append(q(part.words().get(i)));
            js.append("],\"nums\":").append(Arrays.toString(part.nums())).append(",\"names\":[");
            for (int i = 0; i < part.names().size(); i++) js.append(i > 0 ? "," : "").append(q(part.names().get(i)));
            js.append("],\"colors\":[");
            for (int i = 0; i < part.names().size(); i++) {
                String w = part.names().get(i);
                String c = "null";
                if ((w.length() == 2 && w.startsWith("p") && Character.isDigit(w.charAt(1))) || (w.startsWith("x") && w.length() == 7)) c = hex(spec.color(w));
                js.append(i > 0 ? "," : "").append(c);
            }
            js.append("],\"options\":{");
            int k = 0;
            for (var e : part.options().entrySet()) js.append(k++ > 0 ? "," : "").append(q(e.getKey())).append(":").append(q(e.getValue()));
            js.append("}");
            if (part.kind().equals("ramp") || part.kind().equals("tube") || part.kind().equals("track")) {
                Track t = table.field().track(part.id());
                if (t != null) {
                    js.append(",\"track\":[");
                    for (int i = 0; i < t.points(); i++) js.append(i > 0 ? "," : "").append(String.format(Locale.ROOT, "[%.2f,%.2f,%.2f]", t.x(i), t.y(i), t.h(i)));
                    js.append("],\"wireStart\":").append(table.wireStart(part.id()));
                }
            }
            js.append("}");
        }
        js.append("],\"flippers\":[");
        first = true;
        for (Flipper fp : table.field().flippers()) {
            if (!first) js.append(","); first = false;
            js.append(String.format(Locale.ROOT, "{\"id\":\"%s\",\"px\":%.2f,\"py\":%.2f,\"length\":%.2f,\"base\":%.2f,\"tip\":%.2f,\"rest\":%.4f,\"up\":%.4f,\"layer\":%d}",
                    fp.id, fp.px, fp.py, fp.length, fp.baseRadius, fp.tipRadius, fp.rest, fp.up, fp.layer));
        }
        js.append("],\"segments\":[");
        first = true;
        for (Segment s : table.field().segments()) {
            if (!first) js.append(","); first = false;
            js.append(String.format(Locale.ROOT, "[%.2f,%.2f,%.2f,%.2f,%.2f,%d,\"%s\",\"%s\"]", s.ax, s.ay, s.bx, s.by, s.thickness, s.layer, s.material, s.id));
        }
        js.append("],\"lamps\":[");
        first = true;
        for (TableArt.Lamp l : art.lamps) {
            if (!first) js.append(","); first = false;
            js.append(String.format(Locale.ROOT, "{\"name\":\"%s\",\"shape\":\"%s\",\"x\":%.2f,\"y\":%.2f,\"size\":%.2f,\"deg\":%.2f,\"color\":%s,\"layer\":%d}",
                    l.name(), l.shape(), l.x(), l.y(), l.size(), l.deg(), hex(l.color()), l.layer()));
        }
        js.append("]}\n");
        try (Writer w = new FileWriter(new File(dir, id + ".json"))) { w.write(js.toString()); }
    }
    static String hex(Color c) { return String.format("\"#%02x%02x%02x\"", c.getRed(), c.getGreen(), c.getBlue()); }
    static String q(String s) { return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""; }
    interface Field {}
}
