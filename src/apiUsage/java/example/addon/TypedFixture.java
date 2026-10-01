package example.addon;

import cn.howxu.mmcr.publicapi.Machines;
import cn.howxu.mmcr.publicapi.Recipes;
import cn.howxu.mmcr.publicapi.Structures;
import cn.howxu.mmcr.publicapi.behavior.*;
import cn.howxu.mmcr.publicapi.client.jei.*;
import cn.howxu.mmcr.publicapi.client.render.*;
import cn.howxu.mmcr.publicapi.data.*;
import cn.howxu.mmcr.publicapi.event.*;
import cn.howxu.mmcr.publicapi.machine.*;
import cn.howxu.mmcr.publicapi.network.*;
import cn.howxu.mmcr.publicapi.presentation.*;
import cn.howxu.mmcr.publicapi.recipe.*;
import cn.howxu.mmcr.publicapi.recipe.component.*;
import cn.howxu.mmcr.publicapi.recipe.extension.*;
import cn.howxu.mmcr.publicapi.recipe.modifier.*;
import cn.howxu.mmcr.publicapi.recipe.requirement.*;
import cn.howxu.mmcr.publicapi.registration.MachineDefinitionProvider;
import cn.howxu.mmcr.publicapi.runtime.*;
import cn.howxu.mmcr.publicapi.structure.*;
import cn.howxu.mmcr.publicapi.structure.level.*;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;

/** Compile-only external addon scenarios. Every MMCR type comes from the binary Public artifact.
 * Methods requiring a game/registration window are invoked by the addon at that lifecycle phase.
 * @author howxu <dev@howxu.cn>
 */
public final class TypedFixture implements MachineDefinitionProvider {
    private static final ResourceLocation MACHINE = id("machine");
    private static final ResourceLocation POOL = id("pool");
    private static final ResourceLocation LEVEL_TYPE = id("coil");
    private static final ResourceLocation LEVEL = id("coil/iron");
    private static final ResourceLocation MODIFIER = id("upgrade");
    private static final ResourceLocation LINE = id("balance");

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("example_addon", path);
    }

    @Override
    public void register(RegisterMachineDefinitionsEvent event) {
        SmartInterfaceSpec smart = SmartInterfaces.type("power", 1, 0, 10, 1, SmartInterfaces.ValueType.FLOAT);
        SmartModifierSpec mapping = Modifiers.smartEnergy("power", 0, 10, 1, 2, ModifierOperation.MULTIPLY);
        MachineDraft draft = Machines.machine(MACHINE).recipePool(POOL).displayNameKey("machine.example_addon.name")
                .role(MachineKind.HOST).acceptedModule(id("module"))
                .controller((ControllerOptions options) -> options.id(id("controller"))
                        .textures(id("front"), id("side"), id("top"), id("bottom"))
                        .allowVerticalFacing().requireVerticalFacing(false).fullyRotationallySymmetric(false)
                        .tooltip("machine.example_addon.tooltip"))
                .appearance((AppearanceOptions options) -> options.machineBasicBlock("minecraft:iron_block")
                        .controllerBaseTexture(id("base")).formedPortBaseTexture(id("port"))
                        .controllerIdleOverlayTexture(id("idle")).controllerActiveOverlayTexture(id("active")))
                .factory((FactoryOptions options) -> options.hasFactory(true).threadLimit(4).thread("main", id("recipe")))
                .maxParallelism(64).maxParallelAmount(8).parallelizable(true)
                .allowModifiers().allowMultithreading().smartInterface(smart).shareSmartInterfaces()
                .smartInterfaceModifier(mapping)
                .smartInterfaceModifier(Modifiers.smartDuration("power", 0, 10, 1, 0.5F, ModifierOperation.MULTIPLY))
                .smartInterfaceModifier(Modifiers.smartItem("power", IoDirection.OUTPUT, true, 0, 10, 1, 2, ModifierOperation.MULTIPLY))
                .smartInterfaceModifier(Modifiers.smartFluid("power", IoDirection.OUTPUT, true, 0, 10, 1, 2, ModifierOperation.MULTIPLY))
                .smartInterfaceModifier(Modifiers.smartChemical("power", IoDirection.OUTPUT, false, 0, 10, 1, 2, ModifierOperation.MULTIPLY))
                .smartInterfaceModifier(Modifiers.smartHeat("power", IoDirection.OUTPUT, 0, 10, 1, 2, ModifierOperation.MULTIPLY))
                .runningSound(id("running")).finishSound(id("finished"))
                .failureAction(RecipeFailureMode.STILL).networkInterface(1, 4).allowNetworkMachine(MACHINE)
                .requestProcess(id("deposit"), new DepositHandler()).requestFailed(id("deposit"), new FailedDeposit())
                .recipeBehavior((RecipeHooks hooks) -> hooks
                        .idleStart((MachineContext context) -> text(context))
                        .idleEnd((MachineContext context) -> context.screenText().clear(TextScope.OPERATION))
                        .beforeStart((RecipeStartContext context) -> beforeStart(context))
                        .recipeTick((RecipeTickContext context) -> recipeTick(context))
                        .beforeFinish((RecipeFinishContext context) -> beforeFinish(context))
                        .preServerTick((MachineContext context) -> text(context))
                        .postServerTick((MachineContext context) -> context.jadeText().remove(LINE)));
        MachineSpec machine = draft.build();
        NetworkSettings settings = machine.networkInterface();
        Set<ResourceLocation> accepted = settings.allowedMachineIds();
        Map<ResourceLocation, RequestHandler> handlers = machine.requestProcessors();
        Map<ResourceLocation, FailureHandler> failures = machine.requestFailures();
        event.registerMachine(machine);
        event.registerMachine(id("tick_machine"), (MachineDraft tick) -> tick.recipePool(POOL)
                .tickBehavior((TickHooks hooks) -> hooks.serverTick((TickContext context) -> tick(context))));
    }

    public static void structures(RegisterMachineStructuresEvent event) {
        NumericModifierSpec duration = Modifiers.numeric("duration", ModifierScope.INPUT, 0.5,
                ModifierOperation.MULTIPLY, false);
        NumericModifierSpec threads = Modifiers.numeric("factory_threads", ModifierScope.MACHINE, 1,
                ModifierOperation.ADD, false);
        ParallelizationModifierSpec parallel = Modifiers.parallelized(true);
        ModifierBundle bundle = Modifiers.combine(Modifiers.bundle(duration, threads), Modifiers.bundle(parallel));
        LevelTypeSpec type = Levels.type(LEVEL_TYPE, Component.translatable("level.example_addon.coil"));
        MachineLevelSpec level = Levels.level(LEVEL, LEVEL_TYPE, 1, BlockConditions.blockState(Blocks.IRON_BLOCK.defaultBlockState()),
                new ItemStack(Items.IRON_BLOCK), bundle);
        event.registerLevelType(type); event.registerLevel(level);
        event.registerModifier(MODIFIER, bundle); event.registerModifierItem(new ItemStack(Items.DIAMOND), MODIFIER);
        BlockCondition ports = BlockConditions.any(BlockConditions.itemPorts(), BlockConditions.fluidPorts(),
                BlockConditions.energyPorts(), BlockConditions.chemicalPorts(), BlockConditions.heatPorts(),
                BlockConditions.radioactiveChemicalPorts(), BlockConditions.upgradeBus(), BlockConditions.parallelControllers(),
                BlockConditions.factoryController(), BlockConditions.smartInterface(), BlockConditions.dataStorage(),
                BlockConditions.networkInterface(), BlockConditions.coupler());
        PortTierLimits tiers = PortTierLimits.combine(PortTierLimits.itemInput(PortTierLimits.ItemTier.NORMAL),
                PortTierLimits.fluidOutput(PortTierLimits.FluidTier.NORMAL),
                PortTierLimits.energy(PortTierLimits.EnergyTier.NORMAL, IoDirection.INPUT));
        PatternSpec pattern = Structures.pattern().layer("CIP", "XML").controller('C')
                .where('I', BlockConditions.itemInput()).where('P', ports)
                .where('X', BlockConditions.block(Blocks.IRON_BLOCK)).where('M', BlockConditions.block(Blocks.AIR))
                .where('L', BlockConditions.deferredBlock(() -> Blocks.GLASS)).build();
        StructureStageSpec independent = Structures.stage().extension()
                .pattern((PatternDraft options) -> options.pattern("C").controller('C')).build();
        StructureSpec structure = Structures.structure().stateSensitive()
                .fullStructure((StructureStageDraft stage) -> stage
                        .pattern((PatternDraft options) -> options.layer("CIP", "XML").controller('C')
                                .where('I', BlockConditions.itemInput()).where('P', ports)
                                .where('X', BlockConditions.block(Blocks.IRON_BLOCK))
                                .where('M', BlockConditions.block(Blocks.AIR)).where('L', BlockConditions.block(Blocks.GLASS)))
                        .ports((PortLimits.Builder limits) -> limits.min("mmcr:item_input_bus", 1).range("mmcr:fluid_output_hatch", 1, 4))
                        .portTiers((PortTierLimits.Builder limits) -> limits.minItemInput(PortTierLimits.ItemTier.NORMAL)
                                .anyItemOutput().minFluidOutput(PortTierLimits.FluidTier.NORMAL).anyFluidInput()
                                .minEnergyInput(PortTierLimits.EnergyTier.NORMAL).anyEnergyOutput())
                        .requirements((StructureConstraints.Builder constraints) -> constraints.levelSlot('X', LEVEL_TYPE)
                                .modifier('M', MODIFIER, BlockConditions.block(Blocks.DIAMOND_BLOCK))))
                .expandStructure((StructureStageDraft stage) -> stage.pattern((PatternDraft options) -> options.layer("C").controller('C')))
                .extension((StructureStageDraft stage) -> stage.pattern((PatternDraft options) -> options.layer("C").controller('C')))
                .build(MACHINE);
        List<StructureStageSpec> stages = structure.stages();
        Map<Character, BlockCondition> predicates = pattern.predicates();
        List<PortTierLimits.RequirementView> tierViews = tiers.requirements();
        event.registerStructure(structure);
        event.registerStructure(id("tick_machine"), (StructureDraft draft) -> draft.stateInsensitive()
                .singlePattern((PatternDraft options) -> options.layer("C").controller('C')));
    }

    public static ComponentConstraints components(ItemStack stack) {
        ExactCondition exact = ComponentConditions.exact(new JsonPrimitive(1));
        ExactCondition dynamic = ComponentConditions.exact(new Dynamic<>(JsonOps.INSTANCE, new JsonPrimitive(2)));
        RangeCondition range = ComponentConditions.range(1, 10);
        TextCondition plain = ComponentConditions.text("required", TextMatchMode.PLAIN);
        TextCondition full = ComponentConditions.text(Component.translatable("item.example_addon.name"), TextMatchMode.FULL);
        MapCondition map = ComponentConditions.map(Map.of("value", range, "name", plain));
        ListCondition list = ComponentConditions.list(List.of(map, exact, dynamic));
        boolean matches = list.matches(new Dynamic<>(JsonOps.INSTANCE, new JsonPrimitive(1)));
        ComponentConstraints predicates = ComponentConstraints.ofTypes(Map.of(DataComponents.CUSTOM_NAME, full));
        ComponentConstraints patch = ComponentConstraints.ofIds(Map.of(ResourceLocation.parse("minecraft:custom_name"), plain));
        predicates.matches(stack, JsonOps.INSTANCE);
        patch.applyTo(stack, JsonOps.INSTANCE);
        ItemStack display = patch.displayStack(Items.IRON_INGOT, 1, JsonOps.INSTANCE);
        patch.exactPatch();
        return predicates;
    }

    public static void recipes(RegisterMachineRecipesEvent event) {
        ComponentConstraints constraints = components(new ItemStack(Items.IRON_INGOT));
        ItemInputSpec input = IoValues.itemInput(Ingredient.of(Items.IRON_INGOT), 2, constraints, 0.25F);
        ItemOutputSpec output = IoValues.itemOutput(new ItemStack(Items.GOLD_INGOT), 0.5F);
        FluidInputSpec fluidInput = IoValues.fluidInput(FluidIngredient.of(Fluids.WATER), 1000, 0.5F);
        FluidOutputSpec fluidOutput = IoValues.fluidOutput(new FluidStack(Fluids.LAVA, 1000), 0.75F);
        EnergyRateSpec energy = IoValues.energyRate(1L << 40);
        HostConstraint host = IoValues.requiredHost(MACHINE);
        ItemRequirementSpec tagged = Requirements.item(IoDirection.INPUT, input.ingredient(), input.count(),
                ItemStack.EMPTY, 1, List.of("catalyst"), constraints, input.consumeChance());
        FluidRequirementSpec fluid = Requirements.fluid(IoDirection.INPUT, fluidInput.ingredient(), fluidInput.amount(),
                FluidStack.EMPTY, 1, List.of("coolant"), fluidInput.consumeChance());
        EnergyRequirementSpec perTick = Requirements.energy(IoDirection.INPUT, energy.fePerTick(), List.of("power"));
        LevelRequirementSpec level = Requirements.level(LEVEL_TYPE, LEVEL);
        StageRequirementSpec stage = Requirements.stage(1);
        SmartInterfaceRequirementSpec smart = Requirements.smartInput("power", 1, 10);
        CustomIoSpec chemical = IoValues.customIo(ResourceLocation.parse("mmcr:chemical"), IoDirection.INPUT,
                MekanismIo.chemicalInputPayload(ResourceLocation.parse("mekanism:hydrogen"), 10, 0.5F));
        CustomIoSpec heat = IoValues.customIo(ResourceLocation.parse("mmcr:heat"), IoDirection.OUTPUT, MekanismIo.heatOutputPayload(10));
        RecipeDraft draft = Recipes.recipe(id("recipe")).recipePool(POOL).duration(20).priority(1).maxThreads(2)
                .parallelized(true).cancelIfPerTickFails(true).allowPartialOutputs(true)
                .requirement(tagged).requirement(fluid).requirement(perTick).requirement(level).requirement(stage)
                .requirement(Requirements.itemOutput(output)).requirement(Requirements.fluidOutput(fluidOutput))
                .smartInterface(smart).custom(chemical).custom(heat).requiredHost(host.id()).modifier(MODIFIER)
                .inputItemTag(ItemTags.LOGS, 1).inputFluid(Fluids.WATER, 100, 0.5F)
                .outputChance(new ItemStack(Items.DIAMOND), 0.25F).outputFluid(Fluids.LAVA, 100)
                .inputEnergy(energy.fePerTick()).outputEnergy(1);
        RecipeSpec recipe = draft.build();
        List<RequirementSpec> requirements = recipe.requirements();
        ResourceLocation kind = level.kindId();
        ResourceLocation type = level.typeId();
        List<String> tags = tagged.tags();
        ItemStack resolved = tagged.resolvedStack();
        event.registerRecipe(recipe);
        event.registerRecipe(id("second_recipe"), (RecipeDraft options) -> options.recipePool(POOL).inputItem(Items.IRON_INGOT, 1)
                .outputItem(Items.GOLD_INGOT, 1).levelRequirement(LEVEL_TYPE, LEVEL).stageRequirement(1));
    }

    private static void beforeStart(RecipeStartContext context) {
        RecipeView recipe = context.recipe();
        RecipeExecutionView snapshot = context.snapshot();
        List<RequirementSpec> requirements = context.requirements();
        context.setDuration(context.duration() * 2);
        context.replaceExactItemInputCount(Items.IRON_INGOT, 2, 1);
        context.setRequirements(requirements);
        ItemOutputView output = Outputs.item(new ItemStack(Items.GOLD_INGOT), 0.75F);
        ItemStack replacement = output.stack();
        replacement.setCount(2);
        context.setOutputs(List.of(Outputs.item(replacement, output.chance())));
        if (context.effectiveParallelism() > context.requestedParallelism()) context.cancel();
        text(context.machineContext());
    }

    private static void recipeTick(RecipeTickContext context) {
        RecipeView recipe = context.recipe();
        List<RequirementSpec> requirements = context.requirements();
        List<OutputView> outputs = context.outputs();
        IoSnapshot io = context.ioSnapshot().forTags(Set.of("power"));
        if (context.currentTick() < context.totalTick() && io.energyInput() == 0) text(context.machineContext());
    }

    private static void beforeFinish(RecipeFinishContext context) {
        List<OutputView> outputs = context.outputs();
        RecipeAdjustmentSpec adjustment = Modifiers.recipe("output", IoDirection.OUTPUT, 2, ModifierOperation.MULTIPLY, false);
        List<OutputView> replacements = outputs.stream()
                .map((OutputView output) -> output.applyModifiers(List.of(adjustment)).withChance(1)).toList();
        context.setOutputs(replacements);
        if (context.machineContext().dataStorage() == null) context.cancel();
        else if (context.machineContext().dataStorage().contains("discard")) context.discardOutputs();
    }

    private static void tick(TickContext context) {
        DataStore storage = context.dataStorage();
        if (storage == null || !context.isDue(20)) return;
        IoSnapshot view = context.ioView().forTags(Set.of("power"));
        long available = view.itemAmount(Ingredient.of(Items.IRON_INGOT));
        long fluids = view.fluidAmount(FluidIngredient.of(Fluids.WATER));
        long capacity = view.itemOutputCapacity(new ItemStack(Items.GOLD_INGOT));
        Optional<Float> smart = context.smartInterfaceValue("power");
        Map<String, Float> smartValues = context.smartInterfaceValues();
        IoTransaction plan = context.ioPlan().addInput(Requirements.itemInput(Items.IRON_INGOT, 1))
                .addInput(Requirements.fluidInput(Fluids.WATER, 100)).addInput(Requirements.energy(10))
                .addOutput(Requirements.itemOutput(new ItemStack(Items.GOLD_INGOT)), OutputMode.ALLOW_PARTIAL);
        IoSimulation simulation = plan.simulate();
        if (simulation.failure() != null) { failure(simulation.failure()); return; }
        if (!simulation.inputsSatisfied() || !simulation.energySatisfied()) return;
        long accepted = simulation.outputs().stream().mapToLong((OutputAcceptance output) -> output.accepted()).sum();
        DataKey balance = DataKey.of(accepted);
        IoCommitResult result = plan.commitData((DataStore.Transaction transaction) -> storage.set("balance", balance, transaction));
        if (!result.successful() && result.failure() != null) failure(result.failure());
        for (NetworkPortView port : Networks.interfaces(context)) {
            List<NodeView> connections = port.connections();
            for (NodeView node : connections) Networks.sendRequest(port, node, id("deposit"), RequestPayload.of(Map.of("balance", balance)));
        }
        text(context);
    }

    private static String failure(RuntimeFailure failure) {
        ResourceLocation id = failure.id();
        FailureSeverity severity = failure.severity();
        ResourceLocation source = failure.source();
        Map<String, String> details = failure.details();
        List<FailureFrame> trace = failure.trace();
        return id + ":" + severity + ":" + source + ":" + details + ":" + trace;
    }

    private static void text(MachineContext context) {
        ControllerText screen = context.screenText();
        JadeText jade = context.jadeText();
        Component message = Component.translatable("machine.example_addon.balance", context.gameTime());
        screen.append(TextScope.OPERATION, LINE, message);
        screen.appendAfter(TextScope.OPERATION, id("after"), LINE, message);
        screen.replace(LINE, message); screen.remove(TextScope.OPERATION, id("after"));
        jade.append(LINE, message); jade.appendAfter(id("after"), LINE, message);
        jade.replace(LINE, message); jade.remove(id("after")); jade.clear();
    }

    public static ControllerTexts.Registration registerText() {
        return ControllerTexts.register(MACHINE, (ControllerTextContext context) -> context.screenText()
                .append(TextScope.CONTROLLER, LINE, Component.translatable("machine.example_addon.position", context.controllerPos())));
    }

    /** Ordinary external repository/reservation implementation; binding/discovery is not invented.
     * @author howxu <dev@howxu.cn>
     */
    public static final class ExternalRepository implements Repository {
        private final DataStore storage;
        public ExternalRepository(DataStore storage) { this.storage = storage; }
        @Override public ResourceLocation id() { return TypedFixture.id("repository"); }
        @Override public RepositoryRequest request(RepositoryContext context) {
            DataKey value = storage.get(context.key()).orElseThrow();
            if (value.type() != context.requestedType()) throw new IllegalArgumentException("Mismatched requested kind");
            Reservation reservation = new Reservation() {
                private boolean cancelled;
                @Override public boolean commit() {
                    if (cancelled) return false;
                    storage.set(context.key(), value);
                    return true;
                }
                @Override public void cancel() { cancelled = true; }
            };
            return RepositoryRequest.available(id(), context.controllerPos(), context.key(), context.requestedType(), value, reservation);
        }
    }

    /** @author howxu <dev@howxu.cn> */
    public static final class DepositHandler implements RequestHandler {
        @Override public void process(RequestPayload body, RequestDetails request, DataStore sender, DataStore receiver) {
            NodeView peer = request.peer();
            DataKey balance = body.get("balance").orElseThrow();
            if (receiver != null) receiver.set(peer.type() + "/" + peer.hash(), balance);
            if (sender != null) sender.remove("pending");
        }
    }

    /** @author howxu <dev@howxu.cn> */
    public static final class FailedDeposit implements FailureHandler {
        @Override public void fail(RequestPayload body, RequestDetails request, DataStore sender, RequestFailure reason) {
            if (sender != null) {
                sender.set("failure", DataKey.of(reason.name()));
                body.get("balance").ifPresent((DataKey value) -> sender.set("retry", value));
            }
        }
    }

    /** Renderer metadata and typed published state remain client-side.
     * @author howxu <dev@howxu.cn>
     */
    public static final class AddonRenderer implements ControllerRenderer {
        @Override public void render(ControllerRenderContext context, PoseStack poses, MultiBufferSource buffers, int light, int overlay) {
            ControllerRenderContext.StructureView structure = context.structure();
            ControllerRenderContext.CraftingView crafting = context.crafting();
            Map<String, DataKey> values = context.dataStorageValues();
            if (!structure.formed()) return;
            if (crafting.failure() != null) failure(crafting.failure());
            poses.pushPose();
            poses.translate(0, context.partialTick(), 0);
            poses.popPose();
        }
        @Override public boolean shouldRenderOffScreen() { return true; }
        @Override public int getViewDistance() { return 128; }
    }

    public static void client(RegisterControllerRenderersEvent renderers, RegisterJeiWorkstationsEvent workstations,
                              RegisterJeiRecipeInformationEvent information) {
        RendererRegistrar renderer = renderers.registrar();
        renderer.register(MACHINE, new AddonRenderer());
        WorkstationRegistrar stations = workstations.registrar();
        stations.addRecipePoolWorkstation(POOL, Items.IRON_INGOT);
        stations.addRecipePoolWorkstation(POOL, new ItemStack(Items.GOLD_INGOT));
        stations.addMachineWorkstation(MACHINE, id("recipe_type"));
        List<Workstation> entries = stations.entries();
        RecipeInformationRegistrar infos = information.registrar();
        infos.registerRecipePool(POOL, "jei.example_addon.pool", 1200, 'X');
        infos.registerRecipe(id("recipe"), "jei.example_addon.recipe", 20);
        List<RecipeInformation> descriptions = infos.entries();
    }

    /** JSON schema shared by the extension's real codec, not a pre-serialized opaque handle.
     * @author howxu <dev@howxu.cn>
     */
    public record EnergyPayload(long amount) {
        public EnergyPayload {
            if (amount <= 0) throw new IllegalArgumentException("amount must be positive");
        }
        public static final MapCodec<EnergyPayload> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Codec.LONG.fieldOf("amount").forGetter(EnergyPayload::amount)).apply(instance, EnergyPayload::new));
    }

    public static RequirementKind<EnergyPayload> registerExtension() {
        return RequirementKinds.register(new RequirementExtension<EnergyPayload>() {
            @Override public ResourceLocation id() { return TypedFixture.id("extra_energy"); }
            @Override public Set<ResourceLocation> capabilityIds() { return Set.of(ResourceLocation.parse("neoforge:energy")); }
            @Override public MapCodec<EnergyPayload> codec() { return EnergyPayload.CODEC; }
            @Override public IoDirection io(EnergyPayload value) { return IoDirection.INPUT; }
            @Override public List<String> tags(EnergyPayload value) { return List.of("power"); }
            @Override public EnergyPayload copy(EnergyPayload value) { return new EnergyPayload(value.amount()); }
            @Override public RequirementExecution<EnergyPayload> execution() {
                return new RequirementExecution<>() {
                    @Override public RequirementPlanSpec plan(EnergyPayload value, PlanningView context) {
                        return planEnergy(value, context);
                    }
                    @Override public List<ResourceWakeupSpec<?>> resourceWakeups(EnergyPayload value) {
                        return List.of(new ResourceWakeupSpec<>(Set.of(ResourceLocation.parse("mmcr:missing_energy")),
                                ResourceWakeupSpec.Reason.ENERGY_AVAILABLE, ResourceLocation.class,
                                change -> change.resource().equals(ResourceLocation.parse("neoforge:energy"))));
                    }
                };
            }
        });
    }

    private static RequirementPlanSpec planEnergy(EnergyPayload payload, PlanningView context) {
        for (CapabilityAccess capability : context.capabilities()) {
            Optional<LongStore> values = capability.longValues();
            if (values.isEmpty() || !capability.directions().contains(IoDirection.INPUT)) continue;
            LongStore store = values.orElseThrow();
            long available = context.reservations().valueAvailable(store, false);
            long max = Math.min(context.requestedParallelism(), available / payload.amount());
            if (max == 0) continue;
            return context.deferred(max,
                    (long parallelism, ReservationsView reservations) -> {
                        RecipeOperation operation = capability.prepareValue(IoDirection.INPUT, parallelism, Math.multiplyExact(parallelism, payload.amount()), false);
                        RecipeOperation transactional = operation::commit;
                        return new OperationBatch(List.of(transactional), null);
                    },
                    (long parallelism, ReservationsView reservations) -> {
                        boolean reserved = reservations.reserveValueTotal(store, Math.multiplyExact(parallelism, payload.amount()), false);
                        PlanStatus failure = reserved ? null : context.failure(id("reservation_conflict"), capability.kindId(), Map.of("reason", "missing_energy"));
                        return new ReservationCheck(failure, null);
                    });
        }
        return context.blocked(context.failure(id("missing_energy"), id("extra_energy"), Map.of("reason", "missing_energy", "amount", Long.toString(payload.amount()))));
    }

    public static RequirementSpec customRequirement(RequirementKind<EnergyPayload> kind, long amount) {
        RequirementSpec requirement = Requirements.extension(kind, new EnergyPayload(amount));
        Optional<EnergyPayload> decoded = kind.payload(requirement.copy());
        return requirement;
    }

    /** Output extension with a real codec and executable energy requirement conversion.
     * @author howxu <dev@howxu.cn>
     */
    public record EnergyOutputPayload(long amount, float chance) {
        public static final MapCodec<EnergyOutputPayload> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Codec.LONG.fieldOf("amount").forGetter(EnergyOutputPayload::amount),
                Codec.FLOAT.fieldOf("chance").forGetter(EnergyOutputPayload::chance)).apply(instance, EnergyOutputPayload::new));
    }

    public static OutputKind<EnergyOutputPayload> registerOutputExtension() {
        return OutputKinds.register(new OutputExtension<EnergyOutputPayload>() {
            @Override public ResourceLocation id() { return TypedFixture.id("extra_energy_output"); }
            @Override public MapCodec<EnergyOutputPayload> codec() { return EnergyOutputPayload.CODEC; }
            @Override public EnergyOutputPayload copy(EnergyOutputPayload value) { return new EnergyOutputPayload(value.amount(), value.chance()); }
            @Override public float chance(EnergyOutputPayload value) { return value.chance(); }
            @Override public long amount(EnergyOutputPayload value) { return value.amount(); }
            @Override public EnergyOutputPayload withChance(EnergyOutputPayload value, float chance) {
                return new EnergyOutputPayload(value.amount(), chance);
            }
            @Override public EnergyOutputPayload applyModifiers(EnergyOutputPayload value, List<RecipeAdjustmentSpec> modifiers) {
                double amount = Modifiers.apply(modifiers, "energy", IoDirection.OUTPUT, (double) value.amount(), false);
                float chance = Modifiers.apply(modifiers, "energy", IoDirection.OUTPUT, value.chance(), true);
                return new EnergyOutputPayload((long) amount, chance);
            }
            @Override public RequirementSpec toRequirement(EnergyOutputPayload value, List<String> tags) {
                return Requirements.energy(IoDirection.OUTPUT, value.amount(), tags);
            }
            @Override public boolean matchesRequirement(RequirementSpec requirement) {
                return requirement instanceof EnergyRequirementSpec && requirement.io() == IoDirection.OUTPUT;
            }
            @Override public Optional<EnergyOutputPayload> fromRequirement(RequirementSpec requirement) {
                if (requirement instanceof EnergyRequirementSpec energy && energy.io() == IoDirection.OUTPUT)
                    return Optional.of(new EnergyOutputPayload(energy.fePerTick(), 1));
                return Optional.empty();
            }
        });
    }

    public static OutputView customOutput(OutputKind<EnergyOutputPayload> kind, long amount, float chance) {
        OutputView output = Outputs.extension(kind, new EnergyOutputPayload(amount, chance));
        RequirementSpec executable = Outputs.toRequirement(output, List.of("power"));
        Optional<EnergyOutputPayload> payload = kind.payload(output.copy());
        return output;
    }

    // Invoked with main runtime, separately from apiJar-only compilation.
    public static String dataRoundTrip() {
        DataKey values = DataKey.map(Map.ofEntries(
                Map.entry("boolean", DataKey.of(true)), Map.entry("byte", DataKey.of((byte) 1)),
                Map.entry("short", DataKey.of((short) 2)), Map.entry("int", DataKey.of(3)),
                Map.entry("long", DataKey.of(4L)), Map.entry("float", DataKey.of(5F)), Map.entry("double", DataKey.of(6D)),
                Map.entry("bigInteger", DataKey.of(BigInteger.TEN)), Map.entry("bigDecimal", DataKey.of(BigDecimal.TEN)),
                Map.entry("list", DataKey.list(List.of(DataKey.of("payload"))))));
        Map<String, DataKey> map = values.asMap().orElseThrow();
        boolean flag = map.get("boolean").booleanValue(); byte b = map.get("byte").byteValue();
        short s = map.get("short").shortValue(); int i = map.get("int").intValue(); long l = map.get("long").longValue();
        float f = map.get("float").floatValue(); double d = map.get("double").doubleValue();
        BigInteger integer = map.get("bigInteger").bigIntegerValue(); BigDecimal decimal = map.get("bigDecimal").bigDecimalValue();
        RequestPayload payload = RequestPayload.of(map);
        List<DataKey> list = payload.get("list").orElseThrow().asList().orElseThrow();
        DataKind kind = list.getFirst().type();
        return list.getFirst().stringValue();
    }

    public static long codecRoundTrip(long amount) {
        JsonElement encoded = EnergyPayload.CODEC.codec().encodeStart(JsonOps.INSTANCE, new EnergyPayload(amount)).getOrThrow();
        EnergyPayload decoded = EnergyPayload.CODEC.codec().parse(JsonOps.INSTANCE, encoded).getOrThrow();
        return decoded.amount();
    }

    public static List<String> declarationRoundTrip() {
        MachineDraft draft = Machines.machine(MACHINE).recipePool(POOL).displayNameKey("machine.example_addon.first")
                .controller((ControllerOptions options) -> options.id(id("controller")));
        MachineSpec first = draft.build();
        MachineSpec second = draft.displayNameKey("machine.example_addon.second").build();
        StructureSpec structure = Structures.structure().singlePattern((PatternDraft pattern) -> pattern.layer("C").controller('C'))
                .build(first.id());
        RecipeSpec recipe = Recipes.recipe(id("runtime_recipe")).recipePool(second.recipePoolId()).inputEnergy(10).build();
        if (!structure.machineId().equals(first.id()) || !recipe.recipePoolId().equals(first.recipePoolId()))
            throw new IllegalStateException("Declaration identities did not survive their Public boundaries");
        return List.of(first.displayNameKey(), second.displayNameKey());
    }
}
