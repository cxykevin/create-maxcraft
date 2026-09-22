package dev.maxcraft.content.logistics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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

/**
 * Lays a whole row of Mechanical Crafters together with the one placed on top of a finished row.
 *
 * <p>An array is a wall of the same block, and putting it up one block at a time is the slow part of every large
 * recipe: a 30x30 array is nine hundred placements. The row underneath is the template here - place one crafter
 * above any column of a finished row, and the rest of that row lands with it, so a row costs one click.
 *
 * <p>Only the row directly below is read, and it has to be a straight unbroken run of crafters along one horizontal
 * axis; a lone crafter is not a row yet. Anything already standing where the row would go is left alone, and every
 * crafter placed is paid for out of the player's inventory - a player who runs out gets as many as they can afford.
 * Sneaking places the single block and nothing else, and the whole behaviour can be turned off in the server config.
 */
@EventBusSubscriber(modid = Maxcraft.MOD_ID)
public final class CrafterRowFill {

    private CrafterRowFill() {
    }

    /**
     * Runs while the crafter is not in the world yet - the event is the last chance to refuse the placement - which
     * is why the row is read from the block below and the placement being made is skipped by position.
     */
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

        fillRow(level, event.getPos(), event.getPlacedBlock(), player);
    }

    /**
     * Places the rest of a crafter row above {@code pos}, mirroring the row of crafters underneath it.
     *
     * @return how many crafters were placed
     */
    public static int fillRow(ServerLevel level, BlockPos pos, BlockState state, ServerPlayer player) {
        List<BlockPos> row = rowUnder(level, pos.below());
        if (row.size() < 2)
            return 0;

        int placed = 0;
        for (BlockPos column : row) {
            BlockPos target = new BlockPos(column.getX(), pos.getY(), column.getZ());
            if (target.equals(pos) || !level.getBlockState(target)
                .canBeReplaced())
                continue;
            if (!takeCrafter(player))
                break;

            place(level, target, state, player);
            placed++;
        }
        return placed;
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

    /** One crafter out of the player's inventory, the hand that placed the first one going first. */
    private static boolean takeCrafter(ServerPlayer player) {
        if (player.getAbilities().instabuild)
            return true;

        // The block being placed is paid for by the game itself, out of the hand, after this runs - so the last
        // crafter in that hand is left alone. Spending it here would make the placement free, because shrinking an
        // empty stack does nothing.
        Inventory inventory = player.getInventory();
        ItemStack held = player.getMainHandItem();
        if (isCrafter(held) && held.getCount() > 1) {
            held.shrink(1);
            return true;
        }

        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (slot == inventory.selected)
                continue;
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
