package cn.howxu.mmcr.compat.kubejs;

import cn.howxu.mmcr.api.publicapi.machine.MachineBuilder;
import cn.howxu.mmcr.api.publicapi.machine.MachineBehaviorContext;
import cn.howxu.mmcr.api.publicapi.machine.MachineDefinition;
import cn.howxu.mmcr.api.machine.MachineRole;
import cn.howxu.mmcr.api.machine.MachineAppearanceSpec;
import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.api.machine.PortTierRequirementSpec;
import cn.howxu.mmcr.api.machine.RecipeFailureActions;
import cn.howxu.mmcr.api.machine.MachineControllerSpec;
import cn.howxu.mmcr.api.machine.MachineRegistration;
import cn.howxu.mmcr.api.publicapi.machine.MachineBehavior;
import cn.howxu.mmcr.api.publicapi.machine.RecipeBehavior;
import cn.howxu.mmcr.api.publicapi.machine.TickBehavior;
import cn.howxu.mmcr.api.machine.SmartInterfaceModifier;
import cn.howxu.mmcr.api.machine.SmartInterfaceType;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.network.RequestFailed;
import cn.howxu.mmcr.api.network.RequestProcess;
import dev.latvian.mods.kubejs.registry.BuilderBase;
import dev.latvian.mods.rhino.util.HideFromJS;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.Arrays;
import java.util.Objects;
import java.util.function.Consumer;

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
    public transient long maxParallelAmount = 1L;
    private transient int factoryThreadLimit = 1;
    public transient Identifier machineBasicBlock;
    public transient Identifier controllerBaseTexture;
    public transient Identifier formedPortBaseTexture;
    public transient Identifier runningSoundId;
    public transient Identifier finishSoundId;
    private Identifier recipeFamilyId;
    private boolean expandableStructure;
    private MachineControllerSpec explicitControllerSpec;
    private MachineAppearanceSpec explicitAppearance;
    private MachineRole role = MachineRole.NORMAL;
    private boolean explicitRole;
    private final Set<Identifier> acceptedModuleIds = new LinkedHashSet<>();
    private int networkInterfaceMaxCount;
    private int networkInterfaceMaxConnections;
    private final Set<Identifier> allowedNetworkMachineIds = new LinkedHashSet<>();
    private boolean module;
    private final List<String> controllerTooltip = new ArrayList<>();
    private final List<SmartInterfaceType> smartInterfaceTypes = new ArrayList<>();
    private boolean shareSmartInterfaces;
    private final List<SmartInterfaceModifier> smartInterfaceModifiers = new ArrayList<>();
    private MachineBehavior behavior = RecipeBehavior.defaults();
    private MachineBehavior.Kind behaviorKind;
    private MachineBehavior.MachineCallback preServerTick;
    private MachineBehavior.MachineCallback postServerTick;
    private final MachineBuilder callbackBuilder;

    public MachineBuilderJS(Identifier id) {
        super(id);
        callbackBuilder = MachineBuilder.machine(id);
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

    public MachineBuilderJS preServerTick(Consumer<MachineBehaviorContext> callback) {
        if (behaviorKind == MachineBehavior.Kind.TICK) {
            throw new IllegalStateException("Recipe server tick hooks require recipe behavior");
        }
        preServerTick = Objects.requireNonNull(callback, "preServerTick")::accept;
        return this;
    }

    public MachineBuilderJS postServerTick(Consumer<MachineBehaviorContext> callback) {
        if (behaviorKind == MachineBehavior.Kind.TICK) {
            throw new IllegalStateException("Recipe server tick hooks require recipe behavior");
        }
        postServerTick = Objects.requireNonNull(callback, "postServerTick")::accept;
        return this;
    }

    public MachineBuilderJS networkInterface(int maxCount, int maxConnections) {
        networkInterfaceMaxCount = maxCount;
        networkInterfaceMaxConnections = maxConnections;
        return this;
    }

    public MachineBuilderJS allowNetworkMachine(String machineId) {
        allowedNetworkMachineIds.add(Identifier.parse(machineId));
        return this;
    }

    public MachineBuilderJS requestProcess(String requestId, RequestProcess process) {
        callbackBuilder.requestProcess(Identifier.parse(requestId), process);
        return this;
    }

    public MachineBuilderJS requestFailed(String requestId, RequestFailed failure) {
        callbackBuilder.requestFailed(Identifier.parse(requestId), failure);
        return this;
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
                .shareSmartInterfaces(shareSmartInterfaces)
                .behavior(behaviorWithServerTickHooks());
        registration.networkInterface(networkInterfaceMaxCount, networkInterfaceMaxConnections);
        allowedNetworkMachineIds.forEach(registration::allowNetworkMachine);
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
        MachineDefinition callbacks = callbackBuilder.build();
        callbacks.requestProcessors().forEach(registration::requestProcess);
        callbacks.requestFailures().forEach(registration::requestFailed);
        return registration.build();
    }

    public MachineBuilderJS recipeBehavior(Consumer<MachineBehaviorBuilderJS> builder) {
        if (behaviorKind == MachineBehavior.Kind.TICK) {
            throw new IllegalStateException("Machine cannot configure both recipe and tick behavior");
        }
        MachineBehaviorBuilderJS behaviorBuilder = new MachineBehaviorBuilderJS(MachineBehavior.Kind.RECIPE);
        Objects.requireNonNull(builder, "builder").accept(behaviorBuilder);
        behavior = behaviorBuilder.build();
        behaviorKind = MachineBehavior.Kind.RECIPE;
        return this;
    }

    public MachineBuilderJS tickBehavior(Consumer<MachineBehaviorBuilderJS> builder) {
        if (preServerTick != null || postServerTick != null) {
            throw new IllegalStateException("Cannot configure server tick hooks for tick behavior");
        }
        if (behaviorKind == MachineBehavior.Kind.RECIPE) {
            throw new IllegalStateException("Machine cannot configure both recipe and tick behavior");
        }
        MachineBehaviorBuilderJS behaviorBuilder = new MachineBehaviorBuilderJS(MachineBehavior.Kind.TICK);
        Objects.requireNonNull(builder, "builder").accept(behaviorBuilder);
        behavior = behaviorBuilder.build();
        behaviorKind = MachineBehavior.Kind.TICK;
        return this;
    }

    private MachineBehavior behaviorWithServerTickHooks() {
        if (preServerTick == null && postServerTick == null) return behavior;
        if (!(behavior instanceof RecipeBehavior recipe)) {
            throw new IllegalStateException("Recipe server tick hooks require recipe behavior");
        }
        return RecipeBehavior.builder()
                .idleStart(recipe.idleStart())
                .idleEnd(recipe.idleEnd())
                .beforeStart(recipe.beforeStart())
                .recipeTick(recipe.recipeTick())
                .beforeFinish(recipe.beforeFinish())
                .preServerTick(preServerTick == null ? recipe.preServerTick() : preServerTick)
                .postServerTick(postServerTick == null ? recipe.postServerTick() : postServerTick)
                .build();
    }

    public MachineBuilderJS recipeFamily(String recipeFamilyId) {
        this.recipeFamilyId = Identifier.parse(recipeFamilyId);
        return this;
    }

    public MachineBuilderJS expandableStructure() {
        return expandableStructure(true);
    }

    public MachineBuilderJS expandableStructure(boolean expandableStructure) {
        this.expandableStructure = expandableStructure;
        return this;
    }

    public MachineBuilderJS factoryThreads(int factoryThreads) {
        if (factoryThreads < 1) throw new IllegalArgumentException("factoryThreads must be positive");
        this.factoryThreadLimit = factoryThreads;
        return this;
    }

    public MachineBuilderJS controllerSpec(MachineControllerSpec controllerSpec) {
        this.explicitControllerSpec = controllerSpec;
        return this;
    }

    @HideFromJS
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
                    + Arrays.toString(MachineRole.values()), exception);
        }
        return this;
    }

    public MachineBuilderJS runningSound(String soundId) {
        return runningSound(Identifier.parse(soundId));
    }

    @HideFromJS
    public MachineBuilderJS runningSound(Identifier soundId) {
        MachineRegistration.validateSound(soundId);
        this.runningSoundId = soundId;
        return this;
    }

    public MachineBuilderJS finishSound(String soundId) {
        return finishSound(Identifier.parse(soundId));
    }

    @HideFromJS
    public MachineBuilderJS finishSound(Identifier soundId) {
        MachineRegistration.validateSound(soundId);
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

    @HideFromJS
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

    @HideFromJS
    public MachineBuilderJS controllerTextures(Identifier front, Identifier otherFive) {
        this.controllerFrontTexture = front;
        this.controllerSideTexture = otherFive;
        this.controllerTopTexture = otherFive;
        this.controllerBottomTexture = otherFive;
        return this;
    }

    @HideFromJS
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

    @HideFromJS
    public MachineBuilderJS controllerFrontTexture(Identifier texture) {
        this.controllerFrontTexture = texture;
        return this;
    }

    public MachineBuilderJS controllerSideTexture(String texture) {
        return controllerSideTexture(Identifier.parse(texture));
    }

    @HideFromJS
    public MachineBuilderJS controllerSideTexture(Identifier texture) {
        this.controllerSideTexture = texture;
        return this;
    }

    public MachineBuilderJS controllerTopTexture(String texture) {
        return controllerTopTexture(Identifier.parse(texture));
    }

    @HideFromJS
    public MachineBuilderJS controllerTopTexture(Identifier texture) {
        this.controllerTopTexture = texture;
        return this;
    }

    public MachineBuilderJS controllerBottomTexture(String texture) {
        return controllerBottomTexture(Identifier.parse(texture));
    }

    @HideFromJS
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

    public MachineBuilderJS maxParallelAmount(long amount) {
        this.maxParallelAmount = amount;
        return this;
    }

    public MachineBuilderJS maxParallelism(long maxParallelism) {
        return maxParallelAmount(maxParallelism);
    }

    public MachineBuilderJS machineBasicBlock(String blockId) {
        return machineBasicBlock(Identifier.parse(blockId));
    }

    @HideFromJS
    public MachineBuilderJS machineBasicBlock(Identifier blockId) {
        this.machineBasicBlock = blockId;
        return this;
    }

    public MachineBuilderJS controllerBaseTexture(String textureId) {
        return controllerBaseTexture(Identifier.parse(textureId));
    }

    @HideFromJS
    public MachineBuilderJS controllerBaseTexture(Identifier textureId) {
        this.controllerBaseTexture = textureId;
        return this;
    }

    public MachineBuilderJS formedPortBaseTexture(String textureId) {
        return formedPortBaseTexture(Identifier.parse(textureId));
    }

    @HideFromJS
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

    public BlockPredicate anyOfItemInput() { return KubeJSInterfaceHelpers.anyOfItemInput(); }
    public BlockPredicate anyOfItemOutput() { return KubeJSInterfaceHelpers.anyOfItemOutput(); }
    public BlockPredicate anyOfFluidInput() { return KubeJSInterfaceHelpers.anyOfFluidInput(); }
    public BlockPredicate anyOfFluidOutput() { return KubeJSInterfaceHelpers.anyOfFluidOutput(); }
    public BlockPredicate anyOfEnergyInput() { return KubeJSInterfaceHelpers.anyOfEnergyInput(); }
    public BlockPredicate anyOfEnergyOutput() { return KubeJSInterfaceHelpers.anyOfEnergyOutput(); }
    public BlockPredicate parallelControllers() { return KubeJSInterfaceHelpers.parallelControllers(); }
    public BlockPredicate smartInterfaceBlock() { return KubeJSInterfaceHelpers.smartInterface(); }
    public BlockPredicate anyOfPort(String... ids) { return KubeJSInterfaceHelpers.anyOfPort(ids); }
    public BlockPredicate anyOfPort(Identifier... ids) { return KubeJSInterfaceHelpers.anyOfPort(ids); }
    public cn.howxu.mmcr.api.machine.BlockPredicate anyOfPort(cn.howxu.mmcr.api.publicapi.machine.BlockPredicate... predicates) {
        return KubeJSInterfaceHelpers.anyOfPort(predicates);
    }
    public BlockPredicate smartInterface() { return KubeJSInterfaceHelpers.smartInterface(); }
    public BlockPredicate dataStorage() { return KubeJSInterfaceHelpers.dataStorage(); }
    public PortTierRequirementSpec itemInputTier(String id) { return KubeJSInterfaceHelpers.itemInputTier(id); }
    public PortTierRequirementSpec itemOutputTier(String id) { return KubeJSInterfaceHelpers.itemOutputTier(id); }
    public PortTierRequirementSpec fluidInputTier(String id) { return KubeJSInterfaceHelpers.fluidInputTier(id); }
    public PortTierRequirementSpec fluidOutputTier(String id) { return KubeJSInterfaceHelpers.fluidOutputTier(id); }
    public PortTierRequirementSpec energyInputTier(String id) { return KubeJSInterfaceHelpers.energyInputTier(id); }
    public PortTierRequirementSpec energyOutputTier(String id) { return KubeJSInterfaceHelpers.energyOutputTier(id); }

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
        MachineRegistration registration = createObject();
        MachineBuilder builder = MachineBuilder.machine(id)
                .displayNameKey(registration.displayNameKey())
                .controller(controller -> controller
                        .id(registration.controllerSpec().id())
                        .frontTexture(registration.controllerSpec().frontTexture())
                        .sideTexture(registration.controllerSpec().sideTexture())
                        .topTexture(registration.controllerSpec().topTexture())
                        .bottomTexture(registration.controllerSpec().bottomTexture())
                        .allowVerticalFacing(registration.controllerSpec().allowVerticalFacing())
                        .fullyRotationallySymmetric(registration.controllerSpec().fullyRotationallySymmetric())
                        .requireVerticalFacing(registration.controllerSpec().requireVerticalFacing())
                        .tooltip(registration.controllerSpec().tooltip().toArray(String[]::new)))
                .appearance(appearance -> appearance
                        .machineBasicBlock(registration.appearance().machineBasicBlock())
                        .controllerBaseTexture(registration.appearance().controllerBaseTexture())
                        .formedPortBaseTexture(registration.appearance().formedPortBaseTexture()))
                .factory(factory -> factory
                        .hasFactory(factoryThreadLimit > 1)
                        .threadLimit(factoryThreadLimit))
                .role(cn.howxu.mmcr.api.publicapi.machine.MachineRole.valueOf(registration.role().name()))
                .networkInterface(registration.networkInterface().maxCount(), registration.networkInterface().maxConnections())
                .maxParallelism(registration.maxParallelAmount())
                .parallelizable(registration.allowParallelism())
                .failureAction(RecipeFailureActions.getDefaultAction());
        if (registration.behavior() instanceof TickBehavior tick) {
            builder.tickBehavior(behavior -> behavior.serverTick(tick.serverTick()));
        } else if (registration.behavior() instanceof RecipeBehavior recipe) {
            builder.recipeBehavior(behavior -> behavior
                    .idleStart(recipe.idleStart())
                    .idleEnd(recipe.idleEnd())
                    .beforeStart(recipe.beforeStart())
                    .recipeTick(recipe.recipeTick())
                    .beforeFinish(recipe.beforeFinish()))
                    .preServerTick(recipe.preServerTick())
                    .postServerTick(recipe.postServerTick());
        }
        registration.acceptedModuleIds().forEach(builder::acceptedModule);
        registration.networkInterface().allowedMachineIds().forEach(builder::allowNetworkMachine);
        if (registration.role() == MachineRole.MODULE) {
            builder.role(cn.howxu.mmcr.api.publicapi.machine.MachineRole.MODULE);
        }
        MachineDefinition base = builder.build();
        MachineDefinition definition = new MachineDefinition(base.id(), base.displayNameKey(), base.controller(), base.appearance(),
                base.factory(), base.role(), base.acceptedModuleIds(), base.networkInterface(), base.maxParallelism(), base.parallelizable(), base.failureAction(),
                registration.allowModifiers(), registration.allowMultithreading(), factoryThreadLimit,
                registration.expandableStructure(), registration.smartInterfaceTypes().entrySet().stream()
                        .collect(java.util.stream.Collectors.toMap(java.util.Map.Entry::getKey,
                                entry -> toPublicSmartInterfaceType(entry.getValue()))),
                registration.shareSmartInterfaces(), registration.smartInterfaceModifiers().stream()
                        .map(MachineBuilderJS::toPublicSmartInterfaceModifier).toList(),
                registration.runningSoundId(), registration.finishSoundId(), registration.pattern(), registration.behavior(),
                registration.requestProcessors(), registration.requestFailures());
        Plugin.registerStartupMachine(definition);
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

    private static cn.howxu.mmcr.api.publicapi.machine.SmartInterfaceType toPublicSmartInterfaceType(
            SmartInterfaceType type) {
        return new cn.howxu.mmcr.api.publicapi.machine.SmartInterfaceType(type.type(), type.defaultValue(),
                type.minValue(), type.maxValue(), type.priority(),
                cn.howxu.mmcr.api.publicapi.machine.SmartInterfaceType.ValueType.valueOf(type.valueType().name()));
    }

    private static cn.howxu.mmcr.api.publicapi.machine.SmartInterfaceModifier toPublicSmartInterfaceModifier(
            SmartInterfaceModifier modifier) {
        return new cn.howxu.mmcr.api.publicapi.machine.SmartInterfaceModifier(modifier.interfaceType(),
                modifier.target(),
                cn.howxu.mmcr.api.publicapi.recipe.modifier.RecipeModifier.IOType.valueOf(modifier.io().name()),
                modifier.affectsChance(), modifier.minValue(), modifier.maxValue(), modifier.atMin(), modifier.atMax(),
                cn.howxu.mmcr.api.publicapi.recipe.modifier.RecipeModifier.Operation.valueOf(modifier.operation().name()));
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
