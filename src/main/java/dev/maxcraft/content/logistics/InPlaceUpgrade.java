package dev.maxcraft.content.logistics;

import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Swaps a block for an upgraded version of itself, carrying its block entity across.
 *
 * <p>Create's block teardown drops whatever a machine was holding - a packager's box, a stock ticker's shop
 * payments - so the old block entity is detached <em>before</em> the block is replaced. Otherwise the restored data
 * would be a second copy of items that were already spilled on the floor.
 */
public final class InPlaceUpgrade {

    private InPlaceUpgrade() {
    }

    /**
     * Replaces the block at {@code pos} with {@code upgraded}, keeping its block entity's contents, network and
     * configuration.
     *
     * @return true if the swap happened
     */
    public static boolean apply(Level level, BlockPos pos, BlockState upgraded) {
        if (level.isClientSide())
            return false;

        BlockEntity old = level.getBlockEntity(pos);
        if (old == null)
            return false;

        CompoundTag saved = old.saveWithoutMetadata(level.registryAccess());
        saved.remove("id");
        saved.remove("x");
        saved.remove("y");
        saved.remove("z");

        level.removeBlockEntity(pos);
        level.setBlockAndUpdate(pos, upgraded);

        BlockEntity fresh = level.getBlockEntity(pos);
        if (fresh == null)
            return false;

        fresh.loadWithComponents(saved, level.registryAccess());
        fresh.setChanged();
        if (fresh instanceof SmartBlockEntity smart)
            smart.notifyUpdate();
        return true;
    }
}
