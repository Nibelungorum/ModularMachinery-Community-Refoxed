package cn.howxu.mmcr;

import appeng.core.definitions.AEItems;
import com.mojang.authlib.GameProfile;
import cn.howxu.mmcr.api.compat.mekanism.MekanismFailureReasons;
import cn.howxu.mmcr.api.compat.mekanism.ChemicalIngredient;
import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.api.machine.DynamicMachine;
import cn.howxu.mmcr.api.machine.Machine;
import cn.howxu.mmcr.api.machine.MachineAppearanceSpec;
import cn.howxu.mmcr.api.machine.MachineControllerSpec;
import cn.howxu.mmcr.api.machine.MachineRegistry;
import cn.howxu.mmcr.api.machine.PortRequirementSpec;
import cn.howxu.mmcr.api.machine.PortTierRequirementSpec;
import cn.howxu.mmcr.api.machine.definition.PortTiers;
import cn.howxu.mmcr.api.recipe.CustomRecipeIo;
import cn.howxu.mmcr.api.recipe.MachineRecipeBuilder;
import cn.howxu.mmcr.api.recipe.MachineOutput;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.api.registration.StructureRegistration;
import cn.howxu.mmcr.internal.registration.MachineRecipeConverter;
import cn.howxu.mmcr.internal.block.MachineControllerBlock;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import com.mojang.serialization.JsonOps;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier.IOType;
import cn.howxu.mmcr.client.model.MachineModelDataKeys;
import cn.howxu.mmcr.compat.mekanism.MekanismBridge;
import cn.howxu.mmcr.compat.mekanism.MekanismBridgeBootstrap;
import cn.howxu.mmcr.compat.mekanism.MekanismRecipeTypes;
import cn.howxu.mmcr.compat.mekanism.loaded.ChemicalPortBlockEntity;
import cn.howxu.mmcr.compat.mekanism.loaded.HeatPortBlockEntity;
import cn.howxu.mmcr.compat.mekanism.loaded.HeatPortCapability;
import cn.howxu.mmcr.compat.mekanism.loaded.LoadedHeatRequirement;
import cn.howxu.mmcr.compat.mekanism.loaded.LoadedMekanismBridge;
import cn.howxu.mmcr.compat.mekanism.loaded.MekanismPortSizes;
import cn.howxu.mmcr.api.capability.plan.PlanningContext;
import cn.howxu.mmcr.api.capability.plan.RequirementPlan;
import cn.howxu.mmcr.registry.ModBlocks;
import mekanism.api.AutomationType;
import mekanism.api.MekanismAPI;
import mekanism.api.chemical.Chemical;
import mekanism.api.chemical.ChemicalBuilder;
import mekanism.api.chemical.ChemicalResource;
import mekanism.api.heat.HeatAPI;
import mekanism.api.heat.IHeatHandler;
import mekanism.common.capabilities.Capabilities;
import mekanism.common.registries.MekanismBlocks;
import mekanism.common.tile.transmitter.TileEntityThermodynamicConductor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.transfer.transaction.Transaction;

import java.nio.charset.StandardCharsets;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * End-to-end GameTest coverage for the loaded Mekanism port integration.
 *
 * @author howxu <dev@howxu.cn>
 */
public class MekanismPortGameTest {

    public void chemicalStructureChecksCountsAndMinimumTiers(GameTestHelper helper) {
        var machine = portConstraintMachine("chemical_structure_constraints",
                PortRequirementSpec.builder().range("chemical_input_hatch", 1, 1)
                        .range("chemical_output_hatch", 1, 1).build(),
                PortTierRequirementSpec.builder().minChemicalInput(PortTiers.ChemicalTier.ADVANCED)
                        .minChemicalOutput(PortTiers.ChemicalTier.ELITE).build());
        var controller = portConstraintController(helper, machine);
        BlockPos inputPos = new BlockPos(0, 1, 1);
        BlockPos outputPos = new BlockPos(2, 1, 1);
        BlockPos extraPos = new BlockPos(1, 1, 2);
        helper.setBlock(inputPos, ModBlocks.BLOCKS.get("chemical_input_hatch_basic").get().defaultBlockState());
        helper.setBlock(outputPos, ModBlocks.BLOCKS.get("chemical_output_hatch_elite").get().defaultBlockState());
        helper.assertFalse(formPortStructure(controller, machine), "A basic input cannot satisfy an advanced requirement");
        helper.assertValueEqual("chemical_input_hatch>=advanced",
                controller.currentRuntimeSnapshot().structure().lastFormationFailure().portId(), "Input tier failure identifies its family and grade");

        helper.setBlock(inputPos, ModBlocks.BLOCKS.get("chemical_input_hatch_advanced").get().defaultBlockState());
        helper.setBlock(outputPos, ModBlocks.BLOCKS.get("chemical_output_hatch_basic").get().defaultBlockState());
        helper.assertFalse(formPortStructure(controller, machine), "A basic output cannot satisfy an elite requirement");
        helper.assertValueEqual("chemical_output_hatch>=elite",
                controller.currentRuntimeSnapshot().structure().lastFormationFailure().portId(), "Output tier failure identifies its family and grade");

        helper.setBlock(outputPos, ModBlocks.BLOCKS.get("chemical_output_hatch_elite").get().defaultBlockState());
        helper.assertTrue(formPortStructure(controller, machine), "Exact input and output tiers form the compiled structure");
        helper.assertTrue(controller.currentRuntimeSnapshot().structure().formed(), "Successful validation publishes a formed structure");
        controller.invalidateFormedStructure();

        helper.setBlock(inputPos, ModBlocks.BLOCKS.get("chemical_input_hatch_ultimate").get().defaultBlockState());
        helper.setBlock(outputPos, ModBlocks.BLOCKS.get("chemical_output_hatch_ultimate").get().defaultBlockState());
        helper.assertTrue(formPortStructure(controller, machine), "Higher chemical grades satisfy the minimums");
        controller.invalidateFormedStructure();

        helper.setBlock(extraPos, ModBlocks.BLOCKS.get("chemical_input_hatch_basic").get().defaultBlockState());
        helper.assertFalse(formPortStructure(controller, machine), "Different input grades count toward the same family maximum");
        helper.assertValueEqual(PortRequirementSpec.FailureReason.TOO_MANY,
                controller.currentRuntimeSnapshot().structure().lastFormationFailure().reason(), "An extra chemical input reports the count maximum");
        helper.setBlock(extraPos, ModBlocks.CASING.get().defaultBlockState());
        helper.setBlock(inputPos, ModBlocks.CASING.get().defaultBlockState());
        helper.assertFalse(formPortStructure(controller, machine), "The required chemical input count cannot be satisfied by casing");
        helper.assertValueEqual("chemical_input_hatch",
                controller.currentRuntimeSnapshot().structure().lastFormationFailure().portId(), "Missing input reports the count family alias");
        helper.succeed();
    }

    public void singleTierStructureChecksFamilyAndDirection(GameTestHelper helper) {
        var machine = portConstraintMachine("single_tier_structure_constraints", PortRequirementSpec.none(),
                PortTierRequirementSpec.builder().anyRadioactiveChemicalInput().anyHeatOutput().build());
        var controller = portConstraintController(helper, machine);
        BlockPos chemicalPos = new BlockPos(0, 1, 1);
        BlockPos heatPos = new BlockPos(2, 1, 1);
        helper.assertFalse(formPortStructure(controller, machine), "Single-tier requirements reject missing ports");
        helper.setBlock(chemicalPos, ModBlocks.BLOCKS.get("chemical_input_hatch_ultimate").get().defaultBlockState());
        helper.setBlock(heatPos, ModBlocks.BLOCKS.get("heat_output_hatch").get().defaultBlockState());
        helper.assertFalse(formPortStructure(controller, machine), "A normal chemical input cannot satisfy the radioactive family");
        helper.setBlock(chemicalPos, ModBlocks.BLOCKS.get("radioactive_chemical_output_hatch").get().defaultBlockState());
        helper.assertFalse(formPortStructure(controller, machine), "A radioactive output cannot satisfy an input requirement");
        helper.setBlock(chemicalPos, ModBlocks.BLOCKS.get("radioactive_chemical_input_hatch").get().defaultBlockState());
        helper.setBlock(heatPos, ModBlocks.BLOCKS.get("heat_input_hatch").get().defaultBlockState());
        helper.assertFalse(formPortStructure(controller, machine), "A heat input cannot satisfy a heat output requirement");
        helper.assertValueEqual("heat_output_hatch", controller.currentRuntimeSnapshot().structure().lastFormationFailure().portId(),
                "Single-tier failure reports the port family without a fabricated grade");
        helper.setBlock(heatPos, ModBlocks.BLOCKS.get("heat_output_hatch").get().defaultBlockState());
        helper.assertTrue(formPortStructure(controller, machine), "Matching radioactive and heat directions form the structure");
        controller.invalidateFormedStructure();
        helper.succeed();
    }

    public void singleTierStructureChecksCounts(GameTestHelper helper) {
        var machine = portConstraintMachine("single_tier_structure_counts",
                PortRequirementSpec.builder().range("radioactive_chemical_input_hatch", 1, 1)
                        .range("heat_output_hatch", 1, 1).build(), PortTierRequirementSpec.none());
        var controller = portConstraintController(helper, machine);
        BlockPos chemicalPos = new BlockPos(0, 1, 1);
        BlockPos heatPos = new BlockPos(2, 1, 1);
        BlockPos extraPos = new BlockPos(1, 1, 2);
        helper.assertFalse(formPortStructure(controller, machine), "Missing single-tier ports fail the count minimums");
        helper.setBlock(chemicalPos, ModBlocks.BLOCKS.get("radioactive_chemical_input_hatch").get().defaultBlockState());
        helper.setBlock(heatPos, ModBlocks.BLOCKS.get("heat_input_hatch").get().defaultBlockState());
        helper.assertFalse(formPortStructure(controller, machine), "A heat input cannot satisfy the heat output count");
        helper.setBlock(heatPos, ModBlocks.BLOCKS.get("heat_output_hatch").get().defaultBlockState());
        helper.assertTrue(formPortStructure(controller, machine), "The radioactive and heat ports each contribute exactly one count");
        controller.invalidateFormedStructure();

        helper.setBlock(extraPos, ModBlocks.BLOCKS.get("radioactive_chemical_input_hatch").get().defaultBlockState());
        helper.assertFalse(formPortStructure(controller, machine), "An additional radioactive input exceeds its family maximum");
        helper.assertValueEqual(PortRequirementSpec.FailureReason.TOO_MANY,
                controller.currentRuntimeSnapshot().structure().lastFormationFailure().reason(), "Radioactive count is bounded");
        helper.setBlock(extraPos, ModBlocks.BLOCKS.get("heat_output_hatch").get().defaultBlockState());
        helper.assertFalse(formPortStructure(controller, machine), "An additional heat output exceeds its family maximum");
        helper.assertValueEqual(PortRequirementSpec.FailureReason.TOO_MANY,
                controller.currentRuntimeSnapshot().structure().lastFormationFailure().reason(), "Heat count is bounded");
        helper.setBlock(extraPos, ModBlocks.CASING.get().defaultBlockState());
        helper.setBlock(chemicalPos, ModBlocks.BLOCKS.get("chemical_input_hatch_ultimate").get().defaultBlockState());
        helper.assertFalse(formPortStructure(controller, machine), "A normal chemical port cannot satisfy the radioactive count");
        helper.succeed();
    }

    private static DynamicMachine portConstraintMachine(String name, PortRequirementSpec counts, PortTierRequirementSpec tiers) {
        Identifier id = MMCR.id(name);
        return new DynamicMachine(id, "machine.mmcr_test." + name,
                new BlockArray(Map.of(new BlockPos(1, 0, 0), new BlockPredicate.Any(),
                        new BlockPos(-1, 0, 0), new BlockPredicate.Any(), new BlockPos(0, 0, 1), new BlockPredicate.Any())),
                MachineControllerSpec.defaultsFor(id), MachineAppearanceSpec.defaults(), counts, tiers,
                List.of(), Map.of(), 1, false, false, 1);
    }

    private static MachineControllerBlockEntity portConstraintController(GameTestHelper helper, Machine machine) {
        if (!MachineRegistry.containsStatic(machine.registryName())) MachineRegistry.register(machine);
        helper.assertFalse(MachineRegistry.getCompiledStages(machine.registryName()).isEmpty(), "Port constraints use a compiled structure");
        helper.setBlock(new BlockPos(0, 1, 1), ModBlocks.CASING.get().defaultBlockState());
        helper.setBlock(new BlockPos(2, 1, 1), ModBlocks.CASING.get().defaultBlockState());
        helper.setBlock(new BlockPos(1, 1, 2), ModBlocks.CASING.get().defaultBlockState());
        BlockPos controllerPos = new BlockPos(1, 1, 1);
        helper.setBlock(controllerPos, ModBlocks.controllerFor(MMCR.id("test_cube")).get().defaultBlockState()
                .setValue(MachineControllerBlock.FACING, Direction.SOUTH));
        var controller = helper.getBlockEntity(controllerPos, MachineControllerBlockEntity.class);
        controller.setMachine(machine);
        return controller;
    }

    private static boolean formPortStructure(MachineControllerBlockEntity controller, Machine machine) {
        try {
            Method form = MachineControllerBlockEntity.class.getDeclaredMethod("tryFormMachine", Machine.class, Direction.class);
            form.setAccessible(true);
            return (boolean) form.invoke(controller, machine, Direction.SOUTH);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Unable to validate port-constrained structure", exception);
        }
    }

    public void normalChemicalPortRejectsRadioactiveAndAcceptsNonRadioactive(GameTestHelper helper) {
        BlockPos pos = new BlockPos(0, 1, 0);
        helper.setBlock(pos, ModBlocks.BLOCKS.get("chemical_input_hatch_basic").get().defaultBlockState());
        ChemicalPortBlockEntity port = helper.getBlockEntity(pos, ChemicalPortBlockEntity.class);

        ChemicalResource radioactive = registerRadioactiveChemical("radioactive_blocked");
        ChemicalResource oxygen = registerOxygenLikeChemical("oxygen_accepted");

        try (Transaction tx = Transaction.openRoot()) {
            int insertedRadioactive = port.chemicalTank().insert(
                    radioactive, 1_000, tx, AutomationType.EXTERNAL);
            helper.assertValueEqual(0, insertedRadioactive,
                    "Normal chemical port rejects radioactive chemicals");
            int insertedOxygen = port.chemicalTank().insert(
                    oxygen, 1_000, tx, AutomationType.EXTERNAL);
            helper.assertValueEqual(1_000, insertedOxygen,
                    "Normal chemical port accepts non-radioactive chemicals");
            tx.commit();
        }
        helper.succeed();
    }

    public void radioactiveChemicalPortRejectsNonRadioactiveAndAcceptsRadioactive(GameTestHelper helper) {
        BlockPos pos = new BlockPos(0, 1, 0);
        helper.setBlock(pos, ModBlocks.BLOCKS.get("radioactive_chemical_input_hatch").get().defaultBlockState());
        ChemicalPortBlockEntity port = helper.getBlockEntity(pos, ChemicalPortBlockEntity.class);

        ChemicalResource oxygen = registerOxygenLikeChemical("oxygen_rejected_by_radio");
        ChemicalResource radioactive = registerRadioactiveChemical("radioactive_accepted_by_radio");

        try (Transaction tx = Transaction.openRoot()) {
            int insertedOxygen = port.chemicalTank().insert(
                    oxygen, 1_000, tx, AutomationType.EXTERNAL);
            helper.assertValueEqual(0, insertedOxygen,
                    "Radioactive chemical port rejects non-radioactive chemicals");
            int insertedRadioactive = port.chemicalTank().insert(
                    radioactive, 1_000, tx, AutomationType.EXTERNAL);
            helper.assertValueEqual(1_000, insertedRadioactive,
                    "Radioactive chemical port accepts radioactive chemicals");
            tx.commit();
        }
        helper.succeed();
    }

    public void wrenchPreservesNonEmptyRadioactiveChemicalPort(GameTestHelper helper) {
        BlockPos pos = new BlockPos(0, 1, 0);
        helper.setBlock(pos, ModBlocks.BLOCKS.get("radioactive_chemical_input_hatch").get().defaultBlockState());
        ChemicalPortBlockEntity port = helper.getBlockEntity(pos, ChemicalPortBlockEntity.class);
        try (Transaction transaction = Transaction.openRoot()) {
            port.chemicalTank().insert(registerRadioactiveChemical("wrench_protection"), 1_000,
                    transaction, AutomationType.EXTERNAL);
            transaction.commit();
        }

        ServerPlayer player = wrenchPlayer(helper);
        player.setPose(Pose.CROUCHING);
        player.setItemInHand(InteractionHand.MAIN_HAND, AEItems.CERTUS_QUARTZ_WRENCH.stack());
        BlockPos worldPos = helper.absolutePos(pos);
        PlayerInteractEvent.RightClickBlock event = new PlayerInteractEvent.RightClickBlock(player,
                InteractionHand.MAIN_HAND, worldPos, new BlockHitResult(Vec3.atCenterOf(worldPos), Direction.UP, worldPos, false));

        NeoForge.EVENT_BUS.post(event);

        helper.assertTrue(event.isCanceled() && event.getCancellationResult().equals(net.minecraft.world.InteractionResult.FAIL),
                "A non-empty radioactive chemical port rejects wrench dismantling");
        helper.assertTrue(helper.getLevel().getBlockState(worldPos).is(ModBlocks.BLOCKS.get("radioactive_chemical_input_hatch").get()),
                "Rejected wrench dismantling preserves the radioactive chemical port");
        helper.assertTrue(!port.chemicalTank().isEmpty(), "Rejected wrench dismantling preserves the radioactive chemical");
        helper.succeed();
    }

    public void normalChemicalCapacitiesMatchDeclaredTiers(GameTestHelper helper) {
        record TierExpectation(String port, long expected) {}
        List<TierExpectation> tiers = List.of(
                new TierExpectation("chemical_input_hatch_basic",
                        MekanismPortSizes.CHEMICAL_BASIC_CAPACITY),
                new TierExpectation("chemical_input_hatch_advanced",
                        MekanismPortSizes.CHEMICAL_ADVANCED_CAPACITY),
                new TierExpectation("chemical_input_hatch_elite",
                        MekanismPortSizes.CHEMICAL_ELITE_CAPACITY),
                new TierExpectation("chemical_input_hatch_ultimate",
                        MekanismPortSizes.CHEMICAL_ULTIMATE_CAPACITY));
        ChemicalResource oxygen = registerOxygenLikeChemical("oxygen_capacity_probe");
        for (TierExpectation tier : tiers) {
            BlockPos pos = new BlockPos(0, 1, 0);
            helper.setBlock(pos, ModBlocks.BLOCKS.get(tier.port()).get().defaultBlockState());
            ChemicalPortBlockEntity port = helper.getBlockEntity(pos, ChemicalPortBlockEntity.class);
            helper.assertValueEqual(tier.expected(), capacityForResource(port, oxygen),
                    tier.port() + " reports the expected tier capacity for non-radioactive chemicals");
            helper.setBlock(pos, Blocks.AIR.defaultBlockState());
        }
        helper.succeed();
    }

    public void radioactiveChemicalCapacityIsFixedTier(GameTestHelper helper) {
        BlockPos pos = new BlockPos(0, 1, 0);
        helper.setBlock(pos, ModBlocks.BLOCKS.get("radioactive_chemical_input_hatch").get().defaultBlockState());
        ChemicalPortBlockEntity port = helper.getBlockEntity(pos, ChemicalPortBlockEntity.class);
        ChemicalResource radioactive = registerRadioactiveChemical("radioactive_capacity_probe");
        helper.assertValueEqual(MekanismPortSizes.RADIOACTIVE_CHEMICAL_CAPACITY,
                capacityForResource(port, radioactive),
                "Radioactive chemical port reports the documented fixed capacity");
        helper.succeed();
    }

    public void heatTemperatureRequirementReadsWithoutConsumingHeat(GameTestHelper helper) {
        BlockPos heatPos = new BlockPos(0, 1, 0);
        helper.setBlock(heatPos, ModBlocks.BLOCKS.get("heat_input_hatch").get().defaultBlockState());
        HeatPortBlockEntity port = helper.getBlockEntity(heatPos, HeatPortBlockEntity.class);

        double ambient = HeatAPI.getAmbientTemp(port.getLevel(), port.getBlockPos());
        double capacity = port.heatCapacitor().getHeatCapacity();
        double baseline = ambient * capacity;
        setHeat(port, baseline);
        RequirementPlan insufficient = LoadedMekanismBridge.heatHandler().plan(
                LoadedHeatRequirement.minimumTemperature(ambient + 100D),
                List.of(new HeatPortCapability(port)), new PlanningContext(1, 0));
        helper.assertTrue(insufficient.failure() != null,
                "A heat requirement below the port temperature reports a structured failure");
        helper.assertValueEqual(MekanismFailureReasons.HEAT_TEMPERATURE_INSUFFICIENT.id(),
                insufficient.failure().reason().id(),
                "Heat temperature insufficiency exposes its registered reason ID");
        setHeat(port, baseline * 5.5D);
        double before = port.heatCapacitor().getHeat();
        helper.assertTrue(before > baseline,
                "Heat input port holds more heat than its ambient baseline before the recipe check");

        IHeatHandler handler = port.heatHandler();
        helper.assertValueEqual(before, handler.getTemperature() * handler.getHeatCapacity(),
                "Heat input port's IHeatHandler reports the same stored heat as its capacitor");

        helper.assertTrue(handler.getTemperature() >= 450D,
                "Setting heat to 5x the ambient baseline exceeds the 450K minimum temperature requirement");

        helper.assertValueEqual(before, port.heatCapacitor().getHeat(),
                "Reading the heat input port does not consume its stored heat");
        helper.succeed();
    }

    public void heatOutputHandleHeatIncreasesStoredHeat(GameTestHelper helper) {
        BlockPos heatPos = new BlockPos(0, 1, 0);
        helper.setBlock(heatPos, ModBlocks.BLOCKS.get("heat_output_hatch").get().defaultBlockState());
        HeatPortBlockEntity port = helper.getBlockEntity(heatPos, HeatPortBlockEntity.class);

        double ambient = HeatAPI.getAmbientTemp(port.getLevel(), port.getBlockPos());
        double baseline = ambient * port.heatCapacitor().getHeatCapacity();
        double before = port.heatCapacitor().getHeat();

        double delta = 0.25D;
        try (Transaction transaction = Transaction.openRoot()) {
            port.heatCapacitor().handleHeat(delta, transaction);
            transaction.commit();
        }

        helper.assertValueEqual(before + delta, port.heatCapacitor().getHeat(),
                "Heat output port stores the heat delta delivered through handleHeat");
        helper.assertTrue(port.heatCapacitor().getHeat() > baseline,
                "Heat output port stores more heat than its ambient baseline after handleHeat");
        helper.succeed();
    }

    public void heatOutputCapabilityRejectsExternalHeatInput(GameTestHelper helper) {
        BlockPos heatPos = new BlockPos(0, 1, 0);
        helper.setBlock(heatPos, ModBlocks.BLOCKS.get("heat_output_hatch").get().defaultBlockState());
        HeatPortBlockEntity port = helper.getBlockEntity(heatPos, HeatPortBlockEntity.class);
        IHeatHandler capability = helper.getLevel().getCapability(Capabilities.HEAT,
                helper.absolutePos(heatPos),
                helper.getLevel().getBlockState(helper.absolutePos(heatPos)), port, Direction.EAST);
        helper.assertTrue(capability != null,
                "Heat capability is exposed on the EAST side of a heat output port");

        double ambient = HeatAPI.getAmbientTemp(port.getLevel(), port.getBlockPos());
        setHeat(port, ambient * port.heatCapacitor().getHeatCapacity()
                + port.heatCapacitor().getHeatCapacity() * 100D);
        double before = port.heatCapacitor().getHeat();
        try (Transaction transaction = Transaction.openRoot()) {
            capability.handleHeat(10D, transaction);
            capability.handleHeat(-4D, transaction);
            transaction.commit();
        }
        helper.assertValueEqual(before - 4D, port.heatCapacitor().getHeat(),
                "The exposed heat output handler rejects external heat input and permits external extraction");
        helper.succeed();
    }

    public void heatPortLosesHeatToItsEnvironment(GameTestHelper helper) {
        BlockPos heatPos = new BlockPos(0, 1, 0);
        helper.setBlock(heatPos, ModBlocks.BLOCKS.get("heat_input_hatch").get().defaultBlockState());
        HeatPortBlockEntity port = helper.getBlockEntity(heatPos, HeatPortBlockEntity.class);

        double ambient = HeatAPI.getAmbientTemp(port.getLevel(), port.getBlockPos());
        double capacity = port.heatCapacitor().getHeatCapacity();
        setHeat(port, ambient * capacity + capacity * 100D);
        double before = port.heatCapacitor().getHeat();

        port.serverTick();

        helper.assertTrue(port.heatCapacitor().getHeat() < before,
                "A hot heat port loses heat to its ambient environment during a server tick");
        helper.assertTrue(port.heatCapacitor().getTemperature() > ambient,
                "One environment tick does not instantly remove the port's excess heat");
        helper.succeed();
    }

    public void heatOutputPortLosesHeatToItsEnvironment(GameTestHelper helper) {
        BlockPos heatPos = new BlockPos(0, 1, 0);
        helper.setBlock(heatPos, ModBlocks.BLOCKS.get("heat_output_hatch").get().defaultBlockState());
        HeatPortBlockEntity port = helper.getBlockEntity(heatPos, HeatPortBlockEntity.class);

        double ambient = HeatAPI.getAmbientTemp(port.getLevel(), port.getBlockPos());
        double capacity = port.heatCapacitor().getHeatCapacity();
        setHeat(port, ambient * capacity + capacity * 100D);
        double before = port.heatCapacitor().getHeat();

        port.serverTick();

        helper.assertTrue(port.heatCapacitor().getHeat() < before,
                "A hot heat output port loses heat to its ambient environment during a server tick");
        helper.succeed();
    }

    public void heatOutputPortDoesNotAbsorbAmbientHeat(GameTestHelper helper) {
        BlockPos heatPos = new BlockPos(0, 1, 0);
        helper.setBlock(heatPos, ModBlocks.BLOCKS.get("heat_output_hatch").get().defaultBlockState());
        HeatPortBlockEntity port = helper.getBlockEntity(heatPos, HeatPortBlockEntity.class);

        // Set the port noticeably below ambient so the default simulateEnvironment would push heat in.
        double ambient = HeatAPI.getAmbientTemp(port.getLevel(), port.getBlockPos());
        double capacity = port.heatCapacitor().getHeatCapacity();
        double coldHeat = Math.max(0D, (ambient - 50D) * capacity);
        setHeat(port, coldHeat);
        double before = port.heatCapacitor().getHeat();

        port.serverTick();

        helper.assertValueEqual(before, port.heatCapacitor().getHeat(),
                "An output heat port does not absorb heat from its ambient environment");
        helper.succeed();
    }

    public void heatInputPortDoesNotAbsorbAmbientHeat(GameTestHelper helper) {
        BlockPos heatPos = new BlockPos(0, 1, 0);
        helper.setBlock(heatPos, ModBlocks.BLOCKS.get("heat_input_hatch").get().defaultBlockState());
        HeatPortBlockEntity port = helper.getBlockEntity(heatPos, HeatPortBlockEntity.class);

        // Set the port noticeably below ambient so a default environment simulation would push heat in.
        double ambient = HeatAPI.getAmbientTemp(port.getLevel(), port.getBlockPos());
        double capacity = port.heatCapacitor().getHeatCapacity();
        double coldHeat = Math.max(0D, (ambient - 50D) * capacity);
        setHeat(port, coldHeat);
        double before = port.heatCapacitor().getHeat();

        port.serverTick();

        helper.assertValueEqual(before, port.heatCapacitor().getHeat(),
                "An input heat port does not absorb heat from its ambient environment");
        helper.succeed();
    }

    public void heatPortUsesStandardAdjacentExchange(GameTestHelper helper) {
        BlockPos sourcePos = new BlockPos(0, 1, 0);
        BlockPos sinkPos = sourcePos.relative(Direction.EAST);
        helper.setBlock(sourcePos, ModBlocks.BLOCKS.get("heat_output_hatch").get().defaultBlockState());
        helper.setBlock(sinkPos, ModBlocks.BLOCKS.get("heat_input_hatch").get().defaultBlockState());
        HeatPortBlockEntity source = helper.getBlockEntity(sourcePos, HeatPortBlockEntity.class);
        HeatPortBlockEntity sink = helper.getBlockEntity(sinkPos, HeatPortBlockEntity.class);
        source.setAutoIOEnabled(false);
        sink.setAutoIOEnabled(false);

        double sourceAmbient = HeatAPI.getAmbientTemp(source.getLevel(), source.getBlockPos());
        double sinkAmbient = HeatAPI.getAmbientTemp(sink.getLevel(), sink.getBlockPos());
        setHeat(source, sourceAmbient * source.heatCapacitor().getHeatCapacity()
                + source.heatCapacitor().getHeatCapacity() * 100D);
        double sourceBefore = source.heatCapacitor().getHeat();
        double sinkBefore = sink.heatCapacitor().getHeat();

        source.serverTick();

        helper.assertTrue(source.heatCapacitor().getHeat() < sourceBefore,
                "The hotter port gives up heat during adjacent exchange");
        helper.assertTrue(sink.heatCapacitor().getHeat() > sinkBefore,
                "The colder adjacent port receives heat during adjacent exchange");
        helper.assertTrue(sink.heatCapacitor().getHeat() > sinkAmbient * sink.heatCapacitor().getHeatCapacity(),
                "Adjacent exchange raises the sink above its ambient baseline");
        helper.succeed();
    }

    public void ambientHeatPortDoesNotEmitBaselineHeat(GameTestHelper helper) {
        BlockPos sourcePos = new BlockPos(0, 1, 0);
        BlockPos sinkPos = sourcePos.relative(Direction.EAST);
        helper.setBlock(sourcePos, ModBlocks.BLOCKS.get("heat_output_hatch").get().defaultBlockState());
        helper.setBlock(sinkPos, ModBlocks.BLOCKS.get("heat_input_hatch").get().defaultBlockState());
        HeatPortBlockEntity source = helper.getBlockEntity(sourcePos, HeatPortBlockEntity.class);
        HeatPortBlockEntity sink = helper.getBlockEntity(sinkPos, HeatPortBlockEntity.class);
        source.setAutoIOEnabled(false);
        sink.setAutoIOEnabled(false);
        double sinkBefore = sink.heatCapacitor().getHeat();

        source.serverTick();

        helper.assertValueEqual(sinkBefore, sink.heatCapacitor().getHeat(),
                "An ambient heat port does not emit its ambient baseline to an adjacent port");
        helper.succeed();
    }

    public void heatInputPortDoesNotTransferHeatToAdjacentOutput(GameTestHelper helper) {
        BlockPos sourcePos = new BlockPos(0, 1, 0);
        BlockPos sinkPos = sourcePos.relative(Direction.EAST);
        BlockPos referencePos = new BlockPos(0, 1, 3);
        helper.setBlock(sourcePos, ModBlocks.BLOCKS.get("heat_input_hatch").get().defaultBlockState());
        helper.setBlock(sinkPos, ModBlocks.BLOCKS.get("heat_output_hatch").get().defaultBlockState());
        helper.setBlock(referencePos, ModBlocks.BLOCKS.get("heat_input_hatch").get().defaultBlockState());
        HeatPortBlockEntity source = helper.getBlockEntity(sourcePos, HeatPortBlockEntity.class);
        HeatPortBlockEntity sink = helper.getBlockEntity(sinkPos, HeatPortBlockEntity.class);
        HeatPortBlockEntity reference = helper.getBlockEntity(referencePos, HeatPortBlockEntity.class);
        source.setAutoIOEnabled(false);
        sink.setAutoIOEnabled(false);
        reference.setAutoIOEnabled(false);
        double sourceAmbient = HeatAPI.getAmbientTemp(source.getLevel(), source.getBlockPos());
        double heat = sourceAmbient * source.heatCapacitor().getHeatCapacity()
                + source.heatCapacitor().getHeatCapacity() * 100D;
        setHeat(source, heat);
        setHeat(reference, heat);
        double sinkBefore = sink.heatCapacitor().getHeat();

        source.serverTick();
        reference.serverTick();

        helper.assertValueEqual(reference.heatCapacitor().getHeat(), source.heatCapacitor().getHeat(),
                "Heat input does not lose additional heat to an adjacent output port");
        helper.assertValueEqual(sinkBefore, sink.heatCapacitor().getHeat(),
                "Heat output does not receive heat from an adjacent input port");
        helper.succeed();
    }

    public void heatOutputPortDoesNotTransferHeatToAdjacentOutput(GameTestHelper helper) {
        BlockPos sourcePos = new BlockPos(0, 1, 0);
        BlockPos sinkPos = sourcePos.relative(Direction.EAST);
        BlockPos referencePos = new BlockPos(0, 1, 3);
        helper.setBlock(sourcePos, ModBlocks.BLOCKS.get("heat_output_hatch").get().defaultBlockState());
        helper.setBlock(sinkPos, ModBlocks.BLOCKS.get("heat_output_hatch").get().defaultBlockState());
        helper.setBlock(referencePos, ModBlocks.BLOCKS.get("heat_output_hatch").get().defaultBlockState());
        HeatPortBlockEntity source = helper.getBlockEntity(sourcePos, HeatPortBlockEntity.class);
        HeatPortBlockEntity sink = helper.getBlockEntity(sinkPos, HeatPortBlockEntity.class);
        HeatPortBlockEntity reference = helper.getBlockEntity(referencePos, HeatPortBlockEntity.class);
        source.setAutoIOEnabled(false);
        sink.setAutoIOEnabled(false);
        reference.setAutoIOEnabled(false);
        double sourceAmbient = HeatAPI.getAmbientTemp(source.getLevel(), source.getBlockPos());
        double heat = sourceAmbient * source.heatCapacitor().getHeatCapacity()
                + source.heatCapacitor().getHeatCapacity() * 100D;
        setHeat(source, heat);
        setHeat(reference, heat);
        double sinkBefore = sink.heatCapacitor().getHeat();

        source.serverTick();
        reference.serverTick();

        helper.assertValueEqual(reference.heatCapacitor().getHeat(), source.heatCapacitor().getHeat(),
                "Heat output does not lose additional heat to an adjacent output port");
        helper.assertValueEqual(sinkBefore, sink.heatCapacitor().getHeat(),
                "Heat output does not receive heat from another output port");
        helper.succeed();
    }

    public void heatOutputPortTransfersHeatToThermodynamicConductor(GameTestHelper helper) {
        BlockPos outputPos = new BlockPos(0, 1, 0);
        BlockPos conductorPos = outputPos.relative(Direction.EAST);
        helper.setBlock(outputPos, ModBlocks.BLOCKS.get("heat_output_hatch").get().defaultBlockState());
        helper.setBlock(conductorPos, MekanismBlocks.BASIC_THERMODYNAMIC_CONDUCTOR.get().defaultBlockState());
        HeatPortBlockEntity output = helper.getBlockEntity(outputPos, HeatPortBlockEntity.class);
        TileEntityThermodynamicConductor conductor = helper.getBlockEntity(conductorPos,
                TileEntityThermodynamicConductor.class);
        output.setAutoIOEnabled(false);
        double ambient = HeatAPI.getAmbientTemp(output.getLevel(), output.getBlockPos());
        setHeat(output, ambient * output.heatCapacitor().getHeatCapacity()
                + output.heatCapacitor().getHeatCapacity() * 100D);

        helper.runAtTickTime(20, () -> {
            double outputBefore = output.heatCapacitor().getHeat();
            double conductorBefore = conductor.getTransmitter().buffer.getHeat();
            output.serverTick();

            helper.assertTrue(output.heatCapacitor().getHeat() < outputBefore,
                    "A heat output port transfers heat to a connected thermodynamic conductor");
            helper.assertTrue(conductor.getTransmitter().buffer.getHeat() > conductorBefore,
                    "A connected thermodynamic conductor receives heat from a heat output port");
            helper.succeed();
        });
    }

    public void thermodynamicConductorDoesNotHeatOutputPort(GameTestHelper helper) {
        BlockPos outputPos = new BlockPos(0, 1, 0);
        BlockPos conductorPos = outputPos.relative(Direction.EAST);
        helper.setBlock(outputPos, ModBlocks.BLOCKS.get("heat_output_hatch").get().defaultBlockState());
        helper.setBlock(conductorPos, MekanismBlocks.BASIC_THERMODYNAMIC_CONDUCTOR.get().defaultBlockState());
        HeatPortBlockEntity output = helper.getBlockEntity(outputPos, HeatPortBlockEntity.class);
        TileEntityThermodynamicConductor conductor = helper.getBlockEntity(conductorPos,
                TileEntityThermodynamicConductor.class);

        helper.runAtTickTime(20, () -> {
            IHeatHandler outputHandler = helper.getLevel().getCapability(Capabilities.HEAT,
                    helper.absolutePos(outputPos), helper.getLevel().getBlockState(helper.absolutePos(outputPos)), output,
                    Direction.EAST);
            helper.assertTrue(outputHandler != null, "Heat output port exposes its heat handler");
            helper.assertTrue(outputHandler.getInverseConduction() > 1E300,
                    "Heat output presents an effectively insulated inbound boundary to a conductor");
            double before = output.heatCapacitor().getHeat();
            try (Transaction transaction = Transaction.openRoot()) {
                conductor.getTransmitter().buffer.handleHeat(30_000D, transaction);
                conductor.getTransmitter().simulate(transaction);
                transaction.commit();
            }
            helper.assertValueEqual(before, output.heatCapacitor().getHeat(),
                    "A thermodynamic conductor cannot inject heat into a heat output port");
            helper.succeed();
        });
    }

    public void chemicalInputAutoImportsFromAdjacentOutput(GameTestHelper helper) {
        BlockPos inputPos = new BlockPos(0, 1, 0);
        BlockPos sourcePos = inputPos.relative(Direction.EAST);
        helper.setBlock(inputPos, ModBlocks.BLOCKS.get("chemical_input_hatch_basic").get().defaultBlockState());
        helper.setBlock(sourcePos, ModBlocks.BLOCKS.get("chemical_output_hatch_basic").get().defaultBlockState());
        ChemicalPortBlockEntity input = helper.getBlockEntity(inputPos, ChemicalPortBlockEntity.class);
        ChemicalPortBlockEntity source = helper.getBlockEntity(sourcePos, ChemicalPortBlockEntity.class);

        ChemicalResource oxygen = registerOxygenLikeChemical("oxygen_auto_import");
        source.chemicalTank().setContents(oxygen, 5_000L, null);

        input.toggleAutoIOEnabled();
        input.setAllAutoIOSides(false);
        input.setAutoIOSide(Direction.EAST, true);

        helper.runAtTickTime(60, input::serverTick);
        helper.runAtTickTime(80, () -> {
            helper.assertValueEqual(5_000L, input.chemicalTank().amountAsLong(),
                    "Chemical input auto-imports 5_000 units from the adjacent output port");
            helper.assertTrue(source.chemicalTank().amountAsLong() < 5_000L,
                    "Adjacent chemical output port loses contents to the auto-import");
            helper.succeed();
        });
    }

    public void chemicalInputEjectionSpreadsAcrossDirectionsAndEmptiesSource(GameTestHelper helper) {
        BlockPos sourcePos = new BlockPos(0, 1, 0);
        BlockPos northReceiver = sourcePos.relative(Direction.NORTH);
        BlockPos southReceiver = sourcePos.relative(Direction.SOUTH);
        helper.setBlock(sourcePos, ModBlocks.BLOCKS.get("chemical_input_hatch_basic").get().defaultBlockState());
        helper.setBlock(northReceiver, ModBlocks.BLOCKS.get("chemical_input_hatch_basic").get().defaultBlockState());
        helper.setBlock(southReceiver, ModBlocks.BLOCKS.get("chemical_input_hatch_basic").get().defaultBlockState());
        ChemicalPortBlockEntity source = helper.getBlockEntity(sourcePos, ChemicalPortBlockEntity.class);
        ChemicalPortBlockEntity north = helper.getBlockEntity(northReceiver, ChemicalPortBlockEntity.class);
        ChemicalPortBlockEntity south = helper.getBlockEntity(southReceiver, ChemicalPortBlockEntity.class);

        ChemicalResource oxygen = registerOxygenLikeChemical("oxygen_eject");
        try (Transaction tx = Transaction.openRoot()) {
            source.chemicalTank().insert(oxygen, 2_000, tx, AutomationType.EXTERNAL);
            tx.commit();
        }

        helper.runAtTickTime(20, () -> {
            helper.assertTrue(source.ejectContents(),
                    "Chemical input ejection runs the underlying transfer policy");
            long northAmount = north.chemicalTank().amountAsLong();
            long southAmount = south.chemicalTank().amountAsLong();
            helper.assertValueEqual(2_000L, northAmount + southAmount,
                    "Both adjacent chemical input ports collectively receive the 2_000 ejected units");
            helper.assertValueEqual(0L, source.chemicalTank().amountAsLong(),
                    "Chemical input port is empty after ejection");
            helper.succeed();
        });
    }

    public void chemicalInputEjectionPreservesRemainderAgainstPartialTarget(GameTestHelper helper) {
        BlockPos sourcePos = new BlockPos(0, 1, 0);
        BlockPos receiverPos = sourcePos.relative(Direction.NORTH);
        helper.setBlock(sourcePos, ModBlocks.BLOCKS.get("chemical_input_hatch_basic").get().defaultBlockState());
        helper.setBlock(receiverPos, ModBlocks.BLOCKS.get("chemical_input_hatch_basic").get().defaultBlockState());
        ChemicalPortBlockEntity source = helper.getBlockEntity(sourcePos, ChemicalPortBlockEntity.class);
        ChemicalPortBlockEntity receiver = helper.getBlockEntity(receiverPos, ChemicalPortBlockEntity.class);

        ChemicalResource oxygen = registerOxygenLikeChemical("oxygen_eject_remainder");
        long partialCapacity = MekanismPortSizes.CHEMICAL_BASIC_CAPACITY - 2_000L;
        try (Transaction tx = Transaction.openRoot()) {
            source.chemicalTank().insert(oxygen, 3_000, tx, AutomationType.EXTERNAL);
            receiver.chemicalTank().insert(oxygen, (int) partialCapacity, tx, AutomationType.EXTERNAL);
            tx.commit();
        }

        helper.runAtTickTime(20, () -> {
            long beforeMove = source.chemicalTank().amountAsLong();
            helper.assertTrue(source.ejectContents(),
                    "Chemical input ejection runs against a partial target");
            long movedAmount = receiver.chemicalTank().amountAsLong() - partialCapacity;
            helper.assertValueEqual(partialCapacity + movedAmount,
                    receiver.chemicalTank().amountAsLong(),
                    "Chemical receiver accepted exactly the partial remaining capacity");
            helper.assertValueEqual(beforeMove - movedAmount, source.chemicalTank().amountAsLong(),
                    "Chemical input port preserves the unsent remainder after partial ejection");
            helper.succeed();
        });
    }

    public void chemicalAndHeatContentsPersistAcrossReload(GameTestHelper helper) {
        BlockPos chemicalPos = new BlockPos(0, 1, 0);
        BlockPos heatPos = new BlockPos(0, 2, 0);
        helper.setBlock(chemicalPos, ModBlocks.BLOCKS.get("chemical_input_hatch_basic").get().defaultBlockState());
        helper.setBlock(heatPos, ModBlocks.BLOCKS.get("heat_input_hatch").get().defaultBlockState());
        ChemicalPortBlockEntity chemical = helper.getBlockEntity(chemicalPos, ChemicalPortBlockEntity.class);
        HeatPortBlockEntity heat = helper.getBlockEntity(heatPos, HeatPortBlockEntity.class);

        ChemicalResource oxygen = registerOxygenLikeChemical("oxygen_persist");
        chemical.chemicalTank().setContents(oxygen, 12_345L, null);
        double ambient = HeatAPI.getAmbientTemp(heat.getLevel(), heat.getBlockPos());
        double baseline = ambient * heat.heatCapacitor().getHeatCapacity();
        setHeat(heat, baseline * 7.25D);
        double heatBefore = heat.heatCapacitor().getHeat();
        double heatCapacityBefore = heat.heatCapacitor().getHeatCapacity();
        helper.assertTrue(heatBefore > baseline,
                "Heat port holds more than the ambient baseline before persistence check");

        reloadBlockEntity(chemical, helper);
        reloadBlockEntity(heat, helper);

        helper.assertValueEqual(12_345L, chemical.chemicalTank().amountAsLong(),
                "Chemical tank contents survive a save/load cycle");
        helper.assertValueEqual(oxygen, chemical.chemicalTank().resource(),
                "Chemical tank resource identity survives a save/load cycle");
        helper.assertValueEqual(heatBefore, heat.heatCapacitor().getHeat(),
                "Heat capacitor value survives a save/load cycle");
        helper.assertValueEqual(heatCapacityBefore, heat.heatCapacitor().getHeatCapacity(),
                "Heat port capacity remains independent from saved heat");
        helper.succeed();
    }

    public void unavailableBridgeLeavesBuilderWorkingThroughCustomRecipeIo(GameTestHelper helper) {
        try {
            MekanismBridgeBootstrap.installForTesting(MekanismBridgeBootstrap.selectForTesting(false));
            helper.assertFalse(MekanismBridge.get().available(),
                    "Installed unavailable bridge reports available()==false");
            helper.assertValueEqual(MekanismFailureReasons.MEKANISM_UNAVAILABLE.id(),
                    MekanismBridge.get().unavailableReason(),
                    "Installed unavailable bridge reports the documented failure reason");

            MachineRecipeBuilder builder = MachineRecipeBuilder.recipe(
                            Identifier.fromNamespaceAndPath("mmcr_test", "unavailable_chemical_e2e"))
                    .recipePool(Identifier.fromNamespaceAndPath("mmcr_test", "test_cube"))
                    .duration(20)
                    .inputChemical(Identifier.fromNamespaceAndPath("mekanism", "oxygen"), 1_000L, 0.25F)
                    .outputChemical(Identifier.fromNamespaceAndPath("mekanism", "hydrogen"), 200L, 0.5F)
                    .inputHeatTemperature(450D)
                    .outputHeat(120D);
            var declaration = builder.build();
            List<MachineRequirement> customRequirements = declaration.requirements();
            List<CustomRecipeIo> customOutputs = declaration.customOutputs();
            helper.assertValueEqual(2, customRequirements.size(),
                    "Both chemical input and temperature input are kept as canonical requirements with an unavailable bridge");
            helper.assertValueEqual(2, customOutputs.size(),
                    "Both chemical output and heat output are kept as CustomRecipeIo output entries when Mekanism is unavailable");
            MachineRequirement chemicalInput = customRequirements.stream()
                    .filter(io -> io.type().id().equals(MekanismRecipeTypes.CHEMICAL))
                    .findFirst().orElseThrow();
            MachineRequirement temperatureInput = customRequirements.stream()
                    .filter(io -> io.type().id().equals(MekanismRecipeTypes.HEAT_TEMPERATURE))
                    .findFirst().orElseThrow();
            CustomRecipeIo chemicalOutput = customOutputs.stream()
                    .filter(io -> io.typeId().equals(MekanismRecipeTypes.CHEMICAL))
                    .findFirst().orElseThrow();
            CustomRecipeIo heatOutput = customOutputs.stream()
                    .filter(io -> io.typeId().equals(MekanismRecipeTypes.HEAT))
                    .findFirst().orElseThrow();
            helper.assertValueEqual(MekanismRecipeTypes.CHEMICAL, chemicalInput.type().id(),
                    "Chemical input resolves to the canonical type");
            helper.assertValueEqual(IOType.INPUT, chemicalInput.io(),
                    "Chemical input direction stays INPUT even when Mekanism is unavailable");
            helper.assertValueEqual(MekanismRecipeTypes.HEAT_TEMPERATURE, temperatureInput.type().id(),
                    "Temperature input resolves to the canonical type");
            helper.assertValueEqual(IOType.INPUT, temperatureInput.io(),
                    "Temperature input direction stays INPUT even when Mekanism is unavailable");
            helper.assertValueEqual(MekanismRecipeTypes.CHEMICAL, chemicalOutput.typeId(),
                    "Chemical output still resolves through the public CustomRecipeIo path");
            helper.assertValueEqual(IOType.OUTPUT, chemicalOutput.ioType(),
                    "Chemical output direction stays OUTPUT even when Mekanism is unavailable");
            helper.assertValueEqual(MekanismRecipeTypes.HEAT, heatOutput.typeId(),
                    "Heat output still resolves through the public CustomRecipeIo path");
            helper.assertValueEqual(IOType.OUTPUT, heatOutput.ioType(),
                    "Heat output direction stays OUTPUT even when Mekanism is unavailable");
            var chemicalPayload = MachineRequirement.CODEC.encodeStart(JsonOps.INSTANCE, chemicalInput).getOrThrow().getAsJsonObject();
            helper.assertValueEqual("mekanism:oxygen", chemicalPayload.get("id").getAsString(), "Chemical input id survives canonical encoding");
            helper.assertFalse(chemicalPayload.has("kind"), "Default chemical kind is omitted by the canonical codec");
            var expectedChemical = MachineRequirement.CODEC.parse(JsonOps.INSTANCE,
                    MachineRecipeBuilder.chemicalInputPayload(ChemicalIngredient.chemical(
                            Identifier.parse("mekanism:oxygen"), 1000), 0.25F)).getOrThrow();
            helper.assertValueEqual(MachineRequirement.CODEC.encodeStart(JsonOps.INSTANCE, expectedChemical).getOrThrow(),
                    chemicalPayload, "Chemical input retains its full chemical-kind semantics");
            helper.assertValueEqual(1_000L, chemicalPayload.get("amount").getAsLong(), "Chemical input amount survives canonical encoding");
            helper.assertValueEqual(0.25F, chemicalPayload.get("consume_chance").getAsFloat(), "Chemical consume chance survives canonical encoding");
            var chemicalRoundTrip = MachineRequirement.CODEC.parse(JsonOps.INSTANCE, chemicalPayload).getOrThrow();
            helper.assertValueEqual(MachineRequirement.CODEC.encodeStart(JsonOps.INSTANCE, chemicalRoundTrip).getOrThrow(),
                    chemicalPayload, "Chemical input payload round-trips");
            var temperaturePayload = MachineRequirement.CODEC.encodeStart(JsonOps.INSTANCE, temperatureInput).getOrThrow().getAsJsonObject();
            helper.assertValueEqual(450D, temperaturePayload.get("value").getAsDouble(), "Temperature value survives canonical encoding");
            helper.assertValueEqual(temperaturePayload, MachineRequirement.CODEC.encodeStart(JsonOps.INSTANCE,
                    MachineRequirement.CODEC.parse(JsonOps.INSTANCE, temperaturePayload).getOrThrow()).getOrThrow(), "Temperature payload round-trips");
            helper.assertValueEqual("mekanism:hydrogen", chemicalOutput.payload().getAsJsonObject().get("id").getAsString(), "Chemical output id survives");
            helper.assertValueEqual(200L, chemicalOutput.payload().getAsJsonObject().get("amount").getAsLong(), "Chemical output amount survives");
            helper.assertValueEqual(0.5F, chemicalOutput.payload().getAsJsonObject().get("chance").getAsFloat(), "Chemical output chance survives");
            helper.assertValueEqual(120D, heatOutput.payload().getAsJsonObject().get("value").getAsDouble(), "Heat output value survives");
            var recipe = MachineRecipeConverter.toRecipe(declaration,
                    new StructureRegistration.Snapshot(Map.of(), Map.of(), Map.of(), Map.of()));
            helper.assertValueEqual(2, recipe.machineOutputs().size(), "Converter retains both custom outputs");
            for (int index = 0; index < customOutputs.size(); index++) {
                var converted = recipe.machineOutputs().get(index);
                var expected = MachineRecipeConverter.toOutput(customOutputs.get(index));
                helper.assertValueEqual(MachineOutput.CODEC.encodeStart(JsonOps.INSTANCE, expected).getOrThrow(),
                        MachineOutput.CODEC.encodeStart(JsonOps.INSTANCE, converted).getOrThrow(), "Converter retains custom output payload");
            }
            helper.succeed();
        } finally {
            MekanismBridgeBootstrap.resetForTesting();
        }
    }

    public void heatInputCapabilityAcceptsOnlyExternalHeatInput(GameTestHelper helper) {
        BlockPos heatPos = new BlockPos(0, 1, 0);
        helper.setBlock(heatPos, ModBlocks.BLOCKS.get("heat_input_hatch").get().defaultBlockState());
        HeatPortBlockEntity heat = helper.getBlockEntity(heatPos, HeatPortBlockEntity.class);

        IHeatHandler capability = helper.getLevel().getCapability(Capabilities.HEAT,
                helper.absolutePos(heatPos),
                helper.getLevel().getBlockState(helper.absolutePos(heatPos)),
                helper.getBlockEntity(heatPos, HeatPortBlockEntity.class),
                Direction.EAST);
        helper.assertTrue(capability != null,
                "Heat capability is exposed on the EAST side of a heat input port");

        double before = heat.heatCapacitor().getHeat();
        try (Transaction transaction = Transaction.openRoot()) {
            capability.handleHeat(10.25D, transaction);
            capability.handleHeat(-0.25D, transaction);
            transaction.commit();
        }
        helper.assertValueEqual(before + 10.25D, heat.heatCapacitor().getHeat(),
                "The exposed Mekanism heat input handler accepts heat but rejects external extraction");
        helper.succeed();
    }

    public void formedMultiblockPortReflectsBaseTextureChange(GameTestHelper helper) {
        BlockPos portPos = new BlockPos(0, 1, 0);
        var portBlock = ModBlocks.BLOCKS.get("chemical_input_hatch_basic").get();
        helper.setBlock(portPos, portBlock.defaultBlockState());

        ChemicalPortBlockEntity port = helper.getBlockEntity(portPos, ChemicalPortBlockEntity.class);

        Identifier updatedTexture = MMCR.id("block/chemical_basic_casing_updated");
        Identifier initialAppearance = port.appearanceBaseTexture();

        port.linkControllerAppearance(portPos, updatedTexture);

        helper.assertValueEqual(updatedTexture, port.appearanceBaseTexture(),
                "Chemical port reflects the linked appearance texture immediately");
        helper.assertValueEqual(updatedTexture,
                port.getModelData().get(MachineModelDataKeys.PORT_BASE_TEXTURE),
                "Chemical port model data mirrors the linked appearance texture");
        helper.assertTrue(!initialAppearance.equals(updatedTexture),
                "Initial appearance baseline differs from the updated texture");
        helper.succeed();
    }

    private static long capacityForResource(ChemicalPortBlockEntity port, ChemicalResource resource) {
        return port.chemicalTank().capacityAsLong(resource);
    }

    private static ServerPlayer wrenchPlayer(GameTestHelper helper) {
        return new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
                new GameProfile(UUID.nameUUIDFromBytes("mmcr-radioactive-wrench".getBytes(StandardCharsets.UTF_8)),
                        "mmcr-wrench"), ClientInformation.createDefault());
    }

    private static void setHeat(HeatPortBlockEntity port, double target) {
        try (Transaction transaction = Transaction.openRoot()) {
            port.heatCapacitor().setHeat(target, transaction);
            transaction.commit();
        }
    }

    private static void reloadBlockEntity(BlockEntity entity, GameTestHelper helper) {
        try {
            Method save = BlockEntity.class.getDeclaredMethod("saveAdditional", ValueOutput.class);
            save.setAccessible(true);
            Method load = BlockEntity.class.getDeclaredMethod("loadAdditional", ValueInput.class);
            load.setAccessible(true);
            TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING,
                    helper.getLevel().registryAccess());
            save.invoke(entity, output);
            CompoundTag tag = output.buildResult();
            load.invoke(entity, TagValueInput.create(ProblemReporter.DISCARDING,
                    helper.getLevel().registryAccess(), tag));
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Unable to reload block entity " + entity, exception);
        }
    }

    private static ChemicalResource registerOxygenLikeChemical(String path) {
        return ChemicalResource.of(registerChemical(path, false));
    }

    private static ChemicalResource registerRadioactiveChemical(String path) {
        return ChemicalResource.of(registerChemical(path, true));
    }

    private static Holder.Reference<Chemical> registerChemical(String path, boolean radioactive) {
        ResourceKey<Chemical> key = ResourceKey.create(MekanismAPI.CHEMICAL_REGISTRY_NAME,
                Identifier.fromNamespaceAndPath("mmcr_test", path));
        MappedRegistry<Chemical> registry = (MappedRegistry<Chemical>) MekanismAPI.CHEMICAL_REGISTRY;
        return registry.get(key).orElseGet(() -> {
            registry.unfreeze(true);
            Chemical value = new Chemical(ChemicalBuilder.builder()) {
                @Override
                public boolean isRadioactive() {
                    return radioactive;
                }
            };
            Registry.register(registry, key.identifier(), value);
            registry.freeze();
            return registry.get(key).orElseThrow();
        });
    }
}
