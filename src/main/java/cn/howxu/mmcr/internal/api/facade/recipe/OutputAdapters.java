package cn.howxu.mmcr.internal.api.facade.recipe;
import cn.howxu.mmcr.api.recipe.MachineOutput;
import cn.howxu.mmcr.api.recipe.OutputRegistry;
import cn.howxu.mmcr.publicapi.recipe.modifier.RecipeAdjustmentSpec;
import cn.howxu.mmcr.publicapi.recipe.requirement.RequirementSpec;
import cn.howxu.mmcr.publicapi.runtime.*;
import java.util.List;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
/** Runtime outputs retain delegate identity while protecting mutable stack getters.
 * @author howxu <dev@howxu.cn> */
public final class OutputAdapters {
    private OutputAdapters() {}
    public static OutputView wrap(MachineOutput output) {
        return switch (output) { case MachineOutput.ItemOutput v -> new ItemView(v); case MachineOutput.FluidOutput v -> new FluidView(v); default -> new View(output); };
    }
    /** UI-owned values supply independent resources without changing ordinary runtime views. */
    public static OutputView wrap(MachineOutput output, Supplier<MachineOutput> isolatedResource) {
        return switch (output) { case MachineOutput.ItemOutput v -> new ItemView(v, isolatedResource); case MachineOutput.FluidOutput v -> new FluidView(v, isolatedResource); default -> new View(output, isolatedResource); };
    }
    public static MachineOutput unwrap(OutputView output) { if (output instanceof View view) return view.resource(); throw new IllegalArgumentException("Output must be library-produced"); }
    public static List<OutputView> wrap(List<MachineOutput> outputs) { return outputs.stream().map(OutputAdapters::wrap).toList(); }
    public static List<MachineOutput> unwrap(List<OutputView> outputs) { return outputs.stream().map(OutputAdapters::unwrap).toList(); }
    public static ItemOutputView item(ItemStack stack, float chance) { return (ItemOutputView) wrap(new MachineOutput.ItemOutput(stack, chance)); }
    public static FluidOutputView fluid(FluidStack stack, float chance) { return (FluidOutputView) wrap(new MachineOutput.FluidOutput(stack, chance)); }
    public static RequirementSpec toRequirement(OutputView output, List<String> tags) { return RequirementAdapters.wrap(OutputRegistry.toRequirement(unwrap(output), tags)); }
    public static Optional<RequirementSpec> tryToRequirement(OutputView output, List<String> tags) { return Optional.ofNullable(OutputRegistry.tryToRequirement(unwrap(output), tags)).map(RequirementAdapters::wrap); }
    public static Optional<OutputView> fromRequirement(RequirementSpec requirement) { return Optional.ofNullable(OutputRegistry.fromRequirement(RequirementAdapters.unwrap(requirement))).map(OutputAdapters::wrap); }
    public static boolean matchesOutputRequirement(RequirementSpec requirement) { return OutputRegistry.matchesOutputRequirement(RequirementAdapters.unwrap(requirement)); }
    public static boolean matchesOutputRequirement(OutputView output, RequirementSpec requirement) { return OutputRegistry.matchesOutputRequirement(unwrap(output), RequirementAdapters.unwrap(requirement)); }
    private static class View implements OutputView {
        final MachineOutput delegate;
        final Supplier<MachineOutput> isolatedResource;
        View(MachineOutput output) { this(output, null); }
        View(MachineOutput output, Supplier<MachineOutput> isolatedResource) { delegate = output; this.isolatedResource = isolatedResource; }
        MachineOutput resource() { return isolatedResource == null ? delegate : isolatedResource.get(); }
        public ResourceLocation kindId() { return delegate.outputType().id(); }
        public String serializedId() { return delegate.type(); }
        public float chance() { return delegate.chance(); }
        public long amount() { return MachineOutput.scaledAmount(delegate); }
        public OutputView copy() { return isolatedResource == null ? wrap(MachineOutput.copyOf(delegate)) : wrap(resource(), isolatedResource); }
        public OutputView withChance(float chance) { return derive(value -> value.withChance(chance)); }
        public OutputView applyModifiers(List<RecipeAdjustmentSpec> modifiers) {
            var adjustments = ModifierAdapters.adjustments(modifiers);
            return derive(value -> value.applyModifiers(adjustments));
        }
        private OutputView derive(Function<MachineOutput, MachineOutput> operation) {
            MachineOutput derived = operation.apply(resource());
            return isolatedResource == null ? wrap(derived) : wrap(derived, () -> operation.apply(resource()));
        }
    }
    private static final class ItemView extends View implements ItemOutputView {
        ItemView(MachineOutput.ItemOutput value) { super(value); }
        ItemView(MachineOutput.ItemOutput value, Supplier<MachineOutput> isolatedResource) { super(value, isolatedResource); }
        public ItemStack stack() { return ((MachineOutput.ItemOutput) resource()).resolvedStack(); }
    }
    private static final class FluidView extends View implements FluidOutputView {
        FluidView(MachineOutput.FluidOutput value) { super(value); }
        FluidView(MachineOutput.FluidOutput value, Supplier<MachineOutput> isolatedResource) { super(value, isolatedResource); }
        public FluidStack stack() { return ((MachineOutput.FluidOutput) resource()).stack().copy(); }
    }
}
