package cn.howxu.mmcr.internal.registration;

import cn.howxu.mmcr.api.machine.MachineDefinitions;
import cn.howxu.mmcr.api.machine.MachineRegistration;
import cn.howxu.mmcr.api.machine.MachineRegistry;
import cn.howxu.mmcr.api.machine.MachineRoleValidator;
import cn.howxu.mmcr.api.machine.MachineStructureDefinition;
import cn.howxu.mmcr.api.machine.MachineStructureRegistry;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.MachineRecipeJson;
import cn.howxu.mmcr.api.recipe.RecipeRegistry;
import cn.howxu.mmcr.api.recipe.modifier.ModifierRegistry;
import cn.howxu.mmcr.internal.reload.DynamicContentReloadService;
import cn.howxu.mmcr.internal.network.ControllerSpecSync;
import cn.howxu.mmcr.internal.sync.RuntimeContentSnapshot;
import cn.howxu.mmcr.internal.sync.RuntimeContentVersion;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/** Commits runtime content layers as validated reload transactions.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class RuntimeContentCoordinator {
    private RuntimeContentCoordinator() {
    }

    public static DynamicContentReloadService.ReloadResult commitDynamic(
            Map<ResourceLocation, MachineStructureDefinition> structures,
            Map<ResourceLocation, MachineRecipe> recipes) {
        synchronized (RuntimeContentVersion.lock()) {
            return commitDynamicLocked(structures, recipes).result();
        }
    }

    public static CommitResult commitCurrentDynamicAndSnapshot() {
        synchronized (RuntimeContentVersion.lock()) {
            return commitDynamicLocked(MachineStructureRegistry.dynamicSnapshot(), RecipeRegistry.dynamicSnapshot());
        }
    }

    private static CommitResult commitDynamicLocked(
            Map<ResourceLocation, MachineStructureDefinition> structures,
            Map<ResourceLocation, MachineRecipe> recipes) {
        Map<ResourceLocation, MachineStructureDefinition> oldStructures = MachineStructureRegistry.dynamicSnapshot();
        Map<ResourceLocation, MachineRecipe> oldRecipes = RecipeRegistry.dynamicSnapshot();
        Map<ResourceLocation, MachineStructureDefinition> structureReplacement = Map.copyOf(new LinkedHashMap<>(structures));
        Map<ResourceLocation, MachineRecipe> candidateRecipes = Map.copyOf(new LinkedHashMap<>(recipes));
        validate(structureReplacement, candidateRecipes);
        Predicate<ResourceLocation> poolAvailable = poolId -> recipePoolAvailable(poolId, structureReplacement);
        RecipeRegistry.DynamicCandidate dynamicCandidate = RecipeRegistry.validateDynamicCandidate(
                candidateRecipes, poolAvailable);
        List<MachineRecipeJson.RecipeJsonException> recipeErrors = new ArrayList<>(dynamicCandidate.errors());
        Map<ResourceLocation, MachineRecipe> recipeReplacement = dynamicCandidate.acceptedRecipes();

        try {
            MachineStructureRegistry.replaceDynamic(structureReplacement);
            RecipeRegistry.replaceDynamic(recipeReplacement, poolAvailable);
            Map<ResourceLocation, MachineRecipe> publishedRecipes = RecipeRegistry.dynamicSnapshot();
            DynamicContentReloadService.ReloadResult result = DynamicContentReloadService.ReloadResult.fromSnapshots(
                    oldStructures, structureReplacement, oldRecipes, publishedRecipes, recipeErrors);
            return new CommitResult(result, snapshotLocked());
        } catch (RuntimeException | Error failure) {
            try {
                MachineStructureRegistry.replaceDynamic(oldStructures);
                RecipeRegistry.replaceDynamic(oldRecipes);
            } catch (RuntimeException | Error rollbackFailure) {
                failure.addSuppressed(rollbackFailure);
            }
            throw failure;
        }
    }

    public static CommitResult commitDynamicAndSnapshot(
            Map<ResourceLocation, MachineStructureDefinition> structures,
            Map<ResourceLocation, MachineRecipe> recipes) {
        synchronized (RuntimeContentVersion.lock()) {
            return commitDynamicLocked(structures, recipes);
        }
    }

    public static boolean hasPublishableDynamicContent(
            Map<ResourceLocation, MachineStructureDefinition> structures,
            Map<ResourceLocation, MachineRecipe> recipes,
            Set<ResourceLocation> transactionStructureIds,
            Set<ResourceLocation> transactionRecipeIds) {
        synchronized (RuntimeContentVersion.lock()) {
            Map<ResourceLocation, MachineStructureDefinition> structureCandidate = Map.copyOf(new LinkedHashMap<>(structures));
            Map<ResourceLocation, MachineRecipe> recipeCandidate = Map.copyOf(new LinkedHashMap<>(recipes));
            validate(structureCandidate, recipeCandidate);
            if (!transactionStructureIds.isEmpty()) return true;
            return RecipeRegistry.validateDynamicCandidate(recipeCandidate,
                            poolId -> recipePoolAvailable(poolId, structureCandidate))
                    .acceptedRecipes().keySet().stream().anyMatch(transactionRecipeIds::contains);
        }
    }

    public static void replaceDataPackRecipes(Map<ResourceLocation, MachineRecipe> recipes) {
        synchronized (RuntimeContentVersion.lock()) {
            replaceDataPackLocked(recipes);
        }
    }

    public static RuntimeContentSnapshot replaceDataPackRecipesAndSnapshot(
            Map<ResourceLocation, MachineRecipe> recipes) {
        synchronized (RuntimeContentVersion.lock()) {
            Map<ResourceLocation, MachineRecipe> previous = RecipeRegistry.dataPackSnapshot();
            boolean published = false;
            try {
                RecipeRegistry.validateDataPackCandidate(recipes);
                replaceDataPackLocked(recipes);
                published = true;
                return snapshotLocked();
            } catch (RuntimeException | Error failure) {
                if (published) {
                    try {
                        replaceDataPackLocked(previous);
                    } catch (RuntimeException | Error rollbackFailure) {
                        failure.addSuppressed(rollbackFailure);
                    }
                }
                throw failure;
            }
        }
    }

    public static RuntimeContentSnapshot replaceKubeJSRecipesAndSnapshot(
            Map<ResourceLocation, MachineRecipe> recipes) {
        synchronized (RuntimeContentVersion.lock()) {
            Map<ResourceLocation, MachineRecipe> previous = RecipeRegistry.kubeJSSnapshot();
            boolean published = false;
            try {
                RecipeRegistry.replaceKubeJS(recipes);
                published = true;
                return snapshotLocked();
            } catch (RuntimeException | Error failure) {
                if (published) {
                    try {
                        RecipeRegistry.replaceKubeJS(previous);
                    } catch (RuntimeException | Error rollbackFailure) {
                        failure.addSuppressed(rollbackFailure);
                    }
                }
                throw failure;
            }
        }
    }

    public static RuntimeContentSnapshot createSnapshot() {
        synchronized (RuntimeContentVersion.lock()) {
            return snapshotLocked();
        }
    }

    private static RuntimeContentSnapshot snapshotLocked() {
        return new RuntimeContentSnapshot(
                MachineStructureRegistry.effectiveSnapshot(),
                RecipeRegistry.effectiveSnapshot(),
                ControllerSpecSync.createSnapshot(),
                ControllerSpecSync.createAppearanceSnapshot(),
                machineRecipePools(),
                RuntimeContentVersion.current());
    }

    private static Map<ResourceLocation, List<ResourceLocation>> machineRecipePools() {
        Map<ResourceLocation, List<ResourceLocation>> pools = new LinkedHashMap<>();
        MachineDefinitions.allRegistrations().forEach(registration ->
                pools.put(registration.id(), registration.recipePoolIds()));
        return Map.copyOf(pools);
    }

    private static void replaceDataPackLocked(Map<ResourceLocation, MachineRecipe> recipes) {
        RecipeRegistry.replaceDataPack(recipes);
    }

    private static void validate(Map<ResourceLocation, MachineStructureDefinition> structures,
                                 Map<ResourceLocation, MachineRecipe> recipes) {
        Map<ResourceLocation, MachineRegistration> registrations = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, MachineStructureDefinition> entry : structures.entrySet()) {
            ResourceLocation id = entry.getKey();
            MachineStructureDefinition structure = entry.getValue();
            MachineRegistration registration = MachineDefinitions.getRegistration(id);
            if (registration == null) {
                throw new IllegalStateException("No startup machine registration for structure: " + id);
            }
            if (!id.equals(structure.machineId())) {
                throw new IllegalStateException("Structure key does not match machine id: " + id + " != " + structure.machineId());
            }
            structure.declarations().forEach(declaration -> declaration.requirements().modifierReplacements().values()
                    .stream().flatMap(Collection::stream)
                    .forEach(replacement -> {
                        ResourceLocation modifierId = replacement.getModifierId();
                        if (ModifierRegistry.get(modifierId) == null) {
                            throw new IllegalStateException("Structure " + id
                                    + " refers to unknown machine modifier " + modifierId);
                        }
                    }));
            registrations.put(id, registration.withPattern(structure.pattern()));
        }
        MachineRoleValidator.validate(registrations.values(), null);
        MachineRoleValidator.validateCouplerCounts(registrations.values(), id -> {
            MachineStructureDefinition structure = structures.get(id);
            return structure == null ? null : structure.pattern();
        });
        for (Map.Entry<ResourceLocation, MachineRecipe> entry : recipes.entrySet()) {
            ResourceLocation recipeId = entry.getKey();
            MachineRecipe recipe = entry.getValue();
            if (recipeId == null || recipe == null || recipe.id() == null || !recipeId.equals(recipe.id())) {
                throw new IllegalStateException("Recipe key does not match recipe id: "
                        + recipeId + " != " + (recipe == null ? null : recipe.id()));
            }
            if (RecipeRegistry.containsStatic(recipe.id())) {
                throw new IllegalStateException("Dynamic recipe conflicts with static recipe: " + recipe.id());
            }
            if (RecipeRegistry.dataPackSnapshot().containsKey(recipe.id())) {
                throw new IllegalStateException("Dynamic recipe conflicts with data-pack recipe: " + recipe.id());
            }
        }
    }

    private static boolean recipePoolAvailable(ResourceLocation recipePoolId,
                                               Map<ResourceLocation, MachineStructureDefinition> structures) {
        MachineRegistration directRegistration = MachineDefinitions.getRegistration(recipePoolId);
        if (MachineRegistry.containsStatic(recipePoolId)
                && (directRegistration == null || directRegistration.recipePoolIds().contains(recipePoolId))) return true;
        return MachineDefinitions.allRegistrations().stream()
                .filter(registration -> registration.recipePoolIds().contains(recipePoolId))
                .anyMatch(registration -> structures.containsKey(registration.id())
                        || MachineStructureRegistry.startupSnapshot().containsKey(registration.id())
                        || MachineRegistry.containsStatic(registration.id()));
    }

    public record CommitResult(DynamicContentReloadService.ReloadResult result,
                               RuntimeContentSnapshot snapshot) {
    }
}
