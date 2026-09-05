package advancedcrosshair;

import advancedcrosshair.config.AdvancedCrosshairConfig;
import net.fabricmc.api.ClientModInitializer;

public class AdvancedCrosshairClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        // Read the config once up front so the first frame does not pay for it.
        AdvancedCrosshairConfig.get();
    }
}
