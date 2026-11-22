package cn.howxu.mmcr.compat.kubejs;

import cn.howxu.mmcr.api.machine.level.LevelSlot;
import cn.howxu.mmcr.api.controller.ControllerScreenTextRegistry;
import cn.howxu.mmcr.internal.registration.ContentRegistrationCoordinator;
import cn.howxu.mmcr.api.jei.JeiWorkstationRegistration;
import cn.howxu.mmcr.api.machine.modifier.ModifierStacking;
import cn.howxu.mmcr.api.machine.definition.ModifierDefinition;
import dev.latvian.mods.kubejs.event.KubeEvent;
import net.minecraft.resources.ResourceLocation;

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
        registerControllerScreenText(parseResourceLocation(machineId, "machineId"), handler);
    }

    public void registerControllerScreenText(ResourceLocation machineId, Consumer<ControllerScreenTextEventJS> handler) {
        ControllerScreenTextRegistry.register(machineId,
                ControllerScreenTextEventJS.handler(handler));
    }

    public MachineBuilderJS createMachine(String id) {
        return createMachine(ResourceLocation.parse(id));
    }

    public MachineBuilderJS createMachine(ResourceLocation id) {
        return new MachineBuilderJS(id);
    }

    public LevelTypeBuilderJS createLevelType(String id) {
        return createLevelType(ResourceLocation.parse(id));
    }

    public LevelTypeBuilderJS createLevelType(ResourceLocation id) {
        return new LevelTypeBuilderJS(id);
    }

    public MachineLevelBuilderJS createLevel(String id) {
        return createLevel(ResourceLocation.parse(id));
    }

    public MachineLevelBuilderJS createLevel(ResourceLocation id) {
        return new MachineLevelBuilderJS(id);
    }

    public LevelSlot levelSlot(String typeId) {
        return levelSlot(ResourceLocation.parse(typeId));
    }

    public LevelSlot levelSlot(ResourceLocation typeId) {
        return new LevelSlot(typeId);
    }

    public void registerModifier(String id, ModifierDefinition definition) {
        registerModifier(parseResourceLocation(id, "modifierId"), definition);
    }

    public void registerModifier(ResourceLocation id, ModifierDefinition definition) {
        ContentRegistrationCoordinator.startupDefinitions().registerModifier(id, definition);
    }

    public void registerModifier(String id, ModifierDefinition definition, boolean stackable) {
        registerModifier(parseResourceLocation(id, "modifierId"), definition, stackable);
    }

    public void registerModifier(ResourceLocation id, ModifierDefinition definition, boolean stackable) {
        registerModifier(id, definition, stackable, "add");
    }

    public void registerModifier(String id, ModifierDefinition definition, boolean stackable, String blockMode) {
        registerModifier(parseResourceLocation(id, "modifierId"), definition, stackable, blockMode);
    }

    public void registerModifier(ResourceLocation id, ModifierDefinition definition, boolean stackable, String blockMode) {
        ModifierStacking policy = switch (blockMode) {
            case "add" -> ModifierStacking.ADD;
            case "multiply" -> ModifierStacking.MULTIPLY;
            default -> throw new IllegalArgumentException("Unknown block stacking mode: " + blockMode);
        };
        ContentRegistrationCoordinator.startupDefinitions().registerModifier(id, definition, stackable ? policy : ModifierStacking.UNIQUE);
    }

    public void registerModifierItem(String itemId, String modifierId) {
        ResourceLocation parsedModifierId = ControllerScreenTextEventJS.parseResourceLocation(modifierId, "modifierId");
        registerModifierItem(parseResourceLocation(itemId, "itemId"), parsedModifierId);
    }

    public void registerModifierItem(ResourceLocation itemId, ResourceLocation modifierId) {
        ContentRegistrationCoordinator.startupDefinitions().registerModifierItem(itemId, modifierId);
    }

    public void limitModifierItems(String modifierId, double maxCount) {
        limitModifierItems(parseResourceLocation(modifierId, "modifierId"), maxCount);
    }

    public void limitModifierItems(ResourceLocation modifierId, double maxCount) {
        // An int parameter would let Rhino silently truncate a fractional count.
        int count = (int) maxCount;
        if (count != maxCount) throw new IllegalArgumentException(
                "Modifier item limit for " + modifierId + " must be a positive 32-bit integer: " + maxCount);
        ContentRegistrationCoordinator.startupDefinitions().limitModifierItems(modifierId, count);
    }

    public void addRecipePoolWorkstation(String recipePoolId, String itemId) {
        addRecipePoolWorkstation(parseResourceLocation(recipePoolId, "recipePoolId"), parseResourceLocation(itemId, "itemId"));
    }

    public void addRecipePoolWorkstation(ResourceLocation recipePoolId, ResourceLocation itemId) {
        jeiWorkstations.add(new JeiWorkstationRegistration.RecipePoolItem(recipePoolId, itemId));
    }

    public void addMachineWorkstation(String machineId, String recipeTypeId) {
        addMachineWorkstation(parseResourceLocation(machineId, "machineId"), parseResourceLocation(recipeTypeId, "recipeTypeId"));
    }

    public void addMachineWorkstation(ResourceLocation machineId, ResourceLocation recipeTypeId) {
        jeiWorkstations.add(new JeiWorkstationRegistration.Machine(machineId, recipeTypeId));
    }

    public void addMachineWorkStation(String machineId, String recipeTypeId) {
        addMachineWorkstation(machineId, recipeTypeId);
    }

    public void addMachineWorkStation(ResourceLocation machineId, ResourceLocation recipeTypeId) {
        addMachineWorkstation(machineId, recipeTypeId);
    }

    List<JeiWorkstationRegistration> jeiWorkstations() {
        return List.copyOf(jeiWorkstations);
    }

    private static ResourceLocation parseResourceLocation(String value, String name) {
        return ControllerScreenTextEventJS.parseResourceLocation(value, name);
    }
}
