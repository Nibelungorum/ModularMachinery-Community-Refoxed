package cn.howxu.mmcr.internal.client;

import cn.howxu.mmcr.api.jei.JeiWorkstationRegistration;
import cn.howxu.mmcr.api.presentation.ComponentSnapshots;
import cn.howxu.mmcr.api.recipe.RecipeInformation;
import cn.howxu.mmcr.client.recipe.MachineRecipeData;
import cn.howxu.mmcr.compat.jei.MachineStructureDisplay;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** One detached, ordered recipe-viewer registration cycle.
 * @author howxu <dev@howxu.cn>
 */
public final class RecipeViewerSnapshot {
    public record Version(long session, long content, long information, long registration) { }

    private final Version version;
    private final Map<ResourceLocation, List<MachineRecipeData>> recipesByPool;
    private final List<MachineStructureDisplay> structures;
    private final Map<ResourceLocation, List<ResourceLocation>> machinesByPool;
    private final List<JeiWorkstationRegistration> workstations;
    private final Map<ResourceLocation, List<Component>> poolInformation;
    private final Map<ResourceLocation, List<Component>> recipeInformation;

    RecipeViewerSnapshot(Version version, Map<ResourceLocation, List<MachineRecipeData>> recipesByPool,
                         List<MachineStructureDisplay> structures,
                         Map<ResourceLocation, List<ResourceLocation>> machinesByPool,
                         List<JeiWorkstationRegistration> workstations,
                         List<RecipeInformation> publicInformation, List<RecipeInformation> kubeJSInformation) {
        this.version = version;
        this.recipesByPool = copyLists(recipesByPool);
        this.structures = List.copyOf(structures);
        this.machinesByPool = copyLists(machinesByPool);
        this.workstations = List.copyOf(workstations);
        poolInformation = information(RecipeInformation.Target.RECIPE_POOL, publicInformation, kubeJSInformation);
        recipeInformation = information(RecipeInformation.Target.RECIPE, publicInformation, kubeJSInformation);
    }

    public Version version() { return version; }
    public Map<ResourceLocation, List<MachineRecipeData>> recipesByPool() { return recipesByPool; }
    public List<MachineStructureDisplay> structures() { return structures; }
    public Map<ResourceLocation, List<ResourceLocation>> machinesByPool() { return machinesByPool; }
    public List<JeiWorkstationRegistration> workstations() { return workstations; }

    public List<Component> informationFor(ResourceLocation poolId, ResourceLocation recipeId) {
        List<Component> result = new ArrayList<>();
        poolInformation.getOrDefault(poolId, List.of()).forEach(text -> result.add(ComponentSnapshots.copy(text)));
        recipeInformation.getOrDefault(recipeId, List.of()).forEach(text -> result.add(ComponentSnapshots.copy(text)));
        return List.copyOf(result);
    }

    private static Map<ResourceLocation, List<Component>> information(RecipeInformation.Target target,
            List<RecipeInformation> publicInformation, List<RecipeInformation> kubeJSInformation) {
        Map<ResourceLocation, List<Component>> result = new LinkedHashMap<>();
        for (List<RecipeInformation> source : List.of(publicInformation, kubeJSInformation)) {
            source.stream().filter(entry -> entry.target() == target).forEach(entry ->
                    result.computeIfAbsent(entry.targetId(), ignored -> new ArrayList<>())
                            .add(ComponentSnapshots.copy(entry.component())));
        }
        return copyLists(result);
    }

    private static <T> Map<ResourceLocation, List<T>> copyLists(Map<ResourceLocation, List<T>> source) {
        Map<ResourceLocation, List<T>> result = new LinkedHashMap<>();
        source.forEach((id, values) -> result.put(id, List.copyOf(values)));
        return Collections.unmodifiableMap(result);
    }
}
