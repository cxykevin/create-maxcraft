package dev.maxcraft;

import java.util.List;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * The advancements of this mod, and the one place they are handed out.
 *
 * <p>Their conditions are code rather than data: every file declares a single {@code minecraft:impossible}
 * criterion, and the mod grants it the moment it sees the thing happen. "A packager was upgraded", "a 5x5 pattern
 * reached the crafters" and "this shop sells ten kinds of goods" are each a fact about a machine rather than about
 * an item or a location, so no predicate in the file could say them.
 */
public final class MaxcraftAdvancements {

    /** 大包裹 - a Packager was upgraded into a Large Packager. */
    public static final ResourceLocation LARGE_PACKAGE = id("large_package");

    /** 黄铜仪表 - a gauge panel was given a larger crafting grid. The root of this mod's own tree. */
    public static final ResourceLocation BRASS_GAUGE = id("brass_gauge");

    /** 我们需要更大的合成器 - a 5x5 or larger pattern was delivered to a Mechanical Crafter array. */
    public static final ResourceLocation LARGE_CRAFTING = id("large_crafting");

    /** 不是你真合啊 - the Creative Blaze Cake came out of a 30x30 array. Hidden. */
    public static final ResourceLocation CREATIVE_BLAZE_CAKE = id("creative_blaze_cake");

    /** [+N] - a Stock Ticker was upgraded into an Extended Stock Ticker. */
    public static final ResourceLocation MORE_KINDS = id("more_kinds");

    /** 《交易所》 - a Table Cloth shop was set up with ten or more kinds of goods. Hidden. */
    public static final ResourceLocation EXCHANGE = id("exchange");

    /** Every advancement this mod adds, for the self test. */
    public static final List<ResourceLocation> ALL =
        List.of(LARGE_PACKAGE, BRASS_GAUGE, LARGE_CRAFTING, CREATIVE_BLAZE_CAKE, MORE_KINDS, EXCHANGE);

    /** The criterion name every advancement file of this mod declares. */
    private static final String CRITERION = "0";

    /** How close a player has to be to a machine to count as having watched it work. */
    private static final double WITNESS_RANGE = 32.0;

    private MaxcraftAdvancements() {
    }

    /** True if the server knows this advancement; used by the self test. */
    public static boolean isPresent(MinecraftServer server, ResourceLocation advancement) {
        return holder(server, advancement) != null;
    }

    /** True for an advancement that is kept out of the tree until it is earned. */
    public static boolean isHidden(MinecraftServer server, ResourceLocation advancement) {
        AdvancementHolder holder = holder(server, advancement);
        return holder != null && holder.value()
            .display()
            .map(DisplayInfo::isHidden)
            .orElse(false);
    }

    /** The advancement this one hangs under, or null when it is the root of a tree. */
    public static ResourceLocation parentOf(MinecraftServer server, ResourceLocation advancement) {
        AdvancementHolder holder = holder(server, advancement);
        return holder == null ? null : holder.value()
            .parent()
            .orElse(null);
    }

    /** Grants the advancement to the player, unless they already have it. Does nothing on the client. */
    public static void award(Player player, ResourceLocation advancement) {
        if (!(player instanceof ServerPlayer serverPlayer))
            return;
        AdvancementHolder holder = holder(serverPlayer.getServer(), advancement);
        if (holder != null)
            serverPlayer.getAdvancements()
                .award(holder, CRITERION);
    }

    /**
     * Grants the advancement to everyone close enough to have seen it.
     *
     * <p>A Mechanical Crafter array has no owner, so the players standing around it are the ones who watched it
     * work - which is what the two crafter advancements ask for.
     */
    public static void awardWitnesses(Level level, BlockPos pos, ResourceLocation advancement) {
        if (!(level instanceof ServerLevel serverLevel))
            return;
        double range = WITNESS_RANGE * WITNESS_RANGE;
        for (ServerPlayer player : serverLevel.getPlayers(candidate -> candidate.blockPosition()
            .distSqr(pos) <= range))
            award(player, advancement);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(Maxcraft.MOD_ID, path);
    }

    private static AdvancementHolder holder(MinecraftServer server, ResourceLocation advancement) {
        return server == null ? null : server.getAdvancements()
            .get(advancement);
    }
}
