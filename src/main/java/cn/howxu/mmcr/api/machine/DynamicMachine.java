package cn.howxu.mmcr.api.machine;

import cn.howxu.mmcr.api.recipe.modifier.SingleBlockModifierReplacement;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Collections;

public record DynamicMachine(
        Identifier registryName,
        String displayNameKey,
        BlockArray pattern,
        MachineControllerSpec controller,
        MachineAppearanceSpec appearance,
        PortRequirementSpec portRequirements,
        PortTierRequirementSpec portTierRequirements,
        List<DynamicPatternSpec> dynamicPatterns,
        Map<BlockPos, List<SingleBlockModifierReplacement>> modifierReplacements,
        int maxParallelism,
        boolean parallelizable,
        boolean hasFactory,
        int factoryThreadLimit,
        List<FactoryThreadSpec> factoryThreads,
        MachineRole role,
        Set<Identifier> acceptedModuleIds,
        List<MachineStructureStage> structureStages
) implements Machine {
    public DynamicMachine(
            Identifier registryName,
            String displayNameKey,
            BlockArray pattern,
            MachineControllerSpec controller,
            MachineAppearanceSpec appearance,
            PortRequirementSpec portRequirements,
            PortTierRequirementSpec portTierRequirements,
            List<DynamicPatternSpec> dynamicPatterns,
            Map<BlockPos, List<SingleBlockModifierReplacement>> modifierReplacements,
            int maxParallelism,
            boolean parallelizable,
            boolean hasFactory,
            int factoryThreadLimit) {
        this(registryName, displayNameKey, pattern, controller, appearance, portRequirements, portTierRequirements,
                dynamicPatterns, modifierReplacements, maxParallelism, parallelizable, hasFactory, factoryThreadLimit, List.of(),
                MachineRole.NORMAL, Set.of(), List.of());
    }

    public DynamicMachine(Identifier registryName, String displayNameKey, BlockArray pattern) {
        this(registryName, displayNameKey, pattern, MachineControllerSpec.defaultsFor(registryName), MachineAppearanceSpec.defaults(), PortRequirementSpec.none(), PortTierRequirementSpec.none(), List.of(), Map.of(), 1, false, false, 1, List.of(), MachineRole.NORMAL, Set.of(), List.of());
    }

    public DynamicMachine(Identifier registryName, String displayNameKey, BlockArray pattern, MachineControllerSpec controller) {
        this(registryName, displayNameKey, pattern, controller, MachineAppearanceSpec.defaults(), PortRequirementSpec.none(), PortTierRequirementSpec.none(), List.of(), Map.of(), 1, false, false, 1, List.of(), MachineRole.NORMAL, Set.of(), List.of());
    }

    public DynamicMachine(Identifier registryName, String displayNameKey, BlockArray pattern, List<DynamicPatternSpec> dynamicPatterns) {
        this(registryName, displayNameKey, pattern, MachineControllerSpec.defaultsFor(registryName), MachineAppearanceSpec.defaults(), PortRequirementSpec.none(), PortTierRequirementSpec.none(), dynamicPatterns, Map.of(), 1, false, false, 1, List.of(), MachineRole.NORMAL, Set.of(), List.of());
    }

    public DynamicMachine(Identifier registryName, String displayNameKey, BlockArray pattern, MachineControllerSpec controller, PortRequirementSpec portRequirements) {
        this(registryName, displayNameKey, pattern, controller, MachineAppearanceSpec.defaults(), portRequirements, PortTierRequirementSpec.none(), List.of(), Map.of(), 1, false, false, 1, List.of(), MachineRole.NORMAL, Set.of(), List.of());
    }

    public DynamicMachine {
        if (registryName == null) throw new IllegalArgumentException("registryName null");
        displayNameKey = MachineRegistration.defaultDisplayNameKey(registryName, displayNameKey);
        if (pattern == null) throw new IllegalArgumentException("pattern null");
        if (controller == null) throw new IllegalArgumentException("controller null");
        appearance = appearance == null ? MachineAppearanceSpec.defaults() : appearance;
        if (portRequirements == null) throw new IllegalArgumentException("portRequirements null");
        if (portTierRequirements == null) throw new IllegalArgumentException("portTierRequirements null");
        maxParallelism = Math.max(1, maxParallelism);
        factoryThreadLimit = Math.max(1, factoryThreadLimit);
        dynamicPatterns = List.copyOf(dynamicPatterns == null ? List.of() : dynamicPatterns);
        factoryThreads = List.copyOf(factoryThreads == null ? List.of() : factoryThreads);
        structureStages = structureStages == null || structureStages.isEmpty()
                ? List.of(MachineStructureStage.withCompiledRequirements(1, pattern, portRequirements,
                        portTierRequirements, dynamicPatterns, MachineStructureRequirements.EMPTY, modifierReplacements, Map.of()))
                : List.copyOf(structureStages);
        role = role == null ? MachineRole.NORMAL : role;
        acceptedModuleIds = copyAcceptedModuleIds(acceptedModuleIds);
        if (role != MachineRole.HOST && !acceptedModuleIds.isEmpty()) {
            throw new IllegalArgumentException("Only HOST machines may accept modules");
        }
        modifierReplacements = copyModifierReplacements(pattern, modifierReplacements);
    }

    public DynamicMachine(
            Identifier registryName,
            String displayNameKey,
            BlockArray pattern,
            MachineControllerSpec controller,
            MachineAppearanceSpec appearance,
            PortRequirementSpec portRequirements,
            PortTierRequirementSpec portTierRequirements,
            List<DynamicPatternSpec> dynamicPatterns,
            Map<BlockPos, List<SingleBlockModifierReplacement>> modifierReplacements,
            int maxParallelism,
            boolean parallelizable,
            boolean hasFactory,
            int factoryThreadLimit,
            List<FactoryThreadSpec> factoryThreads) {
        this(registryName, displayNameKey, pattern, controller, appearance, portRequirements, portTierRequirements,
                dynamicPatterns, modifierReplacements, maxParallelism, parallelizable, hasFactory, factoryThreadLimit,
                factoryThreads, MachineRole.NORMAL, Set.of(), List.of());
    }

    public DynamicMachine(
            Identifier registryName,
            String displayNameKey,
            BlockArray pattern,
            MachineControllerSpec controller,
            MachineAppearanceSpec appearance,
            PortRequirementSpec portRequirements,
            PortTierRequirementSpec portTierRequirements,
            List<DynamicPatternSpec> dynamicPatterns,
            Map<BlockPos, List<SingleBlockModifierReplacement>> modifierReplacements,
            int maxParallelism,
            boolean parallelizable,
            boolean hasFactory,
            int factoryThreadLimit,
            List<FactoryThreadSpec> factoryThreads,
            List<MachineStructureStage> structureStages) {
        this(registryName, displayNameKey, pattern, controller, appearance, portRequirements, portTierRequirements,
                dynamicPatterns, modifierReplacements, maxParallelism, parallelizable, hasFactory, factoryThreadLimit,
                factoryThreads, MachineRole.NORMAL, Set.of(), structureStages);
    }

    public DynamicMachine(
            Identifier registryName,
            String displayNameKey,
            BlockArray pattern,
            MachineControllerSpec controller,
            MachineAppearanceSpec appearance,
            PortRequirementSpec portRequirements,
            PortTierRequirementSpec portTierRequirements,
            List<DynamicPatternSpec> dynamicPatterns,
            Map<BlockPos, List<SingleBlockModifierReplacement>> modifierReplacements) {
        this(registryName, displayNameKey, pattern, controller, appearance, portRequirements, portTierRequirements, dynamicPatterns, modifierReplacements, 1, false, false, 1, List.of(), MachineRole.NORMAL, Set.of(), List.of());
    }

    public DynamicMachine(
            Identifier registryName,
            String displayNameKey,
            BlockArray pattern,
            MachineControllerSpec controller,
            PortRequirementSpec portRequirements,
            List<DynamicPatternSpec> dynamicPatterns,
            Map<BlockPos, List<SingleBlockModifierReplacement>> modifierReplacements) {
        this(registryName, displayNameKey, pattern, controller, MachineAppearanceSpec.defaults(), portRequirements, PortTierRequirementSpec.none(), dynamicPatterns, modifierReplacements, 1, false, false, 1, List.of(), MachineRole.NORMAL, Set.of(), List.of());
    }

    public DynamicMachine(
            Identifier registryName,
            String displayNameKey,
            BlockArray pattern,
            MachineControllerSpec controller,
            PortRequirementSpec portRequirements,
            List<DynamicPatternSpec> dynamicPatterns,
            Map<BlockPos, List<SingleBlockModifierReplacement>> modifierReplacements,
            int maxParallelism,
            boolean parallelizable,
            boolean hasFactory,
            int factoryThreadLimit) {
        this(registryName, displayNameKey, pattern, controller, MachineAppearanceSpec.defaults(), portRequirements, PortTierRequirementSpec.none(), dynamicPatterns, modifierReplacements, maxParallelism, parallelizable, hasFactory, factoryThreadLimit, List.of(), MachineRole.NORMAL, Set.of(), List.of());
    }

    public DynamicMachine(
            Identifier registryName,
            String displayNameKey,
            BlockArray pattern,
            MachineControllerSpec controller,
            PortRequirementSpec portRequirements,
            List<DynamicPatternSpec> dynamicPatterns) {
        this(registryName, displayNameKey, pattern, controller, MachineAppearanceSpec.defaults(), portRequirements, PortTierRequirementSpec.none(), dynamicPatterns, Map.of(), 1, false, false, 1, List.of(), MachineRole.NORMAL, Set.of(), List.of());
    }

    public DynamicMachine(
            Identifier registryName,
            String displayNameKey,
            BlockArray pattern,
            MachineControllerSpec controller,
            PortRequirementSpec portRequirements,
            PortTierRequirementSpec portTierRequirements,
            List<DynamicPatternSpec> dynamicPatterns,
            Map<BlockPos, List<SingleBlockModifierReplacement>> modifierReplacements) {
        this(registryName, displayNameKey, pattern, controller, MachineAppearanceSpec.defaults(), portRequirements, portTierRequirements, dynamicPatterns, modifierReplacements, 1, false, false, 1, List.of(), MachineRole.NORMAL, Set.of(), List.of());
    }

    public DynamicMachine withRole(MachineRole role, Set<Identifier> acceptedModuleIds) {
        return new DynamicMachine(registryName, displayNameKey, pattern, controller, appearance, portRequirements,
                portTierRequirements, dynamicPatterns, modifierReplacements, maxParallelism, parallelizable, hasFactory,
                factoryThreadLimit, factoryThreads, role, acceptedModuleIds, structureStages);
    }

    public List<SingleBlockModifierReplacement> modifierReplacementsAt(BlockPos pos) {
        return modifierReplacements.getOrDefault(pos, List.of());
    }

    @Override
    public BlockArray pattern() { return structureStages.getFirst().pattern(); }

    @Override
    public List<MachineStructureStage> structureStages() { return structureStages; }

    public Map<BlockPos, List<SingleBlockModifierReplacement>> rotatedModifierReplacements(
            Direction facing, Direction rollFacing) {
        Map<BlockPos, List<SingleBlockModifierReplacement>> rotated = new LinkedHashMap<>();
        Direction normalizedRoll = BlockRotator.normalizedRoll(facing, rollFacing);
        for (var entry : modifierReplacements.entrySet()) {
            BlockPos rotatedPos = BlockRotator.rotateSouthTo(entry.getKey(), facing, normalizedRoll);
            rotated.put(rotatedPos, List.copyOf(entry.getValue()));
        }
        return Collections.unmodifiableMap(rotated);
    }

    private static Map<BlockPos, List<SingleBlockModifierReplacement>> copyModifierReplacements(
            BlockArray pattern, Map<BlockPos, List<SingleBlockModifierReplacement>> replacements) {
        if (replacements == null || replacements.isEmpty()) {
            return Map.of();
        }

        Map<BlockPos, List<SingleBlockModifierReplacement>> copy = new LinkedHashMap<>();
        for (var entry : replacements.entrySet()) {
            BlockPos pos = entry.getKey();
            if (pos == null) throw new IllegalArgumentException("modifierReplacements position null");
            if (!pattern.pattern().containsKey(pos)) {
                throw new IllegalArgumentException("modifier replacement position outside pattern: " + pos);
            }
            List<SingleBlockModifierReplacement> list = entry.getValue();
            if (list == null) throw new IllegalArgumentException("modifierReplacements list null");
            for (SingleBlockModifierReplacement replacement : list) {
                if (replacement == null) throw new IllegalArgumentException("modifier replacement null");
                if (replacement.getReplacement() == null) throw new IllegalArgumentException("modifier replacement predicate null");
            }
            copy.put(pos, List.copyOf(list));
        }
        return Collections.unmodifiableMap(copy);
    }

    private static Set<Identifier> copyAcceptedModuleIds(Set<Identifier> acceptedModuleIds) {
        if (acceptedModuleIds == null || acceptedModuleIds.isEmpty()) return Set.of();
        LinkedHashSet<Identifier> copy = new LinkedHashSet<>();
        for (Identifier acceptedModuleId : acceptedModuleIds) {
            if (acceptedModuleId == null) throw new IllegalArgumentException("accepted module id null");
            copy.add(acceptedModuleId);
        }
        return Collections.unmodifiableSet(copy);
    }
}
