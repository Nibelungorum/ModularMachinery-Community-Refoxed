package cn.howxu.mmcr.api.machine;

import net.minecraft.resources.Identifier;

import java.util.List;

public record DynamicMachine(
        Identifier registryName,
        String localizedName,
        BlockArray pattern,
        MachineControllerSpec controller,
        PortRequirementSpec portRequirements,
        List<DynamicPatternSpec> dynamicPatterns
) implements Machine {
    public DynamicMachine(Identifier registryName, String localizedName, BlockArray pattern) {
        this(registryName, localizedName, pattern, MachineControllerSpec.defaultsFor(registryName), PortRequirementSpec.none(), List.of());
    }

    public DynamicMachine(Identifier registryName, String localizedName, BlockArray pattern, MachineControllerSpec controller) {
        this(registryName, localizedName, pattern, controller, PortRequirementSpec.none(), List.of());
    }

    public DynamicMachine(Identifier registryName, String localizedName, BlockArray pattern, List<DynamicPatternSpec> dynamicPatterns) {
        this(registryName, localizedName, pattern, MachineControllerSpec.defaultsFor(registryName), PortRequirementSpec.none(), dynamicPatterns);
    }

    public DynamicMachine(Identifier registryName, String localizedName, BlockArray pattern, MachineControllerSpec controller, PortRequirementSpec portRequirements) {
        this(registryName, localizedName, pattern, controller, portRequirements, List.of());
    }

    public DynamicMachine {
        if (registryName == null) throw new IllegalArgumentException("registryName null");
        if (localizedName == null) throw new IllegalArgumentException("localizedName null");
        if (pattern == null) throw new IllegalArgumentException("pattern null");
        if (controller == null) throw new IllegalArgumentException("controller null");
        if (portRequirements == null) throw new IllegalArgumentException("portRequirements null");
        dynamicPatterns = List.copyOf(dynamicPatterns == null ? List.of() : dynamicPatterns);
    }
}
