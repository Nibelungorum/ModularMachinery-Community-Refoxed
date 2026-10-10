package cn.howxu.mmcr;

import cn.howxu.mmcr.api.capability.async.AsyncCapabilityRequest;
import cn.howxu.mmcr.api.capability.CapabilitySnapshot;
import cn.howxu.mmcr.api.machine.MachineRegistry;
import cn.howxu.mmcr.api.machine.definition.RecipeStartContext;
import cn.howxu.mmcr.api.recipe.ActiveMachineRecipe;
import cn.howxu.mmcr.api.recipe.CraftingContext;
import cn.howxu.mmcr.api.recipe.MachineOutput;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.MachineRecipeSerializer;
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
import cn.howxu.mmcr.internal.runtime.CraftingRuntime;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.util.IOType;
import com.google.gson.JsonObject;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import io.netty.buffer.Unpooled;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
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
        assertRegistryFallbackAndOwnership(helper);
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

    public void recipeSerializerPreservesEnchantmentComponents(GameTestHelper helper) {
        ItemStack stack = output().stack(null);
        ItemRequirement input = new ItemRequirement(RecipeModifier.IOType.INPUT, Ingredient.of(Items.DIAMOND), 1,
                stack, 1F, DataComponentPredicateSet.EMPTY, 1F);
        MachineRecipe recipe = MachineRecipe.fromCanonical(MMCR.id("recipe_serializer_components"),
                MMCR.id("test_cube"), 20, List.of(input), List.of(new MachineOutput.ItemOutput(stack, 1F)),
                List.of(), 0, 1, false, false, false, Set.of());
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(),
                helper.getLevel().registryAccess());
        try {
            MachineRecipeSerializer.INSTANCE.streamCodec().encode(buf, recipe);
            MachineRecipe decoded = MachineRecipeSerializer.INSTANCE.streamCodec().decode(buf);
            helper.assertTrue(decoded.id().equals(recipe.id()) && decoded.recipePoolId().equals(recipe.recipePoolId()),
                    "Recipe serialization must preserve recipe identity and pool");
            ItemStack decodedInput = ((ItemRequirement) decoded.requirements().getFirst()).stack();
            ItemStack decodedOutput = ((MachineOutput.ItemOutput) decoded.machineOutputs().getFirst()).stack();
            assertComponents(helper, decodedInput);
            assertComponents(helper, decodedOutput);
            helper.assertTrue(ItemStack.isSameItemSameComponents(stack, decodedInput)
                            && ItemStack.isSameItemSameComponents(stack, decodedOutput),
                    "Recipe serialization must preserve registry-backed input and output components");
            helper.assertTrue(!buf.isReadable(), "Recipe serializer must consume the entire network payload");
        } finally {
            buf.release();
        }
        helper.succeed();
    }

    public void cachedOutputSurvivesFinishReplacement(GameTestHelper helper) {
        ItemRequirement requirement = output();
        MachineOutput cached = OutputRegistry.fromRequirement(requirement);
        MachineOutput decoded = MachineOutput.CODEC.parse(JsonOps.INSTANCE,
                MachineOutput.CODEC.encodeStart(JsonOps.INSTANCE, cached).getOrThrow()).getOrThrow();
        MachineRecipe recipe = MachineRecipe.fromCanonical(MMCR.id("output_component_regression"),
                MMCR.id("test_cube"), 20, List.of(requirement), List.of(decoded),
                List.of(), 0, 1, false, false, false, Set.of());
        helper.assertTrue(recipe.machineOutputs().size() == 1 && recipe.runtimeRequirements().size() == 1,
                "Cached output must remain matched to its component-bearing requirement without duplicate outputs");
        helper.setBlock(BlockPos.ZERO, ModBlocks.controllerFor(MMCR.id("test_cube")).get().defaultBlockState());
        MachineControllerBlockEntity controller = helper.getBlockEntity(BlockPos.ZERO);
        controller.setMachine(MachineRegistry.getMachine(MMCR.id("test_cube")));
        var runtime = new CraftingRuntime(controller, controller.componentRuntime());
        var active = new ActiveMachineRecipe(recipe, 2L, new RecipeStartContext.ExecutionSnapshot(20,
                recipe.runtimeRequirements(), recipe.runtimeMachineOutputs()));
        active.setParallelism(2L);
        active.setInputConsumptionPlan(new ActiveMachineRecipe.InputConsumptionPlan(List.of(0)));
        runtime.restore(active, null, 0L, 0L, 0L, 0L);
        helper.assertTrue(runtime.activeRecipe() == active, "Effective component-bearing recipe must restore");
        var presentation = runtime.recipePresentation();
        active.setTick(1);
        helper.assertTrue(runtime.recipePresentation() == presentation, "Progress must reuse the recipe presentation");
        helper.assertTrue(presentation.outputs().getFirst().amount() == 2L, "Presentation must scale component-bearing output");
        ItemStack presented = ((MachineOutput.ItemOutput) presentation.outputs().getFirst().output()).stack();
        assertComponents(helper, presented);
        presented.setCount(64);
        presented.remove(DataComponents.ENCHANTMENTS);
        presented.remove(DataComponents.CUSTOM_NAME);
        ItemStack executionCopy = ((MachineOutput.ItemOutput) active.effectiveOutputs().getFirst()).stack();
        executionCopy.setCount(64);
        executionCopy.remove(DataComponents.CUSTOM_NAME);
        assertComponents(helper, ((MachineOutput.ItemOutput) runtime.recipePresentation().outputs().getFirst().output()).stack());
        assertComponents(helper, ((MachineOutput.ItemOutput) runtime.activeOutputs().getFirst()).resolvedStack());
        var storage = new ItemStackHandler(2);
        var context = new CraftingContext(new CapabilitySnapshot(List.of(new ItemBusCapability(storage, IOType.OUTPUT))));
        var plan = context.planOutputRequirements(recipe.runtimeRequirements(), recipe.runtimeMachineOutputs(), 1, false);
        helper.assertTrue(plan.successful() && plan.plan().commit(), "Finish output replacement must commit successfully");
        assertComponents(helper, storage.getStackInSlot(0));
        helper.assertTrue(storage.getStackInSlot(0).getCount() == 1, "Mutating output copies must not alter finish output quantity");
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

    private static void assertRegistryFallbackAndOwnership(GameTestHelper helper) {
        var originalOps = RegistryOps.create(JsonOps.INSTANCE, helper.getLevel().registryAccess());
        var missingRegistryOps = RegistryOps.create(JsonOps.INSTANCE, RegistryAccess.EMPTY);
        JsonObject enchantments = new JsonObject();
        JsonObject levels = new JsonObject();
        levels.addProperty("minecraft:sharpness", 4);
        enchantments.add("levels", levels);
        var exact = (ComponentPredicate.Exact) ComponentPredicate.exact(new Dynamic<>(originalOps, enchantments));
        var predicates = new DataComponentPredicateSet(Map.of(DataComponents.ENCHANTMENTS, exact));
        exact.value().convert(JsonOps.INSTANCE).getValue().getAsJsonObject().getAsJsonObject("levels")
                .addProperty("minecraft:sharpness", 1);

        ItemStack first = predicates.displayStack(Items.DIAMOND, 1, missingRegistryOps);
        var sharpness = helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(Enchantments.SHARPNESS);
        helper.assertTrue(first.get(DataComponents.ENCHANTMENTS).getLevel(sharpness) == 4,
                "Failed override registry decode must fall back to the original live registry");
        helper.assertTrue(predicates.matches(first, missingRegistryOps),
                "Matching must use original registry fallback after the override fails");
        helper.assertTrue(predicates.exactPatch().orElseThrow().get(DataComponents.ENCHANTMENTS)
                        .orElseThrow().getLevel(sharpness) == 4,
                "Exact patch must preserve original registry ops and defensive value ownership");
        first.remove(DataComponents.ENCHANTMENTS);
        ItemStack second = predicates.displayStack(Items.DIAMOND, 1, JsonOps.INSTANCE);
        helper.assertTrue(second.get(DataComponents.ENCHANTMENTS).getLevel(sharpness) == 4,
                "Plain JSON override must fall back without sharing materialized stack mutations");
        helper.assertTrue(predicates.matches(second, JsonOps.INSTANCE),
                "Matching with plain JSON ops must retain original registry fallback");
        helper.assertTrue(!predicates.matches(first, missingRegistryOps),
                "Removing the actual enchantment component must fail matching");
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
