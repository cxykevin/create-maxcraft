package dev.maxcraft;

import com.mojang.logging.LogUtils;
import dev.maxcraft.content.logistics.GridSizeHolder;
import dev.maxcraft.mixin.ShapedRecipePatternAccess;
import dev.maxcraft.registry.MaxcraftBlockEntityTypes;
import dev.maxcraft.registry.MaxcraftBlocks;
import dev.maxcraft.registry.MaxcraftDataComponents;
import dev.maxcraft.registry.MaxcraftItems;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.slf4j.Logger;

/**
 * Entry point of the Maxcraft mod.
 *
 * <p>Dependencies of this mod:
 * <ul>
 *   <li><b>Create</b> (机械动力) - hard dependency. Resolved from {@code maven.createmod.net}
 *       as a Gradle dependency, so its API is available at compile time and it is loaded in
 *       dev runs.</li>
 *   <li><b>Create: Cyber Goggles</b> (机械动力：赛博护目镜) - <i>soft</i> dependency, declared
 *       as {@code optional} in {@code neoforge.mods.toml}. Nothing in this mod references it,
 *       guard any future use with {@code ModList.get().isLoaded("create_cyber_goggles")}.</li>
 * </ul>
 *
 * <p>What this mod adds:
 * <ul>
 *   <li>大型包裹构件 / Large Package Component, made by sequenced assembly.</li>
 *   <li>大型打包机 / Large Packager, which fills 27 stacks into a single - ordinary - package.</li>
 * </ul>
 *
 * <p>What this mod patches, see {@code dev.maxcraft.mixin}:
 * <ul>
 *   <li>Packages hold any number of items ({@code PackageItemMixin}).</li>
 *   <li>The Repackager merges an order into one package of unbounded size without dropping anything
 *       ({@code PackageRepackageHelperMixin}, {@code RepackagerBlockEntityMixin}).</li>
 *   <li>The Large Packager gets its 27 slot package size ({@code PackagerBlockEntityMixin}).</li>
 * </ul>
 *
 * <p>JEI, Just Enough Characters, Sodium and Lithium are installed in {@code run/mods/} for
 * the dev runs only - this mod neither compiles against nor declares them.
 */
@Mod(Maxcraft.MOD_ID)
public class Maxcraft {

    public static final String MOD_ID = "maxcraft";

    public static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Compile-time proof that the Create API is on the classpath. Using a class literal only
     * loads the class, it never triggers Create's static initialisers.
     */
    @SuppressWarnings("unused")
    private static final Class<?> CREATE_API_MARKER = com.simibubi.create.Create.class;

    public Maxcraft(IEventBus modEventBus, ModContainer modContainer) {
        // Recipes are parsed before the first world loads, and a pattern is checked against this ceiling as it is
        // read, so it has to be raised now - NeoForge only grows it after a bigger recipe has already been built.
        ShapedRecipePatternAccess.maxcraft$setMaxWidth(GridSizeHolder.MAX_GRID_SIZE);
        ShapedRecipePatternAccess.maxcraft$setMaxHeight(GridSizeHolder.MAX_GRID_SIZE);

        modContainer.registerConfig(ModConfig.Type.SERVER, MaxcraftConfig.SPEC);
        MaxcraftDataComponents.register(modEventBus);
        MaxcraftItems.register(modEventBus);
        MaxcraftBlocks.register(modEventBus);
        MaxcraftBlockEntityTypes.register(modEventBus);
        LOGGER.info("Maxcraft loaded - Create API present: {}", CREATE_API_MARKER.getName());
    }
}
