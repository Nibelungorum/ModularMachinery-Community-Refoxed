package cn.howxu.mmcr.compat.jei;

import mezz.jei.api.recipe.types.IRecipeType;
import net.minecraft.resources.Identifier;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * JEI recipe type constants for MMCR.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class JeiMachineRecipeTypes {

    private static final Map<Identifier, IRecipeType<MachineRecipeDisplay>> TYPES = new ConcurrentHashMap<>();

    public static IRecipeType<MachineRecipeDisplay> forMachine(Identifier machineId) {
        return TYPES.computeIfAbsent(machineId, id -> IRecipeType.create(
                Identifier.fromNamespaceAndPath(id.getNamespace(), "machine_recipe/" + id.getPath()),
                MachineRecipeDisplay.class));
    }

    private JeiMachineRecipeTypes() {
    }
}
