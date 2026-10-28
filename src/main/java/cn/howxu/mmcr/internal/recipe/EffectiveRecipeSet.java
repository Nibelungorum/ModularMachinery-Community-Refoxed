package cn.howxu.mmcr.internal.recipe;

import cn.howxu.mmcr.api.machine.modifier.MachineModifier;
import cn.howxu.mmcr.api.recipe.EffectiveRecipe;
import cn.howxu.mmcr.api.recipe.EffectiveRecipeResolver;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.RecipeRegistry;
import cn.howxu.mmcr.internal.runtime.ControllerRuntimeSnapshot;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Immutable worker result for all effective candidates of one controller version key.
 * @author howxu <dev@howxu.cn>
 */
public record EffectiveRecipeSet(Key key, List<EffectiveRecipe> recipes) {
    public EffectiveRecipeSet {
        recipes = List.copyOf(recipes == null ? List.of() : recipes);
    }

    public static EffectiveRecipeSet resolve(ControllerRuntimeSnapshot snapshot, long catalogVersion,
                                             List<MachineRecipe> recipes, List<MachineModifier> modifiers) {
        Key key = new Key(snapshot.structure().version(), snapshot.capabilityVersion(), snapshot.modifierVersion(),
                snapshot.stateVersion(), catalogVersion);
        EffectiveRecipeResolver resolver = new EffectiveRecipeResolver();
        return new EffectiveRecipeSet(key, (recipes == null ? List.<MachineRecipe>of() : recipes).stream()
                .map(recipe -> resolver.resolve(recipe, snapshot, modifiers)).toList());
    }

    public record Key(long structureVersion, long capabilityVersion, long modifierVersion,
                      long stateVersion, long catalogVersion) {
    }

    /** Controller-local cache shared by async searches for the same immutable input versions. */
    public static final class Cache {
        private @Nullable Key key;
        private @Nullable ResourceLocation pool;
        private long upgradeRevision;
        private long maxParallelism;
        private int laneLimit;
        private List<MachineRecipe> sources = List.of();
        private List<MachineModifier> modifiers = List.of();
        private final Map<ResourceLocation, EffectiveRecipe> recipesById = new HashMap<>();
        private final EffectiveRecipeResolver resolver = new EffectiveRecipeResolver();
        private @Nullable EffectiveRecipeSet value;

        public synchronized EffectiveRecipeSet resolve(ControllerRuntimeSnapshot snapshot, long catalogVersion,
                                                       List<MachineRecipe> recipes,
                                                       List<MachineModifier> machineModifiers) {
            List<MachineRecipe> copiedRecipes = List.copyOf(recipes == null ? List.of() : recipes);
            prepare(snapshot, catalogVersion, copiedRecipes.isEmpty() ? pool : copiedRecipes.getFirst().recipePoolId(),
                    machineModifiers);
            if (copiedRecipes.equals(sources) && value != null) return value;
            List<EffectiveRecipe> resolved = copiedRecipes.stream()
                    .map(recipe -> resolveOne(snapshot, catalogVersion, recipe, machineModifiers)).toList();
            sources = copiedRecipes;
            value = new EffectiveRecipeSet(key, resolved);
            return value;
        }

        public synchronized EffectiveRecipe resolveOne(ControllerRuntimeSnapshot snapshot, long catalogVersion,
                                                        MachineRecipe recipe, List<MachineModifier> machineModifiers) {
            prepare(snapshot, catalogVersion, recipe.recipePoolId(), machineModifiers);
            EffectiveRecipe cached = recipesById.get(recipe.id());
            if (cached != null && cached.source() == recipe) return cached;
            EffectiveRecipe resolved = resolver.resolve(recipe, snapshot, modifiers);
            // Only the current catalog owns long-lived entries; temporary pattern recipes cannot grow this map.
            if (RecipeRegistry.getRecipe(recipe.id()) == recipe) recipesById.put(recipe.id(), resolved);
            return resolved;
        }

        private void prepare(ControllerRuntimeSnapshot snapshot, long catalogVersion, @Nullable ResourceLocation requestedPool,
                             List<MachineModifier> machineModifiers) {
            List<MachineModifier> requestedModifiers = List.copyOf(machineModifiers == null ? List.of() : machineModifiers);
            Key requestedKey = new Key(snapshot.structure().version(), snapshot.capabilityVersion(),
                    snapshot.modifierVersion(), snapshot.stateVersion(), catalogVersion);
            if (requestedKey.equals(key) && Objects.equals(pool, requestedPool)
                    && upgradeRevision == snapshot.upgradeContentRevision()
                    && maxParallelism == snapshot.maxParallelism() && laneLimit == snapshot.factory().laneLimit()
                    && requestedModifiers.equals(modifiers)) return;
            key = requestedKey;
            pool = requestedPool;
            upgradeRevision = snapshot.upgradeContentRevision();
            maxParallelism = snapshot.maxParallelism();
            laneLimit = snapshot.factory().laneLimit();
            modifiers = requestedModifiers;
            sources = List.of();
            value = null;
            recipesById.clear();
        }
    }
}
