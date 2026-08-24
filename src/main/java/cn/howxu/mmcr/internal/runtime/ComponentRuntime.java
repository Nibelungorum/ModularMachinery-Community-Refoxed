package cn.howxu.mmcr.internal.runtime;

import cn.howxu.mmcr.api.capability.CapabilityHost;
import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.machine.level.MachineLevel;
import cn.howxu.mmcr.api.recipe.helper.ProcessingComponent;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.internal.multiblock.ModuleConnectionCoordinator;
import cn.howxu.mmcr.internal.multiblock.ModuleConnectionStatus;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Owns the effective component, modifier, level, link, and capability views of a controller.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class ComponentRuntime {
    private List<ProcessingComponent> components = List.of();
    private List<MachineCapability> capabilities = List.of();
    private long capabilityVersion;
    private long modifierVersion;
    private long craftingStateVersion;
    private Map<String, List<RecipeModifier>> foundModifiers = Map.of();
    private Map<Identifier, MachineLevel> foundLevels = Map.of();
    private Set<BlockPos> linkedPortPositions = Set.of();
    private ModuleConnectionStatus moduleConnectionStatus = ModuleConnectionStatus.disconnected();
    private int installedModuleCount;

    public void replaceComponents(List<ProcessingComponent> components) {
        List<ProcessingComponent> nextComponents = List.copyOf(components == null ? List.of() : components);
        List<MachineCapability> nextCapabilities = capabilitiesFor(nextComponents);
        boolean capabilitiesChanged = !this.capabilities.equals(nextCapabilities);
        this.components = nextComponents;
        if (!capabilitiesChanged) return;
        this.capabilities = nextCapabilities;
        capabilityVersion++;
        craftingStateVersion++;
    }

    public List<ProcessingComponent> components() {
        return components;
    }

    public List<MachineCapability> capabilities() {
        return capabilities;
    }

    public long capabilityVersion() {
        return capabilityVersion;
    }

    public long modifierVersion() {
        return modifierVersion;
    }

    public long craftingStateVersion() {
        return craftingStateVersion;
    }

    public void replaceModifiers(Map<String, List<RecipeModifier>> modifiers) {
        Map<String, List<RecipeModifier>> next = new LinkedHashMap<>();
        if (modifiers != null) {
            modifiers.forEach((key, value) -> next.put(key, List.copyOf(value == null ? List.of() : value)));
        }
        if (foundModifiers.equals(next)) return;
        foundModifiers = Collections.unmodifiableMap(next);
        modifierVersion++;
        craftingStateVersion++;
    }

    public Map<String, List<RecipeModifier>> foundModifiers() {
        return foundModifiers;
    }

    public List<RecipeModifier> modifierList() {
        return foundModifiers.values().stream().flatMap(List::stream).toList();
    }

    public void replaceLevels(Map<Identifier, MachineLevel> levels) {
        Map<Identifier, MachineLevel> next = new LinkedHashMap<>(levels == null ? Map.of() : levels);
        foundLevels = Collections.unmodifiableMap(next);
    }

    public Map<Identifier, MachineLevel> foundLevels() {
        return foundLevels;
    }

    public void replaceLinkedPortPositions(Set<BlockPos> positions) {
        linkedPortPositions = Set.copyOf(positions == null ? Set.of() : positions);
    }

    public Set<BlockPos> linkedPortPositions() {
        return linkedPortPositions;
    }

    public boolean hasLinkedPort(BlockPos position) {
        return position != null && linkedPortPositions.contains(position);
    }

    public void refreshModuleConnectionState(MachineControllerBlockEntity controller) {
        moduleConnectionStatus = ModuleConnectionCoordinator.connectionStatus(controller);
        installedModuleCount = ModuleConnectionCoordinator.installedModuleCount(controller);
    }

    public ModuleConnectionStatus moduleConnectionStatus() {
        return moduleConnectionStatus;
    }

    public int installedModuleCount() {
        return installedModuleCount;
    }

    public Optional<Identifier> connectedHostId() {
        return moduleConnectionStatus.connected()
                ? Optional.of(moduleConnectionStatus.connectedHostId())
                : Optional.empty();
    }

    public void clear() {
        replaceComponents(List.of());
        replaceModifiers(Map.of());
        replaceLevels(Map.of());
        replaceLinkedPortPositions(Set.of());
        moduleConnectionStatus = ModuleConnectionStatus.disconnected();
        installedModuleCount = 0;
    }

    private static List<MachineCapability> capabilitiesFor(List<ProcessingComponent> components) {
        List<MachineCapability> result = new ArrayList<>();
        for (ProcessingComponent component : components) {
            if (component.getContainer() instanceof CapabilityHost host) {
                result.addAll(host.capabilitySnapshot().capabilities());
            }
        }
        return List.copyOf(result);
    }
}
