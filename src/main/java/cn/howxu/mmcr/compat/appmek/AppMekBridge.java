package cn.howxu.mmcr.compat.appmek;

import cn.howxu.mmcr.api.capability.CapabilityDirections;
import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.type.CapabilityBinding;
import cn.howxu.mmcr.api.recipe.MachineOutput;
import cn.howxu.mmcr.internal.port.PortFamilyDescriptor;
import cn.howxu.mmcr.internal.tile.IOPortBlockEntity;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

import java.util.List;
import java.util.Optional;

/**
 * Optional AppMek boundary without linking foreign stack or inventory classes.
 *
 * @author howxu <dev@howxu.cn>
 */
public interface AppMekBridge {
    static AppMekBridge get() { return AppMekBridgeBootstrap.get(); }

    boolean available();
    List<CapabilityBinding> appendBindings(List<CapabilityBinding> base, CapabilityDirections directions, boolean transfer);
    List<PortFamilyDescriptor> appendFamilies(List<PortFamilyDescriptor> base);
    void configureInventory(Object inventory);
    Object inventoryView(Object inventory);
    Object storageView(Object storage);
    boolean supportsPatternInput(Object key);
    Optional<MachineOutput> patternOutput(Object key, long amount);
    PatternRequest patternRequest(Object holders);
    long flush(IOPortBlockEntity host, long budget);
    void registerCapabilities(RegisterCapabilitiesEvent event);

    /** @author howxu <dev@howxu.cn> */
    interface PatternRequest {
        List<MachineCapability> capabilities();
        boolean returnRemaining(Object inventory, Object source);
    }
}
