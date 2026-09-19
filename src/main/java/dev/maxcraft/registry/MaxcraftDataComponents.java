package dev.maxcraft.registry;

import java.util.List;

import dev.maxcraft.Maxcraft;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Data components added by this mod.
 */
public final class MaxcraftDataComponents {

    public static final DeferredRegister<DataComponentType<?>> REGISTRAR =
        DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, Maxcraft.MOD_ID);

    /**
     * Complete contents of a package, used when they do not fit into Create's
     * {@code create:package_contents} component.
     *
     * <p>Create stores package contents as a vanilla {@link net.minecraft.world.item.component.ItemContainerContents},
     * which is hard-capped at 256 slots both by its codec and by its constructor. A package that has to carry more
     * than that - which the Repackager produces when it merges oversized packages - stores the full list here and
     * keeps a truncated copy in the vanilla component so that Create's own tooltip and third-party readers such as
     * Create: Cyber Goggles still find something to display.
     *
     * <p>The list is deliberately unbounded: a package may hold any number of stacks.
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<List<ItemStack>>> PACKAGE_BULK_CONTENTS =
        REGISTRAR.register("package_bulk_contents", () -> DataComponentType.<List<ItemStack>>builder()
            .persistent(ItemStack.OPTIONAL_CODEC.listOf())
            .networkSynchronized(ItemStack.OPTIONAL_STREAM_CODEC.apply(ByteBufCodecs.list()))
            .build());

    /**
     * The crafting grid sizes of an extended gauge item, by panel slot.
     *
     * <p>It is deliberately not tucked into the item's {@code minecraft:block_entity_data}: Create rewrites that tag
     * whenever a gauge is tuned, copied or placed, and a component of its own is the one place a size survives all of
     * that. See {@code ExtendedGaugeItem} and {@code FactoryPanelBlockItemMixin}.
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<CompoundTag>> GAUGE_GRID_SIZES =
        REGISTRAR.register("gauge_grid_sizes", () -> DataComponentType.<CompoundTag>builder()
            .persistent(CompoundTag.CODEC)
            .networkSynchronized(ByteBufCodecs.COMPOUND_TAG)
            .build());

    private MaxcraftDataComponents() {
    }

    public static void register(IEventBus modEventBus) {
        REGISTRAR.register(modEventBus);
    }
}
