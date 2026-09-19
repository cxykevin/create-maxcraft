package dev.maxcraft.content.logistics;

import com.simibubi.create.AllBlocks;

import dev.maxcraft.Maxcraft;

import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Marks a gauge that was just crafted out of a large package component.
 *
 * <p>The recipe itself can only give the item its grid sizes - a recipe has no way to write a translatable name, as
 * that component is stored as a flat string. The name, and with it the model, are added here instead. Any other
 * factory gauge is left alone, so Create's own recipes are untouched.
 */
@EventBusSubscriber(modid = Maxcraft.MOD_ID)
public final class GaugeCraftHandler {

    private GaugeCraftHandler() {
    }

    @SubscribeEvent
    public static void onCrafted(PlayerEvent.ItemCraftedEvent event) {
        ItemStack crafted = event.getCrafting();
        if (crafted.isEmpty() || !crafted.is(AllBlocks.FACTORY_GAUGE.get()
            .asItem()))
            return;
        ExtendedGaugeItem.mark(crafted);
    }
}
