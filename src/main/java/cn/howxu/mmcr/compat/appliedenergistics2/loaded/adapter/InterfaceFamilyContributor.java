package cn.howxu.mmcr.compat.appliedenergistics2.loaded.adapter;

import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.helpers.externalstorage.GenericStackInv;
import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.recipe.MachineOutput;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.internal.tile.IOPortBlockEntity;
import cn.howxu.mmcr.internal.api.facade.recipe.OutputAdapters;
import cn.howxu.mmcr.internal.api.facade.recipe.RequirementAdapters;
import cn.howxu.mmcr.publicapi.recipe.requirement.RequirementKind;
import cn.howxu.mmcr.publicapi.runtime.OutputView;
import java.util.Optional;
import java.util.function.Predicate;
import cn.howxu.mmcr.api.capability.CapabilityDirections;
import cn.howxu.mmcr.api.capability.type.CapabilityBinding;
import cn.howxu.mmcr.internal.port.PortFamilyDescriptor;
import java.util.List;

/** AE-side service-provider interface collected before the first interface definition is built.
 * Providers that support an optional mod must remain loadable in its absence.
 * @author howxu <dev@howxu.cn>
 */
public interface InterfaceFamilyContributor {
    default List<CapabilityBinding> bindings(List<CapabilityBinding> base, CapabilityDirections directions, boolean external) { return base; }
    default List<PortFamilyDescriptor> families(List<PortFamilyDescriptor> base) { return base; }
    /** Access the public extension payload without depending on the internal carrier implementation. */
    static <R> Optional<R> payload(RequirementKind<R> kind, MachineRequirement value) {
        return kind.payload(RequirementAdapters.wrap(value));
    }
    static MachineOutput patternOutput(OutputView output) { return OutputAdapters.unwrap(output); }

    default boolean acceptsPatternKey(AEKey key) { return false; }
    default MachineOutput patternOutput(AEKey key, long amount) { return null; }
    default long patternLimit() { return Long.MAX_VALUE; }
    default Predicate<AEKey> inputMatcher(MachineRequirement value) { return null; }
    default List<MachineCapability> requestCapabilities(GenericStackInv inventory, KeyCounter[] holders) { return List.of(); }
    default long flush(IOPortBlockEntity host, long budget) { return 0; }
    default boolean owns(AEKeyType type) { return false; }
    default boolean exposesLocalSlots() { return false; }
}
