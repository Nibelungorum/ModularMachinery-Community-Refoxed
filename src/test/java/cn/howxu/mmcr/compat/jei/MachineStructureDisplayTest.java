package cn.howxu.mmcr.compat.jei;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.api.machine.Machine;
import cn.howxu.mmcr.api.machine.MachineControllerSpec;
import cn.howxu.mmcr.api.machine.MachineStructureStage;
import cn.howxu.mmcr.client.preview.StructureMaterialSummary;
import cn.howxu.mmcr.registry.ModItems;
import cn.howxu.mmcr.test.TestBootstrap;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.ingredient.IRecipeSlotDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.gui.inputs.RecipeSlotUnderMouse;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.library.gui.helpers.GuiHelper;
import mezz.jei.library.ingredients.IngredientInfo;
import mezz.jei.library.ingredients.IngredientManager;
import mezz.jei.library.ingredients.RegisteredIngredients;
import mezz.jei.library.plugins.vanilla.ingredients.ItemStackHelper;
import net.minecraft.client.gui.navigation.ScreenPosition;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * Verifies JEI structure displays retain only registration-safe metadata.
 *
 * @author howxu <dev@howxu.cn>
 */
class MachineStructureDisplayTest {

    @BeforeAll
    static void bootstrap() throws Exception {
        TestBootstrap.bootstrap();
    }

    @Test
    void displayRetainsDefaultStageMaterials() {
        Machine machine = testMachineWithBlocks();
        MachineStructureDisplay display = MachineStructureDisplay.from(machine);
        StructureMaterialSummary materials = display.materials();

        assertThat(display.machine()).isSameAs(machine);
        assertThat(display.defaultSchema().states()).hasSize(2);
        assertThat(materials.entries()).extracting(entry -> entry.stack().getItem())
                .containsExactly(Blocks.STONE.asItem(), Blocks.COBBLESTONE.asItem());
        assertThat(display.ingredients()).allSatisfy(stack -> assertThat(stack.getCount()).isGreaterThan(0));
    }

    @Test
    void switchingStageReplacesMaterialAndTransferSlotsIncludingSharedItemCounts() {
        Machine machine = new Machine() {
            @Override public ResourceLocation registryName() { return MMCR.id("staged_materials"); }
            @Override public BlockArray pattern() {
                return new BlockArray(Map.of(BlockPos.ZERO, new BlockPredicate.OfBlock(Blocks.STONE)));
            }
            @Override public MachineControllerSpec controller() { return MachineControllerSpec.defaultsFor(registryName()); }
            @Override public List<MachineStructureStage> structureStages() {
                var base = Machine.super.structureStages().getFirst();
                var expanded = new BlockArray(Map.of(BlockPos.ZERO, new BlockPredicate.OfBlock(Blocks.STONE),
                        BlockPos.ZERO.east(), new BlockPredicate.OfBlock(Blocks.STONE),
                        BlockPos.ZERO.above(), new BlockPredicate.OfBlock(Blocks.GLASS)));
                return List.of(base, new MachineStructureStage(7, expanded, portRequirements(), portTierRequirements(),
                        dynamicPatterns(), base.requirements()));
            }
        };
        var display = MachineStructureDisplay.from(machine);

        var expanded = display.materialsByStage().get(7).entries();
        assertThat(expanded).extracting(entry -> entry.stack().getItem()).containsExactly(Items.STONE, Items.GLASS);
        assertThat(expanded).extracting(StructureMaterialSummary.Entry::count).containsExactly(2, 1);

        // These slots validate item stacks without rendering or looking up subtypes.
        var items = new IngredientInfo<>(VanillaTypes.ITEM_STACK, List.of(),
                new ItemStackHelper(null, null), null, ItemStack.CODEC, null);
        var guiHelper = new GuiHelper(new IngredientManager(new RegisteredIngredients(
                new LinkedHashMap<>(Map.of(VanillaTypes.ITEM_STACK, items)))));
        List<IRecipeSlotDrawable> slots = new ArrayList<>();
        for (var summary : display.materialsByStage().values()) {
            for (var entry : summary.entries()) {
                slots.add(guiHelper.createRecipeSlotDrawable(RecipeIngredientRole.RENDER_ONLY,
                        slot -> slot.addItemStack(entry.stack()), Set.of(), 0));
            }
        }
        display.ingredients().forEach(stack -> slots.add(guiHelper.createRecipeSlotDrawable(
                RecipeIngredientRole.INPUT, slot -> slot.addItemStack(stack), Set.of(), 0)));
        var output = guiHelper.createRecipeSlotDrawable(RecipeIngredientRole.OUTPUT,
                slot -> slot.addItemStack(MachineStructureCategory.structureOutput(display)), Set.of(), 0);
        slots.add(output);
        var stage = new AtomicInteger(1);
        var view = new StructureStageSlots(display, () -> slots, guiHelper, stage::get);
        var row = new StructureMaterialWidget(view::materials, new ScreenPosition(3, 197));
        var otherPreview = new StructureStageSlots(display, () -> slots, guiHelper, () -> 1);

        assertThat(transferInputs(view)).extracting(ItemStack::getItem, ItemStack::getCount)
                .containsExactly(tuple(Items.STONE, 1));
        assertThat(row.getSlotUnderMouse(17, 1)).isEmpty();
        stage.set(7);
        assertThat(transferInputs(view)).extracting(ItemStack::getItem, ItemStack::getCount)
                .containsExactly(tuple(Items.STONE, 2), tuple(Items.GLASS, 1));
        assertThat(transferInputs(otherPreview)).extracting(ItemStack::getItem, ItemStack::getCount)
                .containsExactly(tuple(Items.STONE, 1));
        assertThat(view.getSlotViews(RecipeIngredientRole.OUTPUT)).containsExactly(output);
        assertThat(row.getSlotUnderMouse(0, 1)).map(RecipeSlotUnderMouse::slot).contains(slots.get(1));
        assertThat(row.getSlotUnderMouse(17, 1)).map(RecipeSlotUnderMouse::slot).contains(slots.get(2));
        stage.set(1);
        assertThat(transferInputs(view)).extracting(ItemStack::getItem, ItemStack::getCount)
                .containsExactly(tuple(Items.STONE, 1));
        assertThat(row.getSlotUnderMouse(0, 1)).map(RecipeSlotUnderMouse::slot).contains(slots.getFirst());
        assertThat(row.getSlotUnderMouse(17, 1)).isEmpty();
    }

    private static List<ItemStack> transferInputs(IRecipeSlotsView view) {
        return view.getSlotViews(RecipeIngredientRole.INPUT).stream().flatMap(IRecipeSlotView::getItemStacks).toList();
    }

    @Test
    void ingredientsReturnCopiesThatCannotMutateTheDisplay() {
        MachineStructureDisplay display = MachineStructureDisplay.from(testMachineWithBlocks());

        ItemStack exposed = display.ingredients().getFirst();
        exposed.setCount(42);

        assertThat(display.ingredients().getFirst().getCount()).isOne();
    }

    @Test
    void displayMaterialEntriesAreDefensiveCopies() {
        MachineStructureDisplay display = MachineStructureDisplay.from(testMachineWithBlocks());

        ItemStack exposed = display.materials().entries().getFirst().stack();
        exposed.setCount(42);

        assertThat(display.materials().entries().getFirst().stack().getCount()).isOne();
    }

    @Test
    void structureOutputUsesBlueprintAndMachineName() {
        Machine machine = testMachineWithBlocks();
        ItemStack output = MachineStructureCategory.structureOutput(MachineStructureDisplay.from(machine));

        assertThat(output.getItem()).isSameAs(ModItems.BLUEPRINT.get());
        assertThat(output.get(DataComponents.CUSTOM_NAME)).isEqualTo(machine.displayName());
        assertThat(output.getItem()).isNotEqualTo(Items.ENCHANTED_BOOK);
    }

    @Test
    void materialsDoNotBuildTheFullDefaultPreviewSchema() {
        Machine machine = new Machine() {
            @Override public ResourceLocation registryName() { return MMCR.id("test_materials_only"); }
            @Override public BlockArray pattern() {
                Map<BlockPos, BlockPredicate> pattern = new LinkedHashMap<>();
                pattern.put(BlockPos.ZERO, new BlockPredicate.OfBlock(Blocks.STONE));
                pattern.put(new BlockPos(1, 0, 0), new BlockPredicate.OfBlock(Blocks.COBBLESTONE));
                return new BlockArray(pattern);
            }
            @Override public MachineControllerSpec controller() {
                throw new AssertionError("material extraction must not build the preview schema");
            }
        };

        assertThat(MachineStructureDisplay.from(machine).materials().entries())
                .extracting(entry -> entry.stack().getItem())
                .containsExactly(Blocks.STONE.asItem(), Blocks.COBBLESTONE.asItem());
    }

    private static Machine testMachineWithBlocks() {
        return new Machine() {
            @Override public ResourceLocation registryName() { return MMCR.id("test_cube"); }
            @Override public BlockArray pattern() {
                Map<BlockPos, BlockPredicate> pattern = new LinkedHashMap<>();
                pattern.put(BlockPos.ZERO, new BlockPredicate.OfBlock(Blocks.STONE));
                pattern.put(new BlockPos(1, 0, 0), new BlockPredicate.OfBlock(Blocks.COBBLESTONE));
                return new BlockArray(pattern);
            }
            @Override public MachineControllerSpec controller() { return MachineControllerSpec.defaultsFor(registryName()); }
        };
    }
}
