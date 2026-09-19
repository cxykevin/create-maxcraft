package dev.maxcraft.mixin;

import java.util.ArrayList;
import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlock;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlockEntity;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlockItem;

import dev.maxcraft.content.logistics.ExtendedGaugeItem;
import dev.maxcraft.content.logistics.GaugePanelSizes;
import dev.maxcraft.content.logistics.GridSizeHolder;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

/**
 * Drops a broken gauge as the extended gauge it was.
 *
 * <p>Create's loot table drops a bare {@code create:factory_gauge}, which loses the sizes its panels were working
 * with. A gauge with a large grid therefore drops a marked gauge that carries those sizes and nothing else - no
 * network, no other block entity data - so it is as bare as one fresh off the crafting table and the player binds it
 * to a network themselves. Ordinary gauges are left to Create's own table.
 *
 * <p>This hooks {@code BlockStateBase#getDrops}, which is where every drop path - breaking by hand, an explosion, a
 * machine - ends up, rather than the static helpers on {@code Block}, which are only some of the ways in.
 */
@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class GaugeDropMixin {

    @Inject(
        method = "getDrops(Lnet/minecraft/world/level/storage/loot/LootParams$Builder;)Ljava/util/List;",
        at = @At("RETURN"),
        cancellable = true
    )
    private void maxcraft$dropExtendedGauge(LootParams.Builder params,
                                            CallbackInfoReturnable<List<ItemStack>> cir) {
        BlockState state = (BlockState) (Object) this;
        if (!(state.getBlock() instanceof FactoryPanelBlock))
            return;

        BlockEntity blockEntity = params.getOptionalParameter(LootContextParams.BLOCK_ENTITY);
        if (!(blockEntity instanceof FactoryPanelBlockEntity gauge))
            return;

        int size = GaugePanelSizes.largestSize(gauge);
        if (size <= GridSizeHolder.MIN_GRID_SIZE)
            return;

        List<ItemStack> drops = cir.getReturnValue();
        List<ItemStack> replaced = null;
        for (int i = 0; i < drops.size(); i++) {
            if (!(drops.get(i)
                .getItem() instanceof FactoryPanelBlockItem))
                continue;
            if (replaced == null)
                replaced = new ArrayList<>(drops);
            replaced.set(i, ExtendedGaugeItem.marked(size));
        }

        if (replaced != null)
            cir.setReturnValue(replaced);
    }
}
