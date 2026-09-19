package dev.maxcraft.mixin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.simibubi.create.AllDataComponents;
import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.box.PackageItem;
import com.simibubi.create.content.logistics.box.PackageItem.PackageOrderData;
import com.simibubi.create.content.logistics.packager.InventorySummary;
import com.simibubi.create.content.logistics.packager.repackager.PackageRepackageHelper;
import com.simibubi.create.content.logistics.stockTicker.PackageOrderWithCrafts;

import dev.maxcraft.MaxcraftConfig;
import dev.maxcraft.logistics.PackageContents;
import dev.maxcraft.logistics.RepackageHelperAccess;

import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * Makes the Repackager merge an order into a single, oversized package instead of slicing it back into nine stack
 * chunks.
 *
 * <p>Both methods below are faithful ports of Create 6.0.10; the only behavioural differences are marked
 * {@code PATCHED}.
 */
@Mixin(PackageRepackageHelper.class)
public abstract class PackageRepackageHelperMixin implements RepackageHelperAccess {

    @Shadow
    protected Map<Integer, List<ItemStack>> collectedPackages;

    @Shadow
    protected abstract List<BigItemStack> repackBasedOnRecipes(InventorySummary summary, PackageOrderWithCrafts order,
                                                               String address, RandomSource r);

    @Shadow
    public abstract int addPackageFragment(ItemStack box);

    /** Set once the Repackager has already handed over its entire inventory. */
    @Unique
    private boolean maxcraft$preCollected;

    /** Set for the Large Re-Packager: wait until the order's contents have fully arrived. */
    @Unique
    private boolean maxcraft$strict;

    /** Set for the Large Re-Packager: one order leaves as one package, never split per recipe. */
    @Unique
    private boolean maxcraft$singlePackage;

    @Override
    public void maxcraft$setStrict(boolean strict) {
        maxcraft$strict = strict;
    }

    @Override
    public void maxcraft$setSinglePackage(boolean singlePackage) {
        maxcraft$singlePackage = singlePackage;
    }

    @Override
    public void maxcraft$preCollect(ItemStack box) {
        boolean previous = maxcraft$preCollected;
        maxcraft$preCollected = false;
        try {
            addPackageFragment(box);
        } finally {
            maxcraft$preCollected = previous;
        }
    }

    @Override
    public void maxcraft$beginPreCollect() {
        maxcraft$preCollected = true;
    }

    @Inject(method = "clear()V", at = @At("HEAD"))
    private void maxcraft$clear(CallbackInfo ci) {
        maxcraft$preCollected = false;
    }

    /**
     * Once the inventory has been collected up front, the Repackager's own scan walks over the same packages again.
     * They are already accounted for, so this only reports completeness instead of adding a second copy of every
     * fragment - which would duplicate the whole order.
     */
    @Inject(
        method = "addPackageFragment(Lnet/minecraft/world/item/ItemStack;)I",
        at = @At("HEAD"),
        cancellable = true
    )
    private void maxcraft$addPackageFragment(ItemStack box, CallbackInfoReturnable<Integer> cir) {
        if (!maxcraft$preCollected)
            return;
        int orderId = PackageItem.getOrderId(box);
        cir.setReturnValue(orderId != -1 && maxcraft$orderComplete(orderId) ? orderId : -1);
    }

    /**
     * PATCHED: every stack that came out of the merged fragments ends up in one package, unless the order is larger
     * than {@link MaxcraftConfig#maxPackageStacks()}, in which case it is split into as few packages as possible.
     * Upstream stopped at {@code PackageItem.SLOTS} and opened a new package for each further nine stacks, which is
     * what made the nine stack ceiling impossible to escape. Nothing is ever truncated either way.
     */
    @Inject(method = "repack(ILnet/minecraft/util/RandomSource;)Ljava/util/List;", at = @At("HEAD"), cancellable = true)
    private void maxcraft$repack(int orderId, RandomSource r, CallbackInfoReturnable<List<BigItemStack>> cir) {
        List<ItemStack> fragments = collectedPackages.get(orderId);
        List<BigItemStack> exportingPackages = new ArrayList<>();
        String address = "";
        PackageOrderWithCrafts orderContext = null;
        InventorySummary summary = new InventorySummary();

        if (fragments != null) {
            for (ItemStack box : fragments) {
                address = PackageItem.getAddress(box);
                if (box.has(AllDataComponents.PACKAGE_ORDER_DATA)) {
                    PackageOrderWithCrafts context = box.get(AllDataComponents.PACKAGE_ORDER_DATA).orderContext();
                    if (context != null && !context.isEmpty())
                        orderContext = context;
                }

                // PATCHED: PackageItem#getContents no longer truncates at nine slots.
                ItemStackHandler contents = PackageItem.getContents(box);
                for (int slot = 0; slot < contents.getSlots(); slot++)
                    summary.add(contents.getStackInSlot(slot));
            }
        }

        List<BigItemStack> orderedStacks = new ArrayList<>();
        if (orderContext != null) {
            // PATCHED: a machine that promises one package per order must not take the per-recipe split, which hands
            // out one package per craft entry and leaves the destination holding a fragment of the ingredients. Its
            // ordering hint is still honoured, so the single package is filled in the order that was asked for.
            if (!maxcraft$singlePackage) {
                List<BigItemStack> packagesSplitByRecipe = repackBasedOnRecipes(summary, orderContext, address, r);
                exportingPackages.addAll(packagesSplitByRecipe);

                if (!packagesSplitByRecipe.isEmpty()) {
                    cir.setReturnValue(maxcraft$finish(exportingPackages, orderContext, address, orderId));
                    return;
                }
            }

            for (BigItemStack stack : orderContext.stacks())
                orderedStacks.add(new BigItemStack(stack.stack, stack.count));
        }

        List<BigItemStack> allItems = summary.getStacks();
        List<ItemStack> outputSlots = new ArrayList<>();

        Repack:
        while (true) {
            allItems.removeIf(e -> e.count == 0);
            if (allItems.isEmpty())
                break;

            BigItemStack targetedEntry = null;
            if (!orderedStacks.isEmpty())
                targetedEntry = orderedStacks.remove(0);

            ItemSearch:
            for (BigItemStack entry : allItems) {
                int targetAmount = entry.count;
                if (targetAmount == 0)
                    continue;
                if (targetedEntry != null) {
                    targetAmount = targetedEntry.count;
                    if (!ItemStack.isSameItemSameComponents(entry.stack, targetedEntry.stack))
                        continue;
                }

                while (targetAmount > 0) {
                    int removedAmount = Math.min(Math.min(targetAmount, entry.stack.getMaxStackSize()), entry.count);
                    if (removedAmount == 0)
                        continue ItemSearch;

                    ItemStack output = entry.stack.copyWithCount(removedAmount);
                    targetAmount -= removedAmount;
                    if (targetedEntry != null)
                        targetedEntry.count = targetAmount;
                    entry.count -= removedAmount;
                    outputSlots.add(output);
                }

                continue Repack;
            }
        }

        // PATCHED: the order ends up in as few packages as the configured package size allows - one, unless the
        // order is larger than the server's limit. Upstream cut it into nine stack packages regardless.
        int limit = Math.max(1, MaxcraftConfig.maxPackageStacks());
        for (int offset = 0; offset < outputSlots.size(); offset += limit) {
            int end = Math.min(outputSlots.size(), offset + limit);
            ItemStackHandler target = new ItemStackHandler(end - offset);
            for (int i = offset; i < end; i++)
                target.setStackInSlot(i - offset, outputSlots.get(i));
            exportingPackages.add(new BigItemStack(PackageItem.containing(target), 1));
        }

        dev.maxcraft.Maxcraft.LOGGER.info("Maxcraft: the re-packager merged order {} into {} package(s), crafting "
            + "patterns in: {}", orderId, exportingPackages.size(),
            orderContext == null ? "none" : orderContext.orderedCrafts()
                .size());

        cir.setReturnValue(maxcraft$finish(exportingPackages, orderContext, address, orderId));
    }

    /** Addresses the finished packages and stamps the order onto the last of them. */
    @Unique
    private List<BigItemStack> maxcraft$finish(List<BigItemStack> exportingPackages,
                                               PackageOrderWithCrafts orderContext, String address, int orderId) {
        for (BigItemStack box : exportingPackages)
            PackageItem.addAddress(box.stack, address);

        for (int i = 0; i < exportingPackages.size(); i++) {
            BigItemStack box = exportingPackages.get(i);
            boolean isfinal = i == exportingPackages.size() - 1;
            PackageOrderWithCrafts outboundOrderContext = isfinal && orderContext != null ? orderContext : null;
            if (PackageItem.getOrderId(box.stack) == -1)
                PackageItem.setOrder(box.stack, orderId, 0, true, 0, true, outboundOrderContext);
        }

        return exportingPackages;
    }

    /**
     * PATCHED: the fragment search no longer gives up after a thousand links and a thousand packages per link.
     * A request large enough to be interesting produces far more fragments than that, and an order the helper
     * refuses to call complete simply sits in the Repackager forever.
     */
    @Inject(method = "isOrderComplete(I)Z", at = @At("HEAD"), cancellable = true)
    private void maxcraft$isOrderComplete(int orderId, CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(maxcraft$orderComplete(orderId));
    }

    /**
     * True when the collected fragments hold at least everything the order context asked for.
     *
     * <p>Fragment indices say a package exists, not that it arrived with everything in it: a request that was only
     * partly fulfilled still produces a run of indices, and upstream is happy to merge that. Comparing against the
     * order context is what makes the Large Re-Packager wait for the whole delivery. Orders without a context - a
     * redstone request, for example - have nothing to compare against and are accepted as they are.
     */
    @Unique
    private boolean maxcraft$coversOrder(List<ItemStack> fragments) {
        PackageOrderWithCrafts context = null;
        for (ItemStack box : fragments) {
            PackageOrderWithCrafts candidate = PackageItem.getOrderContext(box);
            if (candidate != null && !candidate.isEmpty()) {
                context = candidate;
                break;
            }
        }
        if (context == null)
            return true;

        InventorySummary collected = new InventorySummary();
        for (ItemStack box : fragments) {
            ItemStackHandler contents = PackageItem.getContents(box);
            for (int slot = 0; slot < contents.getSlots(); slot++)
                collected.add(contents.getStackInSlot(slot));
        }

        for (BigItemStack requested : context.stacks()) {
            if (requested.stack.isEmpty() || requested.count <= 0)
                continue;
            if (collected.getCountOf(requested.stack) < requested.count)
                return false;
        }
        return true;
    }

    @Unique
    private boolean maxcraft$orderComplete(int orderId) {
        List<ItemStack> fragments = collectedPackages.get(orderId);
        if (fragments == null || fragments.isEmpty())
            return false;

        if (maxcraft$strict && !maxcraft$coversOrder(fragments))
            return false;

        int maxLink = 0;
        int maxFragment = 0;
        for (ItemStack box : fragments) {
            PackageOrderData data = box.get(AllDataComponents.PACKAGE_ORDER_DATA);
            if (data == null)
                continue;
            maxLink = Math.max(maxLink, data.linkIndex());
            maxFragment = Math.max(maxFragment, data.fragmentIndex());
        }

        boolean finalLinkReached = false;
        for (int link = 0; ; link++) {
            if (finalLinkReached)
                return true;
            if (link > maxLink)
                return false;

            for (int fragment = 0; ; fragment++) {
                if (fragment > maxFragment)
                    break;

                PackageOrderData found = null;
                for (ItemStack box : fragments) {
                    PackageOrderData data = box.get(AllDataComponents.PACKAGE_ORDER_DATA);
                    if (data == null)
                        continue;
                    if (data.linkIndex() != link || data.fragmentIndex() != fragment)
                        continue;
                    found = data;
                    break;
                }
                if (found == null)
                    return false;

                finalLinkReached = found.isFinalLink();
                if (found.isFinal())
                    break;
            }
        }
    }
}
