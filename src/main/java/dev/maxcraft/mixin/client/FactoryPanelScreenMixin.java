package dev.maxcraft.mixin.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;

import com.simibubi.create.AllRecipeTypes;
import com.simibubi.create.content.kinetics.crafter.MechanicalCraftingRecipe;
import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBehaviour;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelConnection;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelScreen;
import com.simibubi.create.foundation.gui.AllGuiTextures;
import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.foundation.gui.widget.IconButton;
import com.simibubi.create.foundation.gui.widget.ScrollInput;
import com.simibubi.create.foundation.utility.CreateLang;

import dev.maxcraft.Maxcraft;
import dev.maxcraft.content.logistics.GridSizeHolder;
import dev.maxcraft.logistics.CraftingPattern;
import dev.maxcraft.network.GridSizePacket;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Teaches the Extended Factory Gauge's panels to work with recipes larger than 3x3.
 *
 * <p>Create bakes the 3x3 in three places on this screen: the recipe lookup only asks for vanilla crafting recipes
 * (so mechanical crafting is invisible to it), the pattern is padded to nine entries, and the preview lays slots out
 * as {@code slot % 3}. Panels on a plain Factory Gauge are left exactly as they were - every hook below returns
 * early unless the panel belongs to an extended gauge.
 *
 * <p>The stored pattern is a flat list, so the panel's grid size doubles as the recipe's width. The preview scales
 * its cells to whatever fits the panel, which is why any size from 3x3 to 30x30 can be shown.
 */
@Mixin(FactoryPanelScreen.class)
public abstract class FactoryPanelScreenMixin {

    @Shadow
    private FactoryPanelBehaviour behaviour;

    @Shadow
    private boolean restocker;

    /** True while the panel shows the ingredients of a recipe instead of the materials wired into it. */
    @Shadow
    private boolean craftingActive;

    @Shadow
    private BigItemStack outputConfig;

    @Shadow
    private List<BigItemStack> inputConfig;

    @Shadow
    private CraftingRecipe availableCraftingRecipe;

    @Shadow
    private com.simibubi.create.foundation.gui.widget.IconButton activateCraftingButton;

    /**
     * The address box is placed at {@code (guiLeft + 36, guiTop + windowHeight - 51)} by Create's own init, and it
     * is the only handle this mixin has on the window position: {@code guiLeft} and {@code guiTop} live in catnip's
     * AbstractSimiScreen, and a shadow can only resolve members declared in the target class itself.
     */
    @Shadow
    private com.simibubi.create.content.logistics.AddressEditBox addressBox;

    @Unique
    private static final int MAXCRAFT_ADDRESS_OFFSET_X = 36;

    @Unique
    private static final int MAXCRAFT_ADDRESS_OFFSET_Y = 51;

    /** One page of the input grid holds Create's own 3x3, so the nine slots on screen stay exactly as they were. */
    @Unique
    private static final int MAXCRAFT_SLOTS_PER_PAGE = 9;

    /** Which page of the input grid is on screen. Deliberately not persisted: a reopened panel starts over. */
    @Unique
    private int maxcraft$page;

    /**
     * The recipe's own grid, cell for cell, before it is laid into the panel's grid.
     *
     * <p>This is what the hover preview shows: the shape the machine at the far end actually needs, without the empty
     * cells a large panel pads it with.
     */
    @Unique
    private List<BigItemStack> maxcraft$recipeCells = List.of();

    @Unique
    private int maxcraft$recipeWidth;

    @Unique
    private int maxcraft$recipeHeight;

    /**
     * True while the panel shows the materials it is wired to instead of a recipe's grid.
     *
     * <p>This is the crafting case only: with crafting off, Create already draws exactly this list, and drawing a
     * second copy of it would put two sets of item tooltips on top of each other. While crafting is on, Create draws
     * the recipe's cells instead - a grid of mostly empty cells on a large panel - and the materials take its place,
     * with the recipe's real shape left to a hover.
     */
    @Unique
    private boolean maxcraft$listMode() {
        return maxcraft$extended() && !restocker && craftingActive && availableCraftingRecipe != null
            && !maxcraft$recipeCells.isEmpty();
    }

    @Unique
    private IconButton maxcraft$previousPageButton;

    @Unique
    private IconButton maxcraft$nextPageButton;

    @Unique
    private int maxcraft$windowHeight() {
        return (restocker ? AllGuiTextures.FACTORY_GAUGE_RESTOCK : AllGuiTextures.FACTORY_GAUGE_RECIPE).getHeight()
            + AllGuiTextures.FACTORY_GAUGE_BOTTOM.getHeight();
    }

    @Unique
    private int maxcraft$guiLeft() {
        return addressBox.getX() - MAXCRAFT_ADDRESS_OFFSET_X;
    }

    @Unique
    private int maxcraft$guiTop() {
        return addressBox.getY() - maxcraft$windowHeight() + MAXCRAFT_ADDRESS_OFFSET_Y;
    }

    @Unique
    private GridSizeHolder maxcraft$holder() {
        return (GridSizeHolder) behaviour;
    }

    /** True for a gauge that has been given a large grid; those are the panels that search beyond Create's own. */
    @Unique
    private boolean maxcraft$extended() {
        return maxcraft$holder().maxcraft$isExtended();
    }

    //

    /**
     * Hides the crafting toggle when there is no recipe for what the panel is set to make.
     *
     * <p>Create always offers the button; with no recipe behind it, pressing it only produces a panel with nothing to
     * craft and no ingredients to ask for.
     */
    @Inject(method = "init", at = @At("TAIL"), require = 0)
    private void maxcraft$hideCraftingToggleWithoutRecipe(CallbackInfo ci) {
        if (!maxcraft$extended() || availableCraftingRecipe != null || activateCraftingButton == null)
            return;
        ((ScreenAccessor) this).maxcraft$removeWidget(activateCraftingButton);
    }

    /**
     * Replaces the recipe lookup on an extended panel.
     *
     * <p>Create only asks for vanilla crafting recipes; mechanical crafting recipes are a recipe type of their own,
     * and are the whole point of going past 3x3.
     */
    @Inject(method = "searchForCraftingRecipe", at = @At("HEAD"), cancellable = true)
    private void maxcraft$searchForCraftingRecipe(CallbackInfo ci) {
        if (!maxcraft$extended())
            return;

        ci.cancel();
        availableCraftingRecipe = null;

        ItemStack output = outputConfig.stack;
        if (output.isEmpty() || inputConfig.isEmpty())
            return;

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null)
            return;

        Set<Item> availableItems = new HashSet<>();
        for (BigItemStack entry : inputConfig)
            if (!entry.stack.isEmpty())
                availableItems.add(entry.stack.getItem());

        RecipeType<MechanicalCraftingRecipe> mechanical = AllRecipeTypes.MECHANICAL_CRAFTING.getType();
        List<RecipeHolder<? extends CraftingRecipe>> candidates = new ArrayList<>();
        candidates.addAll(minecraft.level.getRecipeManager()
            .getAllRecipesFor(RecipeType.CRAFTING));
        candidates.addAll(minecraft.level.getRecipeManager()
            .getAllRecipesFor(mechanical));

        // Smallest first, so a recipe that fits an ordinary crafting table is preferred over a large one that makes
        // the same thing.
        candidates.sort(Comparator.comparingInt(holder -> holder.value()
            .getIngredients()
            .size()));

        for (RecipeHolder<? extends CraftingRecipe> holder : candidates) {
            CraftingRecipe recipe = holder.value();
            if (output.getItem() != recipe.getResultItem(minecraft.level.registryAccess())
                .getItem())
                continue;
            if (AllRecipeTypes.shouldIgnoreInAutomation(holder))
                continue;
            if (!maxcraft$inputsCover(recipe, availableItems))
                continue;

            availableCraftingRecipe = recipe;
            Maxcraft.LOGGER.info("Maxcraft: panel at {} found {} ({} ingredients, {}) for {}", behaviour.getPanelPosition(),
                holder.id(), recipe.getIngredients()
                    .size(),
                recipe.getType(), output.getItem());
            return;
        }

        Maxcraft.LOGGER.info("Maxcraft: panel at {} found no recipe for {} from inputs {}", behaviour.getPanelPosition(),
            output.getItem(), availableItems);
    }

    /**
     * A recipe is only usable when it consumes every material the player wired into the panel.
     *
     * <p>Matching loosely - "every ingredient can be found among the inputs" - would accept a recipe that quietly
     * ignores half of what was connected, and the panel would then order materials it has no use for. Every
     * ingredient must be matched, and every input must be used.
     */
    @Unique
    private boolean maxcraft$inputsCover(CraftingRecipe recipe, Set<Item> availableItems) {
        Set<Item> usedByRecipe = new HashSet<>();
        for (Ingredient ingredient : recipe.getIngredients())
            if (!ingredient.isEmpty())
                for (Item item : availableItems)
                    if (ingredient.test(new ItemStack(item)))
                        usedByRecipe.add(item);

        if (!usedByRecipe.containsAll(availableItems))
            return false;

        for (Ingredient ingredient : recipe.getIngredients()) {
            if (ingredient.isEmpty())
                continue;
            boolean satisfiable = false;
            for (Item item : availableItems)
                if (ingredient.test(new ItemStack(item))) {
                    satisfiable = true;
                    break;
                }
            if (!satisfiable)
                return false;
        }
        return true;
    }

    //

    /** Pads the recipe out to the panel's grid instead of Create's nine entries. */
    @Redirect(
        method = "updateConfigs",
        at = @At(
            value = "INVOKE",
            target = "Lcom/simibubi/create/content/logistics/factoryBoard/FactoryPanelScreen;"
                + "convertRecipeToPackageOrderContext(Lnet/minecraft/world/item/crafting/CraftingRecipe;"
                + "Ljava/util/List;Z)Ljava/util/List;"
        )
    )
    private List<BigItemStack> maxcraft$convertRecipe(CraftingRecipe recipe, List<BigItemStack> inputs,
                                                      boolean respectAmounts) {
        if (!maxcraft$extended())
            return FactoryPanelScreen.convertRecipeToPackageOrderContext(recipe, inputs, respectAmounts);

        // The recipe's own grid, cell for cell, and then laid into the panel's grid. Create stops at nine entries,
        // which caps a panel at a 3x3 recipe's worth of ingredients however large the recipe it found is, and a
        // recipe smaller than the grid has to keep its rows and columns rather than being walked cell by cell -
        // a 3x3 recipe on a 5x5 machine uses the machine's top left and leaves the rest of it empty.
        List<BigItemStack> pattern = new ArrayList<>();
        BigItemStack empty = new BigItemStack(ItemStack.EMPTY, 1);
        List<BigItemStack> mutableInputs = BigItemStack.duplicateWrappers(inputs);

        for (Ingredient ingredient : recipe.getIngredients()) {
            BigItemStack chosen = empty;

            if (!ingredient.isEmpty())
                for (BigItemStack candidate : mutableInputs)
                    if (candidate.count > 0 && ingredient.test(candidate.stack)) {
                        chosen = new BigItemStack(candidate.stack, 1);
                        if (respectAmounts)
                            candidate.count -= 1;
                        break;
                    }

            pattern.add(chosen);
        }

        int recipeWidth;
        int recipeHeight;
        if (recipe instanceof ShapedRecipe shaped) {
            recipeWidth = shaped.getWidth();
            recipeHeight = shaped.getHeight();
        } else {
            recipeWidth = Math.max(1, (int) Math.ceil(Math.sqrt(pattern.size())));
            recipeHeight = (pattern.size() + recipeWidth - 1) / recipeWidth;
        }

        maxcraft$recipeCells = List.copyOf(pattern);
        maxcraft$recipeWidth = recipeWidth;
        maxcraft$recipeHeight = recipeHeight;

        List<BigItemStack> laid = CraftingPattern.inGrid(pattern, recipeWidth, recipeHeight,
            maxcraft$holder().maxcraft$gridSize());
        Maxcraft.LOGGER.info("Maxcraft: panel at {} sends a {}x{} recipe as a {}-cell pattern on a {}x{} grid",
            behaviour.getPanelPosition(), recipeWidth, recipeHeight, laid.size(),
            maxcraft$holder().maxcraft$gridSize(), maxcraft$holder().maxcraft$gridSize());
        return laid;
    }

    //
    // Paging through the inputs
    //
    // A panel's input grid is Create's 3x3, and the ninth material is the last one it can show. An extended panel
    // accepts any number of connections, so everything past the ninth used to be drawn a row further down for every
    // three of them: over the address box, the bottom bar and finally out of the window, where it could neither be
    // seen nor clicked. Those materials are put on further pages instead.
    //
    // Only the view is paged. The input and connection lists keep their order - they are what the panel is wired to,
    // and the amount beside each material is sent back by index - so a page changes no more than which nine entries
    // are drawn, clicked and scrolled.

    /**
     * Adds the paging buttons when the panel holds more than one page of materials.
     *
     * <p>They sit in the clear part of the bottom bar: past the redstone link slot at {@code x + 9}, whose click box
     * is checked whether or not a link is attached, and before the package slot at {@code x + 68}. Create's promise
     * timeout starts at {@code x + 97} and its delete and confirm buttons at the right end.
     *
     * <p>Nothing is added while a single page is enough, so a panel that fits in nine looks exactly as it did.
     */
    @Inject(method = "init", at = @At("TAIL"))
    private void maxcraft$addPageButtons(CallbackInfo ci) {
        maxcraft$previousPageButton = null;
        maxcraft$nextPageButton = null;
        // Connections may have been dropped while the panel was open: forget a page that no longer holds anything.
        maxcraft$page = maxcraft$currentPage();

        if (!maxcraft$pagingActive())
            return;

        int x = maxcraft$guiLeft();
        int y = maxcraft$guiTop() + maxcraft$windowHeight() - 25;
        ScreenAccessor screen = (ScreenAccessor) this;

        maxcraft$previousPageButton =
            screen.maxcraft$addRenderableWidget(new IconButton(x + 27, y, AllIcons.I_CONFIG_PREV));
        maxcraft$previousPageButton.withCallback(() -> maxcraft$turnPage(-1));
        maxcraft$nextPageButton =
            screen.maxcraft$addRenderableWidget(new IconButton(x + 49, y, AllIcons.I_CONFIG_NEXT));
        maxcraft$nextPageButton.withCallback(() -> maxcraft$turnPage(1));
        maxcraft$updatePageButtons();
    }

    /**
     * Keeps Create's grid of recipe cells off the screen while the panel is showing its materials as a list.
     *
     * <p>Emptying the list the drawing loop walks is enough to leave the grid out, and it changes nothing else: the
     * arrangement the panel sends to the machine is kept in its own field.
     */
    @ModifyExpressionValue(
        method = "renderWindow",
        at = @At(
            value = "FIELD",
            opcode = Opcodes.GETFIELD,
            target = "Lcom/simibubi/create/content/logistics/factoryBoard/FactoryPanelScreen;"
                + "craftingIngredients:Ljava/util/List;"
        )
    )
    private List<BigItemStack> maxcraft$hideRecipeGrid(List<BigItemStack> recipe) {
        return maxcraft$listMode() ? List.of() : recipe;
    }

    /**
     * Draws the materials the panel is wired to, and the recipe's own shape when the player hovers them.
     *
     * <p>Create only draws them while no recipe is being crafted, and it draws the recipe's cells instead once one is.
     * Here the materials stay on screen, and the recipe's shape is what a hover brings up - but only while the
     * panel's crafting is switched on, since that is what the shape belongs to.
     */
    @Inject(method = "renderWindow", at = @At("TAIL"))
    private void maxcraft$drawMaterialList(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks,
                                           CallbackInfo ci) {
        if (!maxcraft$listMode())
            return;

        Font font = Minecraft.getInstance().font;
        int first = maxcraft$currentPage() * MAXCRAFT_SLOTS_PER_PAGE;
        int shown = Math.min(inputConfig.size() - first, MAXCRAFT_SLOTS_PER_PAGE);
        boolean hovered = false;

        for (int i = 0; i < shown; i++) {
            BigItemStack entry = inputConfig.get(first + i);
            int inputX = maxcraft$guiLeft() + 68 + i % 3 * 20;
            int inputY = maxcraft$guiTop() + 28 + i / 3 * 20;

            graphics.renderItem(entry.stack, inputX, inputY);
            if (!entry.stack.isEmpty())
                graphics.renderItemDecorations(font, entry.stack, inputX, inputY, entry.count + "");

            if (mouseX >= inputX - 2 && mouseX < inputX + 18 && mouseY >= inputY - 2 && mouseY < inputY + 18)
                hovered = true;

            if (!entry.stack.isEmpty() && mouseX >= inputX && mouseX < inputX + 16 && mouseY >= inputY
                && mouseY < inputY + 16)
                graphics.renderTooltip(font, entry.stack, mouseX, mouseY);
        }

        // The shape belongs to the crafting the panel is set up for, so it is only offered while that is switched
        // on - hovering a panel that is merely restocking shows nothing but its materials.
        if (hovered && craftingActive)
            maxcraft$drawRecipePreview(graphics, mouseX, mouseY);
    }

    /**
     * The recipe as the machine has to be laid out: its own rows and columns, no padding.
     *
     * <p>Drawn beside the cursor, kept on screen, with the result named above it so the shape has something to
     * belong to.
     */
    @Unique
    private void maxcraft$drawRecipePreview(GuiGraphics graphics, int mouseX, int mouseY) {
        Font font = Minecraft.getInstance().font;
        int cell = 18;
        int pad = 4;
        int width = maxcraft$recipeWidth * cell;
        int height = maxcraft$recipeHeight * cell;
        int x = Mth.clamp(mouseX + 12, 4, Math.max(4, graphics.guiWidth() - width - pad * 2 - 4));
        int y = Mth.clamp(mouseY + 12, 4, Math.max(4, graphics.guiHeight() - height - pad * 2 - 16));

        graphics.fill(x - 1, y - 1, x + width + pad * 2 + 1, y + height + pad * 2 + 15, 0xFF101010);
        graphics.fill(x, y, x + width + pad * 2, y + height + pad * 2 + 14, 0xFF2B2B2B);

        for (int row = 0; row < maxcraft$recipeHeight; row++) {
            for (int col = 0; col < maxcraft$recipeWidth; col++) {
                int index = row * maxcraft$recipeWidth + col;
                int slotX = x + pad + col * cell;
                int slotY = y + pad + row * cell;
                graphics.fill(slotX, slotY, slotX + cell - 2, slotY + cell - 2, 0xFF1A1A1A);

                if (index >= maxcraft$recipeCells.size())
                    continue;
                ItemStack stack = maxcraft$recipeCells.get(index).stack;
                if (!stack.isEmpty())
                    graphics.renderItem(stack, slotX - 1, slotY - 1);
            }
        }

        ItemStack result = outputConfig.stack;
        if (!result.isEmpty())
            graphics.drawString(font, result.getHoverName(), x + pad, y + height + pad + 4, 0xFFD8D8D8, false);
    }

    @Unique
    private void maxcraft$turnPage(int delta) {
        int page = Mth.clamp(maxcraft$currentPage() + delta, 0, maxcraft$pageCount() - 1);
        if (page == maxcraft$currentPage())
            return;

        maxcraft$page = page;
        maxcraft$updatePageButtons();
    }

    /**
     * Both buttons name the page on screen, so hovering either one is enough to see where the nine slots come from.
     * At either end the button that would step off the list is greyed out rather than removed: the row keeps its
     * shape, and the page count stays readable on the first and last page.
     */
    @Unique
    private void maxcraft$updatePageButtons() {
        if (maxcraft$previousPageButton == null || maxcraft$nextPageButton == null)
            return;

        int page = maxcraft$currentPage();
        int pages = maxcraft$pageCount();
        Component tooltip = Component.translatable("maxcraft.gui.factory_panel.page", page + 1, pages);

        maxcraft$previousPageButton.setToolTip(tooltip);
        maxcraft$previousPageButton.active = page > 0;
        maxcraft$nextPageButton.setToolTip(tooltip);
        maxcraft$nextPageButton.active = page < pages - 1;
    }

    /**
     * True while the materials wired into the panel are what is on screen, and there are more of them than fit.
     *
     * <p>In list mode the materials are on screen whether or not the recipe is being crafted, so paging follows the
     * list rather than Create's crafting flag.
     */
    @Unique
    private boolean maxcraft$pagingActive() {
        return !restocker && (!craftingActive || maxcraft$listMode()) && maxcraft$pageCount() > 1;
    }

    @Unique
    private int maxcraft$pageCount() {
        return Math.max(1, (inputConfig.size() + MAXCRAFT_SLOTS_PER_PAGE - 1) / MAXCRAFT_SLOTS_PER_PAGE);
    }

    /** The page on screen, kept inside the range that currently has inputs on it. */
    @Unique
    private int maxcraft$currentPage() {
        return Mth.clamp(maxcraft$page, 0, maxcraft$pageCount() - 1);
    }

    /** How many of {@code total} entries the page on screen holds - never more than one grid's worth. */
    @Unique
    private int maxcraft$visibleSlotCount(int total) {
        if (!maxcraft$pagingActive())
            return total;

        return Math.min(total - maxcraft$currentPage() * MAXCRAFT_SLOTS_PER_PAGE, MAXCRAFT_SLOTS_PER_PAGE);
    }

    /** Slot {@code index} of the page on screen, as an index into the lists themselves, which never move. */
    @Unique
    private int maxcraft$absoluteSlot(int index) {
        return index + (maxcraft$pagingActive() ? maxcraft$currentPage() * MAXCRAFT_SLOTS_PER_PAGE : 0);
    }

    /**
     * Draws an input of a page past the first at the position of the slot it occupies on screen.
     *
     * <p>Create derives a slot's position from its index in the list, which only lines up with the grid for the
     * first nine, so the drawing has to be redone for the rest. The mouse-over tooltip lives in the method being
     * replaced and is rebuilt here from the same stack that was just drawn, which is what keeps the two together.
     */
    @Inject(method = "renderInputItem", at = @At("HEAD"), cancellable = true)
    private void maxcraft$renderPagedInputItem(GuiGraphics graphics, int slot, BigItemStack itemStack, int mouseX,
                                               int mouseY, CallbackInfo ci) {
        if (!maxcraft$pagingActive())
            return;

        int local = slot - maxcraft$currentPage() * MAXCRAFT_SLOTS_PER_PAGE;
        if (local < 0 || local >= MAXCRAFT_SLOTS_PER_PAGE) {
            ci.cancel();
            return;
        }
        if (local == slot)
            return; // First page: Create's own layout is already the layout of this page.

        ci.cancel();
        maxcraft$drawPagedInputItem(graphics, local, itemStack, mouseX, mouseY);
    }

    @Unique
    private void maxcraft$drawPagedInputItem(GuiGraphics graphics, int slot, BigItemStack itemStack, int mouseX,
                                             int mouseY) {
        Font font = Minecraft.getInstance().font;
        int inputX = maxcraft$guiLeft() + 68 + slot % 3 * 20;
        int inputY = maxcraft$guiTop() + 28 + slot / 3 * 20;

        graphics.renderItem(itemStack.stack, inputX, inputY);
        if (!itemStack.stack.isEmpty())
            graphics.renderItemDecorations(font, itemStack.stack, inputX, inputY, itemStack.count + "");

        if (mouseX < inputX - 2 || mouseX >= inputX + 18 || mouseY < inputY - 2 || mouseY >= inputY + 18)
            return;

        if (itemStack.stack.isEmpty()) {
            graphics.renderComponentTooltip(font, List.of(
                CreateLang.translate("gui.factory_panel.empty_panel")
                    .color(ScrollInput.HEADER_RGB)
                    .component(),
                CreateLang.translate("gui.factory_panel.left_click_disconnect")
                    .style(ChatFormatting.DARK_GRAY)
                    .style(ChatFormatting.ITALIC)
                    .component()
            ), mouseX, mouseY);
            return;
        }

        graphics.renderComponentTooltip(font, List.of(
            CreateLang.translate("gui.factory_panel.sending_item",
                    CreateLang.itemName(itemStack.stack)
                        .add(CreateLang.text(" x" + itemStack.count))
                        .string())
                .color(ScrollInput.HEADER_RGB)
                .component(),
            CreateLang.translate("gui.factory_panel.scroll_to_change_amount")
                .style(ChatFormatting.DARK_GRAY)
                .style(ChatFormatting.ITALIC)
                .component(),
            CreateLang.translate("gui.factory_panel.left_click_disconnect")
                .style(ChatFormatting.DARK_GRAY)
                .style(ChatFormatting.ITALIC)
                .component()
        ), mouseX, mouseY);
    }

    /**
     * Points the click loop at the page on screen.
     *
     * <p>Create walks the whole connection list and takes each slot's position from its index in that list, so on
     * the first page the tenth connection's click box lies over the address box and the eleventh over the promise
     * timeout, and on any later page a click would disconnect whichever input happens to share the position of the
     * slot under the cursor. Bounding the loop at one grid's worth and offsetting the index keeps clicks on the
     * connection that is drawn there.
     */
    @Redirect(method = "mouseClicked", at = @At(value = "INVOKE", target = "Ljava/util/List;size()I"))
    private int maxcraft$visibleConnectionCount(List<FactoryPanelConnection> connections) {
        return maxcraft$visibleSlotCount(connections.size());
    }

    @Redirect(method = "mouseClicked", at = @At(value = "INVOKE", target = "Ljava/util/List;get(I)Ljava/lang/Object;"))
    private Object maxcraft$connectionOnPage(List<FactoryPanelConnection> connections, int index) {
        return connections.get(maxcraft$absoluteSlot(index));
    }

    /** The same remapping for the scroll wheel, which changes how much of a material the panel asks for. */
    @Redirect(method = "mouseScrolled", at = @At(value = "INVOKE", target = "Ljava/util/List;size()I"))
    private int maxcraft$visibleInputCount(List<BigItemStack> inputs) {
        return maxcraft$visibleSlotCount(inputs.size());
    }

    @Redirect(method = "mouseScrolled", at = @At(value = "INVOKE", target = "Ljava/util/List;get(I)Ljava/lang/Object;"))
    private Object maxcraft$inputOnPage(List<BigItemStack> inputs, int index) {
        return inputs.get(maxcraft$absoluteSlot(index));
    }

}
