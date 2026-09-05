package advancedcrosshair.config;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

/**
 * Settings screen, reached through Mod Menu.
 *
 * <p>Built only from vanilla widgets so the mod keeps working with no runtime
 * dependency on Mod Menu or any config library. Every control writes straight
 * into the shared {@link AdvancedCrosshairConfig}; the file is saved when the
 * screen closes.
 */
public class ConfigScreen extends Screen {

    private static final int ROW_HEIGHT = 22;
    private static final int WIDGET_WIDTH = 220;
    private static final int LABEL_WIDTH = 96;
    private static final int INVALID_COLOR = 0xFFFF5555;
    private static final int VALID_COLOR = 0xFFE0E0E0;

    private final Screen parent;
    private final AdvancedCrosshairConfig config = AdvancedCrosshairConfig.get();

    private TextFieldWidget normalColorBox;
    private TextFieldWidget criticalColorBox;

    public ConfigScreen(Screen parent) {
        super(Text.literal("Advanced Crosshair"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int left = this.width / 2 - WIDGET_WIDTH / 2;
        int y = Math.max(32, this.height / 2 - (ROW_HEIGHT * 8) / 2);

        this.addDrawableChild(ButtonWidget.builder(toggleLabel("Advanced Crosshair", config.modEnabled), b -> {
            config.modEnabled = !config.modEnabled;
            b.setMessage(toggleLabel("Advanced Crosshair", config.modEnabled));
        }).dimensions(left, y, WIDGET_WIDTH, 20).build());
        y += ROW_HEIGHT;

        this.addDrawableChild(ButtonWidget.builder(toggleLabel("Normal hit color", config.normalHitEnabled), b -> {
            config.normalHitEnabled = !config.normalHitEnabled;
            b.setMessage(toggleLabel("Normal hit color", config.normalHitEnabled));
        }).dimensions(left, y, WIDGET_WIDTH, 20).build());
        y += ROW_HEIGHT;

        this.normalColorBox = colorField(left, y, config.normalHitColor, value -> config.normalHitColor = value);
        y += ROW_HEIGHT;

        this.addDrawableChild(ButtonWidget.builder(toggleLabel("Critical hit color", config.criticalHitEnabled), b -> {
            config.criticalHitEnabled = !config.criticalHitEnabled;
            b.setMessage(toggleLabel("Critical hit color", config.criticalHitEnabled));
        }).dimensions(left, y, WIDGET_WIDTH, 20).build());
        y += ROW_HEIGHT;

        this.criticalColorBox = colorField(left, y, config.criticalHitColor, value -> config.criticalHitColor = value);
        y += ROW_HEIGHT;

        this.addDrawableChild(new ScaleSlider(left, y, WIDGET_WIDTH, 20));
        y += ROW_HEIGHT;

        this.addDrawableChild(ButtonWidget.builder(toggleLabel("Crosshair with F3 open", config.showWithDebugHud), b -> {
            config.showWithDebugHud = !config.showWithDebugHud;
            b.setMessage(toggleLabel("Crosshair with F3 open", config.showWithDebugHud));
        }).dimensions(left, y, WIDGET_WIDTH, 20).build());
        y += ROW_HEIGHT + 6;

        int half = WIDGET_WIDTH / 2 - 2;
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Reset defaults"), b -> {
            config.resetToDefaults();
            this.clearAndInit();
        }).dimensions(left, y, half, 20).build());
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Done"), b -> this.close())
                .dimensions(left + WIDGET_WIDTH - half, y, half, 20).build());
    }

    private TextFieldWidget colorField(int left, int y, int initial, java.util.function.IntConsumer sink) {
        int boxLeft = left + LABEL_WIDTH;
        TextFieldWidget box = new TextFieldWidget(this.textRenderer, boxLeft, y, WIDGET_WIDTH - LABEL_WIDTH, 20,
                Text.literal("Color"));
        box.setMaxLength(9);
        box.setText(AdvancedCrosshairConfig.toHex(initial));
        box.setChangedListener(text -> {
            Integer parsed = AdvancedCrosshairConfig.parseHex(text);
            // Keep the last good value rather than writing garbage mid-typing,
            // and tint the field so an unfinished code is obviously not applied.
            box.setEditableColor(parsed == null ? INVALID_COLOR : VALID_COLOR);
            if (parsed != null) {
                sink.accept(parsed);
            }
        });
        this.addDrawableChild(box);
        return box;
    }

    private static Text toggleLabel(String name, boolean on) {
        return Text.literal(name + ": " + (on ? "ON" : "OFF"));
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 14, 0xFFFFFFFF);

        int left = this.width / 2 - WIDGET_WIDTH / 2;
        if (this.normalColorBox != null) {
            label(context, "Normal hex", left, this.normalColorBox.getY());
        }
        if (this.criticalColorBox != null) {
            label(context, "Critical hex", left, this.criticalColorBox.getY());
        }
    }

    private void label(DrawContext context, String text, int x, int rowY) {
        context.drawTextWithShadow(this.textRenderer, Text.literal(text), x, rowY + 6, 0xFFA0A0A0);
    }

    @Override
    public void close() {
        config.save();
        this.client.setScreen(this.parent);
    }

    /** Maps the slider position onto [MIN_SCALE, MAX_SCALE] in 5% steps. */
    private final class ScaleSlider extends SliderWidget {

        private ScaleSlider(int x, int y, int width, int height) {
            super(x, y, width, height, Text.empty(),
                    (config.crosshairScale - AdvancedCrosshairConfig.MIN_SCALE)
                            / (AdvancedCrosshairConfig.MAX_SCALE - AdvancedCrosshairConfig.MIN_SCALE));
            this.updateMessage();
        }

        private float scale() {
            float raw = AdvancedCrosshairConfig.MIN_SCALE
                    + (float) this.value * (AdvancedCrosshairConfig.MAX_SCALE - AdvancedCrosshairConfig.MIN_SCALE);
            return AdvancedCrosshairConfig.clampScale(Math.round(raw * 20.0F) / 20.0F);
        }

        @Override
        protected void updateMessage() {
            this.setMessage(Text.literal("Crosshair scale: " + Math.round(scale() * 100.0F) + "%"));
        }

        @Override
        protected void applyValue() {
            config.crosshairScale = scale();
        }
    }
}
