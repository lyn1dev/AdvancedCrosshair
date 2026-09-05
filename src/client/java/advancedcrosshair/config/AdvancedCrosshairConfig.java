package advancedcrosshair.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.fabricmc.loader.api.FabricLoader;

/**
 * Mod settings, persisted to {@code config/advancedcrosshair.json}.
 *
 * <p>Read and written through Gson's tree API rather than reflective binding, so
 * a hand-edited file that is missing keys, carries extra keys, or holds a
 * nonsense value falls back to the default for that one key instead of
 * discarding the whole config.
 *
 * <p>This class deliberately touches no Minecraft classes, so it is byte for
 * byte identical on every version branch.
 */
public final class AdvancedCrosshairConfig {

    public static final int DEFAULT_NORMAL_HIT_COLOR = 0xFFFF3333;
    public static final int DEFAULT_CRITICAL_HIT_COLOR = 0xFF0080FF;

    public static final float MIN_SCALE = 0.5F;
    public static final float MAX_SCALE = 5.0F;
    public static final float DEFAULT_SCALE = 1.0F;

    private static final String FILE_NAME = "advancedcrosshair.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static AdvancedCrosshairConfig instance;

    /** Master switch. When off the mod does nothing and the HUD is pure vanilla. */
    public boolean modEnabled = true;

    public boolean normalHitEnabled = true;
    public int normalHitColor = DEFAULT_NORMAL_HIT_COLOR;

    public boolean criticalHitEnabled = true;
    public int criticalHitColor = DEFAULT_CRITICAL_HIT_COLOR;

    /** 1.0 is the vanilla size. Always clamped to [MIN_SCALE, MAX_SCALE]. */
    public float crosshairScale = DEFAULT_SCALE;

    /** When true the normal crosshair is drawn even while the F3 debug screen is open. */
    public boolean showWithDebugHud = false;

    private AdvancedCrosshairConfig() {
    }

    public static AdvancedCrosshairConfig get() {
        if (instance == null) {
            instance = load();
        }
        return instance;
    }

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
    }

    private static AdvancedCrosshairConfig load() {
        AdvancedCrosshairConfig config = new AdvancedCrosshairConfig();
        Path file = path();
        if (!Files.isRegularFile(file)) {
            return config;
        }

        JsonObject root;
        try {
            JsonElement parsed = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
            if (!parsed.isJsonObject()) {
                return config;
            }
            root = parsed.getAsJsonObject();
        } catch (IOException | RuntimeException e) {
            // Unreadable or malformed: run on defaults rather than break startup.
            return config;
        }

        config.modEnabled = bool(root, "modEnabled", config.modEnabled);
        config.crosshairScale = clampScale(number(root, "crosshairScale", config.crosshairScale));
        config.showWithDebugHud = bool(root, "showCrosshairWithDebugHud", config.showWithDebugHud);

        JsonObject normal = object(root, "normalHit");
        if (normal != null) {
            config.normalHitEnabled = bool(normal, "enabled", config.normalHitEnabled);
            config.normalHitColor = color(normal, "color", config.normalHitColor);
        }

        JsonObject critical = object(root, "criticalHit");
        if (critical != null) {
            config.criticalHitEnabled = bool(critical, "enabled", config.criticalHitEnabled);
            config.criticalHitColor = color(critical, "color", config.criticalHitColor);
        }

        return config;
    }

    public void save() {
        crosshairScale = clampScale(crosshairScale);

        JsonObject root = new JsonObject();
        root.addProperty("modEnabled", modEnabled);

        JsonObject normal = new JsonObject();
        normal.addProperty("enabled", normalHitEnabled);
        normal.addProperty("color", toHex(normalHitColor));
        root.add("normalHit", normal);

        JsonObject critical = new JsonObject();
        critical.addProperty("enabled", criticalHitEnabled);
        critical.addProperty("color", toHex(criticalHitColor));
        root.add("criticalHit", critical);

        root.addProperty("crosshairScale", crosshairScale);
        root.addProperty("showCrosshairWithDebugHud", showWithDebugHud);

        try {
            Path file = path();
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(root), StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException e) {
            // A failed write is not worth taking the game down for.
        }
    }

    public void resetToDefaults() {
        modEnabled = true;
        normalHitEnabled = true;
        normalHitColor = DEFAULT_NORMAL_HIT_COLOR;
        criticalHitEnabled = true;
        criticalHitColor = DEFAULT_CRITICAL_HIT_COLOR;
        crosshairScale = DEFAULT_SCALE;
        showWithDebugHud = false;
    }

    public static float clampScale(float value) {
        if (Float.isNaN(value)) {
            return DEFAULT_SCALE;
        }
        return Math.max(MIN_SCALE, Math.min(MAX_SCALE, value));
    }

    /** Formats as {@code #AARRGGBB}, the same form the config screen accepts back. */
    public static String toHex(int argb) {
        return String.format(Locale.ROOT, "#%08X", argb);
    }

    /**
     * Parses {@code RRGGBB} or {@code AARRGGBB}, with or without a leading '#'.
     * Six digits are treated as fully opaque. Returns null when the text cannot
     * be parsed, so callers can mark the field invalid instead of guessing.
     */
    public static Integer parseHex(String raw) {
        if (raw == null) {
            return null;
        }
        String text = raw.trim();
        if (text.startsWith("#")) {
            text = text.substring(1);
        }
        if (text.length() != 6 && text.length() != 8) {
            return null;
        }
        for (int i = 0; i < text.length(); i++) {
            if (Character.digit(text.charAt(i), 16) < 0) {
                return null;
            }
        }
        long value = Long.parseLong(text, 16);
        if (text.length() == 6) {
            value |= 0xFF000000L;
        }
        return (int) value;
    }

    private static boolean bool(JsonObject owner, String key, boolean fallback) {
        JsonElement e = owner.get(key);
        try {
            return e != null && e.isJsonPrimitive() ? e.getAsBoolean() : fallback;
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static float number(JsonObject owner, String key, float fallback) {
        JsonElement e = owner.get(key);
        try {
            return e != null && e.isJsonPrimitive() ? e.getAsFloat() : fallback;
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static int color(JsonObject owner, String key, int fallback) {
        JsonElement e = owner.get(key);
        if (e == null || !e.isJsonPrimitive()) {
            return fallback;
        }
        try {
            Integer parsed = parseHex(e.getAsString());
            return parsed != null ? parsed : fallback;
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static JsonObject object(JsonObject owner, String key) {
        JsonElement e = owner.get(key);
        return e != null && e.isJsonObject() ? e.getAsJsonObject() : null;
    }
}
