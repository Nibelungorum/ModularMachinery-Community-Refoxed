package cn.howxu.mmcr.compat.jei;

import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.RecipeRegistry;
import cn.howxu.mmcr.internal.sync.RuntimeContentSnapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.core.RegistryAccess;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Collects and orders JEI machine recipe displays.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class MachineRecipeDisplays {

    private static final Comparator<MachineRecipeDisplay> ORDER = Comparator
            .comparing(MachineRecipeDisplay::machineId)
            .thenComparing(Comparator.comparingInt((MachineRecipeDisplay display) -> display.recipe().priority()).reversed())
            .thenComparing(MachineRecipeDisplay::recipeId);

    private MachineRecipeDisplays() {
    }

    public static MachineRecipeDisplay forRecipe(MachineRecipe recipe) {
        return MachineRecipeDisplay.from(recipe, registryAccess());
    }

    public static List<MachineRecipeDisplay> all() {
        RegistryAccess registryAccess = registryAccess();
        return RecipeRegistry.recipes().stream()
                .map(recipe -> MachineRecipeDisplay.from(recipe, registryAccess))
                .sorted(ORDER)
                .toList();
    }

    public static Map<Identifier, List<MachineRecipeDisplay>> byMachine() {
        return all().stream().collect(Collectors.groupingBy(
                MachineRecipeDisplay::machineId,
                LinkedHashMap::new,
                Collectors.toList()));
    }

    public static Map<Identifier, List<MachineRecipeDisplay>> byMachine(RuntimeContentSnapshot snapshot) {
        RegistryAccess registryAccess = registryAccess();
        return snapshot.recipes().values().stream()
                .map(recipe -> MachineRecipeDisplay.from(recipe, registryAccess))
                .sorted(ORDER)
                .collect(Collectors.groupingBy(
                        MachineRecipeDisplay::machineId,
                        LinkedHashMap::new,
                        Collectors.toList()));
    }

    private static RegistryAccess registryAccess() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft == null || minecraft.level == null ? null : minecraft.level.registryAccess();
    }
}
