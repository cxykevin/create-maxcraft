package dev.maxcraft;

import org.slf4j.event.Level;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Server configuration.
 *
 * <p>Written to {@code <world>/serverconfig/maxcraft-server.toml} in single player and to
 * {@code config/maxcraft-server.toml} on a dedicated server, so a server owner decides how large packages may get
 * for everyone playing on it.
 */
@EventBusSubscriber(modid = Maxcraft.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class MaxcraftConfig {

    /** Used until the config has been loaded, and the default written into a fresh config file. */
    public static final int DEFAULT_MAX_PACKAGE_STACKS = 64;

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.IntValue MAX_PACKAGE_STACKS = BUILDER
        .comment("The largest number of stacks a single package may hold.",
            "大型打包机一次打包多少组，理包机合并出来的包裹最大多少组。",
            "A request that needs more is split over several packages rather than truncated - nothing is ever",
            "dropped, and the packages are still ordinary packages.")
        .defineInRange("maxPackageStacks", DEFAULT_MAX_PACKAGE_STACKS, 1, 4096);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private MaxcraftConfig() {
    }

    /** The configured package size. Falls back to the default until the config has been loaded. */
    public static int maxPackageStacks() {
        return SPEC.isLoaded() ? MAX_PACKAGE_STACKS.get() : DEFAULT_MAX_PACKAGE_STACKS;
    }

    @SubscribeEvent
    public static void onLoading(ModConfigEvent.Loading event) {
        refresh(event);
    }

    @SubscribeEvent
    public static void onReloading(ModConfigEvent.Reloading event) {
        refresh(event);
    }

    private static void refresh(ModConfigEvent event) {
        if (event.getConfig()
            .getSpec() != SPEC)
            return;
        Maxcraft.LOGGER.info("Maxcraft: packages may hold up to {} stacks", MAX_PACKAGE_STACKS.get());
    }
}
