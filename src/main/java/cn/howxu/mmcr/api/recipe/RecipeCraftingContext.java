package cn.howxu.mmcr.api.recipe;

import cn.howxu.mmcr.internal.tile.EnergyInputHatchBlockEntity;
import cn.howxu.mmcr.internal.tile.FluidOutputHatchBlockEntity;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.api.recipe.helper.CraftCheck;
import cn.howxu.mmcr.api.recipe.helper.ProcessingComponent;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public final class RecipeCraftingContext {

    private static final Logger LOG = LoggerFactory.getLogger(RecipeCraftingContext.class);

    private final MachineControllerBlockEntity controller;
    private final BlockPos controllerPos;
    private final Map<MachineRequirement, List<?>> routes = new IdentityHashMap<>();
    private CraftCheck lastFailure = CraftCheck.success();

    public RecipeCraftingContext(MachineControllerBlockEntity controller) {
        this.controller = controller;
        this.controllerPos = controller.getBlockPos();
        LOG.debug("Crafting context created: controllerPos={} components={}", controllerPos, controller.getComponents().size());
    }

    public boolean ioTick(MachineRecipe recipe) {
        for (MachineRequirement requirement : recipe.requirements()) {
            if (!requirement.ioTick(this)) {
                lastFailure = CraftCheck.failure("Requirement tick failed: " + requirement.describe());
                LOG.info("[ioTick] recipe={} missing {}", recipe.id(), requirement.describe());
                return false;
            }
        }
        lastFailure = CraftCheck.success();
        return true;
    }

    public boolean simulateInputs(MachineRecipe recipe) {
        routes.clear();
        for (MachineRequirement requirement : recipe.requirements()) {
            if (requirement instanceof ItemRequirement item && item.ioType() == cn.howxu.mmcr.util.IOType.OUTPUT) continue;
            if (requirement instanceof FluidRequirement fluid && fluid.ioType() == cn.howxu.mmcr.util.IOType.OUTPUT) continue;
            CraftCheck check = requirement.simulate(this);
            if (!check.isSuccess()) {
                lastFailure = check;
                LOG.debug("[simulateInputs] recipe={} failed requirement={}: {}", recipe.id(), requirement.describe(), check.getUnlocalizedMessage());
                return false;
            }
        }
        lastFailure = CraftCheck.success();
        return true;
    }

    public boolean simulateOutputs(MachineRecipe recipe) {
        for (MachineRequirement requirement : recipe.requirements()) {
            if (requirement instanceof ItemRequirement item && item.ioType() != cn.howxu.mmcr.util.IOType.OUTPUT) continue;
            if (requirement instanceof FluidRequirement fluid && fluid.ioType() != cn.howxu.mmcr.util.IOType.OUTPUT) continue;
            if (requirement instanceof EnergyRequirement) continue;
            CraftCheck check = requirement.simulate(this);
            if (!check.isSuccess()) {
                lastFailure = check;
                LOG.debug("[simulateOutputs] recipe={} failed requirement={}: {}", recipe.id(), requirement.describe(), check.getUnlocalizedMessage());
                return false;
            }
        }
        lastFailure = CraftCheck.success();
        return true;
    }

    public boolean startCrafting(MachineRecipe recipe) {
        return commitInputs(recipe);
    }

    public boolean finishCrafting(MachineRecipe recipe) {
        return commitOutputs(recipe);
    }

    public boolean commitOutputs(MachineRecipe recipe) {
        LOG.info("[commitOutputs] recipe={} controllerPos={} requirements={}", recipe.id(), controllerPos, recipe.requirements().size());
        for (MachineRequirement requirement : recipe.requirements()) {
            if (requirement instanceof ItemRequirement item && item.ioType() != cn.howxu.mmcr.util.IOType.OUTPUT) continue;
            if (requirement instanceof FluidRequirement fluid && fluid.ioType() != cn.howxu.mmcr.util.IOType.OUTPUT) continue;
            if (requirement instanceof EnergyRequirement) continue;
            if (!requirement.commit(this)) {
                LOG.warn("[commitOutputs] requirement {} failed to commit", requirement.describe());
                return false;
            }
        }
        return true;
    }

    public boolean commitInputs(MachineRecipe recipe) {
        LOG.info("[commitInputs] recipe={} controllerPos={} requirements={}", recipe.id(), controllerPos, recipe.requirements().size());
        for (MachineRequirement requirement : recipe.requirements()) {
            if (requirement instanceof ItemRequirement item && item.ioType() == cn.howxu.mmcr.util.IOType.OUTPUT) continue;
            if (requirement instanceof FluidRequirement fluid && fluid.ioType() == cn.howxu.mmcr.util.IOType.OUTPUT) continue;
            if (requirement instanceof EnergyRequirement) continue;
            if (!requirement.commit(this)) {
                LOG.warn("[commitInputs] requirement {} failed to commit", requirement.describe());
                return false;
            }
        }
        return true;
    }

    private List<EnergyInputHatchBlockEntity> liveEnergyInputs() {
        return liveComponents(EnergyInputHatchBlockEntity.class);
    }

    public List<IEnergyStorage> energyStorages() {
        return energyStorages(liveEnergyInputs());
    }

    private static List<IEnergyStorage> energyStorages(List<EnergyInputHatchBlockEntity> hatches) {
        return hatches.stream()
                .map(hatch -> hatch.getEnergyStorage(null))
                .toList();
    }

    public String energyComponentSummary() {
        return liveEnergyInputs().stream()
                .map(hatch -> hatch.getBlockPos() + ":energy_input_hatch=" + hatch.getEnergyStorage(null).getEnergyStored())
                .toList()
                .toString();
    }

    public List<ProcessingComponent> componentsMatching(MachineRequirement requirement) {
        var level = controller.getLevel();
        if (level == null) return List.of();

        List<ProcessingComponent> matches = new ArrayList<>();
        for (ProcessingComponent component : controller.getComponents()) {
            if (!requirement.matches(component)) continue;
            BlockEntity live = level.getBlockEntity(component.getPos());
            if (live == component.getContainer()) matches.add(component);
        }
        return matches;
    }

    public void route(MachineRequirement requirement, List<?> route) {
        routes.put(requirement, List.copyOf(route));
    }

    @SuppressWarnings("unchecked")
    public <T> List<T> route(MachineRequirement requirement, Class<T> type) {
        List<?> route = routes.get(requirement);
        if (route == null) return null;
        return (List<T>) route;
    }

    public CraftCheck lastFailure() {
        return lastFailure;
    }

    public List<FluidOutputHatchBlockEntity> fluidOutputs() {
        // Fluid outputs do not have a MachineRecipe output type yet; expose routed hatches for that next step.
        return liveComponents(FluidOutputHatchBlockEntity.class);
    }

    private <T extends BlockEntity> List<T> liveComponents(Class<T> type) {
        var level = controller.getLevel();
        if (level == null) return List.of();

        List<T> matches = new ArrayList<>();
        for (ProcessingComponent component : controller.getComponents()) {
            if (!type.isInstance(component.getContainer())) continue;
            BlockEntity live = level.getBlockEntity(component.getPos());
            if (live == component.getContainer()) {
                matches.add(type.cast(live));
            }
        }
        return matches;
    }

}
