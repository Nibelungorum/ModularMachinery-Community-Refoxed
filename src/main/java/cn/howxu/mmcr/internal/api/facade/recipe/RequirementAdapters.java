package cn.howxu.mmcr.internal.api.facade.recipe;

import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.api.recipe.requirement.ItemRequirement;
import cn.howxu.mmcr.api.recipe.requirement.FluidRequirement;
import cn.howxu.mmcr.api.recipe.requirement.EnergyRequirement;
import cn.howxu.mmcr.api.recipe.requirement.LevelRequirement;
import cn.howxu.mmcr.api.recipe.requirement.StageRequirement;
import cn.howxu.mmcr.api.recipe.requirement.SmartInterfaceRequirement;
import cn.howxu.mmcr.compat.create.StressRequirement;
import cn.howxu.mmcr.compat.pneumaticcraft.AirRequirement;
import cn.howxu.mmcr.publicapi.recipe.IoDirection;
import cn.howxu.mmcr.publicapi.recipe.component.ComponentConstraints;
import cn.howxu.mmcr.publicapi.recipe.requirement.*;
import com.mojang.serialization.DynamicOps;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;

/** Typed requirement adapters, including unknown registered kind metadata.
 * @author howxu <dev@howxu.cn> */
public final class RequirementAdapters {
    private RequirementAdapters() {}
    public static RequirementSpec wrap(MachineRequirement value) {
        return switch (value) {
            case ItemRequirement v -> new ItemView(v);
            case FluidRequirement v -> new FluidView(v);
            case EnergyRequirement v -> new EnergyView(v);
            case LevelRequirement v -> new LevelView(v);
            case StageRequirement v -> new StageView(v);
            case SmartInterfaceRequirement v -> new SmartView(v);
            case StressRequirement v -> new StressView(v);
            case AirRequirement v -> new AirView(v);
            default -> new View(value);
        };
    }
    public static MachineRequirement unwrap(RequirementSpec value) {
        if (value instanceof View view) return view.delegate;
        throw new IllegalArgumentException("Requirement must be library-produced");
    }
    public static List<RequirementSpec> wrap(List<MachineRequirement> values) { return values.stream().map(RequirementAdapters::wrap).toList(); }
    public static List<MachineRequirement> unwrap(List<RequirementSpec> values) { return values.stream().map(RequirementAdapters::unwrap).toList(); }
    private static class View implements RequirementSpec {
        final MachineRequirement delegate;
        View(MachineRequirement value) { delegate = value; }
        public ResourceLocation kindId() { return delegate.type().id(); }
        public IoDirection io() { return ModifierAdapters.io(delegate.io()); }
        public List<String> tags() { return List.copyOf(delegate.tags()); }
        public RequirementSpec copy() { return wrap(MachineRequirement.copyOf(delegate)); }
    }
    private static final class ItemView extends View implements ItemRequirementSpec {
        private final ItemRequirement value;
        ItemView(ItemRequirement value) { super(value); this.value = value; }
        public Ingredient item() { return value.item(); }
        public Ingredient ingredient() { return value.ingredient(); }
        public int count() { return value.count(); }
        public ItemStack stack() { return value.stack(); }
        public float chance() { return value.chance(); }
        public ComponentConstraints components() { return ComponentAdapters.wrap(value.components()); }
        public float consumeChance() { return value.consumeChance(); }
        public ItemStack resolvedStack() { return value.resolvedStack(); }
        public ItemStack stack(DynamicOps<?> ops) { return value.stack(ops); }
    }
    private static final class FluidView extends View implements FluidRequirementSpec {
        private final FluidRequirement value;
        FluidView(FluidRequirement value) { super(value); this.value = value; }
        public FluidIngredient fluid() { return value.fluid(); }
        public FluidIngredient ingredient() { return value.ingredient(); }
        public int amount() { return value.amount(); }
        public FluidStack stack() { return value.stack(); }
        public float chance() { return value.chance(); }
        public float consumeChance() { return value.consumeChance(); }
    }
    private static final class EnergyView extends View implements EnergyRequirementSpec {
        EnergyView(EnergyRequirement value) { super(value); }
        public long fePerTick() { return ((EnergyRequirement) delegate).fePerTick(); }
    }
    private static final class AirView extends View implements AirRequirementSpec {
        AirView(AirRequirement value) { super(value); }
        public long airPerTick() { return ((AirRequirement) delegate).airPerTick(); }
        public float minPressure() { return ((AirRequirement) delegate).minPressure(); }
    }
    private static final class StressView extends View implements StressRequirementSpec {
        StressView(StressRequirement value) { super(value); }
        public double stress() { return ((StressRequirement) delegate).stress(); }
        public double minRpm() { return ((StressRequirement) delegate).minRpm(); }
        public double rpm() { return ((StressRequirement) delegate).rpm(); }
    }
    private static final class LevelView extends View implements LevelRequirementSpec {
        LevelView(LevelRequirement value) { super(value); }
        public ResourceLocation typeId() { return ((LevelRequirement) delegate).typeId(); }
        public ResourceLocation levelId() { return ((LevelRequirement) delegate).levelId(); }
    }
    private static final class StageView extends View implements StageRequirementSpec {
        StageView(StageRequirement value) { super(value); }
        public int minStage() { return ((StageRequirement) delegate).minStage(); }
    }
    private static final class SmartView extends View implements SmartInterfaceRequirementSpec {
        SmartView(SmartInterfaceRequirement value) { super(value); }
        public String interfaceType() { return ((SmartInterfaceRequirement) delegate).interfaceType(); }
        public float minValue() { return ((SmartInterfaceRequirement) delegate).minValue(); }
        public float maxValue() { return ((SmartInterfaceRequirement) delegate).maxValue(); }
    }
}
