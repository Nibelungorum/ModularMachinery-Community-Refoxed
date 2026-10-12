package cn.howxu.mmcr.compat.emi;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.jei.JeiWorkstationRegistration;
import cn.howxu.mmcr.internal.client.RecipeViewerSnapshot;
import cn.howxu.mmcr.registry.ModBlocks;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.recipe.VanillaEmiRecipeCategories;
import dev.emi.emi.api.stack.Comparison;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.registry.EmiRecipes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;

/** Workstation associations resolved exclusively against this registration cycle.
 * @author howxu <dev@howxu.cn>
 */
public final class EmiWorkstations {
    private EmiWorkstations() { }

    public static void register(EmiRegistry registry, RecipeViewerSnapshot snapshot, Map<ResourceLocation, EmiRecipeCategory> pools) {
        Map<ResourceLocation, ItemStack> controllers = new LinkedHashMap<>();
        snapshot.machinesByPool().values().forEach(ids -> ids.forEach(id -> controller(controllers, id)));
        snapshot.workstations().forEach(entry -> {
            if (entry instanceof JeiWorkstationRegistration.Machine machine) controller(controllers, machine.machineId());
        });
        // Vanilla fields are initialized by EMI's plugin. Do not cache their initial null values.
        registry.addDeferredRecipes(ignored -> collect(pools, snapshot.machinesByPool(), controllers,
                snapshot.workstations(), EmiRecipes.categories).forEach((category, stacks) -> stacks.forEach(stack ->
                registry.addWorkstation(category, EmiStack.of(stack.copy()).comparison(Comparison.compareComponents())))));
    }

    private static void controller(Map<ResourceLocation, ItemStack> controllers, ResourceLocation id) {
        if (ModBlocks.hasControllerFor(id)) controllers.putIfAbsent(id, new ItemStack(ModBlocks.controllerFor(id).get()));
    }

    static Map<EmiRecipeCategory, List<ItemStack>> collect(Map<ResourceLocation, EmiRecipeCategory> pools,
            Map<ResourceLocation, List<ResourceLocation>> machinesByPool, Map<ResourceLocation, ItemStack> controllers,
            List<JeiWorkstationRegistration> declarations, Collection<EmiRecipeCategory> categories) {
        Map<EmiRecipeCategory, List<ItemStack>> result = new LinkedHashMap<>();
        machinesByPool.forEach((pool, machines) -> {
            EmiRecipeCategory category = pools.get(pool);
            if (category != null) machines.forEach(machine -> add(result, category, controllers.get(machine)));
        });
        declarations.forEach(entry -> {
            switch (entry) {
                case JeiWorkstationRegistration.RecipePoolItem item -> {
                    EmiRecipeCategory category = pools.get(item.recipePoolId());
                    if (category == null || !BuiltInRegistries.ITEM.containsKey(item.itemId())) {
                        MMCR.LOG.warn("Skipping EMI workstation item {} for unknown pool/item {}", item.itemId(), item.recipePoolId());
                    } else add(result, category, new ItemStack(BuiltInRegistries.ITEM.get(item.itemId())));
                }
                case JeiWorkstationRegistration.RecipePoolStack stack -> {
                    EmiRecipeCategory category = pools.get(stack.recipePoolId());
                    if (category == null) MMCR.LOG.warn("Skipping EMI workstation for unknown pool {}", stack.recipePoolId());
                    else add(result, category, stack.workstation());
                }
                case JeiWorkstationRegistration.Machine machine -> {
                    ItemStack controller = controllers.get(machine.machineId());
                    EmiRecipeCategory category = pools.get(machine.recipeTypeId());
                    if (category == null) category = resolveCategory(machine.recipeTypeId(), categories);
                    if (controller == null || category == null) {
                        MMCR.LOG.warn("Skipping EMI workstation for unknown machine/category {} -> {}", machine.machineId(), machine.recipeTypeId());
                    } else add(result, category, controller);
                }
            }
        });
        return result;
    }

    private static void add(Map<EmiRecipeCategory, List<ItemStack>> result, EmiRecipeCategory category, @Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;
        List<ItemStack> stacks = result.computeIfAbsent(category, ignored -> new ArrayList<>());
        if (stacks.stream().noneMatch(existing -> ItemStack.isSameItemSameComponents(existing, stack))) stacks.add(stack.copyWithCount(1));
    }

    public static @Nullable EmiRecipeCategory resolveCategory(ResourceLocation id, Collection<EmiRecipeCategory> categories) {
        if (id.getNamespace().equals("minecraft")) {
            EmiRecipeCategory vanilla = switch (id.getPath()) {
                case "crafting" -> VanillaEmiRecipeCategories.CRAFTING;
                case "smelting" -> VanillaEmiRecipeCategories.SMELTING;
                case "blasting" -> VanillaEmiRecipeCategories.BLASTING;
                case "smoking" -> VanillaEmiRecipeCategories.SMOKING;
                case "campfire_cooking" -> VanillaEmiRecipeCategories.CAMPFIRE_COOKING;
                case "stonecutting" -> VanillaEmiRecipeCategories.STONECUTTING;
                case "smithing" -> VanillaEmiRecipeCategories.SMITHING;
                case "brewing" -> VanillaEmiRecipeCategories.BREWING;
                case "anvil" -> VanillaEmiRecipeCategories.ANVIL_REPAIRING;
                case "fuel" -> VanillaEmiRecipeCategories.FUEL;
                default -> null;
            };
            if (vanilla != null) return vanilla;
        }
        return categories.stream().filter(category -> id.equals(category.getId())).findFirst().orElse(null);
    }
}
