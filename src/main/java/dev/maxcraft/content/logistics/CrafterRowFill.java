package dev.maxcraft.content.logistics;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.crafter.MechanicalCrafterBlock;

import dev.maxcraft.Maxcraft;
import dev.maxcraft.MaxcraftConfig;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Lays a whole row of Mechanical Crafters together with the one placed on top of a finished row.
 *
 * <p>An array is a wall of the same block, and putting it up one block at a time is the slow part of every large
 * recipe: a 30x30 array is nine hundred placements. The row underneath is the template here - place one crafter
 * above any column of a finished row, and the rest of that row lands with it, so a row costs one click.
 *
 * <p>Only the row directly below is read, and it has to be a straight unbroken run of crafters along one horizontal
 * axis; a lone crafter is not a row yet. Anything already standing where the row would go is left alone. A row is
 * placed whole or not at all: if the player is not carrying enough crafters for the whole row it is not started,
 * and every crafter placed is paid for out of their inventory. Sneaking places the single block and nothing else,
 * and the whole behaviour can be turned off in the server config.
 */
@EventBusSubscriber(modid = Maxcraft.MOD_ID)
public final class CrafterRowFill {

    /**
     * The rows a placement asked for, filled at the end of the tick instead of during the placement itself.
     *
     * <p>The block that asks for a row is paid for by the game itself, out of the hand, and the game does that with
     * a copy of the stack taken <em>before</em> the placement event fires. Anything this mod takes out of that hand
     * while the event runs is written over again when the copy goes back, which is how a free row looked. By the end
     * of the tick the copy is in place, so the crafters a row costs can be taken for real.
     */
    private static final List<Pending> PENDING = new ArrayList<>();

    private CrafterRowFill() {
    }

    /** A placement that asked for a row, waiting for the end of the tick. */
    private record Pending(ServerLevel level, BlockPos pos, BlockState state, ServerPlayer player) {
    }

    @SubscribeEvent
    public static void onPlaced(BlockEvent.EntityPlaceEvent event) {
        if (!MaxcraftConfig.autoFillCrafterRows())
            return;
        if (!(event.getEntity() instanceof ServerPlayer player) || player.isShiftKeyDown())
            return;
        if (!(event.getLevel() instanceof ServerLevel level))
            return;
        if (!(event.getPlacedBlock()
            .getBlock() instanceof MechanicalCrafterBlock))
            return;

        PENDING.add(new Pending(level, event.getPos(), event.getPlacedBlock(), player));
    }

    /** Fills the rows the placements of this tick asked for. */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        flushPending();
    }

    /** Fills every row waiting to be paid for. The tick calls this; the self test calls it by hand. */
    public static void flushPending() {
        if (PENDING.isEmpty())
            return;

        List<Pending> due = List.copyOf(PENDING);
        PENDING.clear();
        for (Pending pending : due)
            if (!pending.player()
                .hasDisconnected())
                fillRow(pending.level(), pending.pos(), pending.state(), pending.player());
    }

    /**
     * Places the rest of a crafter row above {@code pos}, mirroring the row of crafters underneath it.
     *
     * <p>Called once the block that asked for the row is in the world and has been paid for, so a row of n blocks
     * costs the player the n-1 crafters taken here plus the one the game took for the block itself.
     *
     * @return how many crafters were placed
     */
    public static int fillRow(ServerLevel level, BlockPos pos, BlockState state, ServerPlayer player) {
        List<BlockPos> row = rowUnder(level, pos.below());
        if (row.size() < 2)
            return 0;

        List<BlockPos> targets = new ArrayList<>();
        for (BlockPos column : row) {
            BlockPos target = new BlockPos(column.getX(), pos.getY(), column.getZ());
            if (!target.equals(pos) && level.getBlockState(target)
                .canBeReplaced())
                targets.add(target);
        }
        if (targets.isEmpty())
            return 0;

        // A row is placed whole or not at all: a row the player cannot pay for is better left alone than started.
        if (!player.getAbilities().instabuild && craftersCarried(player) < targets.size())
            return 0;

        for (BlockPos target : targets) {
            // Cannot run out - the row was counted before it was started - but never place for free.
            if (!takeCrafter(player))
                break;
            place(level, target, state, player);
        }
        return targets.size();
    }

    /** The unbroken run of crafters through this position, along whichever horizontal axis is the longer one. */
    private static List<BlockPos> rowUnder(ServerLevel level, BlockPos from) {
        if (!isCrafter(level, from))
            return List.of();

        List<BlockPos> alongX = run(level, from, Direction.EAST, Direction.WEST);
        List<BlockPos> alongZ = run(level, from, Direction.SOUTH, Direction.NORTH);
        return alongZ.size() > alongX.size() ? alongZ : alongX;
    }

    /** The crafters of one line through {@code from}, from the far end back to it and on to the other end. */
    private static List<BlockPos> run(ServerLevel level, BlockPos from, Direction forwards, Direction backwards) {
        List<BlockPos> before = new ArrayList<>();
        BlockPos cursor = from;
        while (isCrafter(level, cursor = cursor.relative(backwards)))
            before.add(cursor);
        Collections.reverse(before);

        List<BlockPos> row = new ArrayList<>(before);
        row.add(from);
        cursor = from;
        while (isCrafter(level, cursor = cursor.relative(forwards)))
            row.add(cursor);
        return row;
    }

    private static boolean isCrafter(ServerLevel level, BlockPos pos) {
        return AllBlocks.MECHANICAL_CRAFTER.has(level.getBlockState(pos));
    }

    /** How many crafters the player is carrying, the hand they are holding included. */
    private static int craftersCarried(ServerPlayer player) {
        int carried = 0;
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (isCrafter(stack))
                carried += stack.getCount();
        }
        return carried;
    }

    /** One crafter out of the player's inventory, the hand they are holding going first. */
    private static boolean takeCrafter(ServerPlayer player) {
        if (player.getAbilities().instabuild)
            return true;

        ItemStack held = player.getMainHandItem();
        if (isCrafter(held)) {
            held.shrink(1);
            return true;
        }

        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (isCrafter(stack)) {
                stack.shrink(1);
                inventory.setChanged();
                return true;
            }
        }
        return false;
    }

    private static boolean isCrafter(ItemStack stack) {
        return stack.is(AllBlocks.MECHANICAL_CRAFTER.asItem());
    }

    /** The sound and game event a hand-placed block makes, so a filled row sounds like what it is. */
    private static void place(ServerLevel level, BlockPos pos, BlockState state, ServerPlayer player) {
        level.setBlockAndUpdate(pos, state);

        SoundType sound = state.getSoundType();
        level.playSound(null, pos, sound.getPlaceSound(), SoundSource.BLOCKS, (sound.getVolume() + 1.0F) / 2.0F,
            sound.getPitch() * 0.8F);
        level.gameEvent(GameEvent.BLOCK_PLACE, pos, GameEvent.Context.of(player, state));
    }
}
