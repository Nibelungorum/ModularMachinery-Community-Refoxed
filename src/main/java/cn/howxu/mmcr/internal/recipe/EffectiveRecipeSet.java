package cn.howxu.mmcr.internal.recipe;

import cn.howxu.mmcr.api.machine.modifier.MachineModifier;
import cn.howxu.mmcr.api.recipe.EffectiveRecipe;
import cn.howxu.mmcr.api.recipe.EffectiveRecipeResolver;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.RecipeRegistry;
import cn.howxu.mmcr.internal.runtime.ControllerRuntimeSnapshot;

import net.minecraft.resources.Identifier;
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
        private @Nullable Identifier pool;
        private long upgradeRevision;
        private long maxParallelism;
        private int laneLimit;
        private List<Cached> last = List.of();
        private List<MachineModifier> modifiers = List.of();
        private final Map<Identifier, Cached> recipesById = new HashMap<>();
        private final EffectiveRecipeResolver resolver = new EffectiveRecipeResolver();
        private @Nullable EffectiveRecipeSet value;

        public synchronized EffectiveRecipeSet resolve(ControllerRuntimeSnapshot snapshot, long catalogVersion,
                                                       List<MachineRecipe> recipes,
                                                       List<MachineModifier> machineModifiers) {
            List<MachineRecipe> copiedRecipes = List.copyOf(recipes == null ? List.of() : recipes);
            prepare(snapshot, catalogVersion, copiedRecipes.isEmpty() ? pool : copiedRecipes.getFirst().recipePoolId(),
                    machineModifiers);
            boolean same = value != null && copiedRecipes.size() == last.size();
            for (int i = 0; same && i < last.size(); i++) {
                same = last.get(i).current(copiedRecipes.get(i));
            }
            if (same) return value;
            var entries = copiedRecipes.stream().map(recipe -> entry(snapshot, recipe)).toList();
            var result = new EffectiveRecipeSet(key, entries.stream().map(Cached::view).toList());
            boolean stable = entries.stream().allMatch(entry -> entry.evidence().current());
            value = stable ? result : null;
            last = stable ? entries : List.of();
            return result;
        }

        public synchronized EffectiveRecipe resolveOne(ControllerRuntimeSnapshot snapshot, long catalogVersion,
                                                        MachineRecipe recipe, List<MachineModifier> machineModifiers) {
            prepare(snapshot, catalogVersion, recipe.recipePoolId(), machineModifiers);
            return entry(snapshot, recipe).view();
        }

        private record Cached(EffectiveRecipe view, DerivationEvidence evidence) {
            boolean current(MachineRecipe source) { return source == view.source() && evidence.current(); }
        }

        private Cached entry(ControllerRuntimeSnapshot snapshot, MachineRecipe recipe) {
            var cached = recipesById.get(recipe.id());
            if (cached != null && cached.current(recipe)) return cached;
            recipesById.remove(recipe.id());
            var evidence = new DerivationEvidence(recipe);
            var view = resolver.resolve(recipe, snapshot, modifiers, evidence::record);
            view.outputs().forEach(evidence::record);
            var entry = new Cached(view, evidence);
            if (RecipeRegistry.getRecipe(recipe.id()) == recipe && evidence.current()) recipesById.put(recipe.id(), entry);
            return entry;
        }

        private void prepare(ControllerRuntimeSnapshot snapshot, long catalogVersion, @Nullable Identifier requestedPool,
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
            last = List.of();
            value = null;
            recipesById.clear();
        }
    }
}
