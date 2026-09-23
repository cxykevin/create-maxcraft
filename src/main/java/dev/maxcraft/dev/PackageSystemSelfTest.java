package dev.maxcraft.dev;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.kinetics.base.HorizontalKineticBlock;
import com.simibubi.create.content.kinetics.crafter.MechanicalCrafterBlockEntity;
import com.simibubi.create.content.kinetics.crafter.MechanicalCraftingRecipe;
import com.simibubi.create.content.kinetics.crafter.RecipeGridHandler;
import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.box.PackageItem;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBehaviour;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlock;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlockEntity;
import com.simibubi.create.content.logistics.packager.PackagerBlock;
import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;
import com.simibubi.create.content.logistics.packager.repackager.PackageRepackageHelper;
import com.simibubi.create.content.logistics.stockTicker.PackageOrderWithCrafts;
import com.simibubi.create.content.logistics.stockTicker.StockTickerBlock;
import com.simibubi.create.content.logistics.stockTicker.StockTickerBlockEntity;

import dev.maxcraft.Maxcraft;
import dev.maxcraft.MaxcraftAdvancements;
import dev.maxcraft.MaxcraftConfig;
import dev.maxcraft.content.logistics.ExtendedGaugeItem;
import dev.maxcraft.content.logistics.ExtendedStockTickerBlock;
import dev.maxcraft.content.logistics.ExtendedStockTickerBlockEntity;
import dev.maxcraft.content.logistics.GaugePanelSizes;
import dev.maxcraft.content.logistics.GridSizeHolder;
import dev.maxcraft.content.logistics.LargePackagerBlock;
import dev.maxcraft.content.logistics.LargeRepackagerBlock;
import dev.maxcraft.content.logistics.LargePackagerBlockEntity;
import dev.maxcraft.content.logistics.CrafterInputLinks;
import dev.maxcraft.content.logistics.CrafterRowFill;
import dev.maxcraft.logistics.CraftingPattern;
import dev.maxcraft.logistics.PackageContents;
import dev.maxcraft.logistics.RepackageHelperAccess;
import dev.maxcraft.registry.MaxcraftBlockEntityTypes;
import dev.maxcraft.registry.MaxcraftDataComponents;
import dev.maxcraft.registry.MaxcraftBlocks;
import dev.maxcraft.registry.MaxcraftItems;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * Exercises the package and Repackager patches inside a running server, then shuts it down.
 *
 * <p>Enabled by setting the environment variable {@code MAXCRAFT_SELFTEST=1}; it is inert otherwise. Run it with:
 * <pre>MAXCRAFT_SELFTEST=1 gradle runServer</pre>
 * A non-zero exit is reported by the {@code SELFTEST FAILED} line.
 */
@EventBusSubscriber(modid = Maxcraft.MOD_ID)
public final class PackageSystemSelfTest {

    private static final String ENV = "MAXCRAFT_SELFTEST";

    private static int checks;
    private static int failures;
    private static ServerLevel serverLevel;

    private PackageSystemSelfTest() {
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        if (!"1".equals(System.getenv(ENV)))
            return;

        try {
            serverLevel = event.getServer().overworld();
            runTests(event.getServer().registryAccess());
        } catch (Throwable t) {
            failures++;
            Maxcraft.LOGGER.error("SELFTEST: threw", t);
        }

        Maxcraft.LOGGER.info("SELFTEST: {} checks, {} failures", checks, failures);
        Maxcraft.LOGGER.info(failures == 0 ? "SELFTEST PASSED" : "SELFTEST FAILED");

        // The result is already on the console, so the run never needs to outlive a slow world save.
        Thread watchdog = new Thread(() -> {
            try {
                Thread.sleep(20_000L);
            } catch (InterruptedException e) {
                return;
            }
            Maxcraft.LOGGER.warn("SELFTEST: server did not stop within 20s, forcing exit");
            Runtime.getRuntime().halt(failures == 0 ? 0 : 1);
        }, "maxcraft-selftest-watchdog");
        watchdog.setDaemon(true);
        watchdog.start();

        event.getServer().halt(false);
    }

    private static void runTests(RegistryAccess registries) {
        registration();

        // 1. An ordinary package still round-trips through Create's own component.
        smallPackage(registries);

        // 2. A package holding far more than nine stacks survives every hop.
        oversizedPackage(registries);

        // 3. The Repackager merges an order - oversized fragments included - into a single package.
        repackagerMerge(registries);

        // 4. Collecting the whole inventory up front must not count a fragment twice.
        repackagerPreCollection();

        // 5. The Large Packager fills 27 stacks, and the ordinary packager still fills 9.
        packagerThreads(registries);

        // 6. A Stock Ticker upgrades into an Extended one without losing its network.
        stockTickerConversion();

        // 7. A Packager upgrades into a Large Packager without losing what it was holding.
        largePackagerUpgrade();

        // 8. The Large Re-Packager waits for a whole order before merging it.
        largeRepackagerStrictness();

        // 9. The package size limit comes from the server config.
        packageSizeLimit();

        // 10. The Extended Factory Gauge keeps a crafting grid size per panel.
        factoryGaugeGridSize();

        // 11. A gauge item used on a gauge that is already there extends only the panel it adds.
        gaugeItemUsedOnExistingGauge();

        // 12. A recipe smaller than the machine is laid into the machine's top left.
        patternLayout();

        // 13. Every item description is written under the id the game will look it up with.
        itemDescriptions();

        // 14. A crafter placed on top of a row of them brings the rest of that row with it.
        crafterRowFill();

        // 15. The Large Package Component links a whole matrix of crafters in one click.
        crafterInputLinks();

        // 16. The same, at the size the mod exists for: a full 30x30 array.
        crafterMatrix();

        // 17. The row fill through a real click: whole rows only, and paid for out of the inventory.
        crafterRowFillPays();
    }

    /**
     * A real 30x30 array, linked and unlinked in one click each. Anything that only works for a handful of crafters
     * is no use to the array this mod is for, so the scale is part of the test.
     */
    private static void crafterMatrix() {
        ServerLevel level = serverLevel;
        if (level == null) {
            check("a server level is available for the array test", false);
            return;
        }

        BlockState crafter = AllBlocks.MECHANICAL_CRAFTER.get()
            .defaultBlockState();
        BlockPos corner = new BlockPos(200, 100, 200);
        int side = 30;
        for (int x = 0; x < side; x++)
            for (int z = 0; z < side; z++)
                level.setBlockAndUpdate(corner.offset(x, 0, z), crafter);

        BlockPos clicked = corner.offset(side / 2, 0, side / 2);
        List<BlockPos> matrix = CrafterInputLinks.matrix(level, clicked);
        int expected = side * side;
        Maxcraft.LOGGER.info("SELFTEST: the array is {} crafters", matrix.size());
        check("a 30x30 array is one matrix of 900 crafters", matrix.size() == expected);
        check("a fresh array shares nothing", !CrafterInputLinks.sharesInput(level, clicked, matrix));

        int linked = CrafterInputLinks.link(level, clicked, matrix);
        Maxcraft.LOGGER.info("SELFTEST: linking the array reported {}", linked);
        check("one click links the whole array", linked == expected);

        check("the array is one shared input now", CrafterInputLinks.sharesInput(level, clicked, matrix));

        int unlinked = CrafterInputLinks.unlink(level, clicked, matrix);
        Maxcraft.LOGGER.info("SELFTEST: unlinking the array reported {}", unlinked);
        check("one click takes the whole array apart again",
            unlinked == expected && !CrafterInputLinks.sharesInput(level, clicked, matrix));

        // Then the two clicks a player makes, run through Create's own interaction entry point.
        FakePlayer clicker = FakePlayerFactory.getMinecraft(level);
        ItemStack component = new ItemStack(MaxcraftItems.LARGE_PACKAGE_COMPONENT.get());
        clicker.setItemInHand(InteractionHand.MAIN_HAND, component);
        Direction back = crafter.getValue(HorizontalKineticBlock.HORIZONTAL_FACING)
            .getOpposite();

        click(level, clicked, clicker, component, back);
        check("a click on the back links the whole array", CrafterInputLinks.sharesInput(level, clicked, matrix));

        click(level, clicked, clicker, component, back);
        check("the next click takes the whole array apart again",
            !CrafterInputLinks.sharesInput(level, clicked, matrix));

        // And what a client is sent has to carry it: this is the data the connection texture is drawn from, and
        // without a sync the server is right while every client keeps drawing the links that were there before.
        check("the link state sent to clients follows the click", sentLinkSize(level, clicked) == 1);

        clicker.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
    }

    /** How many positions the link state a client would be sent for this crafter holds. */
    private static int sentLinkSize(ServerLevel level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof MechanicalCrafterBlockEntity crafter))
            return -1;
        CompoundTag input = crafter.getUpdateTag(level.registryAccess())
            .getCompound("ConnectedInput");
        return input.get("Data") instanceof ListTag data ? data.size() : -1;
    }

    /** One right-click on a crafter's back, run the way the server runs it. */
    private static void click(ServerLevel level, BlockPos pos, FakePlayer player, ItemStack stack, Direction face) {
        level.getBlockState(pos)
            .useItemOn(stack, level, player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(pos), face, pos, false));
    }

    /**
     * The Large Package Component links a whole matrix of crafters in one click, and gives every crafter its own
     * input back on the next. Create does this in pairs, along the edge between two crafters; the point of the
     * component is the matrix an array is.
     */
    private static void crafterInputLinks() {
        ServerLevel level = serverLevel;
        if (level == null) {
            check("a server level is available for the crafter link test", false);
            return;
        }

        BlockState crafter = AllBlocks.MECHANICAL_CRAFTER.get()
            .defaultBlockState();
        BlockPos corner = new BlockPos(16, 100, 4);
        for (int x = 0; x <= 2; x++)
            for (int z = 0; z <= 1; z++)
                level.setBlockAndUpdate(corner.offset(x, 0, z), crafter);
        // A crafter standing apart is a matrix of its own, and must not be swept up by this one.
        level.setBlockAndUpdate(corner.offset(5, 0, 0), crafter);

        BlockPos middle = corner.offset(1, 0, 0);
        List<BlockPos> matrix = CrafterInputLinks.matrix(level, middle);
        check("the matrix is every crafter touching it, and nothing else",
            matrix.size() == 6 && !matrix.contains(corner.offset(5, 0, 0)));
        check("a fresh matrix shares nothing", !CrafterInputLinks.sharesInput(level, middle, matrix));

        int linked = CrafterInputLinks.link(level, middle, matrix);
        check("one click links the whole matrix",
            linked == 6 && matrix.stream()
                .allMatch(member -> CrafterInputLinks.sharesInput(level, member, matrix)));

        int unlinked = CrafterInputLinks.unlink(level, middle, matrix);
        check("the next click gives every crafter its own input back",
            unlinked == 6 && !CrafterInputLinks.sharesInput(level, middle, matrix));

        Direction front = crafter.getValue(HorizontalKineticBlock.HORIZONTAL_FACING);
        check("the back of a crafter is the face opposite the one it faces",
            CrafterInputLinks.isBackFace(crafter, front.getOpposite())
                && !CrafterInputLinks.isBackFace(crafter, front));
    }

    /**
     * The description of an item is read from {@code <description id>.tooltip.summary}, and a block item takes that
     * id from its block - so text filed under "item." would never be found on a machine that is placed and picked up
     * as a block. This checks every item this mod describes against the language files that ship with it, in both
     * languages, because a missing key means no tooltip at all rather than a wrong one.
     */
    private static void itemDescriptions() {
        for (String lang : List.of("zh_cn", "en_us")) {
            JsonObject translations = maxcraftTranslations(lang);
            describe(translations, lang, MaxcraftItems.LARGE_PACKAGE_COMPONENT.get());
            describe(translations, lang, MaxcraftItems.CREATIVE_BLAZE_CAKE_INCOMPLETE.get());
            describe(translations, lang, MaxcraftBlocks.LARGE_PACKAGER_ITEM.get());
            describe(translations, lang, MaxcraftBlocks.LARGE_REPACKAGER_ITEM.get());
            describe(translations, lang, MaxcraftBlocks.EXTENDED_STOCK_TICKER_ITEM.get());
            describe(translations, lang, AllBlocks.PACKAGER.get()
                .asItem());
            describe(translations, lang, AllBlocks.REPACKAGER.get()
                .asItem());
            describe(translations, lang, AllBlocks.STOCK_TICKER.get()
                .asItem());
            describe(translations, lang, AllBlocks.FACTORY_GAUGE.get()
                .asItem());
        }
    }

    private static void describe(JsonObject translations, String lang, Item item) {
        String key = item.getDescriptionId() + ".tooltip.summary";
        check("the description of " + item.getDescriptionId() + " is written under " + key + " (" + lang + ")",
            translations.has(key));
    }

    private static JsonObject maxcraftTranslations(String lang) {
        String path = "/assets/maxcraft/lang/" + lang + ".json";
        try (InputStream in = PackageSystemSelfTest.class.getResourceAsStream(path)) {
            if (in == null) {
                Maxcraft.LOGGER.error("SELFTEST: {} is not on the class path", path);
                return new JsonObject();
            }
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                .getAsJsonObject();
        } catch (Exception e) {
            Maxcraft.LOGGER.error("SELFTEST: could not read {}", path, e);
            return new JsonObject();
        }
    }

    /** The new content exists and is wired to the right machines. */
    private static void registration() {
        check("the large package component is registered",
            MaxcraftItems.LARGE_PACKAGE_COMPONENT.get() != Items.AIR);

        check("the large packager block is registered", MaxcraftBlocks.LARGE_PACKAGER.get() != Blocks.AIR);
        check("the large repackager block is registered", MaxcraftBlocks.LARGE_REPACKAGER.get() != Blocks.AIR);
        check("the extended stock ticker is registered",
            MaxcraftBlocks.EXTENDED_STOCK_TICKER.get() != Blocks.AIR);

        check("the large packager has its own block entity type",
            MaxcraftBlocks.LARGE_PACKAGER.get()
                .getBlockEntityType() != AllBlocks.PACKAGER.get()
                    .getBlockEntityType());
        check("the large repackager has its own block entity type",
            MaxcraftBlocks.LARGE_REPACKAGER.get()
                .getBlockEntityType() != AllBlocks.REPACKAGER.get()
                    .getBlockEntityType());
        check("the extended stock ticker has its own block entity type",
            MaxcraftBlocks.EXTENDED_STOCK_TICKER.get()
                .getBlockEntityType() != AllBlocks.STOCK_TICKER.get()
                    .getBlockEntityType());

        check("the server config is loaded", MaxcraftConfig.SPEC.isLoaded());
        if (MaxcraftConfig.SPEC.isLoaded())
            check("the configured package size is the one in use",
                MaxcraftConfig.maxPackageStacks() == MaxcraftConfig.MAX_PACKAGE_STACKS.get());

        check("the large package component has a sequenced assembly recipe", hasRecipe("large_package_component"));
        check("the Easter egg's first step is a 30x30 grid of Blaze Cakes",
            isFullGridTurning("mechanical_crafting/creative_blaze_cake_incomplete", AllItems.BLAZE_CAKE.get(),
                MaxcraftItems.CREATIVE_BLAZE_CAKE_INCOMPLETE.get()));
        check("the Easter egg's second step is a 30x30 grid of the unfinished cake",
            isFullGridTurning("mechanical_crafting/creative_blaze_cake", MaxcraftItems.CREATIVE_BLAZE_CAKE_INCOMPLETE.get(),
                AllItems.CREATIVE_BLAZE_CAKE.get()));

        check("every advancement of this mod loaded",
            MaxcraftAdvancements.ALL.stream()
                .allMatch(advancement -> MaxcraftAdvancements.isPresent(serverLevel.getServer(), advancement)));
        check("the two secret advancements stay hidden",
            MaxcraftAdvancements.isHidden(serverLevel.getServer(), MaxcraftAdvancements.CREATIVE_BLAZE_CAKE)
                && MaxcraftAdvancements.isHidden(serverLevel.getServer(), MaxcraftAdvancements.EXCHANGE));
        check("the advancements hang in the order they are earned",
            hangsUnder(MaxcraftAdvancements.LARGE_PACKAGE, ResourceLocation.fromNamespaceAndPath("create", "packager"))
                && hangsUnder(MaxcraftAdvancements.MORE_KINDS, MaxcraftAdvancements.LARGE_PACKAGE)
                && hangsUnder(MaxcraftAdvancements.BRASS_GAUGE, MaxcraftAdvancements.MORE_KINDS)
                && hangsUnder(MaxcraftAdvancements.LARGE_CRAFTING, MaxcraftAdvancements.BRASS_GAUGE)
                && hangsUnder(MaxcraftAdvancements.CREATIVE_BLAZE_CAKE, MaxcraftAdvancements.BRASS_GAUGE)
                && hangsUnder(MaxcraftAdvancements.EXCHANGE, MaxcraftAdvancements.MORE_KINDS));

        // The Easter egg watcher hands whatever the crafter's own lookup returns straight back, so "nothing matched"
        // is a null it has to tolerate. A single item with no recipe of its own is the arrangement that says so.
        check("a crafter arrangement that matches nothing comes back as null",
            RecipeGridHandler.tryToApplyRecipe(serverLevel,
                new RecipeGridHandler.GroupedItems(new ItemStack(Items.DIRT))) == null);
    }

    /** True when the advancement hangs directly under the given parent. */
    private static boolean hangsUnder(ResourceLocation advancement, ResourceLocation parent) {
        return parent.equals(MaxcraftAdvancements.parentOf(serverLevel.getServer(), advancement));
    }

    private static boolean hasRecipe(String path) {
        return serverLevel != null && serverLevel.getRecipeManager()
            .byKey(ResourceLocation.fromNamespaceAndPath(Maxcraft.MOD_ID, path))
            .isPresent();
    }

    /**
     * The Easter egg's shape: a full 30x30 grid of one item turning into the next. Checks that the recipe really
     * loaded as 30x30 with 900 filled cells - a pattern over the ceiling would silently not load at all - that every
     * cell asks for the one ingredient, and that the result is what the chain expects.
     */
    private static boolean isFullGridTurning(String path, Item input, Item result) {
        if (serverLevel == null)
            return false;
        int size = GridSizeHolder.MAX_GRID_SIZE;
        return serverLevel.getRecipeManager()
            .byKey(ResourceLocation.fromNamespaceAndPath(Maxcraft.MOD_ID, path))
            .map(holder -> holder.value() instanceof MechanicalCraftingRecipe recipe
                && recipe.getWidth() == size
                && recipe.getHeight() == size
                && recipe.getIngredients()
                    .size() == size * size
                && recipe.getIngredients()
                    .stream()
                    .allMatch(ingredient -> !ingredient.isEmpty() && ingredient.test(new ItemStack(input)))
                && recipe.getResultItem(serverLevel.registryAccess())
                    .is(result))
            .orElse(false);
    }

    /**
     * One crafter placed on top of a finished row brings the rest of that row with it, and pays for every crafter it
     * places. Building an array is the mod's slowest chore, so this is the one that has to keep working.
     */
    private static void crafterRowFill() {
        ServerLevel level = serverLevel;
        if (level == null) {
            check("a server level is available for the crafter row test", false);
            return;
        }

        BlockState crafter = AllBlocks.MECHANICAL_CRAFTER.get()
            .defaultBlockState();
        BlockPos corner = new BlockPos(16, 100, 0);
        for (int x = 0; x <= 2; x++) {
            level.setBlockAndUpdate(corner.offset(x, 0, 0), crafter);
            level.setBlockAndUpdate(corner.offset(x, 1, 0), Blocks.AIR.defaultBlockState());
        }
        level.setBlockAndUpdate(corner.offset(4, 0, 0), crafter);
        level.setBlockAndUpdate(corner.offset(4, 1, 0), Blocks.AIR.defaultBlockState());

        FakePlayer player = FakePlayerFactory.getMinecraft(level);
        player.getAbilities().instabuild = false;
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(AllBlocks.MECHANICAL_CRAFTER.asItem(), 3));

        // The block the player is placing itself, then the row that should land with it.
        BlockPos above = corner.offset(1, 1, 0);
        level.setBlockAndUpdate(above, crafter);
        int placed = CrafterRowFill.fillRow(level, above, crafter, player);

        check("placing a crafter on top of a row fills the rest of that row",
            placed == 2
                && isCrafter(level, corner.offset(0, 1, 0))
                && isCrafter(level, corner.offset(2, 1, 0)));
        check("every crafter the fill placed was paid for", player.getMainHandItem()
            .getCount() == 1);
        check("a row that is already there is left alone", CrafterRowFill.fillRow(level, above, crafter, player) == 0);

        // A single crafter under the new one is not a row, and must not start one.
        level.setBlockAndUpdate(corner.offset(4, 1, 0), crafter);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(AllBlocks.MECHANICAL_CRAFTER.asItem(), 2));
        check("a lone crafter underneath does not fill anything",
            CrafterRowFill.fillRow(level, corner.offset(4, 1, 0), crafter, player) == 0);

        // The fill takes exactly what the row costs, out of the hand first and then out of the rest of the pack.
        for (int x = 0; x <= 2; x++)
            level.setBlockAndUpdate(corner.offset(x, 1, 0), Blocks.AIR.defaultBlockState());
        ItemStack spare = new ItemStack(AllBlocks.MECHANICAL_CRAFTER.asItem(), 4);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(AllBlocks.MECHANICAL_CRAFTER.asItem(), 1));
        player.getInventory()
            .setItem(10, spare);
        level.setBlockAndUpdate(above, crafter);
        check("the fill takes exactly what the row costs, hand first",
            CrafterRowFill.fillRow(level, above, crafter, player) == 2
                && player.getMainHandItem()
                    .isEmpty()
                && spare.getCount() == 3);
        player.getInventory()
            .setItem(10, ItemStack.EMPTY);

        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
    }

    /**
     * The row fill as the game runs it: use the item on the top of a row, event and placement and inventory and all.
     * A row the player cannot pay for whole is left alone, and a row that is paid for costs exactly what it looks
     * like it costs - one crafter per block, the block being placed included.
     */
    private static void crafterRowFillPays() {
        ServerLevel level = serverLevel;
        if (level == null) {
            check("a server level is available for the row payment test", false);
            return;
        }

        BlockState crafter = AllBlocks.MECHANICAL_CRAFTER.get()
            .defaultBlockState();
        BlockPos corner = new BlockPos(16, 100, 10);
        for (int x = 0; x <= 2; x++) {
            level.setBlockAndUpdate(corner.offset(x, 0, 0), crafter);
            level.setBlockAndUpdate(corner.offset(x, 1, 0), Blocks.AIR.defaultBlockState());
        }

        FakePlayer player = FakePlayerFactory.getMinecraft(level);
        player.getAbilities().instabuild = false;
        BlockPos top = corner.offset(1, 0, 0);

        // Two crafters for a row of three: the block being placed lands, and the rest of the row is not started.
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(AllBlocks.MECHANICAL_CRAFTER.asItem(), 2));
        useOnTop(player, top);
        CrafterRowFill.flushPending();
        check("a row that cannot be paid for is not started",
            player.getMainHandItem()
                .getCount() == 1
                && isCrafter(level, corner.offset(1, 1, 0))
                && !isCrafter(level, corner.offset(0, 1, 0))
                && !isCrafter(level, corner.offset(2, 1, 0)));

        // The same row again with enough to pay for it: three crafters, three blocks, an empty hand.
        for (int x = 0; x <= 2; x++)
            level.setBlockAndUpdate(corner.offset(x, 1, 0), Blocks.AIR.defaultBlockState());
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(AllBlocks.MECHANICAL_CRAFTER.asItem(), 3));
        useOnTop(player, top);
        CrafterRowFill.flushPending();
        ItemStack left = player.getMainHandItem();
        Maxcraft.LOGGER.info("SELFTEST: the paid row left {}{}{} in the world and {} crafters in the hand",
            isCrafter(level, corner.offset(0, 1, 0)) ? "X" : ".",
            isCrafter(level, corner.offset(1, 1, 0)) ? "X" : ".",
            isCrafter(level, corner.offset(2, 1, 0)) ? "X" : ".",
            left.getCount());
        check("a row that can be paid for lands whole",
            isCrafter(level, corner.offset(0, 1, 0))
                && isCrafter(level, corner.offset(1, 1, 0))
                && isCrafter(level, corner.offset(2, 1, 0)));
        check("... and costs one crafter per block", left.isEmpty());

        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
    }

    /** Uses the item in hand on the top face of a block, the way a player's click does. */
    private static void useOnTop(FakePlayer player, BlockPos pos) {
        player.getMainHandItem()
            .useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false)));
    }

    private static boolean isCrafter(ServerLevel level, BlockPos pos) {
        return AllBlocks.MECHANICAL_CRAFTER.has(level.getBlockState(pos));
    }

    private static void smallPackage(RegistryAccess registries) {
        ItemStackHandler handler = filled(9, Items.COPPER_INGOT, 64);
        ItemStack box = PackageItem.containing(handler);

        check("small package keeps Create's component",
            box.has(com.simibubi.create.AllDataComponents.PACKAGE_CONTENTS));
        check("small package does not need the bulk component", !PackageContents.hasBulkContents(box));
        check("small package contents survive a read", total(PackageItem.getContents(box)) == 9 * 64);
        check("small package contents survive a save", total(PackageItem.getContents(roundTripNbt(registries, box))) == 9 * 64);
        check("small package contents survive a packet", total(PackageItem.getContents(roundTripStream(registries, box))) == 9 * 64);
    }

    private static void oversizedPackage(RegistryAccess registries) {
        int slots = 300;
        ItemStackHandler handler = filled(slots, Items.IRON_INGOT, 64);
        ItemStack box = PackageItem.containing(handler);

        check("300 stack package is stored in bulk", PackageContents.hasBulkContents(box));
        check("300 stack package keeps a readable mirror",
            box.has(com.simibubi.create.AllDataComponents.PACKAGE_CONTENTS));

        ItemStackHandler read = PackageItem.getContents(box);
        check("300 stack package reads back every slot", read.getSlots() >= slots);
        check("300 stack package keeps every item", total(read) == slots * 64);

        ItemStackHandler fromNbt = PackageItem.getContents(roundTripNbt(registries, box));
        check("300 stack package survives a save", total(fromNbt) == slots * 64);
        check("300 stack package keeps all slots after a save", fromNbt.getSlots() >= slots);

        ItemStackHandler fromPacket = PackageItem.getContents(roundTripStream(registries, box));
        check("300 stack package survives a packet", total(fromPacket) == slots * 64);

        // A stack larger than an item's stack size must be split, never clamped.
        ItemStackHandler weird = new ItemStackHandler(1);
        weird.setStackInSlot(0, new ItemStack(Items.COPPER_INGOT, 1000));
        check("oversized stacks are split instead of clamped",
            total(PackageItem.getContents(PackageItem.containing(weird))) == 1000);
    }

    private static void repackagerMerge(RegistryAccess registries) {
        PackageRepackageHelper helper = new PackageRepackageHelper();
        int orderId = 4242;
        int normalFragments = 9;
        int oversizedSlots = 40;

        for (int fragment = 0; fragment < normalFragments; fragment++) {
            ItemStack box = PackageItem.containing(filled(9, Items.COPPER_INGOT, 64));
            PackageItem.addAddress(box, "selftest");
            PackageItem.setOrder(box, orderId, 0, true, fragment, false, null);
            helper.addPackageFragment(box);
        }

        ItemStack big = PackageItem.containing(filled(oversizedSlots, Items.GOLD_INGOT, 64));
        PackageItem.addAddress(big, "selftest");
        PackageItem.setOrder(big, orderId, 0, true, normalFragments, true, null);
        check("oversized fragment completes the order",
            helper.addPackageFragment(big) == orderId);

        long expected = (long) normalFragments * 9 * 64 + (long) oversizedSlots * 64;
        long expectedSlots = (long) normalFragments * 9 + oversizedSlots;
        int limit = MaxcraftConfig.maxPackageStacks();
        List<BigItemStack> merged = helper.repack(orderId, RandomSource.create(1));

        check("the order is split into as few packages as the limit allows ("
                + merged.size() + " for " + expectedSlots + " stacks at " + limit + ")",
            merged.size() == (expectedSlots + limit - 1) / limit);

        long total = 0;
        boolean withinLimit = true;
        for (BigItemStack entry : merged) {
            int slots = PackageItem.getContents(entry.stack).getSlots();
            withinLimit &= slots <= limit;
            total += total(PackageItem.getContents(entry.stack));
        }
        check("no merged package exceeds the configured limit", withinLimit);
        check("the merge keeps every item (expected " + expected + ", got " + total + ")", total == expected);

        if (!merged.isEmpty()) {
            ItemStack result = merged.get(0).stack;
            check("the merged package exceeds nine stacks", PackageItem.getContents(result).getSlots() > 9);
            check("the merged package keeps its address", "selftest".equals(PackageItem.getAddress(result)));
            long savedTotal = 0;
            for (BigItemStack entry : merged)
                savedTotal += total(PackageItem.getContents(roundTripNbt(registries, entry.stack)));
            check("every merged package survives a save (expected " + expected + ", got " + savedTotal + ")",
                savedTotal == expected);
        }
    }

    /**
     * Mirrors what {@code RepackagerBlockEntityMixin} does: the helper is handed every fragment in the inventory
     * before the module's own scan walks over the same packages again. The duplicate fragment index stands in for a
     * fragment that upstream would have deleted without merging.
     */
    private static void repackagerPreCollection() {
        PackageRepackageHelper helper = new PackageRepackageHelper();
        RepackageHelperAccess access = (RepackageHelperAccess) helper;
        int orderId = 77;

        List<ItemStack> fragments = new ArrayList<>();
        for (int fragment = 0; fragment < 3; fragment++)
            fragments.add(fragment(orderId, fragment, fragment == 2));

        ItemStack duplicate = fragment(orderId, 1, false);
        fragments.add(duplicate);

        for (ItemStack box : fragments)
            access.maxcraft$preCollect(box);
        access.maxcraft$beginPreCollect();

        // The upstream scan finds the order complete on the third package and stops; the rest are already collected.
        for (ItemStack box : fragments)
            helper.addPackageFragment(box);

        long expected = 4L * 9 * 64;
        List<BigItemStack> merged = helper.repack(orderId, RandomSource.create(1));
        long total = 0;
        for (BigItemStack entry : merged)
            total += total(PackageItem.getContents(entry.stack));

        check("pre-collected fragments are not counted twice (expected " + expected + ", got " + total + ")",
            total == expected);
        check("the duplicate fragment is merged rather than voided", merged.size() == 1);
    }

    /**
     * Places both packagers over a chest of 27 distinct stacks and lets them run one cycle.
     *
     * <p>This walks the whole path: the block, its block entity type and capability, the mixin that reads the block
     * to size the package, and Create's own packaging loop.
     */
    private static void packagerThreads(RegistryAccess registries) {
        ServerLevel level = serverLevel;
        if (level == null) {
            check("a server level is available for the packager test", false);
            return;
        }

        ItemStack large = runPackager(level, new BlockPos(4, 100, 0), MaxcraftBlocks.LARGE_PACKAGER.get()
            .defaultBlockState(), 27);
        check("the large packager produces a package", !large.isEmpty());
        check("the large packager fills 27 stacks", PackageItem.getContents(large)
            .getSlots() == 27);
        check("the large packager keeps every item", total(PackageItem.getContents(large)) == 27 * 64);
        check("the large package is an ordinary package item", PackageItem.isPackage(large));
        check("the large package survives a save",
            total(PackageItem.getContents(roundTripNbt(registries, large))) == 27 * 64);

        ItemStack normal = runPackager(level, new BlockPos(-4, 100, 0), AllBlocks.PACKAGER.get()
            .defaultBlockState(), 27);
        check("the ordinary packager still fills 9 stacks", PackageItem.getContents(normal)
            .getSlots() == 9);
        check("the ordinary packager is untouched", total(PackageItem.getContents(normal)) == 9 * 64);
    }

    /** Fills a chest with {@code count} distinct stacks, places a packager above it and runs one packaging cycle. */
    private static ItemStack runPackager(ServerLevel level, BlockPos pos, BlockState state, int count) {
        // Clear first so a repeated run never inherits a machine that already produced its package.
        level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(pos.below(), Blocks.AIR.defaultBlockState());

        level.setBlockAndUpdate(pos.below(), Blocks.CHEST.defaultBlockState());
        level.setBlockAndUpdate(pos, state.setValue(PackagerBlock.FACING, Direction.UP)
            .setValue(PackagerBlock.POWERED, false));

        IItemHandler chest = level.getCapability(Capabilities.ItemHandler.BLOCK, pos.below(), null);
        if (chest == null) {
            check("the packager test found a chest inventory", false);
            return ItemStack.EMPTY;
        }
        for (int slot = 0; slot < count; slot++) {
            ItemStack stack = new ItemStack(Items.STONE, 64);
            stack.set(DataComponents.CUSTOM_NAME, Component.literal("selftest_" + slot));
            chest.insertItem(slot, stack, false);
        }

        if (!(level.getBlockEntity(pos) instanceof PackagerBlockEntity packager)) {
            check("the packager test found a packager block entity", false);
            return ItemStack.EMPTY;
        }

        packager.tick();
        packager.attemptToSend(null);
        return packager.heldBox;
    }

    /**
     * A partly delivered order looks complete to Create - every fragment index is present and the last one is marked
     * final - so the plain re-packager merges it straight away. The large one compares the delivery against what the
     * order asked for and waits instead.
     */
    private static void largeRepackagerStrictness() {
        int requested = 1000;
        int orderId = 11;

        // One package holding 512 of the 1000 that were asked for, marked final: index-complete, content-incomplete.
        ItemStack partial = PackageItem.containing(filled(8, Items.COPPER_INGOT, 64));
        PackageItem.addAddress(partial, "strict-test");
        PackageItem.setOrder(partial, orderId, 0, true, 0, true,
            PackageOrderWithCrafts.simple(List.of(new BigItemStack(new ItemStack(Items.COPPER_INGOT), requested))));

        PackageRepackageHelper plain = new PackageRepackageHelper();
        check("a plain re-packager accepts a partly delivered order",
            plain.addPackageFragment(partial) == orderId);

        PackageRepackageHelper strict = new PackageRepackageHelper();
        ((RepackageHelperAccess) strict).maxcraft$setStrict(true);
        check("the large re-packager refuses a partly delivered order",
            strict.addPackageFragment(partial) == -1);

        // The rest arrives: now the delivery covers the order.
        ItemStack remainder = PackageItem.containing(filled(8, Items.COPPER_INGOT, 64));
        PackageItem.setOrder(remainder, orderId, 0, true, 1, true, null);
        check("the large re-packager accepts the completed order",
            strict.addPackageFragment(remainder) == orderId);

        List<BigItemStack> merged = strict.repack(orderId, RandomSource.create(1));
        check("the completed order merges into exactly one package", merged.size() == 1);

        long total = 0;
        for (BigItemStack entry : merged)
            total += total(PackageItem.getContents(entry.stack));
        check("the merge keeps every delivered item (expected 1024, got " + total + ")", total == 1024);

        // An order with no context - a redstone request - has nothing to compare against and is not held back.
        ItemStack noContext = fragment(77, 0, true);
        PackageRepackageHelper strictNoContext = new PackageRepackageHelper();
        ((RepackageHelperAccess) strictNoContext).maxcraft$setStrict(true);
        check("an order without a context still merges",
            strictNoContext.addPackageFragment(noContext) == 77);
    }

    /**
     * The grid size is panel data on Create's own gauge. There is no separate machine: a panel with a size above
     * Create's 3x3 is what searches for larger recipes and sends a shaped pattern.
     */
    private static void factoryGaugeGridSize() {
        ServerLevel level = serverLevel;
        if (level == null) {
            check("a server level is available for the factory gauge test", false);
            return;
        }

        BlockPos pos = new BlockPos(-4, 100, 16);
        level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(pos, AllBlocks.FACTORY_GAUGE.get()
            .defaultBlockState());

        if (!(level.getBlockEntity(pos) instanceof FactoryPanelBlockEntity gauge)) {
            check("the factory gauge has a block entity", false);
            return;
        }
        FactoryPanelBehaviour behaviour = gauge.panels.get(FactoryPanelBlock.PanelSlot.TOP_LEFT);
        if (behaviour == null) {
            check("the factory gauge has panels", false);
            return;
        }
        GridSizeHolder holder = (GridSizeHolder) behaviour;

        check("a fresh panel uses Create's 3x3", holder.maxcraft$gridSize() == GridSizeHolder.MIN_GRID_SIZE);
        check("a fresh panel is not extended", !holder.maxcraft$isExtended());
        check("a fresh panel has no stored size", !holder.maxcraft$hasStoredSize());

        holder.maxcraft$setGridSize(GridSizeHolder.DEFAULT_EXTENDED_GRID_SIZE);
        check("the panel is extended once it is given a larger grid", holder.maxcraft$isExtended());
        check("the size is stored on the panel", holder.maxcraft$hasStoredSize());

        holder.maxcraft$setGridSize(1000);
        check("the grid size is clamped at the top",
            holder.maxcraft$gridSize() == GridSizeHolder.MAX_GRID_SIZE);
        holder.maxcraft$setGridSize(1);
        check("the grid size is clamped at the bottom",
            holder.maxcraft$gridSize() == GridSizeHolder.MIN_GRID_SIZE);

        holder.maxcraft$setGridSize(12);
        check("the grid size can be set", holder.maxcraft$gridSize() == 12);

        // The sizes are kept on the block entity, not on the panels: Create only writes a panel's own data once the
        // panel is in use, which is exactly what a gauge placed from an item is not.
        CompoundTag saved = gauge.saveWithFullMetadata(level.registryAccess());
        check("the grid size is saved with the gauge",
            saved.getCompound(GaugePanelSizes.KEY)
                .getInt(FactoryPanelBlock.PanelSlot.TOP_LEFT.getSerializedName()) == 12);

        holder.maxcraft$setGridSize(3);
        gauge.loadWithComponents(saved, level.registryAccess());
        check("the grid size is read back onto the panel", holder.maxcraft$gridSize() == 12);

        // A gauge's four panels are four machines sharing a block: one panel's size is not the gauge's.
        CompoundTag one = new CompoundTag();
        one.putInt(FactoryPanelBlock.PanelSlot.BOTTOM_RIGHT.getSerializedName(), 7);
        GaugePanelSizes.apply(gauge, one);
        GridSizeHolder bottomRight =
            (GridSizeHolder) gauge.panels.get(FactoryPanelBlock.PanelSlot.BOTTOM_RIGHT);
        check("a size belongs to one panel, not to the whole gauge",
            bottomRight.maxcraft$gridSize() == 7 && holder.maxcraft$gridSize() == 12);
        check("an untouched panel has no stored size of its own",
            !((GridSizeHolder) gauge.panels.get(FactoryPanelBlock.PanelSlot.TOP_RIGHT)).maxcraft$hasStoredSize());

        // A gauge with a large grid drops a marked gauge, so it can be put back down as what it was.
        List<ItemStack> drops = dropsOf(level, pos);
        check("an extended gauge drops one item", drops.size() == 1);
        ItemStack drop = drops.isEmpty() ? ItemStack.EMPTY : drops.get(0);
        check("the drop is still a factory gauge", drop.is(AllBlocks.FACTORY_GAUGE.asItem()));
        CompoundTag dropSizes = drop.get(MaxcraftDataComponents.GAUGE_GRID_SIZES.get());
        check("the drop carries the grid size", GaugePanelSizes.sizeOf(dropSizes) == 12);
        check("the drop is marked as an extended gauge",
            drop.get(DataComponents.CUSTOM_MODEL_DATA) != null
                && drop.get(DataComponents.CUSTOM_MODEL_DATA)
                    .value() == ExtendedGaugeItem.MODEL_DATA);
        check("the drop is bare, so it has to be bound to a network again",
            !drop.has(DataComponents.BLOCK_ENTITY_DATA));

        // Two gauges with the same kind of panel come back as the same item, so a chest of them stacks.
        holder.maxcraft$setGridSize(GridSizeHolder.MIN_GRID_SIZE);
        bottomRight.maxcraft$setGridSize(12);
        List<ItemStack> otherDrops = dropsOf(level, pos);
        check("gauges whose panels only differ by slot drop the same item",
            otherDrops.size() == 1 && ItemStack.isSameItemSameComponents(drop, otherDrops.get(0)));
        check("that item stacks", ItemStack.isSameItemSameComponents(drop, otherDrops.get(0))
            && drop.getMaxStackSize() > 1);

        // ...and an ordinary gauge is left entirely to Create's own table, once every panel is back to 3x3.
        holder.maxcraft$setGridSize(GridSizeHolder.MIN_GRID_SIZE);
        bottomRight.maxcraft$setGridSize(GridSizeHolder.MIN_GRID_SIZE);
        List<ItemStack> plain = dropsOf(level, pos);
        check("an ordinary gauge drops plainly", plain.size() == 1
            && plain.get(0)
                .get(MaxcraftDataComponents.GAUGE_GRID_SIZES.get()) == null);
    }

    /** The items a block would drop, worked out the same way the game works them out. */
    private static List<ItemStack> dropsOf(ServerLevel level, BlockPos pos) {
        LootParams.Builder params = new LootParams.Builder(level)
            .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos))
            .withParameter(LootContextParams.TOOL, ItemStack.EMPTY)
            .withOptionalParameter(LootContextParams.BLOCK_ENTITY, level.getBlockEntity(pos));
        return level.getBlockState(pos)
            .getDrops(params);
    }

    /**
     * A recipe smaller than the machine it is sent to keeps its own rows and columns: a 3x3 recipe on a 5x5 machine
     * fills the top left 3x3 and leaves the rest of the grid empty.
     */
    private static void patternLayout() {
        List<BigItemStack> recipe = new ArrayList<>();
        for (int i = 0; i < 9; i++)
            recipe.add(new BigItemStack(new ItemStack(Items.OAK_LOG, i + 1), 1));

        List<BigItemStack> laid = CraftingPattern.inGrid(recipe, 3, 3, 5);
        check("a 3x3 recipe in a 5x5 grid is 25 cells", laid.size() == 25);

        boolean topLeft = true;
        for (int row = 0; row < 5; row++) {
            for (int col = 0; col < 5; col++) {
                BigItemStack cell = laid.get(row * 5 + col);
                boolean inside = row < 3 && col < 3;
                if (inside) {
                    topLeft &= cell == recipe.get(row * 3 + col);
                } else {
                    topLeft &= cell.stack.isEmpty();
                }
            }
        }
        check("the recipe lands in the top left and nothing else is filled", topLeft);

        // A recipe that does not fit the grid is left at its own size rather than squashed into it.
        List<BigItemStack> big = new ArrayList<>();
        for (int i = 0; i < 12; i++)
            big.add(new BigItemStack(new ItemStack(Items.OAK_LOG, i + 1), 1));
        check("a recipe wider than the grid keeps its own shape",
            CraftingPattern.inGrid(big, 4, 3, 3).size() == 12);
        check("a recipe the same size as the grid is unchanged",
            CraftingPattern.inGrid(recipe, 3, 3, 3).size() == 9);
    }

    /**
     * Using a gauge item on a gauge that already has a panel adds one more panel - and only the panel that item
     * added is the extended one. This is how a player puts a second panel on a gauge they have already built, and it
     * goes through the block's own use rather than through placing a block.
     */
    private static void gaugeItemUsedOnExistingGauge() {
        ServerLevel level = serverLevel;
        if (level == null) {
            check("a server level is available for the second panel test", false);
            return;
        }

        BlockPos pos = new BlockPos(-8, 100, 16);
        level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(pos, AllBlocks.FACTORY_GAUGE.get()
            .defaultBlockState());

        if (!(level.getBlockEntity(pos) instanceof FactoryPanelBlockEntity gauge)) {
            check("the second gauge has a block entity", false);
            return;
        }

        FakePlayer player = FakePlayerFactory.getMinecraft(level);
        BlockState state = level.getBlockState(pos);

        // An ordinary gauge item, tuned the way Create wants one, adds an ordinary panel.
        ItemStack plain = AllBlocks.FACTORY_GAUGE.asStack();
        plain.set(DataComponents.BLOCK_ENTITY_DATA, CustomData.of(tunedGaugeTag()));
        level.getBlockState(pos)
            .useItemOn(plain, level, player, InteractionHand.MAIN_HAND,
                gaugeHit(pos, state, FactoryPanelBlock.PanelSlot.TOP_LEFT));
        check("a gauge item adds a panel to a gauge that is already there", gauge.activePanels() == 1);

        // The extended one, on another slot, adds the extended panel.
        ItemStack extended = ExtendedGaugeItem.marked(6);
        extended.set(DataComponents.BLOCK_ENTITY_DATA, CustomData.of(tunedGaugeTag()));
        level.getBlockState(pos)
            .useItemOn(extended, level, player, InteractionHand.MAIN_HAND,
                gaugeHit(pos, state, FactoryPanelBlock.PanelSlot.BOTTOM_RIGHT));
        check("an extended gauge item adds a second panel", gauge.activePanels() == 2);

        GridSizeHolder added = (GridSizeHolder) gauge.panels.get(FactoryPanelBlock.PanelSlot.BOTTOM_RIGHT);
        GridSizeHolder existing = (GridSizeHolder) gauge.panels.get(FactoryPanelBlock.PanelSlot.TOP_LEFT);
        check("the panel the extended item added is extended", added.maxcraft$gridSize() == 6);
        check("the panel that was already on the gauge is untouched",
            existing.maxcraft$gridSize() == GridSizeHolder.MIN_GRID_SIZE);

        // Using it on a slot that already has a panel changes nothing at all - the panel is left as it was and the
        // item is not used up, rather than the panel being turned into an extended one out of nowhere.
        ItemStack onExisting = ExtendedGaugeItem.marked(8);
        onExisting.set(DataComponents.BLOCK_ENTITY_DATA, CustomData.of(tunedGaugeTag()));
        int before = existing.maxcraft$gridSize();
        int held = onExisting.getCount();
        level.getBlockState(pos)
            .useItemOn(onExisting, level, player, InteractionHand.MAIN_HAND,
                gaugeHit(pos, state, FactoryPanelBlock.PanelSlot.TOP_LEFT));
        check("an extended item used on a panel already there changes nothing",
            existing.maxcraft$gridSize() == before);
        check("and it is not used up", onExisting.getCount() == held);
        check("the other panel is untouched", added.maxcraft$gridSize() == 6);

        // Putting the same item down as a block is the other half of the same story, and has to end the same way.
        BlockPos placed = new BlockPos(-8, 100, 20);
        level.setBlockAndUpdate(placed, Blocks.AIR.defaultBlockState());
        BlockState placedState = AllBlocks.FACTORY_GAUGE.get()
            .defaultBlockState();
        level.setBlockAndUpdate(placed, placedState);

        ItemStack toPlace = ExtendedGaugeItem.marked(7);
        toPlace.set(DataComponents.BLOCK_ENTITY_DATA, CustomData.of(tunedGaugeTag()));
        level.getBlockState(placed)
            .getBlock()
            .setPlacedBy(level, placed, placedState, player, toPlace);

        if (!(level.getBlockEntity(placed) instanceof FactoryPanelBlockEntity placedGauge)) {
            check("the gauge put down has a block entity", false);
            return;
        }
        check("putting an extended gauge down makes its panel extended",
            placedGauge.activePanels() == 1 && placedGauge.panels.values()
                .stream()
                .anyMatch(behaviour -> behaviour.isActive()
                    && ((GridSizeHolder) behaviour).maxcraft$gridSize() == 7));

        // The third way Create adds a panel, and the one a player hits as soon as the gauge already has one: the
        // click falls through to item placement, and getStateForPlacement adds the panel while deciding what it
        // would place. Nothing is put down - but the panel is there, and has to come out extended.
        BlockPos fallThrough = new BlockPos(-8, 100, 24);
        level.setBlockAndUpdate(fallThrough, Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(fallThrough.below(), Blocks.STONE.defaultBlockState());
        BlockState fallThroughState = AllBlocks.FACTORY_GAUGE.get()
            .defaultBlockState();
        level.setBlockAndUpdate(fallThrough, fallThroughState);

        if (!(level.getBlockEntity(fallThrough) instanceof FactoryPanelBlockEntity secondGauge)) {
            check("the fall-through gauge has a block entity", false);
            return;
        }

        ItemStack fallThroughItem = ExtendedGaugeItem.marked(9);
        fallThroughItem.set(DataComponents.BLOCK_ENTITY_DATA, CustomData.of(tunedGaugeTag()));
        // Called the way Create's own placement would: the block is not put down, but deciding what to put down is
        // what adds the panel.
        AllBlocks.FACTORY_GAUGE.get()
            .getStateForPlacement(new BlockPlaceContext(player, InteractionHand.MAIN_HAND, fallThroughItem,
                gaugeHit(fallThrough, fallThroughState, FactoryPanelBlock.PanelSlot.BOTTOM_LEFT)));

        check("a gauge item that falls through to placement still extends its panel",
            secondGauge.activePanels() == 1 && secondGauge.panels.values()
                .stream()
                .anyMatch(behaviour -> behaviour.isActive()
                    && ((GridSizeHolder) behaviour).maxcraft$gridSize() == 9));
    }

    /**
     * A gauge item the way Create expects to be given one: it has to carry block entity data, and the network in it
     * is what the new panel is put on.
     */
    private static CompoundTag tunedGaugeTag() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Freq", UUID.randomUUID());
        tag.putString("id", "create:factory_panel");
        return tag;
    }

    /** Where in the block a click has to land for Create to pick this panel. */
    private static BlockHitResult gaugeHit(BlockPos pos, BlockState state, FactoryPanelBlock.PanelSlot slot) {
        for (double x = 0.05; x < 1.0; x += 0.1) {
            for (double z = 0.05; z < 1.0; z += 0.1) {
                Vec3 location = new Vec3(pos.getX() + x, pos.getY() + 0.5, pos.getZ() + z);
                if (FactoryPanelBlock.getTargetedSlot(pos, state, location) == slot)
                    return new BlockHitResult(location, Direction.UP, pos, false);
            }
        }
        return new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
    }

    /** The packager's package size is the server config's, not a constant baked into the machine. */
    private static void packageSizeLimit() {
        ServerLevel level = serverLevel;
        if (level == null) {
            check("a server level is available for the package size test", false);
            return;
        }

        BlockPos pos = new BlockPos(0, 100, 16);
        level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(pos.below(), Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(pos.below()
            .east(), Blocks.AIR.defaultBlockState());

        level.setBlockAndUpdate(pos.below(), Blocks.CHEST.defaultBlockState());
        level.setBlockAndUpdate(pos.below()
            .east(), Blocks.CHEST.defaultBlockState());
        level.setBlockAndUpdate(pos, MaxcraftBlocks.LARGE_PACKAGER.get()
            .defaultBlockState()
            .setValue(PackagerBlock.FACING, Direction.UP)
            .setValue(PackagerBlock.POWERED, false)
            .setValue(PackagerBlock.LINKED, false));

        IItemHandler chest = level.getCapability(Capabilities.ItemHandler.BLOCK, pos.below(), null);
        if (chest == null) {
            check("the package size test found a chest", false);
            return;
        }
        for (int slot = 0; slot < chest.getSlots(); slot++) {
            ItemStack stack = new ItemStack(Items.STONE, 64);
            stack.set(DataComponents.CUSTOM_NAME, Component.literal("size_" + slot));
            chest.insertItem(slot, stack, false);
        }

        if (!(level.getBlockEntity(pos) instanceof LargePackagerBlockEntity packager)) {
            check("the package size test found a large packager", false);
            return;
        }

        // Turn the config down for a moment: the machine has to follow it rather than any built in constant.
        int original = MaxcraftConfig.maxPackageStacks();
        int probe = 12;
        try {
            MaxcraftConfig.MAX_PACKAGE_STACKS.set(probe);
            check("the config change is visible to the machine", MaxcraftConfig.maxPackageStacks() == probe);

            packager.tick();
            packager.attemptToSend(null);
            int slots = PackageItem.getContents(packager.heldBox)
                .getSlots();
            check("the large packager fills the configured number of stacks (" + slots + " should be " + probe + ")",
                slots == probe);
            check("the package is an ordinary package", PackageItem.isPackage(packager.heldBox));
        } finally {
            MaxcraftConfig.MAX_PACKAGE_STACKS.set(original);
        }

        check("the config is back where it started", MaxcraftConfig.maxPackageStacks() == original);
    }

    /** Upgrades a packager in place and checks that its state, its box and its address came along. */
    private static void largePackagerUpgrade() {
        ServerLevel level = serverLevel;
        if (level == null) {
            check("a server level is available for the packager upgrade test", false);
            return;
        }

        BlockPos pos = new BlockPos(4, 100, 12);
        level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(pos, AllBlocks.PACKAGER.get()
            .defaultBlockState()
            .setValue(PackagerBlock.FACING, Direction.NORTH)
            .setValue(PackagerBlock.POWERED, true)
            .setValue(PackagerBlock.LINKED, true));

        if (!(level.getBlockEntity(pos) instanceof PackagerBlockEntity packager)) {
            check("the upgrade test found a packager", false);
            return;
        }

        ItemStack held = PackageItem.containing(filled(9, Items.COPPER_INGOT, 64));
        PackageItem.addAddress(held, "upgrade-test");
        packager.heldBox = held;
        packager.signBasedAddress = "sign-address";
        packager.setChanged();

        check("a packager converts into a large packager", LargePackagerBlock.convert(level, pos));
        check("the converted block is the large packager",
            level.getBlockState(pos)
                .getBlock() == MaxcraftBlocks.LARGE_PACKAGER.get());
        check("the upgrade keeps the facing",
            level.getBlockState(pos)
                .getValue(PackagerBlock.FACING) == Direction.NORTH);
        check("the upgrade keeps powered and linked",
            level.getBlockState(pos)
                .getValue(PackagerBlock.POWERED)
                && level.getBlockState(pos)
                    .getValue(PackagerBlock.LINKED));

        if (level.getBlockEntity(pos) instanceof LargePackagerBlockEntity upgraded) {
            check("the upgrade keeps the block entity type",
                upgraded.getType() == MaxcraftBlockEntityTypes.LARGE_PACKAGER.get());
            check("the upgrade keeps the box it was holding",
                PackageItem.isPackage(upgraded.heldBox)
                    && "upgrade-test".equals(PackageItem.getAddress(upgraded.heldBox)));
            check("the upgrade keeps the sign address", "sign-address".equals(upgraded.signBasedAddress));
        } else {
            check("the upgraded packager block entity exists", false);
        }

        check("a large packager is not converted again", !LargePackagerBlock.convert(level, pos));

        BlockPos repackagerPos = new BlockPos(8, 100, 12);
        level.setBlockAndUpdate(repackagerPos, Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(repackagerPos, AllBlocks.REPACKAGER.get()
            .defaultBlockState()
            .setValue(PackagerBlock.FACING, Direction.NORTH));
        check("a re-packager is not turned into a large packager",
            !LargePackagerBlock.convert(level, repackagerPos));
        check("the re-packager is still a re-packager",
            level.getBlockState(repackagerPos)
                .getBlock() == AllBlocks.REPACKAGER.get());

        check("a re-packager converts into a large re-packager", LargeRepackagerBlock.convert(level, repackagerPos));
        check("the converted block is the large re-packager",
            level.getBlockState(repackagerPos)
                .getBlock() == MaxcraftBlocks.LARGE_REPACKAGER.get());
        check("the upgrade keeps the re-packager's facing",
            level.getBlockState(repackagerPos)
                .getValue(PackagerBlock.FACING) == Direction.NORTH);
        check("a large re-packager is not converted again", !LargeRepackagerBlock.convert(level, repackagerPos));
    }

    /** Places a stock ticker on a network, upgrades it, and checks that everything came along. */
    private static void stockTickerConversion() {
        ServerLevel level = serverLevel;
        if (level == null) {
            check("a server level is available for the conversion test", false);
            return;
        }

        BlockPos pos = new BlockPos(0, 100, 8);
        level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(pos, AllBlocks.STOCK_TICKER.get()
            .defaultBlockState()
            .setValue(StockTickerBlock.FACING, Direction.NORTH));

        if (!(level.getBlockEntity(pos) instanceof StockTickerBlockEntity ticker)) {
            check("the conversion test found a stock ticker", false);
            return;
        }

        check("a plain ticker keeps Create's 9 order types",
            ExtendedStockTickerBlockEntity.orderLimit(ticker, 9) == 9);

        UUID network = UUID.randomUUID();
        ticker.behaviour.freqId = network;
        ticker.setChanged();

        check("a stock ticker converts", ExtendedStockTickerBlock.convert(level, pos));
        check("the converted block is the extended stock ticker",
            level.getBlockState(pos)
                .getBlock() == MaxcraftBlocks.EXTENDED_STOCK_TICKER.get());
        check("the conversion keeps the facing",
            level.getBlockState(pos)
                .getValue(StockTickerBlock.FACING) == Direction.NORTH);

        if (level.getBlockEntity(pos) instanceof ExtendedStockTickerBlockEntity extended) {
            check("the conversion keeps the configured network", network.equals(extended.behaviour.freqId));
            check("the conversion keeps the block entity type",
                extended.getType() == MaxcraftBlockEntityTypes.EXTENDED_STOCK_TICKER.get());
            check("an extended ticker accepts 27 order types",
                ExtendedStockTickerBlockEntity.orderLimit(extended, 9) == ExtendedStockTickerBlockEntity.ORDER_LIMIT);
        } else {
            check("the converted block entity exists", false);
        }

        check("an extended ticker is not converted a second time", !ExtendedStockTickerBlock.convert(level, pos));
    }

    private static ItemStack fragment(int orderId, int index, boolean isFinal) {
        ItemStack box = PackageItem.containing(filled(9, Items.IRON_INGOT, 64));
        PackageItem.addAddress(box, "selftest");
        PackageItem.setOrder(box, orderId, 0, true, index, isFinal, null);
        return box;
    }

    //

    private static ItemStackHandler filled(int slots, Item item, int count) {
        ItemStackHandler handler = new ItemStackHandler(slots);
        for (int slot = 0; slot < slots; slot++)
            handler.setStackInSlot(slot, new ItemStack(item, count));
        return handler;
    }

    private static int total(ItemStackHandler handler) {
        int sum = 0;
        for (int slot = 0; slot < handler.getSlots(); slot++)
            sum += handler.getStackInSlot(slot).getCount();
        return sum;
    }

    private static ItemStack roundTripNbt(HolderLookup.Provider registries, ItemStack stack) {
        Tag tag = stack.save(registries);
        return ItemStack.parse(registries, tag).orElse(ItemStack.EMPTY);
    }

    private static ItemStack roundTripStream(RegistryAccess registries, ItemStack stack) {
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), registries);
        ItemStack.STREAM_CODEC.encode(buffer, stack);
        return ItemStack.STREAM_CODEC.decode(buffer);
    }

    private static void check(String what, boolean ok) {
        checks++;
        if (ok) {
            Maxcraft.LOGGER.info("SELFTEST ok   - {}", what);
            return;
        }
        failures++;
        Maxcraft.LOGGER.error("SELFTEST FAIL - {}", what);
    }
}
