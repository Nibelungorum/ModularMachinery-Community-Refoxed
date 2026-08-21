package cn.howxu.mmcr.compat.jei;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.MachineRegistry;
import cn.howxu.mmcr.api.machine.MachineDefinitions;
import cn.howxu.mmcr.registry.ModBlocks;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.registration.IRecipeTransferRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * JEI plugin entrypoint for MMCR.
 *
 * @author howxu <dev@howxu.cn>
 */
@mezz.jei.api.JeiPlugin
public final class JeiPlugin implements IModPlugin {

    @Override
    public Identifier getPluginUid() {
        return MMCR.id("jei");
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        var guiHelper = registration.getJeiHelpers().getGuiHelper();
        registration.addRecipeCategories(new MachineStructureCategory(guiHelper));
        Map<Identifier, net.minecraft.network.chat.Component> machineTitles = new LinkedHashMap<>();
        MachineRegistry.getAll().values().forEach(machine -> machineTitles.put(machine.registryName(), machine.displayName()));
        MachineDefinitions.effectiveSnapshot().forEach((id, machine) -> machineTitles.putIfAbsent(id, machine.displayName()));
        JeiRuntimeReloader.markRegisteredMachineCategories(machineTitles.keySet());
        machineTitles.forEach((id, title) -> registration.addRecipeCategories(
                new MachineRecipeCategory(guiHelper, id, title)));
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime runtime) {
        JeiRuntimeReloader.setRuntime(runtime);
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        registration.addRecipes(JeiMachineRecipeTypes.STRUCTURE, MachineRegistry.getAll().values().stream()
                .map(MachineStructureDisplay::from)
                .toList());
        var displaysByMachine = MachineRecipeDisplays.byMachine();
        Set<Identifier> machineIds = MachineRegistry.getAll().values().stream()
                .map(machine -> machine.registryName())
                .collect(Collectors.toSet());
        displaysByMachine.forEach((machineId, displays) -> {
            if (!machineIds.contains(machineId)) {
                displays.forEach(display -> MMCR.LOG.warn("Skipping JEI recipe {} for unknown machine {}", display.recipeId(), machineId));
            }
        });
        MachineRegistry.getAll().values().forEach(machine -> registration.addRecipes(
                JeiMachineRecipeTypes.forMachine(machine.registryName()),
                displaysByMachine.getOrDefault(machine.registryName(), List.of())));
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        machineIds().forEach(machineId -> {
            ItemStack controller = new ItemStack(ModBlocks.controllerFor(machineId).get());
            registration.addCraftingStation(JeiMachineRecipeTypes.forMachine(machineId), controller);
        });
    }

    @Override
    public void registerRecipeTransferHandlers(IRecipeTransferRegistration registration) {
        var helper = registration.getTransferHelper();
        machineIds().forEach(machineId -> {
            var type = JeiMachineRecipeTypes.forMachine(machineId);
            registration.addRecipeTransferHandler(new MachineRecipeTransferHandler(helper, type), type);
        });
    }

    private static Set<Identifier> machineIds() {
        Set<Identifier> ids = new java.util.LinkedHashSet<>(MachineRegistry.getAll().keySet());
        ids.addAll(MachineDefinitions.effectiveSnapshot().keySet());
        return ids;
    }

}
