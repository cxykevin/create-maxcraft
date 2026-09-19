package dev.maxcraft.client;

import com.simibubi.create.infrastructure.ponder.AllCreatePonderTags;
import com.simibubi.create.infrastructure.ponder.scenes.highLogistics.PackagerScenes;
import com.simibubi.create.infrastructure.ponder.scenes.highLogistics.RepackagerScenes;
import com.simibubi.create.infrastructure.ponder.scenes.highLogistics.StockTickerScenes;

import dev.maxcraft.Maxcraft;

import java.util.Map;
import java.util.stream.Collectors;

import net.createmod.ponder.api.registration.PonderPlugin;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.createmod.ponder.api.registration.PonderTagRegistrationHelper;
import net.createmod.ponder.foundation.PonderIndex;
import net.minecraft.resources.ResourceLocation;

/**
 * 思索 / Ponder for the machines this mod adds.
 *
 * <p>Every machine here is a bigger version of one of Create's own, so it does not come with scenes of its own: the
 * machines are pointed at Create's scenes for the machine they are based on, which are the very same schematics and
 * the very same animations a player already knows. Hovering a Large Packager and holding the ponder key plays
 * exactly what hovering a Packager plays, so nothing new has to be learned to use one.
 *
 * <p>The text of those scenes is looked up per item, so this mod ships the same wording under its own ids - see
 * {@code assets/maxcraft/lang/*.json}. Create keeps the originals for its own machines.
 *
 * <p>The Extended Factory Gauge is Create's own Factory Gauge with a larger grid, so it already opens Create's gauge
 * scenes on its own and needs nothing here.
 */
public final class MaxcraftPonderPlugin implements PonderPlugin {

    /** The machine of Create's that each machine of this mod borrows its scenes from. */
    private static final String PACKAGER = "large_packager";
    private static final String REPACKAGER = "large_repackager";
    private static final String STOCK_TICKER = "extended_stock_ticker";

    @Override
    public String getModId() {
        return Maxcraft.MOD_ID;
    }

    @Override
    public void registerScenes(PonderSceneRegistrationHelper<ResourceLocation> helper) {
        // Create's scenes live in assets/create/ponder, so they are asked for by their own namespace.
        helper.forComponents(id(PACKAGER))
            .addStoryBoard(create("high_logistics/packager"), PackagerScenes::packager)
            .addStoryBoard(create("high_logistics/packager_address"), PackagerScenes::packagerAddress);

        helper.forComponents(id(REPACKAGER))
            .addStoryBoard(create("high_logistics/repackager"), RepackagerScenes::repackager);

        helper.forComponents(id(STOCK_TICKER))
            .addStoryBoard(create("high_logistics/stock_ticker"), StockTickerScenes::stockTicker)
            .addStoryBoard(create("high_logistics/stock_ticker_address"), StockTickerScenes::stockTickerAddress);

        // Reported while registering, which is the moment the scenes are known to be there.
        Map<ResourceLocation, Long> bound = PonderIndex.getSceneAccess()
            .getRegisteredEntries()
            .stream()
            .collect(Collectors.groupingBy(Map.Entry::getKey, Collectors.counting()));
        Maxcraft.LOGGER.info("Maxcraft: Create scenes bound to this mod's machines - {} {}, {} {}, {} {}",
            PACKAGER, bound.getOrDefault(id(PACKAGER), 0L), REPACKAGER,
            bound.getOrDefault(id(REPACKAGER), 0L), STOCK_TICKER, bound.getOrDefault(id(STOCK_TICKER), 0L));
    }

    @Override
    public void registerTags(PonderTagRegistrationHelper<ResourceLocation> helper) {
        // The machines belong in the same page of the ponder index as the machines they are based on.
        try {
            helper.addToTag(AllCreatePonderTags.HIGH_LOGISTICS)
                .add(id(PACKAGER))
                .add(id(REPACKAGER))
                .add(id(STOCK_TICKER));
        } catch (RuntimeException e) {
            // Create's tags are registered by Create's plugin; should that not have happened first, the scenes above
            // still work, the machines would only be missing from that one page of the index.
            Maxcraft.LOGGER.warn("Maxcraft: could not file the large machines under Create's high logistics tag", e);
        }
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(Maxcraft.MOD_ID, path);
    }

    private static ResourceLocation create(String path) {
        return ResourceLocation.fromNamespaceAndPath("create", path);
    }
}
