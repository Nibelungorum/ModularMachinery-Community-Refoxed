package cn.howxu.mmcr.compat.kubejs;

import cn.howxu.mmcr.api.machine.level.LevelSlot;
import cn.howxu.mmcr.api.machine.level.MachineLevelRegistry;
import cn.howxu.mmcr.api.publicapi.controller.ControllerScreenTextRegistry;
import cn.howxu.mmcr.api.publicapi.event.MMCRMachineStructuresEvent;
import cn.howxu.mmcr.api.publicapi.jei.JeiWorkstationRegistration;
import cn.howxu.mmcr.api.publicapi.machine.ModifierDefinition;
import dev.latvian.mods.kubejs.event.KubeEvent;
import net.minecraft.resources.Identifier;
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
        ControllerScreenTextRegistry.register(ControllerScreenTextEventJS.parseIdentifier(machineId, "machineId"),
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
        var id = Identifier.parse(typeId);
        if (MachineLevelRegistry.getType(id) == null) {
            throw new IllegalArgumentException("Unknown machine level type: " + typeId);
        }
        return new LevelSlot(id);
    }

    public void registerModifier(String id, ModifierDefinition definition) {
        Identifier modifierId = ControllerScreenTextEventJS.parseIdentifier(id, "modifierId");
        MMCRMachineStructuresEvent.current().registerModifier(modifierId, definition);
    }

    public void registerModifierItem(ItemStack stack, String modifierId) {
        Identifier parsedModifierId = ControllerScreenTextEventJS.parseIdentifier(modifierId, "modifierId");
        MMCRMachineStructuresEvent.current().registerModifierItem(stack, parsedModifierId);
    }

    public void addRecipePoolWorkstation(String recipePoolId, String itemId) {
        jeiWorkstations.add(new JeiWorkstationRegistration.RecipePoolItem(
                parseIdentifier(recipePoolId, "recipePoolId"), parseIdentifier(itemId, "itemId")));
    }

    public void addRecipePoolWorkstation(String recipePoolId, ItemStack workstation) {
        jeiWorkstations.add(new JeiWorkstationRegistration.RecipePoolStack(
                parseIdentifier(recipePoolId, "recipePoolId"), workstation));
    }

    public void addMachineWorkstation(String machineId, String recipeTypeId) {
        jeiWorkstations.add(new JeiWorkstationRegistration.Machine(
                parseIdentifier(machineId, "machineId"), parseIdentifier(recipeTypeId, "recipeTypeId")));
    }

    public void addMachineWorkStation(String machineId, String recipeTypeId) {
        addMachineWorkstation(machineId, recipeTypeId);
    }

    List<JeiWorkstationRegistration> jeiWorkstations() {
        return List.copyOf(jeiWorkstations);
    }

    private static Identifier parseIdentifier(String value, String name) {
        return ControllerScreenTextEventJS.parseIdentifier(value, name);
    }
}
