package dev.maxcraft.content.logistics;

import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;

import dev.maxcraft.registry.MaxcraftBlockEntityTypes;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A Packager that fills a package up to {@link dev.maxcraft.MaxcraftConfig#maxPackageStacks()} stacks instead of
 * {@code PackageItem.SLOTS}.
 *
 * <p>The packing behaviour lives in {@code PackagerBlockEntityMixin}, which reads the block to decide how many slots
 * to fill.
 *
 * <p>Unpacking also goes through this machine, and {@code CrafterUnpackingHandlerMixin} reads the same block to let
 * its deliveries fill a whole Mechanical Crafter array.
 */
public class LargePackagerBlockEntity extends PackagerBlockEntity {

    public LargePackagerBlockEntity(BlockPos pos, BlockState state) {
        super(MaxcraftBlockEntityTypes.LARGE_PACKAGER.get(), pos, state);
    }
}
