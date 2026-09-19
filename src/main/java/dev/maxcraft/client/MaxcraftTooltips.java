package dev.maxcraft.client;

import com.simibubi.create.foundation.item.ItemDescription;
import com.simibubi.create.foundation.item.TooltipModifier;

import dev.maxcraft.Maxcraft;
import dev.maxcraft.registry.MaxcraftBlocks;
import dev.maxcraft.registry.MaxcraftItems;

import net.createmod.catnip.lang.FontHelper.Palette;
import net.minecraft.world.item.Item;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * The item descriptions of this mod, written the way Create writes its own.
 *
 * <p>Nothing is drawn here. Create's own description code is handed the items, and it reads the text from the
 * language files - so the summary, the conditions and what happens under them look and behave exactly like the
 * description of a Wrench or a Potato Cannon: one dark grey line asking for shift while it is not held, and the
 * text, with the important words picked out, as soon as it is.
 *
 * <p>The machines this mod is based on explain the upgrade they accept in the same way, which is why their text
 * lives under Create's own keys in this mod's language files.
 */
@EventBusSubscriber(modid = Maxcraft.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class MaxcraftTooltips {

    private MaxcraftTooltips() {
    }

    @SubscribeEvent
    public static void registerItemDescriptions(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            describe(MaxcraftItems.LARGE_PACKAGE_COMPONENT.get());
            describe(MaxcraftBlocks.LARGE_PACKAGER_ITEM.get());
            describe(MaxcraftBlocks.LARGE_REPACKAGER_ITEM.get());
            describe(MaxcraftBlocks.EXTENDED_STOCK_TICKER_ITEM.get());
            Maxcraft.LOGGER.info("Maxcraft: the items of this mod describe themselves in Create's style");
        });
    }

    /**
     * Gives an item of this mod a description in Create's style.
     *
     * <p>An item can only have one description, and Create already gave one to every item it registers itself - the
     * Factory Gauge among them - so this is only ever called for items of this mod's own.
     */
    private static void describe(Item item) {
        TooltipModifier.REGISTRY.register(item, new ItemDescription.Modifier(item, Palette.GRAY_AND_WHITE));
    }
}
