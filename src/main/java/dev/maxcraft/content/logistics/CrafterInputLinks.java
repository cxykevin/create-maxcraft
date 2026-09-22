package dev.maxcraft.content.logistics;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.simibubi.create.content.kinetics.base.HorizontalKineticBlock;
import com.simibubi.create.content.kinetics.crafter.ConnectedInputHandler;
import com.simibubi.create.content.kinetics.crafter.CrafterHelper;
import com.simibubi.create.content.kinetics.crafter.MechanicalCrafterBlockEntity;

import dev.maxcraft.Maxcraft;
import dev.maxcraft.registry.MaxcraftItems;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Links and unlinks the shared input of a whole Mechanical Crafter matrix.
 *
 * <p>Create connects crafters in pairs, along the edge between two of them, so a large array takes one wrench click
 * per edge - and an array is exactly where the shared input matters most, because a delivered package is unpacked
 * into the group that received it. With a Large Package Component in hand the whole matrix is one click: right-click
 * the back of any crafter of it and every crafter that touches it, directly or through more crafters, ends up on
 * one shared input. Right-click again and every crafter of the matrix gets its own input back.
 *
 * <p>The click reports how many crafters share the input, because the interesting number is not the one the player
 * can see: merging with a neighbour brings that neighbour's whole group along.
 *
 * <p>The front of a crafter is the face it faces, which is the one Create's own interactions work on, so the back is
 * the face opposite it.
 */
public final class CrafterInputLinks {

    private CrafterInputLinks() {
    }

    /** True if this stack is the tool that links crafters. */
    public static boolean isLinkItem(ItemStack stack) {
        return stack.is(MaxcraftItems.LARGE_PACKAGE_COMPONENT.get());
    }

    /** True when the click landed on the back of the crafter, the face opposite the one it faces. */
    public static boolean isBackFace(BlockState state, Direction clicked) {
        return state.hasProperty(HorizontalKineticBlock.HORIZONTAL_FACING)
            && clicked == state.getValue(HorizontalKineticBlock.HORIZONTAL_FACING)
                .getOpposite();
    }

    /** Links or unlinks the whole matrix this crafter is part of, and tells the player how many crafters it was. */
    public static void toggleMatrix(Level level, BlockPos pos, Player player) {
        List<BlockPos> matrix = matrix(level, pos);
        if (matrix.size() < 2) {
            player.displayClientMessage(Component.translatable("maxcraft.crafter_input.none"), true);
            return;
        }

        // A matrix only comes apart when every crafter of it already shares one input; a half-linked array is
        // linked, not taken apart, so the first click on an array is always the one that builds it.
        boolean wholeMatrix = sharedCrafters(level, pos, matrix) == matrix.size();
        int count = wholeMatrix ? unlink(level, pos, matrix) : link(level, pos, matrix);

        // Logged after the fact, and with what the array really looks like now: if a click ever does not do what
        // its message says, these two lines are the ones that show it.
        int now = sharedCrafters(level, pos, matrix);
        if (!wholeMatrix && now != matrix.size())
            Maxcraft.LOGGER.warn("Maxcraft: linking the matrix of {} crafters at {} only reached {}",
                matrix.size(), pos, now);
        Maxcraft.LOGGER.info("Maxcraft: {} {} the matrix of {} crafters at {} - {} share one input now",
            player.getName()
                .getString(),
            wholeMatrix ? "unlinked" : "linked", matrix.size(), pos, now);
        player.displayClientMessage(Component.translatable(
            wholeMatrix ? "maxcraft.crafter_input.unlinked" : "maxcraft.crafter_input.linked", count), true);
    }

    /**
     * Every crafter of the matrix this one belongs to, itself included: the crafters that touch it, the crafters
     * that touch those, and so on. A crafter standing apart is a matrix of its own and is not part of this one.
     */
    public static List<BlockPos> matrix(Level level, BlockPos pos) {
        List<BlockPos> matrix = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();
        Deque<BlockPos> todo = new ArrayDeque<>();
        seen.add(pos);
        todo.add(pos);

        while (!todo.isEmpty()) {
            BlockPos current = todo.poll();
            matrix.add(current);
            for (Direction side : Direction.values()) {
                BlockPos next = current.relative(side);
                if (seen.add(next) && CrafterHelper.getCrafter(level, next) != null)
                    todo.add(next);
            }
        }
        return matrix;
    }

    /** True when this crafter already shares its input with another crafter of the matrix. */
    public static boolean sharesInput(Level level, BlockPos pos, List<BlockPos> matrix) {
        return sharedCrafters(level, pos, matrix) > 1;
    }

    /**
     * Links the whole matrix into one shared input.
     *
     * @return how many crafters of the matrix share it once this is done
     */
    public static int link(Level level, BlockPos pos, List<BlockPos> matrix) {
        MechanicalCrafterBlockEntity controller = CrafterHelper.getCrafter(level, pos);
        if (controller == null)
            return 0;

        List<BlockPos> members = matrix.stream()
            .filter(member -> !member.equals(pos))
            .toList();
        ConnectedInputHandler.initAndAddAll(level, controller, members);
        syncToClients(controller);
        return sharedCrafters(level, pos, matrix);
    }

    /**
     * Gives every crafter of the matrix its own input back, whatever shape the shared inputs were in.
     *
     * @return how many crafters of the matrix shared an input with this one to begin with
     */
    public static int unlink(Level level, BlockPos pos, List<BlockPos> matrix) {
        int shared = sharedCrafters(level, pos, matrix);
        for (BlockPos member : matrix) {
            MechanicalCrafterBlockEntity crafter = CrafterHelper.getCrafter(level, member);
            if (crafter == null)
                continue;
            ConnectedInputHandler.initAndAddAll(level, crafter, List.of());
            syncToClients(crafter);
        }
        return shared;
    }

    /**
     * Marks a crafter whose input was just replaced as changed and sends it out.
     *
     * <p>Create replaces a crafter's input by assigning the field in {@code initAndAddAll}, with no dirty flag and
     * no packet. The crafters it attaches are synced by the code that walks them, but the controller it installs,
     * and every crafter an unlink resets, are not - so without this the server would be right while every client
     * kept drawing the links that were there before, which is exactly what "unlinking does nothing" looks like.
     */
    private static void syncToClients(MechanicalCrafterBlockEntity crafter) {
        crafter.setChanged();
        crafter.connectivityChanged();
    }

    /** How many crafters of the matrix share this one's input, itself included. */
    private static int sharedCrafters(Level level, BlockPos pos, List<BlockPos> matrix) {
        int shared = 1;
        for (BlockPos member : matrix)
            if (!member.equals(pos) && CrafterHelper.areCraftersConnected(level, pos, member))
                shared++;
        return shared;
    }
}
