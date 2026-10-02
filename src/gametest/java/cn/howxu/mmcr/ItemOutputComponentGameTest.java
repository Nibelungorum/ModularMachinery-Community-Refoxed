package cn.howxu.mmcr;

import cn.howxu.mmcr.api.capability.async.AsyncCapabilityRequest;
import cn.howxu.mmcr.api.capability.CapabilitySnapshot;
import cn.howxu.mmcr.api.recipe.CraftingContext;
import cn.howxu.mmcr.api.recipe.MachineOutput;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.OutputRegistry;
import cn.howxu.mmcr.api.recipe.component.ComponentPredicate;
import cn.howxu.mmcr.api.recipe.component.DataComponentPredicateSet;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.recipe.requirement.ItemRequirement;
import cn.howxu.mmcr.internal.capability.NativeAsyncResourceValues;
import cn.howxu.mmcr.internal.capability.ItemBusCapability;
import cn.howxu.mmcr.internal.api.facade.recipe.RequirementAdapters;
import cn.howxu.mmcr.publicapi.recipe.Outputs;
import cn.howxu.mmcr.publicapi.runtime.ItemOutputView;
import cn.howxu.mmcr.util.IOType;
import com.google.gson.JsonObject;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Checks output components with the live server registry, without tick timing assertions.
 * @author howxu <dev@howxu.cn>
 */
public class ItemOutputComponentGameTest {
    public void outputResolvesPlainJsonEnchantments(GameTestHelper helper) {
        ItemRequirement requirement = output();
        ItemStack resolved = requirement.stack(null);
        assertComponents(helper, resolved);
        helper.assertTrue(requirement.stack().get(DataComponents.ENCHANTMENTS).isEmpty(),
                "Resolving output components must not mutate the recipe's base stack");
        helper.succeed();
    }

    public void asyncOutputPreservesEnchantmentComponents(GameTestHelper helper) {
        var prepared = CraftingContext.prepareAsyncPlan(List.of(output()), 1, List.of());
        var request = (AsyncCapabilityRequest.Resource)
                prepared.requirements().getFirst().requests().getFirst();
        ItemStack resource = NativeAsyncResourceValues.item(request.actions().getFirst().resource());
        assertComponents(helper, resource);
        helper.succeed();
    }

    public void cachedOutputSurvivesFinishReplacement(GameTestHelper helper) {
        ItemRequirement requirement = output();
        MachineOutput cached = OutputRegistry.fromRequirement(requirement);
        MachineOutput decoded = MachineOutput.CODEC.parse(JsonOps.INSTANCE,
                MachineOutput.CODEC.encodeStart(JsonOps.INSTANCE, cached).getOrThrow()).getOrThrow();
        MachineRecipe recipe = MachineRecipe.fromCanonical(MMCR.id("output_component_regression"),
                MMCR.id("output_component_regression_pool"), 20, List.of(requirement), List.of(decoded),
                List.of(), 0, 1, false, false, false, Set.of());
        helper.assertTrue(recipe.machineOutputs().size() == 1 && recipe.runtimeRequirements().size() == 1,
                "Cached output must remain matched to its component-bearing requirement without duplicate outputs");
        var storage = new ItemStackHandler(2);
        var context = new CraftingContext(new CapabilitySnapshot(List.of(new ItemBusCapability(storage, IOType.OUTPUT))));
        var plan = context.planOutputRequirements(recipe.runtimeRequirements(), recipe.runtimeMachineOutputs(), 1, false);
        helper.assertTrue(plan.successful() && plan.plan().commit(), "Finish output replacement must commit successfully");
        assertComponents(helper, storage.getStackInSlot(0));
        helper.assertTrue(storage.getStackInSlot(1).isEmpty(), "Finish output replacement must not produce a second unenchanted output");
        helper.succeed();
    }

    public void publicOutputViewPreservesComponentsWhenRebuilt(GameTestHelper helper) {
        ItemRequirement requirement = output();
        var view = (ItemOutputView) Outputs.fromRequirement(RequirementAdapters.wrap(requirement)).orElseThrow();
        ItemStack stack = view.stack();
        assertComponents(helper, stack);
        stack.setCount(2);
        var rebuilt = Outputs.item(stack, view.chance());
        assertComponents(helper, rebuilt.stack());
        helper.assertTrue(rebuilt.stack().getCount() == 2,
                "Rebuilt output must retain the callback's stack changes");
        assertComponents(helper, view.stack());
        helper.assertTrue(view.stack().getCount() == 1
                        && requirement.stack().get(DataComponents.ENCHANTMENTS).isEmpty(),
                "Reading and modifying the public view must not mutate the source output or its base stack");
        helper.succeed();
    }

    private static ItemRequirement output() {
        JsonObject enchantments = new JsonObject();
        JsonObject levels = new JsonObject();
        levels.addProperty("minecraft:sharpness", 4);
        enchantments.add("levels", levels);
        ItemStack stack = new ItemStack(Items.DIAMOND);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("Output component regression"));
        return new ItemRequirement(RecipeModifier.IOType.OUTPUT, null, 0, stack, 1F,
                new DataComponentPredicateSet(Map.of(DataComponents.ENCHANTMENTS,
                        ComponentPredicate.exact(new Dynamic<>(JsonOps.INSTANCE, enchantments)))), 1F);
    }

    private static void assertComponents(GameTestHelper helper, ItemStack stack) {
        var sharpness = helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(Enchantments.SHARPNESS);
        helper.assertTrue(stack.get(DataComponents.ENCHANTMENTS).getLevel(sharpness) == 4,
                "Output must materialize the plain JSON enchantment using the live registry");
        helper.assertTrue(Component.literal("Output component regression").equals(stack.get(DataComponents.CUSTOM_NAME)),
                "Output must preserve the existing custom name alongside enchantments");
    }
}
