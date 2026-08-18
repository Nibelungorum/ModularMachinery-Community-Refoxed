package cn.howxu.mmcr.compat.kubejs;

import cn.howxu.mmcr.api.machine.MachineDefinitions;
import cn.howxu.mmcr.api.machine.MachineRole;
import cn.howxu.mmcr.api.machine.MachineAppearanceSpec;
import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.MachineControllerSpec;
import cn.howxu.mmcr.api.machine.MachineRegistration;
import cn.howxu.mmcr.api.machine.SmartInterfaceModifier;
import cn.howxu.mmcr.api.machine.SmartInterfaceType;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.sound.MachineSoundRegistry;
import dev.latvian.mods.kubejs.registry.BuilderBase;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class MachineBuilderJS extends BuilderBase<MachineRegistration> {
    public transient String displayNameKey;
    public transient Identifier controllerFrontTexture;
    public transient Identifier controllerSideTexture;
    public transient Identifier controllerTopTexture;
    public transient Identifier controllerBottomTexture;
    public transient boolean allowVerticalFacing = false;
    public transient boolean fullyRotationallySymmetric = false;
    public transient boolean requireVerticalFacing = false;
    public transient boolean allowModifiers = false;
    public transient boolean allowMultithreading = false;
    public transient boolean allowParallelism = false;
    public transient int maxParallelAmount = 1;
    public transient Identifier machineBasicBlock;
    public transient Identifier controllerBaseTexture;
    public transient Identifier formedPortBaseTexture;
    public transient Identifier runningSoundId;
    public transient Identifier finishSoundId;
    public transient BlockArray pattern;
    private Identifier recipeFamilyId;
    private boolean expandableStructure;
    private MachineControllerSpec explicitControllerSpec;
    private MachineAppearanceSpec explicitAppearance;
    private MachineRole role = MachineRole.NORMAL;
    private boolean explicitRole;
    private final Set<Identifier> acceptedModuleIds = new LinkedHashSet<>();
    private boolean module;
    private final List<String> controllerTooltip = new ArrayList<>();
    private final List<SmartInterfaceType> smartInterfaceTypes = new ArrayList<>();
    private boolean shareSmartInterfaces;
    private final List<SmartInterfaceModifier> smartInterfaceModifiers = new ArrayList<>();

    public MachineBuilderJS(Identifier id) {
        super(id);
    }

    public MachineBuilderJS(String id) {
        this(Identifier.parse(id));
    }

    public MachineBuilderJS displayNameKey(String key) {
        this.displayNameKey = key;
        return this;
    }

    @Deprecated(forRemoval = true)
    public MachineBuilderJS localizedName(String name) {
        return displayNameKey(name);
    }

    @Override
    public MachineRegistration createObject() {
        var registration = MachineRegistration.builder(id)
                .displayNameKey(displayNameKey)
                .controllerSpec(explicitControllerSpec != null ? explicitControllerSpec : controllerSpec())
                .appearance(explicitAppearance != null ? explicitAppearance : appearanceSpec())
                .recipeFamilyId(recipeFamilyId != null ? recipeFamilyId : id)
                .allowModifiers(allowModifiers)
                .allowMultithreading(allowMultithreading)
                .allowParallelism(allowParallelism)
                .maxParallelAmount(maxParallelAmount)
                .runningSound(runningSoundId)
                .finishSound(finishSoundId)
                .pattern(pattern)
                .shareSmartInterfaces(shareSmartInterfaces);
        if (expandableStructure) registration.expandableStructure();
        if (explicitRole && (role == MachineRole.NORMAL && (!acceptedModuleIds.isEmpty() || module)
                || role == MachineRole.HOST && module)) {
            throw new IllegalArgumentException("Machine roles are mutually exclusive");
        }
        acceptedModuleIds.forEach(registration::host);
        if (module || explicitRole && role == MachineRole.MODULE) registration.module();
        if (explicitRole && role == MachineRole.HOST && acceptedModuleIds.isEmpty()) {
            throw new IllegalArgumentException("HOST role requires at least one accepted module ID; use host(...)");
        }
        smartInterfaceTypes.forEach(registration::smartInterfaceType);
        smartInterfaceModifiers.forEach(registration::smartInterfaceModifier);
        return registration.build();
    }

    public MachineBuilderJS recipeFamily(String recipeFamilyId) {
        this.recipeFamilyId = Identifier.parse(recipeFamilyId);
        return this;
    }

    public MachineBuilderJS expandableStructure(boolean expandableStructure) {
        this.expandableStructure = expandableStructure;
        return this;
    }

    public MachineBuilderJS factoryThreads(int factoryThreads) {
        return maxParallelAmount(factoryThreads);
    }

    public MachineBuilderJS controllerSpec(MachineControllerSpec controllerSpec) {
        this.explicitControllerSpec = controllerSpec;
        return this;
    }

    public MachineBuilderJS appearance(MachineAppearanceSpec appearance) {
        this.explicitAppearance = appearance;
        return this;
    }

    public MachineBuilderJS role(String role) {
        try {
            this.role = MachineRole.valueOf(role.toUpperCase(Locale.ROOT));
            this.explicitRole = true;
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid machine role '" + role + "'. Valid roles: "
                    + java.util.Arrays.toString(MachineRole.values()), exception);
        }
        return this;
    }

    public MachineBuilderJS runningSound(String soundId) {
        return runningSound(Identifier.parse(soundId));
    }

    public MachineBuilderJS runningSound(Identifier soundId) {
        this.runningSoundId = soundId;
        return this;
    }

    public MachineBuilderJS finishSound(String soundId) {
        return finishSound(Identifier.parse(soundId));
    }

    public MachineBuilderJS finishSound(Identifier soundId) {
        this.finishSoundId = soundId;
        return this;
    }

    public MachineBuilderJS host(String... moduleIds) {
        if (moduleIds == null) return this;
        for (String moduleId : moduleIds) {
            if (moduleId != null) acceptedModuleIds.add(Identifier.parse(moduleId));
        }
        return this;
    }

    public MachineBuilderJS host(Collection<String> moduleIds) {
        if (moduleIds == null) return this;
        for (String moduleId : moduleIds) {
            if (moduleId != null) acceptedModuleIds.add(Identifier.parse(moduleId));
        }
        return this;
    }

    public MachineBuilderJS module() {
        return module(true);
    }

    public MachineBuilderJS module(boolean module) {
        this.module = module;
        return this;
    }

    public MachineBuilderJS pattern(BlockArray pattern) {
        this.pattern = pattern;
        return this;
    }

    public MachineBuilderJS registerRunningSound(String soundId) {
        return registerRunningSound(Identifier.parse(soundId));
    }

    public MachineBuilderJS registerRunningSound(Identifier soundId) {
        this.runningSoundId = soundId;
        MachineSoundRegistry.requestRegistration(soundId);
        return this;
    }

    public MachineBuilderJS registerFinishSound(String soundId) {
        return registerFinishSound(Identifier.parse(soundId));
    }

    public MachineBuilderJS registerFinishSound(Identifier soundId) {
        this.finishSoundId = soundId;
        MachineSoundRegistry.requestRegistration(soundId);
        return this;
    }

    public MachineBuilderJS controllerTooltip(String... lines) {
        if (lines == null) return this;
        for (String line : lines) {
            if (line != null && !line.isBlank()) controllerTooltip.add(line);
        }
        return this;
    }

    public MachineBuilderJS controllerTextures(String front, String otherFive) {
        return controllerTextures(Identifier.parse(front), Identifier.parse(otherFive));
    }

    public MachineBuilderJS controllerTextures(Identifier front, Identifier otherFive) {
        this.controllerFrontTexture = front;
        this.controllerSideTexture = otherFive;
        this.controllerTopTexture = otherFive;
        this.controllerBottomTexture = otherFive;
        return this;
    }

    public MachineBuilderJS controllerTextures(Identifier front, Identifier side, Identifier top, Identifier bottom) {
        this.controllerFrontTexture = front;
        this.controllerSideTexture = side;
        this.controllerTopTexture = top;
        this.controllerBottomTexture = bottom;
        return this;
    }

    public MachineBuilderJS controllerFrontTexture(String texture) {
        return controllerFrontTexture(Identifier.parse(texture));
    }

    public MachineBuilderJS controllerFrontTexture(Identifier texture) {
        this.controllerFrontTexture = texture;
        return this;
    }

    public MachineBuilderJS controllerSideTexture(String texture) {
        return controllerSideTexture(Identifier.parse(texture));
    }

    public MachineBuilderJS controllerSideTexture(Identifier texture) {
        this.controllerSideTexture = texture;
        return this;
    }

    public MachineBuilderJS controllerTopTexture(String texture) {
        return controllerTopTexture(Identifier.parse(texture));
    }

    public MachineBuilderJS controllerTopTexture(Identifier texture) {
        this.controllerTopTexture = texture;
        return this;
    }

    public MachineBuilderJS controllerBottomTexture(String texture) {
        return controllerBottomTexture(Identifier.parse(texture));
    }

    public MachineBuilderJS controllerBottomTexture(Identifier texture) {
        this.controllerBottomTexture = texture;
        return this;
    }

    public MachineBuilderJS allowVerticalFacing() {
        return allowVerticalFacing(true);
    }

    public MachineBuilderJS allowVerticalFacing(boolean allow) {
        this.allowVerticalFacing = allow;
        return this;
    }

    public MachineBuilderJS fullyRotationallySymmetric() {
        return fullyRotationallySymmetric(true);
    }

    public MachineBuilderJS fullyRotationallySymmetric(boolean symmetric) {
        this.fullyRotationallySymmetric = symmetric;
        return this;
    }

    public MachineBuilderJS requireVerticalFacing() {
        return requireVerticalFacing(true);
    }

    public MachineBuilderJS requireVerticalFacing(boolean required) {
        this.requireVerticalFacing = required;
        if (required) this.allowVerticalFacing = true;
        return this;
    }

    public MachineBuilderJS allowModifiers() {
        return allowModifiers(true);
    }

    public MachineBuilderJS allowModifiers(boolean allow) {
        this.allowModifiers = allow;
        return this;
    }

    public MachineBuilderJS allowMultithreading() {
        return allowMultithreading(true);
    }

    public MachineBuilderJS allowMultithreading(boolean allow) {
        this.allowMultithreading = allow;
        return this;
    }

    public MachineBuilderJS allowParallelism() {
        return allowParallelism(true);
    }

    public MachineBuilderJS allowParallelism(boolean allow) {
        this.allowParallelism = allow;
        return this;
    }

    public MachineBuilderJS maxParallelAmount(int amount) {
        this.maxParallelAmount = amount;
        return this;
    }

    public MachineBuilderJS machineBasicBlock(String blockId) {
        return machineBasicBlock(Identifier.parse(blockId));
    }

    public MachineBuilderJS machineBasicBlock(Identifier blockId) {
        this.machineBasicBlock = blockId;
        return this;
    }

    public MachineBuilderJS controllerBaseTexture(String textureId) {
        return controllerBaseTexture(Identifier.parse(textureId));
    }

    public MachineBuilderJS controllerBaseTexture(Identifier textureId) {
        this.controllerBaseTexture = textureId;
        return this;
    }

    public MachineBuilderJS formedPortBaseTexture(String textureId) {
        return formedPortBaseTexture(Identifier.parse(textureId));
    }

    public MachineBuilderJS formedPortBaseTexture(Identifier textureId) {
        this.formedPortBaseTexture = textureId;
        return this;
    }

    public MachineBuilderJS appearance(String machineBasicBlock) {
        return machineBasicBlock(machineBasicBlock);
    }

    public SmartInterfaceTypeBuilderJS smartInterface(String type, float defaultValue) {
        return smartInterface(type, defaultValue, Float.MAX_VALUE);
    }

    public SmartInterfaceTypeBuilderJS smartInterface(String type, float minValue, float maxValue) {
        return new SmartInterfaceTypeBuilderJS(this, type, minValue, maxValue);
    }

    public MachineBuilderJS shareSmartInterface() {
        return shareSmartInterface(true);
    }

    public MachineBuilderJS shareSmartInterface(boolean share) {
        this.shareSmartInterfaces = share;
        return this;
    }

    public MachineBuilderJS durationByInterface(String type, float min, float max, float atMin, float atMax) {
        return durationByInterface(type, min, max, atMin, atMax, RecipeModifier.Operation.MULTIPLY);
    }

    public MachineBuilderJS durationByInterface(String type, float min, float max, float atMin, float atMax,
            RecipeModifier.Operation operation) {
        smartInterfaceModifiers.add(SmartInterfaceModifier.duration(type, min, max, atMin, atMax, operation));
        return this;
    }

    public MachineBuilderJS energyByInterface(String type, float min, float max, float atMin, float atMax) {
        return energyByInterface(type, min, max, atMin, atMax, RecipeModifier.Operation.MULTIPLY);
    }

    public MachineBuilderJS energyByInterface(String type, float min, float max, float atMin, float atMax,
            RecipeModifier.Operation operation) {
        smartInterfaceModifiers.add(SmartInterfaceModifier.energy(type, min, max, atMin, atMax, operation));
        return this;
    }

    public MachineBuilderJS itemInputByInterface(String type, float min, float max, float atMin, float atMax) {
        return itemByInterface(type, RecipeModifier.IOType.INPUT, false, min, max, atMin, atMax,
                RecipeModifier.Operation.MULTIPLY);
    }

    public MachineBuilderJS itemOutputByInterface(String type, float min, float max, float atMin, float atMax) {
        return itemByInterface(type, RecipeModifier.IOType.OUTPUT, false, min, max, atMin, atMax,
                RecipeModifier.Operation.MULTIPLY);
    }

    public MachineBuilderJS itemInputChanceByInterface(String type, float min, float max, float atMin, float atMax) {
        return itemInputChanceByInterface(type, min, max, atMin, atMax, RecipeModifier.Operation.MULTIPLY);
    }

    public MachineBuilderJS itemInputChanceByInterface(String type, float min, float max, float atMin, float atMax,
            RecipeModifier.Operation operation) {
        return itemByInterface(type, RecipeModifier.IOType.INPUT, true, min, max, atMin, atMax, operation);
    }

    public MachineBuilderJS itemOutputChanceByInterface(String type, float min, float max, float atMin, float atMax) {
        return itemOutputChanceByInterface(type, min, max, atMin, atMax, RecipeModifier.Operation.MULTIPLY);
    }

    public MachineBuilderJS itemOutputChanceByInterface(String type, float min, float max, float atMin, float atMax,
            RecipeModifier.Operation operation) {
        return itemByInterface(type, RecipeModifier.IOType.OUTPUT, true, min, max, atMin, atMax, operation);
    }

    public MachineBuilderJS fluidInputByInterface(String type, float min, float max, float atMin, float atMax) {
        return fluidByInterface(type, RecipeModifier.IOType.INPUT, false, min, max, atMin, atMax,
                RecipeModifier.Operation.MULTIPLY);
    }

    public MachineBuilderJS fluidOutputByInterface(String type, float min, float max, float atMin, float atMax) {
        return fluidByInterface(type, RecipeModifier.IOType.OUTPUT, false, min, max, atMin, atMax,
                RecipeModifier.Operation.MULTIPLY);
    }

    public MachineBuilderJS fluidInputChanceByInterface(String type, float min, float max, float atMin, float atMax) {
        return fluidInputChanceByInterface(type, min, max, atMin, atMax, RecipeModifier.Operation.MULTIPLY);
    }

    public MachineBuilderJS fluidInputChanceByInterface(String type, float min, float max, float atMin, float atMax,
            RecipeModifier.Operation operation) {
        return fluidByInterface(type, RecipeModifier.IOType.INPUT, true, min, max, atMin, atMax, operation);
    }

    public MachineBuilderJS fluidOutputChanceByInterface(String type, float min, float max, float atMin, float atMax) {
        return fluidOutputChanceByInterface(type, min, max, atMin, atMax, RecipeModifier.Operation.MULTIPLY);
    }

    public MachineBuilderJS fluidOutputChanceByInterface(String type, float min, float max, float atMin, float atMax,
            RecipeModifier.Operation operation) {
        return fluidByInterface(type, RecipeModifier.IOType.OUTPUT, true, min, max, atMin, atMax, operation);
    }

    private MachineBuilderJS itemByInterface(String type, RecipeModifier.IOType io, boolean chance, float min, float max,
            float atMin, float atMax, RecipeModifier.Operation operation) {
        smartInterfaceModifiers.add(SmartInterfaceModifier.item(type, io, chance, min, max, atMin, atMax, operation));
        return this;
    }

    private MachineBuilderJS fluidByInterface(String type, RecipeModifier.IOType io, boolean chance, float min,
            float max, float atMin, float atMax, RecipeModifier.Operation operation) {
        smartInterfaceModifiers.add(SmartInterfaceModifier.fluid(type, io, chance, min, max, atMin, atMax, operation));
        return this;
    }

    public void registerObject() {
        MachineDefinitions.register(createObject());
    }

    public MachineBuilderJS register() {
        registerObject();
        return this;
    }

    private MachineControllerSpec controllerSpec() {
        MachineControllerSpec defaults = MachineControllerSpec.defaultsFor(id);
        return new MachineControllerSpec(
                defaults.id(),
                controllerFrontTexture != null ? controllerFrontTexture : defaults.frontTexture(),
                controllerSideTexture != null ? controllerSideTexture : defaults.sideTexture(),
                controllerTopTexture != null ? controllerTopTexture : defaults.topTexture(),
                controllerBottomTexture != null ? controllerBottomTexture : defaults.bottomTexture(),
                allowVerticalFacing,
                fullyRotationallySymmetric,
                requireVerticalFacing,
                controllerTooltip);
    }

    private MachineAppearanceSpec appearanceSpec() {
        MachineAppearanceSpec base = machineBasicBlock == null
                ? MachineAppearanceSpec.defaults()
                : MachineAppearanceSpec.fromBasicBlock(machineBasicBlock);
        return new MachineAppearanceSpec(
                base.machineBasicBlock(),
                controllerBaseTexture != null ? controllerBaseTexture : base.controllerBaseTexture(),
                formedPortBaseTexture != null ? formedPortBaseTexture : base.formedPortBaseTexture());
    }

    public static final class SmartInterfaceTypeBuilderJS {
        private final MachineBuilderJS parent;
        private final String type;
        private final float minValue;
        private final float maxValue;
        private int priority;
        private SmartInterfaceType.ValueType valueType = SmartInterfaceType.ValueType.FLOAT;

        private SmartInterfaceTypeBuilderJS(MachineBuilderJS parent, String type, float minValue, float maxValue) {
            this.parent = parent;
            this.type = type;
            this.minValue = minValue;
            this.maxValue = maxValue;
        }

        public SmartInterfaceTypeBuilderJS priority(int priority) {
            this.priority = priority;
            return this;
        }

        public SmartInterfaceTypeBuilderJS valueType(String valueType) {
            this.valueType = SmartInterfaceType.ValueType.byName(valueType);
            return this;
        }

        public MachineBuilderJS end() {
            parent.smartInterfaceTypes.add(new SmartInterfaceType(type, minValue, maxValue, priority, valueType));
            return parent;
        }
    }
}
