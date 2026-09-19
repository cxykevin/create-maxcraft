package dev.maxcraft.mixin;

import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlock;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlock.PanelSlot;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlockEntity;
import com.tterrag.registrate.util.entry.BlockEntry;

import dev.maxcraft.content.logistics.ExtendedGaugeItem;
import dev.maxcraft.content.logistics.GridSizeHolder;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Hands back an extended gauge when an extended panel is taken off a gauge.
 *
 * <p>Taking a panel off is not the same as breaking the block, and Create does it in two places, neither of which
 * involves the loot table: a wrench takes one panel off the block entity, and breaking a gauge that still has two or
 * more panels takes one off instead of removing the block. Both of them hand the player a bare
 * {@code create:factory_gauge} directly, which is why an extended panel used to turn back into an ordinary item
 * however the loot was patched.
 *
 * <p>The size of the panel being taken off is noted at the start of the two entry points, where the panel is still
 * there to be asked, and the gauge handed over at the end carries it. It is still bare, so it has to be bound to a
 * network again before it can be placed. Breaking a gauge that has only one panel left does destroy the block, and
 * that drop goes through the loot table, which {@code GaugeDropMixin} takes care of.
 */
@Mixin(FactoryPanelBlock.class)
public abstract class FactoryPanelPickupMixin {

    /**
     * The size of the panel about to come off. Static because the two places that hand the item over are compiled as
     * static lambda bodies, and harmless because a block is a singleton and this only ever runs on the game thread.
     */
    @Unique
    private static int maxcraft$panelSize;

    @Inject(method = "onDestroyedByPlayer", at = @At("HEAD"))
    private void maxcraft$noteBrokenPanel(BlockState state, Level level, BlockPos pos, Player player,
                                          boolean willHarvest, FluidState fluid,
                                          CallbackInfoReturnable<Boolean> cir) {
        double range = player.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE) + 1.0;
        HitResult hitResult = player.pick(range, 1.0F, false);
        maxcraft$note(state, level, pos, hitResult == null ? null : hitResult.getLocation());
    }

    @Inject(method = "onSneakWrenched", at = @At("HEAD"))
    private void maxcraft$noteWrenchedPanel(BlockState state, UseOnContext context,
                                            CallbackInfoReturnable<InteractionResult> cir) {
        maxcraft$note(state, context.getLevel(), context.getClickedPos(), context.getClickLocation());
    }

    /** The gauge Create hands over, carrying the size of the panel that came off it. */
    @Redirect(
        method = { "lambda$onSneakWrenched$0", "lambda$tryDestroySubPanelFirst$3" },
        at = @At(
            value = "INVOKE",
            target = "Lcom/tterrag/registrate/util/entry/BlockEntry;asStack()Lnet/minecraft/world/item/ItemStack;"
        )
    )
    private static ItemStack maxcraft$removedPanelItem(BlockEntry<FactoryPanelBlock> entry) {
        if (maxcraft$panelSize > GridSizeHolder.MIN_GRID_SIZE)
            return ExtendedGaugeItem.marked(maxcraft$panelSize);
        return entry.asStack();
    }

    /** Reads the panel the player is aiming at, the same way Create works out which one it is about to take off. */
    @Unique
    private static void maxcraft$note(BlockState state, LevelAccessor level, BlockPos pos, @Nullable Vec3 location) {
        maxcraft$panelSize = 0;
        if (location == null)
            return;
        if (!(level.getBlockEntity(pos) instanceof FactoryPanelBlockEntity gauge))
            return;

        PanelSlot slot = FactoryPanelBlock.getTargetedSlot(pos, state, location);
        if (slot != null && gauge.panels.get(slot) instanceof GridSizeHolder holder && holder.maxcraft$isExtended())
            maxcraft$panelSize = holder.maxcraft$gridSize();
    }
}
