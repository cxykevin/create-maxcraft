package dev.maxcraft.logistics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.simibubi.create.AllDataComponents;
import com.simibubi.create.content.logistics.box.PackageItem;

import dev.maxcraft.registry.MaxcraftDataComponents;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * Reads and writes package contents without the 9-stack limit Create ships with.
 *
 * <p>Create funnels every read of a package through {@link PackageItem#getContents(ItemStack)} and every write
 * through {@link PackageItem#containing(ItemStackHandler)}. Those two methods build fixed 9 slot handlers, so a
 * package holding more than nine stacks either loses everything past slot 9 or fails outright. Both are patched by
 * {@code PackageItemMixin} to delegate here.
 *
 * <p>Storage layout, in order of preference:
 * <ol>
 *   <li>{@code maxcraft:package_bulk_contents} - the full, unbounded list. Present only for oversized packages.</li>
 *   <li>{@code create:package_contents} - Create's own component, at most 256 slots.</li>
 * </ol>
 * Packages that fit into 256 slots keep using Create's component alone, so ordinary packages stay byte-identical
 * to unpatched Create and remain readable by anything that knows about {@code create:package_contents}.
 *
 * <p>How many stacks a package may hold is the server's business: see
 * {@link dev.maxcraft.MaxcraftConfig#maxPackageStacks()}. The limit is applied where packages are made - the packager
 * and the re-packager - and a package that turns up larger than the limit anyway is still read in full rather than
 * truncated, because losing items is worse than a large package.
 */
public final class PackageContents {

    /** {@link ItemContainerContents} refuses to hold more than this many slots. */
    public static final int VANILLA_SLOT_LIMIT = 256;

    /** {@code ItemStack.CODEC} only accepts counts in {@code [1, 99]}, so nothing above this may be persisted. */
    public static final int MAX_PERSISTED_COUNT = 99;

    private PackageContents() {
    }

    /** True if the package carries contents that do not fit into Create's own component. */
    public static boolean hasBulkContents(ItemStack box) {
        return box.has(MaxcraftDataComponents.PACKAGE_BULK_CONTENTS.get());
    }

    /**
     * Every slot of the package in order, trailing empty slots trimmed, nothing truncated.
     * Stacks are returned as they are - use {@link #toHandler} for a sanitised view.
     */
    public static List<ItemStack> read(ItemStack box) {
        List<ItemStack> bulk = box.get(MaxcraftDataComponents.PACKAGE_BULK_CONTENTS.get());
        if (bulk != null) {
            List<ItemStack> copy = new ArrayList<>(bulk.size());
            for (ItemStack stack : bulk)
                copy.add(stack.copy());
            return trim(copy);
        }

        ItemContainerContents contents =
            box.getOrDefault(AllDataComponents.PACKAGE_CONTENTS, ItemContainerContents.EMPTY);
        int slots = contents.getSlots();
        List<ItemStack> copy = new ArrayList<>(slots);
        for (int slot = 0; slot < slots; slot++)
            copy.add(contents.getStackInSlot(slot));
        return trim(copy);
    }

    /** The same as {@link #read(ItemStack)}, for an inventory of any size. */
    public static List<ItemStack> read(net.neoforged.neoforge.items.IItemHandler handler) {
        List<ItemStack> copy = new ArrayList<>(handler.getSlots());
        for (int slot = 0; slot < handler.getSlots(); slot++)
            copy.add(handler.getStackInSlot(slot).copy());
        return trim(copy);
    }

    /**
     * Writes contents back onto a package, choosing the component that can hold them.
     *
     * @return true if the bulk component was needed
     */
    public static boolean write(ItemStack box, List<ItemStack> contents) {
        List<ItemStack> normalized = normalize(contents);

        if (normalized.size() <= VANILLA_SLOT_LIMIT) {
            box.set(AllDataComponents.PACKAGE_CONTENTS, ItemContainerContents.fromItems(normalized));
            box.remove(MaxcraftDataComponents.PACKAGE_BULK_CONTENTS.get());
            return false;
        }

        box.set(MaxcraftDataComponents.PACKAGE_BULK_CONTENTS.get(), normalized);
        // Mirror a readable slice into Create's component. It is built from the non-empty stacks so that the
        // component is present for every non-empty package, which is what Create's tooltip and Create: Cyber
        // Goggles test for before they render contents.
        List<ItemStack> mirror = new ArrayList<>(VANILLA_SLOT_LIMIT);
        for (ItemStack stack : normalized) {
            if (stack.isEmpty())
                continue;
            mirror.add(stack);
            if (mirror.size() == VANILLA_SLOT_LIMIT)
                break;
        }
        box.set(AllDataComponents.PACKAGE_CONTENTS, ItemContainerContents.fromItems(mirror));
        return true;
    }

    /** Writes the contents of an inventory onto a package. */
    public static boolean write(ItemStack box, net.neoforged.neoforge.items.IItemHandler handler) {
        return write(box, read(handler));
    }

    /**
     * A handler view of the package with at least {@link PackageItem#SLOTS} slots, sized to everything the package
     * actually holds. Stacks above their legal size are split into consecutive slots rather than being clamped, so
     * the total number of items always survives.
     */
    public static ItemStackHandler toHandler(ItemStack box) {
        List<ItemStack> contents = read(box);
        List<ItemStack> slots = new ArrayList<>(contents.size());
        List<ItemStack> overflow = new ArrayList<>();

        for (ItemStack stack : contents) {
            if (stack.isEmpty()) {
                slots.add(ItemStack.EMPTY);
                continue;
            }
            int limit = legalCount(stack);
            if (stack.getCount() <= limit) {
                slots.add(stack.copy());
                continue;
            }
            slots.add(stack.copyWithCount(limit));
            int remaining = stack.getCount() - limit;
            while (remaining > 0) {
                overflow.add(stack.copyWithCount(Math.min(remaining, limit)));
                remaining -= limit;
            }
        }
        slots.addAll(overflow);

        ItemStackHandler handler = new ItemStackHandler(Math.max(PackageItem.SLOTS, slots.size()));
        for (int slot = 0; slot < slots.size(); slot++)
            handler.setStackInSlot(slot, slots.get(slot));
        return handler;
    }

    /** Drops trailing empty slots, exactly like {@code ItemContainerContents#fromItems} does. */
    private static List<ItemStack> trim(List<ItemStack> stacks) {
        int last = stacks.size();
        while (last > 0 && stacks.get(last - 1).isEmpty())
            last--;
        if (last == stacks.size())
            return stacks;
        return new ArrayList<>(stacks.subList(0, last));
    }

    /**
     * Makes every stack persistable: counts are split so that no stack exceeds its item's stack size (and never
     * the 99 the item codec allows), which keeps the data safe to save and to send.
     */
    private static List<ItemStack> normalize(List<ItemStack> contents) {
        List<ItemStack> normalized = new ArrayList<>(contents.size());
        for (ItemStack stack : contents) {
            if (stack == null || stack.isEmpty()) {
                normalized.add(ItemStack.EMPTY);
                continue;
            }
            int limit = legalCount(stack);
            int remaining = stack.getCount();
            while (remaining > limit) {
                normalized.add(stack.copyWithCount(limit));
                remaining -= limit;
            }
            if (remaining > 0)
                normalized.add(stack.copyWithCount(remaining));
        }
        return trim(normalized);
    }

    private static int legalCount(ItemStack stack) {
        return Math.max(1, Math.min(stack.getMaxStackSize(), MAX_PERSISTED_COUNT));
    }

    /** An unmodifiable empty list, used where a package has no contents at all. */
    public static List<ItemStack> empty() {
        return Collections.emptyList();
    }
}
