package cn.howxu.mmcr.compat.appliedenergistics2;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.recipe.EffectiveRecipe;
import cn.howxu.mmcr.api.recipe.MachineOutput;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier.IOType;
import cn.howxu.mmcr.api.recipe.requirement.ItemRequirement;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.api.recipe.requirement.StageRequirement;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.PatternBatchSupport;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.PatternBatchSupport.Safety;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.pattern.PatternBatchBinding;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Binding proofs use exact resources and effective single-lane capacity.
 * @author howxu <dev@howxu.cn>
 */
class PatternBatchSupportTest {
    @BeforeAll
    static void bootstrap() throws Exception {
        TestBootstrap.bootstrap();
        TestBootstrap.bootstrapCapabilities();
    }

    @Test
    void patternUnitAndRecipeCraftsAreNotInterchangeable() {
        var recipe = recipe("two_crafts", List.of(input()), List.of(output(1)));
        var effective = effective(recipe, 256);
        var original = pattern(AEItemKey.of(Items.IRON_INGOT), 20, List.of(stack(Items.GOLD_INGOT, 2)));
        var profile = PatternBatchSupport.inspectRecipe(recipe, effective, original);
        assertThat(profile.binding().recipeCraftsPerPattern()).isEqualTo(2L);
        assertThat(profile.maxMultiplier()).isEqualTo(128L);
        assertThat(PatternBatchSupport.matches(profile.binding(), recipe, effective, original)).isTrue();
        assertThat(PatternBatchSupport.matches(new PatternBatchBinding(MMCR.id("other_pool"), recipe.id(), 2),
                recipe, effective, original)).isFalse();
        assertThat(PatternBatchSupport.inspectRecipe(recipe, effective,
                pattern(AEItemKey.of(Items.DIAMOND), 20, original.getOutputs()))).isNull();
    }

    @Test
    void componentIdentityAndConflictingByproductRatiosCannotBind() {
        var recipe = recipe("components", List.of(input()), List.of(output(1)));
        var named = new ItemStack(Items.GOLD_INGOT);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("different component"));
        assertThat(PatternBatchSupport.inspectRecipe(recipe, effective(recipe, 256),
                pattern(AEItemKey.of(Items.IRON_INGOT), 1, List.of(new GenericStack(AEItemKey.of(named), 1))))).isNull();
        var byproducts = recipe("byproducts", List.of(input()), List.of(output(1),
                new MachineOutput.ItemOutput(new ItemStack(Items.DIAMOND), 1F)));
        assertThat(PatternBatchSupport.inspectRecipe(byproducts, effective(byproducts, 256),
                pattern(AEItemKey.of(Items.IRON_INGOT), 2,
                        List.of(stack(Items.GOLD_INGOT, 2), stack(Items.DIAMOND, 3))))).isNull();
    }

    @Test
    void unknownRequirementIsNotDisguisedAsAKnownSerialRecipe() {
        var known = recipe("known", List.of(input()), List.of(output(1)));
        var unknown = recipe("unknown", List.of(input(), new StageRequirement(1)), List.of(output(1)));
        var pattern = pattern(AEItemKey.of(Items.IRON_INGOT), 1, List.of(stack(Items.GOLD_INGOT, 1)));
        assertThat(PatternBatchSupport.inspectRecipe(known, effective(known, 1), pattern).safety()).isEqualTo(Safety.PROVEN_LINEAR);
        assertThat(PatternBatchSupport.inspectRecipe(unknown, effective(unknown, 1), pattern).safety()).isEqualTo(Safety.UNPROVEN);
    }

    @Test
    void sameKeyTotalsBoundMultipliersWithoutFloatQuantization() {
        var recipe = recipe("long_inputs", List.of(input()), List.of(output(1)));
        var effective = effective(recipe, Integer.MAX_VALUE);
        var huge = pattern(AEItemKey.of(Items.IRON_INGOT), Long.MAX_VALUE / 2 + 1,
                List.of(stack(Items.GOLD_INGOT, 1)));
        assertThat(PatternBatchSupport.inspectRecipe(recipe, effective, huge).maxMultiplier()).isOne();
        var small = pattern(AEItemKey.of(Items.IRON_INGOT), 1, List.of(stack(Items.GOLD_INGOT, 1)));
        assertThat(PatternBatchSupport.inspectRecipe(recipe, effective, small).maxMultiplier()).isEqualTo((long) Integer.MAX_VALUE);
    }

    private static MachineRecipe recipe(String name, List<MachineRequirement> requirements, List<MachineOutput> outputs) {
        return MachineRecipe.fromCanonical(MMCR.id("batch_support_" + name), MMCR.id("test_cube"), 20,
                requirements, outputs, List.of(), 0, 1, false, true, false, Set.of());
    }

    private static EffectiveRecipe effective(MachineRecipe recipe, long maximum) {
        return new EffectiveRecipe(recipe, recipe.requirements(), recipe.machineOutputs(), 20, maximum,
                4, 4, true, 0, 0, 0, 0);
    }

    private static ItemRequirement input() {
        return new ItemRequirement(IOType.INPUT, Ingredient.of(Items.IRON_INGOT), 1, ItemStack.EMPTY);
    }

    private static MachineOutput output(int amount) {
        return new MachineOutput.ItemOutput(new ItemStack(Items.GOLD_INGOT, amount), 1F);
    }

    private static GenericStack stack(Item item, long amount) {
        return new GenericStack(AEItemKey.of(item), amount);
    }

    private static IPatternDetails pattern(AEKey key, long amount, List<GenericStack> outputs) {
        return new IPatternDetails() {
            @Override public AEItemKey getDefinition() { return AEItemKey.of(Items.PAPER); }
            @Override public List<GenericStack> getOutputs() { return outputs; }
            @Override public IInput[] getInputs() {
                return new IInput[]{new IInput() {
                    @Override public GenericStack[] getPossibleInputs() { return new GenericStack[]{new GenericStack(key, amount)}; }
                    @Override public long getMultiplier() { return 1; }
                    @Override public boolean isValid(AEKey input, Level level) { return key.equals(input); }
                    @Override public AEKey getRemainingKey(AEKey template) { return null; }
                }};
            }
        };
    }
}
