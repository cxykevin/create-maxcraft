package dev.maxcraft.content.logistics;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.logistics.packager.PackagerBlock;
import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;

import dev.maxcraft.registry.MaxcraftBlockEntityTypes;
import dev.maxcraft.registry.MaxcraftBlocks;
import dev.maxcraft.registry.MaxcraftItems;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The Large Packager: same machine as Create's Packager, but it fills a package up to
 * {@link dev.maxcraft.MaxcraftConfig#maxPackageStacks()} stacks per cycle instead of nine.
 *
 * <p>The package it produces is an ordinary package - just a large one - so the rest of the logistics chain (frogports,
 * postboxes, unpacking, the Repackager) needs no changes at all.
 *
 * <p>Upgraded from a Packager in place: right click one with a Large Package Component, or craft the two together.
 * Converting carries the packager's block entity data across, so a box it was holding, its queued packages and its
 * sign address all survive.
 */
public class LargePackagerBlock extends PackagerBlock {

    public LargePackagerBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntityType<? extends PackagerBlockEntity> getBlockEntityType() {
        return MaxcraftBlockEntityTypes.LARGE_PACKAGER.get();
    }

    /** True if the held stack can upgrade a plain packager. */
    public static boolean isUpgradeItem(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() == MaxcraftItems.LARGE_PACKAGE_COMPONENT.get();
    }

    /**
     * Replaces a packager with a large one, carrying its block entity data over.
     *
     * @return true if the conversion happened
     */
    public static boolean convert(Level level, BlockPos pos) {
        if (level.isClientSide())
            return false;

        BlockState state = level.getBlockState(pos);
        // Only Create's own packager converts - not the Re-Packager, which shares this block class, and not an
        // already large packager.
        if (!AllBlocks.PACKAGER.has(state))
            return false;
        if (!(level.getBlockEntity(pos) instanceof PackagerBlockEntity))
            return false;

        // Copy the state properties by hand; the two blocks do not share a state definition instance.
        BlockState upgraded = MaxcraftBlocks.LARGE_PACKAGER.get()
            .defaultBlockState()
            .setValue(FACING, state.getValue(FACING))
            .setValue(POWERED, state.getValue(POWERED))
            .setValue(LINKED, state.getValue(LINKED));

        return InPlaceUpgrade.apply(level, pos, upgraded);
    }

    /** The interaction form: converts and takes the component out of the player's hand. */
    public static boolean convert(Level level, BlockPos pos, Player player, ItemStack stack) {
        if (!convert(level, pos))
            return false;
        if (!level.isClientSide() && (player == null || !player.getAbilities().instabuild))
            stack.shrink(1);
        return true;
    }
}
