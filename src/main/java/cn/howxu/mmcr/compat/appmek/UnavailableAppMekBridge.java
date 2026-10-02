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

/** @author howxu <dev@howxu.cn> */
enum UnavailableAppMekBridge implements AppMekBridge {
    INSTANCE;

    private static final PatternRequest EMPTY_REQUEST = new PatternRequest() {
        @Override public List<MachineCapability> capabilities() { return List.of(); }
        @Override public boolean returnRemaining(Object inventory, Object source) { return true; }
    };

    @Override public boolean available() { return false; }
    @Override public List<CapabilityBinding> appendBindings(List<CapabilityBinding> base, CapabilityDirections directions, boolean transfer) { return base; }
    @Override public List<PortFamilyDescriptor> appendFamilies(List<PortFamilyDescriptor> base) { return base; }
    @Override public void configureInventory(Object inventory) {}
    @Override public Object inventoryView(Object inventory) { return inventory; }
    @Override public Object storageView(Object storage) { return storage; }
    @Override public boolean supportsPatternInput(Object key) { return false; }
    @Override public Optional<MachineOutput> patternOutput(Object key, long amount) { return Optional.empty(); }
    @Override public PatternRequest patternRequest(Object holders) { return EMPTY_REQUEST; }
    @Override public long flush(IOPortBlockEntity host, long budget) { return 0L; }
    @Override public void registerCapabilities(RegisterCapabilitiesEvent event) {}
}
