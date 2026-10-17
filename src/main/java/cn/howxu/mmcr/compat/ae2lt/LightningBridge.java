package cn.howxu.mmcr.compat.ae2lt;

import cn.howxu.mmcr.api.capability.CapabilityDirections;
import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.type.CapabilityBinding;
import cn.howxu.mmcr.api.recipe.MachineOutput;
import cn.howxu.mmcr.internal.port.PortFamilyDescriptor;
import cn.howxu.mmcr.internal.tile.IOPortBlockEntity;
import java.util.List;
import java.util.Optional;

/** Optional native boundary; no AE2 or AE2LT types are resolved when either mod is absent.
 * @author howxu <dev@howxu.cn>
 */
public interface LightningBridge {
    static LightningBridge get() { return LightningBridgeBootstrap.bridge(); }
    boolean available();
    default void registerCapabilityDefinitions() {}
    default List<CapabilityBinding> appendBindings(List<CapabilityBinding> base, CapabilityDirections directions) { return base; }
    default List<PortFamilyDescriptor> appendFamilies(List<PortFamilyDescriptor> base) { return base; }
    default boolean supportsPatternInput(Object key) { return false; }
    default Optional<MachineOutput> patternOutput(Object key, long amount) { return Optional.empty(); }
    default List<MachineCapability> patternInputs(Object inventory) { return List.of(); }
    default long flush(IOPortBlockEntity host, long budget) { return 0L; }
}
