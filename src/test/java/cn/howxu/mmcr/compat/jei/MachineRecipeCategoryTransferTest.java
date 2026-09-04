package cn.howxu.mmcr.compat.jei;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.api.machine.level.LevelModifier;
import cn.howxu.mmcr.api.machine.level.LevelType;
import cn.howxu.mmcr.api.machine.level.MachineLevel;
import cn.howxu.mmcr.api.recipe.MachineIngredient;
import cn.howxu.mmcr.api.recipe.LevelRequirement;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.test.RecipeTestSupport;
import cn.howxu.mmcr.test.TestBootstrap;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.ingredient.IRecipeSlotRichTooltipCallback;
import mezz.jei.api.recipe.RecipeIngredientRole;
import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MachineRecipeCategoryTransferTest {

    @BeforeAll
    static void bootstrap() throws Exception {
        TestBootstrap.bootstrap();
        Items.IRON_INGOT.builtInRegistryHolder().bindComponents(DataComponentMap.EMPTY);
        Items.GOLD_INGOT.builtInRegistryHolder().bindComponents(DataComponentMap.EMPTY);
    }

    @Test
    void transferSlotsContainAllItemAndFluidInputsAndOutputs() throws Exception {
        FluidIngredient fluidInput = FluidIngredient.of(HolderSet.direct(
                Fluids.WATER.builtInRegistryHolder(), Fluids.LAVA.builtInRegistryHolder()));
        MachineRecipe recipe = RecipeTestSupport.create(
                MMCR.id("jei_transfer_slots"), MMCR.id("blast_furnace"), 20,
                List.of(
                        new MachineIngredient.ItemIngredient(Ingredient.of(Items.IRON_INGOT), 4),
                        new MachineIngredient.FluidIngredient(fluidInput, 250)),
                List.of(new ItemStack(Items.GOLD_INGOT, 3)),
                List.of(), 0, 1, false,
                List.of(new FluidStack(Fluids.LAVA, 500, DataComponentPatch.builder()
                        .set(DataComponents.CUSTOM_NAME, Component.literal("Fluid output"))
                        .build())));
        MachineRecipeDisplay display = MachineRecipeDisplay.from(recipe);
        List<CapturedSlot> slots = new ArrayList<>();

        invokeAddTransferSlots(recipeLayoutBuilder(slots), display);

        assertThat(slots).extracting(CapturedSlot::role)
                .containsExactly(
                        RecipeIngredientRole.INPUT,
                        RecipeIngredientRole.INPUT,
                        RecipeIngredientRole.OUTPUT,
                        RecipeIngredientRole.OUTPUT);
        assertThat(slots).allSatisfy(slot -> {
            assertThat(slot.x()).isEqualTo(-1000);
            assertThat(slot.y()).isEqualTo(-1000);
        });
        assertThat(slots.get(0).fluidAdds()).extracting(CapturedFluid::fluid)
                .containsExactly(Fluids.WATER, Fluids.LAVA);
        assertThat(slots.get(0).fluidAdds()).extracting(CapturedFluid::amount)
                .containsOnly(250L);
        assertThat(slots.get(1).itemStacks()).singleElement()
                .extracting(ItemStack::getCount).isEqualTo(4);
        assertThat(slots.get(2).fluidAdds()).singleElement()
                .satisfies(add -> assertThat(add.fluid()).isEqualTo(Fluids.LAVA));
        assertThat(slots.get(2).fluidAdds()).singleElement()
                .extracting(CapturedFluid::amount).isEqualTo(500L);
        assertThat(slots.get(2).fluidAdds()).singleElement()
                .extracting(CapturedFluid::componentsPatch)
                .satisfies(patch -> {
                    assertThat(patch.isEmpty()).isFalse();
                    assertThat(patch).isEqualTo(display.fluidOutputs().getFirst().getComponentsPatch());
                });
        assertThat(slots.get(3).itemAdds()).singleElement()
                .extracting(ItemStack::getCount).isEqualTo(3);
    }

    @Test
    void levelRequirementUsesRenderOnlySlotAndMinimumLevelTooltip() throws Exception {
        TestBootstrap.beginRegistration();
        Identifier typeId = MMCR.id("slot_coil_type");
        Identifier copperId = MMCR.id("slot_copper");
        Identifier ironId = MMCR.id("slot_iron");
        TestBootstrap.registerType(new LevelType(typeId, Component.literal("Coils")));
        registerLevel(copperId, typeId, 0, Blocks.COPPER_BLOCK);
        registerLevel(ironId, typeId, 1, Blocks.IRON_BLOCK);
        LevelRequirement requirement = new LevelRequirement(typeId, ironId);
        MachineRecipe recipe = RecipeTestSupport.create(
                MMCR.id("jei_level_slot"), MMCR.id("level_slot_test_machine"), 20,
                List.of(), List.of(), List.of(), 0, 1, false, List.of(), List.of(), false,
                List.of(requirement));
        MachineRecipeDisplay display = MachineRecipeDisplay.from(recipe);
        MachineRecipeLayout layout = MachineRecipeLayout.forDisplay(display, 4);
        List<CapturedSlot> slots = new ArrayList<>();

        MachineRecipeCategory.addLevelRequirementSlots(recipeLayoutBuilder(slots), layout, display, label -> 40);

        assertThat(slots).singleElement().satisfies(slot -> {
            assertThat(slot.role()).isEqualTo(RecipeIngredientRole.RENDER_ONLY);
            assertThat(slot.x()).isEqualTo(layout.durationTextX() + 40 + 2);
            assertThat(slot.y()).isEqualTo(layout.levelRequirementSlotY(display, 0));
            assertThat(slot.standardBackground()).isTrue();
            assertThat(slot.itemAdds()).singleElement()
                    .extracting(ItemStack::getItem).isEqualTo(Blocks.IRON_BLOCK.asItem());
            assertThat(tooltipLines(slot.tooltipCallbacks().getFirst()))
                    .containsExactly(MachineRecipeCategory.minimumLevelTooltip(requirement));
        });
    }

    private static IRecipeLayoutBuilder recipeLayoutBuilder(List<CapturedSlot> slots) {
        return (IRecipeLayoutBuilder) Proxy.newProxyInstance(
                MachineRecipeCategoryTransferTest.class.getClassLoader(),
                new Class<?>[]{IRecipeLayoutBuilder.class},
                (proxy, method, arguments) -> {
                    if (method.getName().equals("addSlot") && arguments.length == 3
                            && arguments[0] instanceof RecipeIngredientRole role) {
                        CapturedSlot capture = new CapturedSlot(role,
                                ((Number) arguments[1]).intValue(), ((Number) arguments[2]).intValue());
                        slots.add(capture);
                        return recipeSlotBuilder(capture);
                    } else if (method.getName().equals("addInputSlot")
                            || method.getName().equals("addOutputSlot")) {
                        RecipeIngredientRole role = method.getName().equals("addInputSlot")
                                ? RecipeIngredientRole.INPUT : RecipeIngredientRole.OUTPUT;
                        int x = ((Number) arguments[0]).intValue();
                        int y = ((Number) arguments[1]).intValue();
                        CapturedSlot capture = new CapturedSlot(role, x, y);
                        slots.add(capture);
                        return recipeSlotBuilder(capture);
                    }
                    return null;
                });
    }

    private static void invokeAddTransferSlots(IRecipeLayoutBuilder builder,
                                               MachineRecipeDisplay display) throws Exception {
        Method method = MachineRecipeCategory.class.getDeclaredMethod(
                "addTransferSlots", IRecipeLayoutBuilder.class, MachineRecipeDisplay.class);
        method.setAccessible(true);
        method.invoke(null, builder, display);
    }

    private static List<FormattedText> tooltipLines(IRecipeSlotRichTooltipCallback callback) {
        List<FormattedText> lines = new ArrayList<>();
        ITooltipBuilder tooltip = (ITooltipBuilder) Proxy.newProxyInstance(
                MachineRecipeCategoryTransferTest.class.getClassLoader(), new Class<?>[]{ITooltipBuilder.class},
                (proxy, method, arguments) -> {
                    if (method.getName().equals("add") && arguments.length == 1
                            && arguments[0] instanceof FormattedText text) {
                        lines.add(text);
                    }
                    return null;
                });
        callback.onRichTooltip(null, tooltip);
        return lines;
    }

    private static void registerLevel(Identifier id, Identifier typeId, int priority, Block block) {
        TestBootstrap.registerLevel(new MachineLevel(id, typeId, priority,
                new BlockPredicate.OfBlockState(block.defaultBlockState()),
                new ItemStack(block.asItem()), LevelModifier.IDENTITY));
    }

    @SuppressWarnings("unchecked")
    private static Object recipeSlotBuilder(CapturedSlot capture) {
        return Proxy.newProxyInstance(
                MachineRecipeCategoryTransferTest.class.getClassLoader(),
                new Class<?>[]{IRecipeSlotBuilder.class},
                (proxy, method, arguments) -> {
                    if (method.getName().equals("addItemStacks")) {
                        capture.itemStacks.addAll((List<ItemStack>) arguments[0]);
                    } else if (method.getName().equals("setStandardSlotBackground")) {
                        capture.standardBackground = true;
                    } else if (method.getName().equals("addRichTooltipCallback")) {
                        capture.tooltipCallbacks.add((IRecipeSlotRichTooltipCallback) arguments[0]);
                    } else if (method.getName().equals("add") && arguments.length == 1
                            && arguments[0] instanceof ItemStack stack) {
                        capture.itemAdds.add(stack);
                    } else if (method.getName().equals("add") && arguments.length >= 2
                            && arguments[0] instanceof Fluid fluid) {
                        DataComponentPatch patch = arguments.length >= 3
                                ? (DataComponentPatch) arguments[2] : DataComponentPatch.EMPTY;
                        capture.fluidAdds.add(new CapturedFluid(fluid, ((Number) arguments[1]).longValue(), patch));
                    }
                    return method.getReturnType().isAssignableFrom(IRecipeSlotBuilder.class) ? proxy : null;
                });
    }

    private static final class CapturedSlot {
        private final RecipeIngredientRole role;
        private final int x;
        private final int y;
        private final List<ItemStack> itemStacks = new ArrayList<>();
        private final List<ItemStack> itemAdds = new ArrayList<>();
        private final List<CapturedFluid> fluidAdds = new ArrayList<>();
        private final List<IRecipeSlotRichTooltipCallback> tooltipCallbacks = new ArrayList<>();
        private boolean standardBackground;

        private CapturedSlot(RecipeIngredientRole role, int x, int y) {
            this.role = role;
            this.x = x;
            this.y = y;
        }

        private RecipeIngredientRole role() {
            return role;
        }

        private int x() {
            return x;
        }

        private int y() {
            return y;
        }

        private List<ItemStack> itemStacks() {
            return itemStacks;
        }

        private List<ItemStack> itemAdds() {
            return itemAdds;
        }

        private List<CapturedFluid> fluidAdds() {
            return fluidAdds;
        }

        private List<IRecipeSlotRichTooltipCallback> tooltipCallbacks() {
            return tooltipCallbacks;
        }

        private boolean standardBackground() {
            return standardBackground;
        }
    }

    private record CapturedFluid(Fluid fluid, long amount, DataComponentPatch componentsPatch) {
    }
}
