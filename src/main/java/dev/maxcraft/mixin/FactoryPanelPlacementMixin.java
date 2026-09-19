package dev.maxcraft.mixin;

import java.util.UUID;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlock;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlock.PanelSlot;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlockEntity;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlockItem;

import dev.maxcraft.Maxcraft;
import dev.maxcraft.content.logistics.ExtendedGaugeItem;
import dev.maxcraft.content.logistics.GaugePanelSizes;
import dev.maxcraft.content.logistics.GridSizeHolder;
import dev.maxcraft.registry.MaxcraftDataComponents;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Gives the panel a gauge is placed with the grid size its item carries.
 *
 * <p>A gauge item is used in two ways and both end in the same place: put down on the ground it becomes a gauge with
 * one panel, and used on a gauge that is already there it adds one more panel to that gauge. Create adds that panel
 * through {@code FactoryPanelBlockEntity#addPanel} either way, so this notes the sizes off the item at the two entry
 * points and hands them to the panel Create adds - the one panel involved, never the gauge's other three.
 *
 * <p>The sizes ride in a component of this mod's own, so nothing Create does to a gauge item's block entity data on
 * the way can lose them. The item itself stays bare, and has to be bound to a network before any of this runs.
 */
@Mixin(FactoryPanelBlock.class)
public abstract class FactoryPanelPlacementMixin {

    /**
     * The sizes of the item being placed, while it is being placed. Static because one of the two places that adds
     * the panel is compiled as a static lambda body; harmless because a block is a singleton and this only runs on
     * the game thread.
     */
    @Unique
    private static CompoundTag maxcraft$pendingSizes;

    /** A gauge item put down on the ground. */
    @Inject(method = "setPlacedBy", at = @At("HEAD"))
    private void maxcraft$notePlacedGauge(Level level, BlockPos pos, BlockState state, LivingEntity placer,
                                          ItemStack stack, CallbackInfo ci) {
        maxcraft$note(stack);
    }

    /**
     * A gauge item used on a gauge that is already there when the item's own placement runs instead.
     *
     * <p>This is the third way Create adds a panel, and the least obvious one: a gauge item held against a gauge
     * that cannot take the click as a block interaction falls through to item placement, and Create's
     * {@code getStateForPlacement} then adds the panel as a side effect of deciding what to place - the block itself
     * is not put down at all. A player who has one panel on a gauge already gets here every time, because that
     * panel's value box takes the click.
     */
    @Inject(method = "getStateForPlacement", at = @At("HEAD"))
    private void maxcraft$notePlacementItem(BlockPlaceContext context, CallbackInfoReturnable<BlockState> cir) {
        maxcraft$note(context.getItemInHand());
    }

    /** A gauge item used on a gauge that is already there, which adds a panel to it. */
    @Inject(method = "useItemOn", at = @At("HEAD"))
    private void maxcraft$noteUsedGauge(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                        InteractionHand hand, BlockHitResult hitResult,
                                        CallbackInfoReturnable<ItemInteractionResult> cir) {
        maxcraft$note(stack);
    }

    /** Reads the sizes off the item being used, and says what it found. */
    @Unique
    private static void maxcraft$note(ItemStack stack) {
        maxcraft$pendingSizes = stack.get(MaxcraftDataComponents.GAUGE_GRID_SIZES.get());
        Maxcraft.LOGGER.info("Maxcraft: gauge item used: {} x{}, marked={}, sizes={}", stack.getItem(),
            stack.getCount(), stack.get(DataComponents.CUSTOM_MODEL_DATA), maxcraft$pendingSizes);

        if (maxcraft$pendingSizes != null && !maxcraft$pendingSizes.isEmpty())
            return;

        // A gauge that says it is extended but carries no sizes was made before the sizes moved into a component of
        // their own, and there is nothing left in it to give a panel. Worth saying out loud rather than quietly
        // placing an ordinary panel.
        var marker = stack.get(DataComponents.CUSTOM_MODEL_DATA);
        if (marker != null && marker.value() == ExtendedGaugeItem.MODEL_DATA) {
            Maxcraft.LOGGER.warn("Maxcraft: this extended gauge item carries no grid sizes - it was made by an older "
                + "build and has to be replaced with a freshly crafted or creative one");
        } else if (stack.getItem() instanceof FactoryPanelBlockItem) {
            // An ordinary gauge adds an ordinary panel, which is exactly what Create does with it - but it is easy
            // to pick one up by mistake when the extended gauge is what was wanted, so say which one this was.
            Maxcraft.LOGGER.info("Maxcraft: an ordinary factory gauge is being placed - its panel uses Create's 3x3. "
                + "Use an extended gauge (brass, \"扩展工厂仪表\") or right click the panel with a large package "
                + "component for a larger grid");
        }
    }

    @Redirect(
        method = "lambda$setPlacedBy$1",
        at = @At(
            value = "INVOKE",
            target = "Lcom/simibubi/create/content/logistics/factoryBoard/FactoryPanelBlockEntity;addPanel("
                + "Lcom/simibubi/create/content/logistics/factoryBoard/FactoryPanelBlock$PanelSlot;"
                + "Ljava/util/UUID;)Z"
        )
    )
    private static boolean maxcraft$addedPanelOnPlace(FactoryPanelBlockEntity gauge, PanelSlot slot, UUID network) {
        return maxcraft$addedPanel(gauge, slot, network);
    }

    @Redirect(
        method = "getStateForPlacement",
        at = @At(
            value = "INVOKE",
            target = "Lcom/simibubi/create/content/logistics/factoryBoard/FactoryPanelBlockEntity;addPanel("
                + "Lcom/simibubi/create/content/logistics/factoryBoard/FactoryPanelBlock$PanelSlot;"
                + "Ljava/util/UUID;)Z"
        )
    )
    private boolean maxcraft$addedPanelOnPlacement(FactoryPanelBlockEntity gauge, PanelSlot slot, UUID network) {
        return maxcraft$addedPanel(gauge, slot, network);
    }

    @Redirect(
        method = "lambda$useItemOn$2",
        at = @At(
            value = "INVOKE",
            target = "Lcom/simibubi/create/content/logistics/factoryBoard/FactoryPanelBlockEntity;addPanel("
                + "Lcom/simibubi/create/content/logistics/factoryBoard/FactoryPanelBlock$PanelSlot;"
                + "Ljava/util/UUID;)Z"
        )
    )
    private boolean maxcraft$addedPanelOnUse(FactoryPanelBlockEntity gauge, PanelSlot slot, UUID network) {
        return maxcraft$addedPanel(gauge, slot, network);
    }

    /** Lets Create add the panel, then gives that one panel the size the item was carrying. */
    @Unique
    private static boolean maxcraft$addedPanel(FactoryPanelBlockEntity gauge, PanelSlot slot, UUID network) {
        boolean added = gauge.addPanel(slot, network);

        // A slot that already has a panel is not given a second one. Nothing is changed and, because Create only
        // uses the item up when a panel really was added, nothing is used up either: right clicking a panel that is
        // already there with an extended gauge simply does nothing, which is what a player aiming at it expects.
        if (!added) {
            Maxcraft.LOGGER.info("Maxcraft: the gauge at {} already has a panel in {} - nothing was changed", 
                gauge.getBlockPos(), slot);
            return false;
        }

        CompoundTag sizes = maxcraft$pendingSizes;
        if (sizes == null || sizes.isEmpty()) {
            Maxcraft.LOGGER.info("Maxcraft: panel {} on the gauge at {} was added with no sizes to give it", slot,
                gauge.getBlockPos());
            return true;
        }
        if (!(gauge.panels.get(slot) instanceof GridSizeHolder holder))
            return true;

        int size = GaugePanelSizes.sizeOf(sizes);
        holder.maxcraft$setGridSize(size);
        GaugePanelSizes.appearanceChanged(gauge);
        Maxcraft.LOGGER.info("Maxcraft: panel {} on the gauge at {} is now {}x{}", slot, gauge.getBlockPos(), size, size);
        return true;
    }
}
