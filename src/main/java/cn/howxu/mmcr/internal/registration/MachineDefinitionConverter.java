package cn.howxu.mmcr.internal.registration;

import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.internal.structure.PatternRows;
import cn.howxu.mmcr.api.machine.DynamicMachine;
import cn.howxu.mmcr.api.machine.FactoryThreadSpec;
import cn.howxu.mmcr.api.machine.MachineAppearanceSpec;
import cn.howxu.mmcr.api.machine.MachineControllerSpec;
import cn.howxu.mmcr.api.machine.MachineRegistration;
import cn.howxu.mmcr.api.machine.MachineRole;
import cn.howxu.mmcr.api.machine.MachineStructureFamily;
import cn.howxu.mmcr.api.machine.MachineStructureDefinition;
import cn.howxu.mmcr.api.machine.MachineStructureRequirements;
import cn.howxu.mmcr.api.machine.MachineStructureStage;
import cn.howxu.mmcr.api.machine.PortRequirementSpec;
import cn.howxu.mmcr.api.machine.PortTierRequirementSpec;
import cn.howxu.mmcr.api.machine.RecipeFailureActions;
import cn.howxu.mmcr.api.machine.definition.AppearanceSpec;
import cn.howxu.mmcr.api.machine.definition.ControllerSpec;
import cn.howxu.mmcr.api.machine.definition.FactorySpec;
import cn.howxu.mmcr.api.machine.definition.MachineDefinition;
import cn.howxu.mmcr.api.machine.definition.PatternDefinition;
import cn.howxu.mmcr.api.machine.definition.PortRequirements;
import cn.howxu.mmcr.api.machine.definition.PortTiers;
import cn.howxu.mmcr.api.machine.definition.StructureRequirements;
import cn.howxu.mmcr.api.machine.definition.StructureStage;
import cn.howxu.mmcr.api.machine.definition.MachineLevel;
import cn.howxu.mmcr.api.machine.definition.BlockPredicate;
import cn.howxu.mmcr.api.data.view.DataStorage;
import cn.howxu.mmcr.api.network.view.MachineReference;
import cn.howxu.mmcr.api.network.view.RequestBody;
import cn.howxu.mmcr.api.network.view.RequestFailureReason;
import cn.howxu.mmcr.api.network.view.RequestInfo;
import cn.howxu.mmcr.api.registration.ApiRegistrationException;
import java.util.stream.Collectors;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Internal conversion boundary for public startup machine declarations.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class MachineDefinitionConverter {
    private MachineDefinitionConverter() {
    }

    public static cn.howxu.mmcr.api.machine.level.MachineLevel toMachineLevel(MachineLevel level) {
        return new cn.howxu.mmcr.api.machine.level.MachineLevel(level.id(), level.typeId(), level.priority(),
                toBlockPredicate(level.statePredicate()), level.representative().stack(), level.modifier());
    }

    public static BlockArray toBlockArray(PatternDefinition pattern) {
        Map<Character, cn.howxu.mmcr.api.machine.BlockPredicate> symbols = new LinkedHashMap<>();
        PatternRows rows = PatternRows.copyOf(pattern.layers());
        pattern.predicates().forEach((symbol, predicate) -> {
            if (symbol != ' ' && rows.hasSymbol(symbol)) symbols.put(symbol, toBlockPredicate(predicate));
        });
        return BlockArray.fromRows(rows, symbols, pattern.controllerSymbol());
    }

    public static DynamicMachine toDynamicMachine(MachineDefinition definition, cn.howxu.mmcr.api.machine.definition.MachineStructureDefinition structure) {
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
                definition.networkInterface(),
                toStructureStages(structure),
                definition.failureAction(),
                definition.behavior(), definition.requestProcessors(), toInternalRequestFailures(definition.requestFailures()));
    }

    public static MachineRegistration toRegistration(MachineDefinition definition) {
        validateRegistrationFields(definition);
        return toStartupRegistration(definition);
    }

    public static MachineRegistration toStartupRegistration(MachineDefinition definition) {
        return toStartupRegistration(definition, null);
    }

    public static MachineRegistration toStartupRegistration(MachineDefinition definition,
            cn.howxu.mmcr.api.machine.definition.MachineStructureDefinition structure) {
        if (structure != null) validateDefinitionStructureIds(definition, structure);
        MachineRegistration.Builder builder = MachineRegistration.builder(definition.id())
                .recipePoolIds(definition.recipePoolIds())
                .displayNameKey(definition.displayNameKey())
                .controllerSpec(toControllerSpec(definition.id(), definition.controller()))
                .appearance(toAppearanceSpec(definition.appearance()))
                .allowParallelism(definition.parallelizable())
                .maxParallelAmount(definition.maxParallelism())
                .allowMultithreading(definition.allowMultithreading())
                .factoryThreadLimit(definition.factory().threadLimit())
                .networkInterface(definition.networkInterface())
                .shareSmartInterfaces(definition.shareSmartInterfaces())
                .behavior(definition.behavior());
        definition.allowedModifierIds().forEach(builder::allowModifier);
        definition.requestProcessors().forEach(builder::requestProcess);
        definition.requestFailures().forEach((id, failure) -> builder.requestFailed(id, toInternalRequestFailed(failure)));
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

    public static MachineStructureDefinition toStructureDefinition(cn.howxu.mmcr.api.machine.definition.MachineStructureDefinition structure) {
        return new MachineStructureDefinition(structure.machineId(), structure.stages().stream()
                .map(stage -> toDeclaration(stage, structure.stateSensitive())).toList());
    }

    public static MachineStructureDefinition.Declaration toDeclaration(StructureStage stage) {
        return toDeclaration(stage, false);
    }

    private static MachineStructureDefinition.Declaration toDeclaration(StructureStage stage, boolean stateSensitive) {
        return new MachineStructureDefinition.Declaration(toDeclarationKind(stage.kind()), toBlockArray(stage.pattern()),
                toPortRequirementSpec(stage.portRequirements()), toPortTierRequirementSpec(stage.portTiers()),
                List.of(), toStructureRequirements(stage.requirements()), stateSensitive);
    }

    private static cn.howxu.mmcr.api.machine.BlockPredicate toBlockPredicate(BlockPredicate predicate) {
        if (predicate.isMachineCoupler()) return cn.howxu.mmcr.api.machine.BlockPredicate.machineCoupler();
        if (predicate.blockState().isPresent()) {
            return new cn.howxu.mmcr.api.machine.BlockPredicate.OfBlockState(predicate.blockState().get());
        }
        if (predicate.block().isPresent()) {
            return new cn.howxu.mmcr.api.machine.BlockPredicate.OfBlock(predicate.block().get());
        }
        if (predicate.blockSupplier().isPresent()) {
            return new cn.howxu.mmcr.api.machine.BlockPredicate.DeferredBlock(predicate.blockSupplier().get());
        }
        if (predicate.tag().isPresent()) {
            return new cn.howxu.mmcr.api.machine.BlockPredicate.OfTag(predicate.tag().get());
        }
        return new cn.howxu.mmcr.api.machine.BlockPredicate.AnyOf(
                predicate.alternatives().stream().map(MachineDefinitionConverter::toBlockPredicate).toList());
    }

    private static MachineControllerSpec toControllerSpec(ResourceLocation machineId, ControllerSpec spec) {
        MachineControllerSpec defaults = MachineControllerSpec.defaultsFor(machineId);
        ResourceLocation id = spec.id() != null ? spec.id() : defaults.id();
        ResourceLocation front = spec.frontTexture() != null ? spec.frontTexture() : defaults.frontTexture();
        ResourceLocation side = spec.sideTexture() != null ? spec.sideTexture() : defaults.sideTexture();
        ResourceLocation top = spec.topTexture() != null ? spec.topTexture() : defaults.topTexture();
        ResourceLocation bottom = spec.bottomTexture() != null ? spec.bottomTexture() : defaults.bottomTexture();
        return new MachineControllerSpec(id, front, side, top, bottom, spec.allowVerticalFacing(),
                spec.fullyRotationallySymmetric(), spec.requireVerticalFacing(), spec.tooltip());
    }

    private static MachineAppearanceSpec toAppearanceSpec(AppearanceSpec spec) {
        MachineAppearanceSpec base = MachineAppearanceSpec.defaults();
        return new MachineAppearanceSpec(
                spec.machineBasicBlock() != null ? spec.machineBasicBlock() : base.machineBasicBlock(),
                spec.controllerBaseTexture(),
                spec.formedPortBaseTexture(),
                spec.controllerIdleOverlayTexture(),
                spec.controllerActiveOverlayTexture());
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
        return PortTierRequirementSpec.from(requirements);
    }

    private static MachineStructureRequirements toStructureRequirements(StructureRequirements requirements) {
        MachineStructureRequirements.Builder builder = MachineStructureRequirements.builder();
        requirements.levelSlots().forEach(builder::levelSlot);
        requirements.modifierReplacements().forEach((symbol, uses) -> uses.forEach(use ->
                builder.modifier(symbol, use.modifierId(), toBlockPredicate(use.replacement()))));
        return builder.build();
    }

    private static List<FactoryThreadSpec> toFactoryThreads(FactorySpec factory) {
        return factory.threads().stream()
                .map(thread -> new FactoryThreadSpec(thread.name(), thread.recipeIds()))
                .toList();
    }

    private static Map<ResourceLocation, cn.howxu.mmcr.api.network.RequestFailed> toInternalRequestFailures(
            Map<ResourceLocation, cn.howxu.mmcr.api.network.view.RequestFailed> failures) {
        return failures.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey,
                entry -> toInternalRequestFailed(entry.getValue()), (first, ignored) -> first, LinkedHashMap::new));
    }

    public static Map<ResourceLocation, cn.howxu.mmcr.api.network.view.RequestFailed> fromInternalRequestFailures(
            Map<ResourceLocation, cn.howxu.mmcr.api.network.RequestFailed> failures) {
        return failures.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey,
                entry -> (body, request, senderStorage, reason) -> entry.getValue().fail(
                        (cn.howxu.mmcr.api.network.RequestBody) body.bridgeValue(),
                        new cn.howxu.mmcr.api.network.RequestInfo(request.requestId(),
                                (cn.howxu.mmcr.api.network.MachineReference) request.peer().bridgeValue()),
                        senderStorage == null ? null : (cn.howxu.mmcr.api.data.DataStorage) senderStorage.bridgeValue(),
                        cn.howxu.mmcr.api.network.RequestFailureReason.valueOf(reason.name())),
                (first, ignored) -> first, LinkedHashMap::new));
    }

    private static cn.howxu.mmcr.api.network.RequestFailed toInternalRequestFailed(
            cn.howxu.mmcr.api.network.view.RequestFailed failure) {
        return (body, request, senderStorage, reason) -> failure.fail(RequestBody.fromInternal(body),
                new RequestInfo(request.requestId(), MachineReference.fromInternal(request.peer())),
                senderStorage == null ? null : DataStorage.view(senderStorage),
                RequestFailureReason.valueOf(reason.name()));
    }

    private static List<MachineStructureStage> toStructureStages(cn.howxu.mmcr.api.machine.definition.MachineStructureDefinition structure) {
        return MachineStructureFamily.of(toStructureDefinition(structure)).stages();
    }

    private static MachineStructureDefinition.Declaration.Kind toDeclarationKind(StructureStage.Kind kind) {
        return switch (kind) {
            case FULL -> MachineStructureDefinition.Declaration.Kind.FULL;
            case EXPANSION -> MachineStructureDefinition.Declaration.Kind.FULL;
            case EXTENSION -> MachineStructureDefinition.Declaration.Kind.EXTENSION;
        };
    }

    private static void validateRegistrationFields(MachineDefinition definition) {
        if (!definition.factory().threads().isEmpty()) {
            throw new IllegalArgumentException("MachineRegistration cannot represent factory threads");
        }
        if (definition.failureAction() != RecipeFailureActions.getDefaultAction()) {
            throw new IllegalArgumentException("MachineRegistration cannot represent failure action");
        }
    }

    private static void validateDefinitionStructureIds(MachineDefinition definition,
            cn.howxu.mmcr.api.machine.definition.MachineStructureDefinition structure) {
        if (!definition.id().equals(structure.machineId())) {
            throw new ApiRegistrationException("Machine definition id " + definition.id()
                    + " does not match structure machine id " + structure.machineId());
        }
    }
}
