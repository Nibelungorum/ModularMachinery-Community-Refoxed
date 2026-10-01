package cn.howxu.mmcr.publicapi.recipe;
import cn.howxu.mmcr.internal.api.facade.recipe.OutputAdapters;
import cn.howxu.mmcr.internal.api.facade.recipe.OutputExtensionAdapter;
import cn.howxu.mmcr.publicapi.recipe.requirement.RequirementSpec;
import cn.howxu.mmcr.publicapi.runtime.*;
import java.util.List;
import java.util.Optional;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
/** Runtime output factories for behavior setOutputs and registered requirement conversions.
 * @author howxu <dev@howxu.cn> */
public final class Outputs {
    private Outputs() {}
    public static <O> OutputView extension(OutputKind<O> kind, O payload) { return OutputExtensionAdapter.output(kind, payload); }
    public static ItemOutputView item(ItemStack stack, float chance) { return OutputAdapters.item(stack, chance); }
    public static ItemOutputView item(ItemStack stack) { return item(stack, 1F); }
    public static FluidOutputView fluid(FluidStack stack, float chance) { return OutputAdapters.fluid(stack, chance); }
    public static FluidOutputView fluid(FluidStack stack) { return fluid(stack, 1F); }
    public static RequirementSpec toRequirement(OutputView output, List<String> tags) { return OutputAdapters.toRequirement(output, tags); }
    public static Optional<RequirementSpec> tryToRequirement(OutputView output, List<String> tags) { return OutputAdapters.tryToRequirement(output, tags); }
    public static Optional<OutputView> fromRequirement(RequirementSpec requirement) { return OutputAdapters.fromRequirement(requirement); }
    public static boolean matchesOutputRequirement(RequirementSpec requirement) { return OutputAdapters.matchesOutputRequirement(requirement); }
    public static boolean matchesOutputRequirement(OutputView output, RequirementSpec requirement) { return OutputAdapters.matchesOutputRequirement(output, requirement); }
}
