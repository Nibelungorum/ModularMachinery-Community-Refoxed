package cn.howxu.mmcr.api.machine;

import net.minecraft.resources.Identifier;

public record DynamicMachine(
        Identifier registryName,
        String localizedName,
        BlockArray pattern,
        MachineControllerSpec controller,
        PortRequirementSpec portRequirements
) implements Machine {
    public DynamicMachine(Identifier registryName, String localizedName, BlockArray pattern) {
        this(registryName, localizedName, pattern, MachineControllerSpec.defaultsFor(registryName), PortRequirementSpec.none());
    }

    public DynamicMachine(Identifier registryName, String localizedName, BlockArray pattern, MachineControllerSpec controller) {
        this(registryName, localizedName, pattern, controller, PortRequirementSpec.none());
    }

    public DynamicMachine {
        if (registryName == null) throw new IllegalArgumentException("registryName null");
        if (localizedName == null) throw new IllegalArgumentException("localizedName null");
        if (pattern == null) throw new IllegalArgumentException("pattern null");
        if (controller == null) throw new IllegalArgumentException("controller null");
        if (portRequirements == null) throw new IllegalArgumentException("portRequirements null");
    }
}
