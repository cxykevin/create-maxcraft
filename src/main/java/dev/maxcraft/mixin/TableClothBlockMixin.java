package dev.maxcraft.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.simibubi.create.AllDataComponents;
import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.redstoneRequester.AutoRequestData;
import com.simibubi.create.content.logistics.tableCloth.TableClothBlock;

import dev.maxcraft.MaxcraftAdvancements;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Awards 《交易所》 when a shop is set up with ten or more kinds of goods.
 *
 * <p>A shop is a Table Cloth placed with a configured request on it - the list the Stock Keeper screen builds, which
 * Create keeps at nine kinds. Ten kinds can therefore only have come from an Extended Stock Ticker, which is exactly
 * what the advancement is about.
 */
@Mixin(TableClothBlock.class)
public abstract class TableClothBlockMixin {

    /** The number of kinds Create's own request screen can hold; one past it means an extended ticker built the list. */
    private static final int CREATE_KIND_LIMIT = 9;

    @Inject(method = "setPlacedBy", at = @At("HEAD"))
    private void maxcraft$shopConfigured(Level level, BlockPos pos, BlockState state, LivingEntity placer,
                                         ItemStack stack, CallbackInfo ci) {
        if (level.isClientSide() || !(placer instanceof Player player))
            return;

        AutoRequestData request = stack.get(AllDataComponents.AUTO_REQUEST_DATA);
        if (request == null || request.encodedRequest() == null)
            return;

        List<BigItemStack> goods = request.encodedRequest()
            .stacks();
        long kinds = goods.stream()
            .filter(good -> !good.stack.isEmpty())
            .map(good -> good.stack.getItem())
            .distinct()
            .count();
        if (kinds > CREATE_KIND_LIMIT)
            MaxcraftAdvancements.award(player, MaxcraftAdvancements.EXCHANGE);
    }
}
