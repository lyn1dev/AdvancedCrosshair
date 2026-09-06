package advancedcrosshair.config;

import java.awt.Color;

import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.OptionGroup;
import dev.isxander.yacl3.api.YetAnotherConfigLib;
import dev.isxander.yacl3.api.controller.BooleanControllerBuilder;
import dev.isxander.yacl3.api.controller.ColorControllerBuilder;
import dev.isxander.yacl3.api.controller.FloatSliderControllerBuilder;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/**
 * Settings screen, built with YetAnotherConfigLib and reached through Mod Menu.
 *
 * <p>YACL supplies the graphical colour picker — hue wheel, saturation and
 * lightness gradient, and an alpha slider — that the two colour options open,
 * so colours can be dialled in visually instead of typed as hex.
 */
public final class ConfigScreen {

    private ConfigScreen() {
    }

    public static Screen create(Screen parent) {
        AdvancedCrosshairConfig config = AdvancedCrosshairConfig.get();

        return YetAnotherConfigLib.createBuilder()
                .title(Text.literal("Advanced Crosshair"))
                .category(ConfigCategory.createBuilder()
                        .name(Text.literal("Crosshair"))

                        .group(OptionGroup.createBuilder()
                                .name(Text.literal("General"))
                                .option(Option.<Boolean>createBuilder()
                                        .name(Text.literal("Enable Advanced Crosshair"))
                                        .description(OptionDescription.of(Text.literal(
                                                "Master switch. When off the HUD is left exactly as vanilla draws it.")))
                                        .binding(true,
                                                () -> config.modEnabled,
                                                value -> config.modEnabled = value)
                                        .controller(opt -> BooleanControllerBuilder.create(opt)
                                                .coloured(true)
                                                .onOffFormatter())
                                        .build())
                                .build())

                        .group(OptionGroup.createBuilder()
                                .name(Text.literal("Normal hit"))
                                .description(OptionDescription.of(Text.literal(
                                        "Shown whenever you are aimed at a living entity you could hit.")))
                                .option(Option.<Boolean>createBuilder()
                                        .name(Text.literal("Colour normal hits"))
                                        .binding(true,
                                                () -> config.normalHitEnabled,
                                                value -> config.normalHitEnabled = value)
                                        .controller(opt -> BooleanControllerBuilder.create(opt)
                                                .coloured(true)
                                                .onOffFormatter())
                                        .build())
                                .option(Option.<Color>createBuilder()
                                        .name(Text.literal("Normal hit colour"))
                                        .description(OptionDescription.of(Text.literal(
                                                "Opens a colour picker. Alpha is respected, so lower it for a subtler tint.")))
                                        .binding(new Color(AdvancedCrosshairConfig.DEFAULT_NORMAL_HIT_COLOR, true),
                                                () -> new Color(config.normalHitColor, true),
                                                value -> config.normalHitColor = value.getRGB())
                                        .controller(opt -> ColorControllerBuilder.create(opt).allowAlpha(true))
                                        .build())
                                .build())

                        .group(OptionGroup.createBuilder()
                                .name(Text.literal("Critical hit"))
                                .description(OptionDescription.of(Text.literal(
                                        "Shown when the hit would land as a critical. Turning this off falls back to "
                                                + "the normal hit colour, since a crit-capable moment is still a hit.")))
                                .option(Option.<Boolean>createBuilder()
                                        .name(Text.literal("Colour critical hits"))
                                        .binding(true,
                                                () -> config.criticalHitEnabled,
                                                value -> config.criticalHitEnabled = value)
                                        .controller(opt -> BooleanControllerBuilder.create(opt)
                                                .coloured(true)
                                                .onOffFormatter())
                                        .build())
                                .option(Option.<Color>createBuilder()
                                        .name(Text.literal("Critical hit colour"))
                                        .description(OptionDescription.of(Text.literal(
                                                "Opens a colour picker. Alpha is respected, so lower it for a subtler tint.")))
                                        .binding(new Color(AdvancedCrosshairConfig.DEFAULT_CRITICAL_HIT_COLOR, true),
                                                () -> new Color(config.criticalHitColor, true),
                                                value -> config.criticalHitColor = value.getRGB())
                                        .controller(opt -> ColorControllerBuilder.create(opt).allowAlpha(true))
                                        .build())
                                .build())

                        .group(OptionGroup.createBuilder()
                                .name(Text.literal("Appearance"))
                                .option(Option.<Float>createBuilder()
                                        .name(Text.literal("Crosshair scale"))
                                        .description(OptionDescription.of(Text.literal(
                                                "Size of the crosshair relative to vanilla. The attack indicator moves "
                                                        + "down to stay clear of a scaled-up crosshair.")))
                                        .binding(AdvancedCrosshairConfig.DEFAULT_SCALE,
                                                () -> config.crosshairScale,
                                                value -> config.crosshairScale = value)
                                        .controller(opt -> FloatSliderControllerBuilder.create(opt)
                                                .range(AdvancedCrosshairConfig.MIN_SCALE, AdvancedCrosshairConfig.MAX_SCALE)
                                                .step(0.05F)
                                                .formatValue(value -> Text.literal(Math.round(value * 100.0F) + "%")))
                                        .build())
                                .option(Option.<Boolean>createBuilder()
                                        .name(Text.literal("Crosshair with F3 open"))
                                        .description(OptionDescription.of(Text.literal(
                                                "When on, the normal crosshair is drawn instead of the debug screen's "
                                                        + "three-dimensional one.")))
                                        .binding(false,
                                                () -> config.showWithDebugHud,
                                                value -> config.showWithDebugHud = value)
                                        .controller(opt -> BooleanControllerBuilder.create(opt)
                                                .coloured(true)
                                                .onOffFormatter())
                                        .build())
                                .build())
                        .build())
                .save(config::save)
                .build()
                .generateScreen(parent);
    }
}
