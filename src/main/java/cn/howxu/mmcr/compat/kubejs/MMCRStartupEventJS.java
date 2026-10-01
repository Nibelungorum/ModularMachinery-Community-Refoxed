package cn.howxu.mmcr.compat.kubejs;

import cn.howxu.mmcr.api.machine.level.LevelSlot;
import cn.howxu.mmcr.api.machine.level.MachineLevelRegistry;
import cn.howxu.mmcr.api.controller.ControllerScreenTextRegistry;
import cn.howxu.mmcr.api.registration.StructureRegistration;
import cn.howxu.mmcr.api.jei.JeiWorkstationRegistration;
import cn.howxu.mmcr.api.machine.definition.ModifierDefinition;
import dev.latvian.mods.kubejs.event.KubeEvent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Startup-script MMCR declarations exposed by {@code MMCREvents.startup}.
 * The API is available as {@code event.getAPI()} in KubeJS.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class MMCRStartupEventJS implements KubeEvent {
    private final KubeJSApi api = new KubeJSApi();
    private final List<JeiWorkstationRegistration> jeiWorkstations = new ArrayList<>();

    public KubeJSApi getAPI() {
        return api;
    }

    public void registerControllerScreenText(String machineId, Consumer<ControllerScreenTextEventJS> handler) {
        ControllerScreenTextRegistry.register(ControllerScreenTextEventJS.parseResourceLocation(machineId, "machineId"),
                ControllerScreenTextEventJS.handler(handler));
    }

    public MachineBuilderJS createMachine(String id) {
        return new MachineBuilderJS(id);
    }

    public LevelTypeBuilderJS createLevelType(String id) {
        return new LevelTypeBuilderJS(id);
    }

    public MachineLevelBuilderJS createLevel(String id) {
        return new MachineLevelBuilderJS(id);
    }

    public LevelSlot levelSlot(String typeId) {
        var id = ResourceLocation.parse(typeId);
        if (MachineLevelRegistry.getType(id) == null) {
            throw new IllegalArgumentException("Unknown machine level type: " + typeId);
        }
        return new LevelSlot(id);
    }

    public void registerModifier(String id, ModifierDefinition definition) {
        ResourceLocation modifierId = ControllerScreenTextEventJS.parseResourceLocation(id, "modifierId");
        StructureRegistration.current().registerModifier(modifierId, definition);
    }

    public void registerModifierItem(ItemStack stack, String modifierId) {
        ResourceLocation parsedModifierId = ControllerScreenTextEventJS.parseResourceLocation(modifierId, "modifierId");
        StructureRegistration.current().registerModifierItem(stack, parsedModifierId);
    }

    public void addRecipePoolWorkstation(String recipePoolId, String itemId) {
        jeiWorkstations.add(new JeiWorkstationRegistration.RecipePoolItem(
                parseResourceLocation(recipePoolId, "recipePoolId"), parseResourceLocation(itemId, "itemId")));
    }

    public void addRecipePoolWorkstation(String recipePoolId, ItemStack workstation) {
        jeiWorkstations.add(new JeiWorkstationRegistration.RecipePoolStack(
                parseResourceLocation(recipePoolId, "recipePoolId"), workstation));
    }

    public void addMachineWorkstation(String machineId, String recipeTypeId) {
        jeiWorkstations.add(new JeiWorkstationRegistration.Machine(
                parseResourceLocation(machineId, "machineId"), parseResourceLocation(recipeTypeId, "recipeTypeId")));
    }

    public void addMachineWorkStation(String machineId, String recipeTypeId) {
        addMachineWorkstation(machineId, recipeTypeId);
    }

    List<JeiWorkstationRegistration> jeiWorkstations() {
        return List.copyOf(jeiWorkstations);
    }

    private static ResourceLocation parseResourceLocation(String value, String name) {
        return ControllerScreenTextEventJS.parseResourceLocation(value, name);
    }
}
