package dev.maxcraft.registry;

import dev.maxcraft.Maxcraft;

import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Items added by this mod.
 */
public final class MaxcraftItems {

    public static final DeferredRegister.Items REGISTRAR = DeferredRegister.createItems(Maxcraft.MOD_ID);

    /** 大型包裹构件 / Large Package Component - the part a Large Packager is built from. */
    public static final DeferredHolder<Item, Item> LARGE_PACKAGE_COMPONENT =
        REGISTRAR.registerSimpleItem("large_package_component", new Item.Properties());

    /**
     * The work-in-progress item the sequenced assembly turns into the component. Kept out of the creative tabs; it
     * only exists because Create's sequenced assembly needs a transitional item.
     */
    public static final DeferredHolder<Item, Item> LARGE_PACKAGE_COMPONENT_INCOMPLETE =
        REGISTRAR.registerSimpleItem("large_package_component_incomplete", new Item.Properties());

    private MaxcraftItems() {
    }

    public static void register(IEventBus modEventBus) {
        REGISTRAR.register(modEventBus);
    }
}
