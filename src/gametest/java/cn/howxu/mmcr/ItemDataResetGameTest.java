package cn.howxu.mmcr;

import cn.howxu.mmcr.internal.item.MultiblockDetectorSelection;
import cn.howxu.mmcr.internal.item.TerminalData;
import cn.howxu.mmcr.internal.item.TerminalInventoryMode;
import cn.howxu.mmcr.registry.ModDataComponents;
import cn.howxu.mmcr.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapelessRecipe;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Verifies that single-item crafting restores tools to their pristine default stacks.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class ItemDataResetGameTest {
    private ItemDataResetGameTest() {
    }

    public static void detectorClearsSelectionAndMask(GameTestHelper helper) {
        ItemStack stack = new ItemStack(ModItems.MULTIBLOCK_DETECTOR.get());
        stack.set(ModDataComponents.MULTIBLOCK_DETECTOR_SELECTION.get(),
                new MultiblockDetectorSelection(BlockPos.ZERO, Direction.NORTH,
                        new BlockPos(1, 0, 0), new BlockPos(3, 2, 2)));
        stack.set(ModDataComponents.MULTIBLOCK_DETECTOR_MASK.get(), true);
        assertResetRecipe(helper, "multiblock_detector_reset", stack);
    }

    public static void terminalClearsBindingsAndConfiguration(GameTestHelper helper) {
        ItemStack stack = new ItemStack(ModItems.TERMINAL.get());
        GlobalPos controller = GlobalPos.of(helper.getLevel().dimension(), new BlockPos(1, 2, 3));
        stack.set(ModDataComponents.TERMINAL_DATA.get(), TerminalData.DEFAULT
                .withController(controller).withContainer(controller).withAe2AccessPoint(controller)
                .withInventoryMode(TerminalInventoryMode.AE2)
                .withSelectedLevel(MMCR.id("reset_test_type"), MMCR.id("reset_test_level"))
                .withStage(2).withPreview(true, 1));
        assertResetRecipe(helper, "terminal_reset", stack);
    }

    public static void blueprintClearsMachineAndStage(GameTestHelper helper) {
        ItemStack stack = new ItemStack(ModItems.BLUEPRINT.get());
        stack.set(ModDataComponents.BLUEPRINT_MACHINE.get(), MMCR.id("reset_test_machine"));
        stack.set(ModDataComponents.BLUEPRINT_STAGE.get(), 2);
        assertResetRecipe(helper, "blueprint_reset", stack);
    }

    private static void assertResetRecipe(GameTestHelper helper, String recipeId, ItemStack stack) {
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("Configured tool"));
        stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        ItemStack original = stack.copy();
        ItemStack pristine = new ItemStack(stack.getItem());
        var key = MMCR.id(recipeId);
        var holder = helper.getLevel().getRecipeManager().byKey(key);
        helper.assertTrue(holder.isPresent() && holder.orElseThrow().value() instanceof ShapelessRecipe,
                "Reset must be available as a vanilla shapeless recipe: " + recipeId);
        ShapelessRecipe recipe = (ShapelessRecipe) holder.orElseThrow().value();

        for (ItemStack ingredient : List.of(stack, pristine)) {
            for (int size : new int[] {2, 3}) {
                for (int slot = 0; slot < size * size; slot++) {
                    List<ItemStack> grid = new ArrayList<>(Collections.nCopies(size * size, ItemStack.EMPTY));
                    grid.set(slot, ingredient.copy());
                    CraftingInput input = CraftingInput.of(size, size, grid);
                    var match = helper.getLevel().getRecipeManager()
                            .getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel());
                    helper.assertTrue(match.isPresent() && match.orElseThrow().id().equals(key),
                            "A single tool must select its reset recipe in either crafting grid");
                    helper.assertTrue(ItemStack.matches(recipe.assemble(input, helper.getLevel().registryAccess()), pristine),
                            "Reset must return exactly one pristine tool, without inherited components");
                    helper.assertTrue(ItemStack.matches(grid.get(slot), ingredient),
                            "Assembling must not mutate the crafting input during result preview");
                    helper.assertTrue(recipe.getRemainingItems(input).stream().allMatch(ItemStack::isEmpty),
                            "Reset must not return an extra tool as a crafting remainder");
                }
            }
        }
        helper.assertTrue(!recipe.matches(CraftingInput.of(1, 1, List.of(ItemStack.EMPTY)), helper.getLevel()),
                "Reset must not craft without an input");
        helper.assertTrue(!recipe.matches(CraftingInput.of(1, 1, List.of(new ItemStack(Items.STICK))), helper.getLevel()),
                "Reset must not accept unrelated items");
        helper.assertTrue(!recipe.matches(CraftingInput.of(2, 1, List.of(stack, stack.copy())), helper.getLevel()),
                "Reset must consume only one tool, not multiple occupied slots");
        helper.assertTrue(!recipe.matches(CraftingInput.of(2, 1, List.of(stack, new ItemStack(Items.STICK))), helper.getLevel()),
                "Reset must reject extra materials");
        helper.assertTrue(ItemStack.matches(stack, original),
                "Matching and assembling must not mutate the original configured tool");
        helper.succeed();
    }
}
