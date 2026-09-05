package advancedcrosshair;

import advancedcrosshair.config.ConfigScreen;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/**
 * Hooks the settings screen up to Mod Menu.
 *
 * <p>Mod Menu is a compile-only dependency: this class is referenced solely from
 * the {@code modmenu} entrypoint, which nothing loads unless Mod Menu is
 * installed, so the mod runs fine without it.
 */
public class ModMenuIntegration implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return ConfigScreen::new;
    }
}
