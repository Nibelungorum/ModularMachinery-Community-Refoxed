package cn.howxu.mmcr.internal.api.facade.recipe;
import cn.howxu.mmcr.api.recipe.MachineOutput;
import cn.howxu.mmcr.api.recipe.OutputRegistry;
import cn.howxu.mmcr.publicapi.recipe.modifier.RecipeAdjustmentSpec;
import cn.howxu.mmcr.publicapi.recipe.requirement.RequirementSpec;
import cn.howxu.mmcr.publicapi.runtime.*;
import java.util.List;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
/** Runtime outputs retain delegate identity while protecting mutable stack getters.
 * @author howxu <dev@howxu.cn> */
public final class OutputAdapters {
    private OutputAdapters() {}
    public static OutputView wrap(MachineOutput output) {
        return switch (output) { case MachineOutput.ItemOutput v -> new ItemView(v); case MachineOutput.FluidOutput v -> new FluidView(v); default -> new View(output); };
    }
    public static MachineOutput unwrap(OutputView output) { if (output instanceof View view) return view.delegate; throw new IllegalArgumentException("Output must be library-produced"); }
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
        View(MachineOutput output) { delegate = output; }
        public ResourceLocation kindId() { return delegate.outputType().id(); }
        public String serializedId() { return delegate.type(); }
        public float chance() { return delegate.chance(); }
        public long amount() { return MachineOutput.scaledAmount(delegate); }
        public OutputView copy() { return wrap(MachineOutput.copyOf(delegate)); }
        public OutputView withChance(float chance) { return wrap(delegate.withChance(chance)); }
        public OutputView applyModifiers(List<RecipeAdjustmentSpec> modifiers) { return wrap(delegate.applyModifiers(ModifierAdapters.adjustments(modifiers))); }
    }
    private static final class ItemView extends View implements ItemOutputView {
        ItemView(MachineOutput.ItemOutput value) { super(value); }
        public ItemStack stack() { return ((MachineOutput.ItemOutput) delegate).stack().copy(); }
    }
    private static final class FluidView extends View implements FluidOutputView {
        FluidView(MachineOutput.FluidOutput value) { super(value); }
        public FluidStack stack() { return ((MachineOutput.FluidOutput) delegate).stack().copy(); }
    }
}
