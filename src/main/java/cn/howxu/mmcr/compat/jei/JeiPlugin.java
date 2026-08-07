package cn.howxu.mmcr.compat.jei;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.MachineDefinitions;
import cn.howxu.mmcr.client.gui.MachineMenuScreen;
import cn.howxu.mmcr.internal.menu.MachineControllerMenu;
import cn.howxu.mmcr.registry.ModBlocks;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.gui.handlers.IGuiClickableArea;
import mezz.jei.api.gui.handlers.IGuiContainerHandler;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.registration.IRecipeTransferRegistration;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Collection;
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
        MachineDefinitions.all().forEach(machine ->
                registration.addRecipeCategories(new MachineRecipeCategory(guiHelper, machine)));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        var displaysByMachine = MachineRecipeDisplays.byMachine();
        Set<Identifier> machineIds = MachineDefinitions.all().stream()
                .map(machine -> machine.registryName())
                .collect(Collectors.toSet());
        displaysByMachine.forEach((machineId, displays) -> {
            if (!machineIds.contains(machineId)) {
                displays.forEach(display -> MMCR.LOG.warn("Skipping JEI recipe {} for unknown machine {}", display.recipeId(), machineId));
            }
        });
        MachineDefinitions.all().forEach(machine -> registration.addRecipes(
                JeiMachineRecipeTypes.forMachine(machine.registryName()),
                displaysByMachine.getOrDefault(machine.registryName(), List.of())));
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        MachineDefinitions.all().forEach(machine ->
                registration.addCraftingStation(
                        JeiMachineRecipeTypes.forMachine(machine.registryName()),
                        new ItemStack(ModBlocks.controllerFor(machine).get())));
    }

    @Override
    public void registerRecipeTransferHandlers(IRecipeTransferRegistration registration) {
        var helper = registration.getTransferHelper();
        MachineDefinitions.all().forEach(machine -> {
            var type = JeiMachineRecipeTypes.forMachine(machine.registryName());
            registration.addRecipeTransferHandler(new MachineRecipeTransferHandler(helper, type), type);
        });
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registration.addGuiContainerHandler(MachineMenuScreen.class, new IGuiContainerHandler<>() {
            @Override
            public Collection<IGuiClickableArea> getGuiClickableAreas(MachineMenuScreen screen, double mouseX, double mouseY) {
                if (!(screen.getMenu() instanceof MachineControllerMenu menu)) return List.of();
                Identifier machineId = menu.machineId();
                if (machineId == null) return List.of();
                return List.of(IGuiClickableArea.createBasic(8, 24, 160, 24,
                        JeiMachineRecipeTypes.forMachine(machineId)));
            }
        });
    }
}
