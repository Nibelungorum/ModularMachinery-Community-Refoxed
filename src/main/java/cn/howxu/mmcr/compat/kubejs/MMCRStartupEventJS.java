package cn.howxu.mmcr.compat.kubejs;

import cn.howxu.mmcr.api.machine.level.LevelSlot;
import cn.howxu.mmcr.api.controller.ControllerScreenTextRegistry;
import cn.howxu.mmcr.api.registration.StructureRegistration;
import cn.howxu.mmcr.api.jei.JeiWorkstationRegistration;
import cn.howxu.mmcr.api.machine.definition.ModifierDefinition;
import dev.latvian.mods.kubejs.event.KubeEvent;
import net.minecraft.resources.Identifier;

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
        registerControllerScreenText(parseIdentifier(machineId, "machineId"), handler);
    }

    public void registerControllerScreenText(Identifier machineId, Consumer<ControllerScreenTextEventJS> handler) {
        ControllerScreenTextRegistry.register(machineId,
                ControllerScreenTextEventJS.handler(handler));
    }

    public MachineBuilderJS createMachine(String id) {
        return createMachine(Identifier.parse(id));
    }

    public MachineBuilderJS createMachine(Identifier id) {
        return new MachineBuilderJS(id);
    }

    public LevelTypeBuilderJS createLevelType(String id) {
        return createLevelType(Identifier.parse(id));
    }

    public LevelTypeBuilderJS createLevelType(Identifier id) {
        return new LevelTypeBuilderJS(id);
    }

    public MachineLevelBuilderJS createLevel(String id) {
        return createLevel(Identifier.parse(id));
    }

    public MachineLevelBuilderJS createLevel(Identifier id) {
        return new MachineLevelBuilderJS(id);
    }

    public LevelSlot levelSlot(String typeId) {
        return levelSlot(Identifier.parse(typeId));
    }

    public LevelSlot levelSlot(Identifier typeId) {
        return new LevelSlot(typeId);
    }

    public void registerModifier(String id, ModifierDefinition definition) {
        registerModifier(parseIdentifier(id, "modifierId"), definition);
    }

    public void registerModifier(Identifier id, ModifierDefinition definition) {
        StructureRegistration.current().registerModifier(id, definition);
    }

    public void registerModifierItem(String itemId, String modifierId) {
        Identifier parsedModifierId = ControllerScreenTextEventJS.parseIdentifier(modifierId, "modifierId");
        registerModifierItem(parseIdentifier(itemId, "itemId"), parsedModifierId);
    }

    public void registerModifierItem(Identifier itemId, Identifier modifierId) {
        StructureRegistration.current().registerModifierItem(itemId, modifierId);
    }

    public void addRecipePoolWorkstation(String recipePoolId, String itemId) {
        addRecipePoolWorkstation(parseIdentifier(recipePoolId, "recipePoolId"), parseIdentifier(itemId, "itemId"));
    }

    public void addRecipePoolWorkstation(Identifier recipePoolId, Identifier itemId) {
        jeiWorkstations.add(new JeiWorkstationRegistration.RecipePoolItem(recipePoolId, itemId));
    }

    public void addMachineWorkstation(String machineId, String recipeTypeId) {
        addMachineWorkstation(parseIdentifier(machineId, "machineId"), parseIdentifier(recipeTypeId, "recipeTypeId"));
    }

    public void addMachineWorkstation(Identifier machineId, Identifier recipeTypeId) {
        jeiWorkstations.add(new JeiWorkstationRegistration.Machine(machineId, recipeTypeId));
    }

    public void addMachineWorkStation(String machineId, String recipeTypeId) {
        addMachineWorkstation(machineId, recipeTypeId);
    }

    public void addMachineWorkStation(Identifier machineId, Identifier recipeTypeId) {
        addMachineWorkstation(machineId, recipeTypeId);
    }

    List<JeiWorkstationRegistration> jeiWorkstations() {
        return List.copyOf(jeiWorkstations);
    }

    private static Identifier parseIdentifier(String value, String name) {
        return ControllerScreenTextEventJS.parseIdentifier(value, name);
    }
}
