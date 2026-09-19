package dev.maxcraft.content.logistics;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;
import com.simibubi.create.content.logistics.packager.repackager.RepackagerBlock;

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
 * 大型理包机 / Large Re-Packager.
 *
 * <p>Upgraded from a Re-Packager in place: right click one with a Large Package Component, or craft the two together.
 * Converting carries the re-packager's block entity data across.
 */
public class LargeRepackagerBlock extends RepackagerBlock {

    public LargeRepackagerBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntityType<? extends PackagerBlockEntity> getBlockEntityType() {
        return MaxcraftBlockEntityTypes.LARGE_REPACKAGER.get();
    }

    /** True if the held stack can upgrade a plain re-packager. */
    public static boolean isUpgradeItem(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() == MaxcraftItems.LARGE_PACKAGE_COMPONENT.get();
    }

    /**
     * Replaces a re-packager with a large one, carrying its block entity data over.
     *
     * @return true if the conversion happened
     */
    public static boolean convert(Level level, BlockPos pos) {
        if (level.isClientSide())
            return false;

        BlockState state = level.getBlockState(pos);
        // Only Create's own re-packager converts, and only once.
        if (!AllBlocks.REPACKAGER.has(state))
            return false;
        if (!(level.getBlockEntity(pos) instanceof PackagerBlockEntity))
            return false;

        BlockState upgraded = MaxcraftBlocks.LARGE_REPACKAGER.get()
            .defaultBlockState()
            .setValue(FACING, state.getValue(FACING))
            .setValue(POWERED, state.getValue(POWERED));

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
