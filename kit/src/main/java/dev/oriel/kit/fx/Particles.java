package dev.oriel.kit.fx;

import dev.oriel.engine.css.CssColors;
import dev.oriel.engine.dom.Document;
import dev.oriel.engine.dom.Element;
import dev.oriel.engine.replaced.CanvasContent;

import java.util.ArrayList;
import java.util.WeakHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pixel particles on a {@code <canvas>}: one canvas pixel is one GUI px, so every particle is a crisp square, cross
 * or tiny glyph. Declared in markup and driven from Java:
 *
 * <pre>{@code <canvas class="fx" data-fx="embers" data-rate="20" data-area="bottom" data-seed="3"></canvas>}</pre>
 *
 * {@link #attach} finds every {@code canvas[data-fx]} of a page and animates it; {@link #emitter} returns one, for
 * bursts ({@link Emitter#burst}) or a changed rate. The canvas takes the pixel size of its box, so size it with CSS (usually
 * {@code position: absolute; inset: 0} over the thing it decorates, with {@code pointer-events: none}).
 *
 * <p>Attributes: {@code data-fx} the preset ({@link Preset}), {@code data-rate} particles a second (0 for
 * bursts only), {@code data-color} a comma-separated colour ramp from birth to death, {@code data-area}
 * {@code full | bottom | top | centre | ring}, {@code data-seed} the random seed. Everything is stepped at a fixed
 * 60 Hz from a seeded generator, so a page at the same time always shows the same particles (and screenshots
 * and tests are repeatable).
 */
public final class Particles {
    /** A particle's look. */
    public enum Shape { DOT, BIG, PLUS, STAR, SPARK, RING, NOTE }

    /**
     * A particle system's defaults: spawn rate a second, life range in seconds, initial velocity ranges (px a
     * second), gravity (px/s², positive is down), horizontal sway, colour ramp, shape, spawn area.
     */
    public record Preset(float rate, float lifeMin, float lifeMax, float vxMin, float vxMax, float vyMin, float vyMax,
                         float gravity, float sway, int[] colors, Shape shape, String area) {}

    private static final Map<String, Preset> PRESETS = Map.of(
            "embers", new Preset(16, 1.0f, 2.2f, -4, 4, -30, -12, -4, 6,
                    argb("#fff3a0", "#ffb23a", "#ff6a1a", "#c0301a", "#5a1a10"), Shape.DOT, "bottom"),
            "sparks", new Preset(0, 0.25f, 0.6f, -110, 110, -140, -30, 260, 0,
                    argb("#ffffff", "#fff0a0", "#ffb030", "#ff6010"), Shape.SPARK, "centre"),
            "glint", new Preset(7, 0.6f, 1.2f, -2, 2, -6, -2, 0, 2,
                    argb("#ffd8ff", "#ff9cff", "#c070ff", "#7040e0"), Shape.PLUS, "full"),
            "stars", new Preset(5, 0.7f, 1.4f, -2, 2, -8, -3, 0, 1,
                    argb("#ffffff", "#fff2a8", "#ffc93a", "#d08a10"), Shape.STAR, "full"),
            "notes", new Preset(3, 1.6f, 2.4f, -6, 6, -22, -14, 0, 10,
                    argb("#7cff5a", "#ffe14a", "#ff5a7a", "#c46aff", "#5ab8ff"), Shape.NOTE, "bottom"),
            "bubbles", new Preset(6, 1.2f, 2.2f, -2, 2, -20, -10, 0, 5,
                    argb("#ffffff", "#c8a8ff", "#9070e0"), Shape.RING, "bottom"),
            "dust", new Preset(4, 3.0f, 5.0f, -3, 3, -4, 2, 0, 3,
                    argb("#ffffff40", "#ffffff30", "#ffffff00"), Shape.DOT, "full"),
            "hearts", new Preset(0, 0.6f, 1.0f, -30, 30, -60, -30, 40, 0,
                    argb("#ff8a9a", "#ff4060", "#a01830"), Shape.BIG, "centre"));

    private final Document doc;
    private final List<Emitter> emitters = new ArrayList<>();

    private Particles(Document doc) {
        this.doc = doc;
    }

    /**
     * Starts every {@code canvas[data-fx]} of {@code doc} and returns the page's particles; keep it to add bursts.
     * Nothing is held outside the page, so a page that is thrown away takes its particles with it.
     */
    public static Particles attach(Document doc) {
        Particles fx = new Particles(doc);
        for (Element canvas : doc.querySelectorAll("canvas[data-fx]")) fx.emitter(canvas);
        doc.scheduler().requestAnimationFrame(fx::frame);
        return fx;
    }

    /** The emitter of {@code canvas}, started on first use (a canvas without {@code data-fx} emits only bursts). */
    public Emitter emitter(Element canvas) {
        for (Emitter e : emitters) if (e.canvas == canvas) return e;
        Emitter e = new Emitter(canvas);
        emitters.add(e);
        return e;
    }

    /** The emitter of the canvas with this id. */
    public Emitter emitter(String id) {
        Element canvas = doc.getElementById(id);
        if (canvas == null) throw new IllegalArgumentException("no canvas #" + id);
        return emitter(canvas);
    }

    /** The named preset, or null. */
    public static Preset preset(String name) {
        return PRESETS.get(name);
    }

    private void frame(double now) {
        emitters.removeIf(e -> !e.canvas.isConnected());
        for (Emitter e : emitters) e.advance(now);
        doc.scheduler().requestAnimationFrame(this::frame);
    }

    static int[] argb(String... css) {
        int[] out = new int[css.length];
        for (int i = 0; i < css.length; i++) {
            Integer c = CssColors.parse(css[i], 0xffffffff);
            out[i] = c == null ? 0xffffffff : c;
        }
        return out;
    }

    /** One canvas's particles. */
    public static final class Emitter {
        private static final double STEP = 1000.0 / 60;
        private static final int MAX = 600;

        final Element canvas;
        private Preset preset;
        private float rate;
        private int[] colors;
        private String area;
        private long seed;
        private final List<P> live = new ArrayList<>();
        private double last = Double.NaN, owed, carry;
        private int width, height;
        private int[] pixels = new int[0];
        private boolean drawn;

        Emitter(Element canvas) {
            this.canvas = canvas;
            Preset p = PRESETS.getOrDefault(canvas.getAttribute("data-fx"), PRESETS.get("sparks"));
            preset = p;
            rate = number("data-rate", p.rate());
            String c = canvas.getAttribute("data-color");
            colors = c == null || c.isBlank() ? p.colors() : argb(c.split("\\s*,\\s*"));
            String a = canvas.getAttribute("data-area");
            area = a == null || a.isBlank() ? p.area() : a;
            seed = (long) number("data-seed", 1) * 0x9E3779B97F4A7C15L + 1;
        }

        /** Particles a second (0 stops the stream; live particles finish). */
        public Emitter rate(float perSecond) {
            rate = Math.max(0, perSecond);
            return this;
        }

        /** The colour ramp, birth to death. */
        public Emitter colors(String... css) {
            colors = argb(css);
            return this;
        }

        /** The number of live particles. */
        public int count() {
            return live.size();
        }

        /** {@code count} particles of preset {@code name} at canvas px {@code (x, y)}. */
        public void burst(String name, float x, float y, int count) {
            Preset p = PRESETS.getOrDefault(name, preset);
            for (int i = 0; i < count && live.size() < MAX; i++) live.add(spawn(p, p.colors(), x, y));
        }

        private float number(String attr, float fallback) {
            String v = canvas.getAttribute(attr);
            if (v == null) return fallback;
            try {
                return Float.parseFloat(v.trim());
            } catch (NumberFormatException e) {
                return fallback;
            }
        }

        private float random() {
            seed ^= seed << 13;
            seed ^= seed >>> 7;
            seed ^= seed << 17;
            return (seed >>> 40) / (float) (1L << 24);
        }

        private float range(float a, float b) {
            return a + (b - a) * random();
        }

        void advance(double now) {
            int w = Math.max(1, Math.round(canvas.clientWidth())), h = Math.max(1, Math.round(canvas.clientHeight()));
            if (w != width || h != height) {
                width = w;
                height = h;
                pixels = new int[w * h];
                canvas.setAttribute("width", String.valueOf(w));
                canvas.setAttribute("height", String.valueOf(h));
                drawn = true;
            }
            if (Double.isNaN(last)) last = now;
            owed = Math.min(owed + now - last, 250);
            last = now;
            boolean stepped = false;
            while (owed >= STEP) {
                owed -= STEP;
                step();
                stepped = true;
            }
            if (stepped && (drawn || !live.isEmpty())) draw();
        }

        private void step() {
            float dt = 1 / 60f;
            carry += rate * dt;
            while (carry >= 1 && live.size() < MAX) {
                carry -= 1;
                float[] at = origin();
                live.add(spawn(preset, colors, at[0], at[1]));
            }
            if (live.size() >= MAX) carry = 0;
            for (int i = live.size() - 1; i >= 0; i--) {
                P p = live.get(i);
                p.age += dt;
                if (p.age >= p.life) {
                    live.remove(i);
                    continue;
                }
                p.vy += p.gravity * dt;
                p.x += (p.vx + (float) Math.sin(p.phase + p.age * 3) * p.sway) * dt;
                p.y += p.vy * dt;
            }
        }

        private float[] origin() {
            return switch (area) {
                case "bottom" -> new float[] {range(0, width), height - 1};
                case "top" -> new float[] {range(0, width), 0};
                case "centre" -> new float[] {width / 2f + range(-3, 3), height / 2f + range(-3, 3)};
                case "ring" -> {
                    float a = range(0, (float) (Math.PI * 2)), r = Math.min(width, height) / 2f - 2;
                    yield new float[] {width / 2f + (float) Math.cos(a) * r, height / 2f + (float) Math.sin(a) * r};
                }
                default -> new float[] {range(0, width), range(0, height)};
            };
        }

        private P spawn(Preset p, int[] ramp, float x, float y) {
            P q = new P();
            q.x = x;
            q.y = y;
            q.vx = range(p.vxMin(), p.vxMax());
            q.vy = range(p.vyMin(), p.vyMax());
            q.life = range(p.lifeMin(), p.lifeMax());
            q.gravity = p.gravity();
            q.sway = p.sway() * range(0.4f, 1);
            q.phase = range(0, 6.28f);
            q.shape = p.shape();
            q.ramp = ramp;
            q.hue = (int) (random() * ramp.length);
            return q;
        }

        private void draw() {
            java.util.Arrays.fill(pixels, 0);
            for (P p : live) paint(p);
            if (canvas.replaced instanceof CanvasContent content && content.surface().width() == width
                    && content.surface().height() == height) {
                content.surface().setPixels(0, 0, width, height, pixels);
                canvas.ownerDocument().invalidatePaint();
            }
            drawn = !live.isEmpty();
        }

        private void paint(P p) {
            float t = p.age / p.life;
            int color;
            if (p.shape == Shape.NOTE) color = p.ramp[p.hue];
            else {
                float f = t * (p.ramp.length - 1);
                int i = Math.min((int) f, p.ramp.length - 2);
                color = p.ramp.length == 1 ? p.ramp[0] : mix(p.ramp[i], p.ramp[i + 1], f - i);
            }
            float fade = t < 0.1f ? t / 0.1f : t > 0.7f ? (1 - t) / 0.3f : 1;
            color = withAlpha(color, fade);
            int x = Math.round(p.x), y = Math.round(p.y);
            switch (p.shape) {
                case DOT -> plot(x, y, color);
                case BIG -> {
                    plot(x, y, color);
                    plot(x + 1, y, color);
                    plot(x, y + 1, color);
                    plot(x + 1, y + 1, color);
                }
                case PLUS -> {
                    plot(x, y, color);
                    if (t > 0.2f && t < 0.8f) {
                        int arm = withAlpha(color, 0.7f);
                        plot(x - 1, y, arm);
                        plot(x + 1, y, arm);
                        plot(x, y - 1, arm);
                        plot(x, y + 1, arm);
                    }
                }
                case STAR -> {
                    plot(x, y, color);
                    int reach = t > 0.3f && t < 0.7f ? 2 : 1, arm = withAlpha(color, 0.6f);
                    for (int r = 1; r <= reach; r++) {
                        plot(x - r, y, arm);
                        plot(x + r, y, arm);
                        plot(x, y - r, arm);
                        plot(x, y + r, arm);
                    }
                }
                case SPARK -> {
                    plot(x, y, color);
                    float len = (float) Math.hypot(p.vx, p.vy);
                    if (len > 1) {
                        int tail = withAlpha(color, 0.5f);
                        plot(Math.round(p.x - p.vx / len * 1.5f), Math.round(p.y - p.vy / len * 1.5f), tail);
                    }
                }
                case RING -> {
                    plot(x - 1, y, color);
                    plot(x + 1, y, color);
                    plot(x, y - 1, color);
                    plot(x, y + 1, color);
                    plot(x - 1, y - 1, withAlpha(0xffffffff, fade * 0.8f));
                }
                case NOTE -> {
                    for (int dy = 0; dy < 4; dy++) plot(x + 1, y - dy, color);
                    plot(x + 2, y - 3, color);
                    plot(x, y, color);
                    plot(x - 1, y, color);
                    plot(x - 1, y + 1, color);
                    plot(x, y + 1, color);
                }
            }
        }

        private void plot(int x, int y, int argb) {
            if (x < 0 || y < 0 || x >= width || y >= height) return;
            int a = argb >>> 24;
            if (a == 0) return;
            int i = y * width + x, d = pixels[i], da = d >>> 24;
            if (a == 255 || da == 0) {
                pixels[i] = argb;
                return;
            }
            float sa = a / 255f, ra = sa + da / 255f * (1 - sa);
            int r = (int) ((((argb >> 16) & 255) * sa + ((d >> 16) & 255) * da / 255f * (1 - sa)) / ra);
            int g = (int) ((((argb >> 8) & 255) * sa + ((d >> 8) & 255) * da / 255f * (1 - sa)) / ra);
            int b = (int) (((argb & 255) * sa + (d & 255) * da / 255f * (1 - sa)) / ra);
            pixels[i] = Math.round(ra * 255) << 24 | r << 16 | g << 8 | b;
        }

        private static int withAlpha(int argb, float f) {
            int a = Math.round((argb >>> 24) * Math.max(0, Math.min(1, f)));
            return a << 24 | (argb & 0xffffff);
        }

        private static int mix(int a, int b, float t) {
            int out = 0;
            for (int s = 0; s <= 24; s += 8) {
                int ca = (a >>> s) & 255, cb = (b >>> s) & 255;
                out |= Math.round(ca + (cb - ca) * t) << s;
            }
            return out;
        }
    }

    private static final class P {
        float x, y, vx, vy, age, life, gravity, sway, phase;
        Shape shape;
        int[] ramp;
        int hue;
    }
}
