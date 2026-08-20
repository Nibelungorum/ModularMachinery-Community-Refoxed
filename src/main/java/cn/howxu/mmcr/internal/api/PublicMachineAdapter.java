package cn.howxu.mmcr.internal.api;

import cn.howxu.mmcr.api.machine.DynamicMachine;
import cn.howxu.mmcr.api.machine.FactoryThreadSpec;
import cn.howxu.mmcr.api.machine.MachineAppearanceSpec;
import cn.howxu.mmcr.api.machine.MachineControllerSpec;
import cn.howxu.mmcr.api.machine.MachineRegistration;
import cn.howxu.mmcr.api.machine.MachineRole;
import cn.howxu.mmcr.api.machine.MachineStructureDefinition;
import cn.howxu.mmcr.api.machine.MachineStructureRequirements;
import cn.howxu.mmcr.api.machine.MachineStructureStage;
import cn.howxu.mmcr.api.machine.PortRequirementSpec;
import cn.howxu.mmcr.api.machine.PortTierRequirementSpec;
import cn.howxu.mmcr.api.publicapi.machine.AppearanceSpec;
import cn.howxu.mmcr.api.publicapi.machine.ControllerSpec;
import cn.howxu.mmcr.api.publicapi.machine.FactorySpec;
import cn.howxu.mmcr.api.publicapi.machine.MachineDefinition;
import cn.howxu.mmcr.api.publicapi.machine.PatternDefinition;
import cn.howxu.mmcr.api.publicapi.machine.PortRequirements;
import cn.howxu.mmcr.api.publicapi.machine.PortTiers;
import cn.howxu.mmcr.api.publicapi.machine.StructureRequirements;
import cn.howxu.mmcr.api.publicapi.machine.ModifierDefinition;
import cn.howxu.mmcr.api.publicapi.machine.StructureStage;
import cn.howxu.mmcr.api.publicapi.ApiRegistrationException;
import cn.howxu.mmcr.api.recipe.modifier.SingleBlockModifierReplacement;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Internal conversion boundary for public startup machine declarations.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class PublicMachineAdapter {
    private PublicMachineAdapter() {
    }

    public static cn.howxu.mmcr.api.machine.BlockArray toBlockArray(PatternDefinition pattern) {
        LinkedHashMap<BlockPos, cn.howxu.mmcr.api.machine.BlockPredicate> entries = new LinkedHashMap<>();
        LinkedHashMap<BlockPos, Character> symbolsByPosition = new LinkedHashMap<>();
        BlockPos controller = null;
        int xOrigin = pattern.width() / 2;
        int yOrigin = pattern.height() / 2;
        int zOrigin = pattern.depth() / 2;

        for (int rowIndex = 0; rowIndex < pattern.height(); rowIndex++) {
            int y = rowIndex - yOrigin;
            for (int layerIndex = 0; layerIndex < pattern.depth(); layerIndex++) {
                int z = layerIndex - zOrigin;
                String row = pattern.layers().get(layerIndex).get(rowIndex);
                for (int columnIndex = 0; columnIndex < pattern.width(); columnIndex++) {
                    char symbol = row.charAt(columnIndex);
                    if (symbol == ' ') continue;
                    int x = columnIndex - xOrigin;
                    BlockPos pos = new BlockPos(x, y, z);
                    entries.put(pos, toBlockPredicate(pattern.predicates().get(symbol)));
                    symbolsByPosition.put(pos, symbol);
                    if (symbol == pattern.controllerSymbol()) controller = pos;
                }
            }
        }

        if (controller != null && !controller.equals(BlockPos.ZERO)) {
            LinkedHashMap<BlockPos, cn.howxu.mmcr.api.machine.BlockPredicate> normalized = new LinkedHashMap<>();
            LinkedHashMap<BlockPos, Character> normalizedSymbols = new LinkedHashMap<>();
            for (var entry : entries.entrySet()) {
                BlockPos normalizedPos = entry.getKey().subtract(controller);
                normalized.put(normalizedPos, entry.getValue());
                normalizedSymbols.put(normalizedPos, symbolsByPosition.get(entry.getKey()));
            }
            entries = normalized;
            symbolsByPosition = normalizedSymbols;
        }
        return new cn.howxu.mmcr.api.machine.BlockArray(entries, java.util.Map.of(), symbolsByPosition);
    }

    public static DynamicMachine toDynamicMachine(MachineDefinition definition, cn.howxu.mmcr.api.publicapi.machine.MachineStructureDefinition structure) {
        validateDefinitionStructureIds(definition, structure);
        return new DynamicMachine(
                definition.id(),
                definition.displayNameKey(),
                toBlockArray(structure.stages().getFirst().pattern()),
                toControllerSpec(definition.id(), definition.controller()),
                toAppearanceSpec(definition.appearance()),
                toPortRequirementSpec(structure.stages().getFirst().portRequirements()),
                toPortTierRequirementSpec(structure.stages().getFirst().portTiers()),
                List.of(),
                Map.of(),
                definition.maxParallelism(),
                definition.parallelizable(),
                definition.factory().hasFactory(),
                definition.factory().threadLimit(),
                toFactoryThreads(definition.factory()),
                definition.role(),
                definition.acceptedModuleIds(),
                toStructureStages(structure),
                definition.failureAction());
    }

    public static MachineRegistration toRegistration(MachineDefinition definition) {
        validateRegistrationFields(definition);
        return toStartupRegistration(definition);
    }

    public static MachineRegistration toStartupRegistration(MachineDefinition definition) {
        return toStartupRegistration(definition, null);
    }

    public static MachineRegistration toStartupRegistration(MachineDefinition definition,
            cn.howxu.mmcr.api.publicapi.machine.MachineStructureDefinition structure) {
        if (structure != null) validateDefinitionStructureIds(definition, structure);
        MachineRegistration.Builder builder = MachineRegistration.builder(definition.id())
                .displayNameKey(definition.displayNameKey())
                .controllerSpec(toControllerSpec(definition.id(), definition.controller()))
                .appearance(toAppearanceSpec(definition.appearance()))
                .allowParallelism(definition.parallelizable())
                .maxParallelAmount(definition.maxParallelAmount())
                .allowModifiers(definition.allowModifiers())
                .allowMultithreading(definition.allowMultithreading())
                .shareSmartInterfaces(definition.shareSmartInterfaces());
        definition.smartInterfaceTypes().values().forEach(builder::smartInterfaceType);
        definition.smartInterfaceModifiers().forEach(builder::smartInterfaceModifier);
        builder.runningSound(definition.runningSoundId()).finishSound(definition.finishSoundId());
        if (structure != null) {
            builder.pattern(toBlockArray(structure.stages().getFirst().pattern()));
            if (structure.stages().size() > 1) builder.expandableStructure();
        } else {
            builder.pattern(definition.pattern());
            if (definition.expandableStructure()) builder.expandableStructure();
        }
        if (definition.role() == MachineRole.MODULE) builder.module();
        if (definition.role() == MachineRole.HOST) {
            definition.acceptedModuleIds().forEach(builder::host);
        }
        return builder.build();
    }

    public static MachineStructureDefinition toStructureDefinition(cn.howxu.mmcr.api.publicapi.machine.MachineStructureDefinition structure) {
        return toStructureDefinition(structure, Map.of());
    }

    public static MachineStructureDefinition toStructureDefinition(
            cn.howxu.mmcr.api.publicapi.machine.MachineStructureDefinition structure,
            Map<Identifier, ModifierDefinition> modifiers) {
        return new MachineStructureDefinition(structure.machineId(), structure.stages().stream()
                .map(stage -> toDeclaration(stage, modifiers)).toList());
    }

    public static MachineStructureDefinition.Declaration toDeclaration(StructureStage stage) {
        return toDeclaration(stage, Map.of());
    }

    private static MachineStructureDefinition.Declaration toDeclaration(StructureStage stage,
            Map<Identifier, ModifierDefinition> modifiers) {
        return new MachineStructureDefinition.Declaration(toDeclarationKind(stage.kind()), toBlockArray(stage.pattern()),
                toPortRequirementSpec(stage.portRequirements()), toPortTierRequirementSpec(stage.portTiers()),
                List.of(), toStructureRequirements(stage.requirements(), modifiers));
    }

    private static cn.howxu.mmcr.api.machine.BlockPredicate toBlockPredicate(
            cn.howxu.mmcr.api.publicapi.machine.BlockPredicate predicate) {
        if (predicate.isMachineCoupler()) return cn.howxu.mmcr.api.machine.BlockPredicate.machineCoupler();
        return predicate.block()
                .<cn.howxu.mmcr.api.machine.BlockPredicate>map(cn.howxu.mmcr.api.machine.BlockPredicate.OfBlock::new)
                .or(() -> predicate.blockSupplier()
                        .map(cn.howxu.mmcr.api.machine.BlockPredicate.DeferredBlock::new))
                .or(() -> predicate.tag().map(cn.howxu.mmcr.api.machine.BlockPredicate.OfTag::new))
                .orElseGet(() -> new cn.howxu.mmcr.api.machine.BlockPredicate.AnyOf(
                        predicate.alternatives().stream()
                                .map(PublicMachineAdapter::toBlockPredicate)
                                .toList()));
    }

    private static MachineControllerSpec toControllerSpec(Identifier machineId, ControllerSpec spec) {
        MachineControllerSpec defaults = MachineControllerSpec.defaultsFor(machineId);
        Identifier id = spec.id() != null ? spec.id() : defaults.id();
        Identifier front = spec.frontTexture() != null ? spec.frontTexture() : defaults.frontTexture();
        Identifier side = spec.sideTexture() != null ? spec.sideTexture() : defaults.sideTexture();
        Identifier top = spec.topTexture() != null ? spec.topTexture() : defaults.topTexture();
        Identifier bottom = spec.bottomTexture() != null ? spec.bottomTexture() : defaults.bottomTexture();
        return new MachineControllerSpec(id, front, side, top, bottom, spec.allowVerticalFacing(),
                spec.fullyRotationallySymmetric(), spec.requireVerticalFacing(), spec.tooltip());
    }

    private static MachineAppearanceSpec toAppearanceSpec(AppearanceSpec spec) {
        MachineAppearanceSpec base = spec.machineBasicBlock() == null
                ? MachineAppearanceSpec.defaults()
                : MachineAppearanceSpec.fromBasicBlock(spec.machineBasicBlock());
        return new MachineAppearanceSpec(
                base.machineBasicBlock(),
                spec.controllerBaseTexture() != null ? spec.controllerBaseTexture() : base.controllerBaseTexture(),
                spec.formedPortBaseTexture() != null ? spec.formedPortBaseTexture() : base.formedPortBaseTexture());
    }

    private static PortRequirementSpec toPortRequirementSpec(PortRequirements requirements) {
        if (requirements.requirements().isEmpty()) return PortRequirementSpec.none();
        PortRequirementSpec.Builder builder = PortRequirementSpec.builder();
        requirements.requirements().forEach((portId, range) -> {
            if (range.max().isPresent()) builder.range(portId, range.min(), range.max().getAsInt());
            else builder.min(portId, range.min());
        });
        return builder.build();
    }

    private static PortTierRequirementSpec toPortTierRequirementSpec(PortTiers requirements) {
        if (requirements.requirements().isEmpty()) return PortTierRequirementSpec.none();
        return new PortTierRequirementSpec(requirements.requirements().stream()
                .map(requirement -> new PortTierRequirementSpec.Requirement(
                        switch (requirement.category()) {
                            case ITEM -> PortTierRequirementSpec.PortCategory.ITEM;
                            case FLUID -> PortTierRequirementSpec.PortCategory.FLUID;
                            case ENERGY -> PortTierRequirementSpec.PortCategory.ENERGY;
                        },
                        requirement.ioType(),
                        requirement.minTier(),
                        requirement.minTierId()))
                .toList());
    }

    private static MachineStructureRequirements toStructureRequirements(StructureRequirements requirements,
            Map<Identifier, ModifierDefinition> modifiers) {
        MachineStructureRequirements.Builder builder = MachineStructureRequirements.builder();
        requirements.levelSlots().forEach(builder::levelSlot);
        requirements.modifierReplacements().forEach((symbol, replacements) -> replacements.forEach(replacement -> {
            ModifierDefinition definition = modifiers.get(replacement.modifierId());
            if (definition == null) throw new ApiRegistrationException("Unknown machine modifier " + replacement.modifierId());
            builder.modifier(symbol, new SingleBlockModifierReplacement(replacement.modifierId().toString(),
                    toBlockPredicate(replacement.replacement()), definition.modifiers(), replacement.descriptiveStack()));
        }));
        return builder.build();
    }

    private static List<FactoryThreadSpec> toFactoryThreads(FactorySpec factory) {
        return factory.threads().stream()
                .map(thread -> new FactoryThreadSpec(thread.name(), thread.recipeIds()))
                .toList();
    }

    private static List<MachineStructureStage> toStructureStages(cn.howxu.mmcr.api.publicapi.machine.MachineStructureDefinition structure) {
        return cn.howxu.mmcr.api.machine.MachineStructureFamily.of(toStructureDefinition(structure)).stages();
    }

    private static MachineStructureDefinition.Declaration.Kind toDeclarationKind(StructureStage.Kind kind) {
        return switch (kind) {
            case FULL -> MachineStructureDefinition.Declaration.Kind.FULL;
            case EXTENSION -> MachineStructureDefinition.Declaration.Kind.EXTENSION;
        };
    }

    private static void validateRegistrationFields(MachineDefinition definition) {
        if (definition.factory().hasFactory() || definition.factory().threadLimit() != 1
                || !definition.factory().threads().isEmpty()) {
            throw new IllegalArgumentException("MachineRegistration cannot represent factory settings");
        }
        if (definition.failureAction() != cn.howxu.mmcr.api.machine.RecipeFailureActions.getDefaultAction()) {
            throw new IllegalArgumentException("MachineRegistration cannot represent failure action");
        }
    }

    private static void validateDefinitionStructureIds(MachineDefinition definition,
            cn.howxu.mmcr.api.publicapi.machine.MachineStructureDefinition structure) {
        if (!definition.id().equals(structure.machineId())) {
            throw new ApiRegistrationException("Machine definition id " + definition.id()
                    + " does not match structure machine id " + structure.machineId());
        }
    }
}
