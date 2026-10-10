package cn.howxu.mmcr;

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
import cn.howxu.mmcr.internal.block.MachineControllerBlock;
import cn.howxu.mmcr.internal.port.EnergyHatchSize;
import cn.howxu.mmcr.internal.port.FluidHatchSize;
import cn.howxu.mmcr.internal.port.ItemBusSize;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.registry.PortKinds;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

/** Synchronously exercises compiled structure constraints against real registered ports.
 * @author howxu <dev@howxu.cn>
 */
public final class PortConstraintGameTest {
    private static final BlockPos LEFT = new BlockPos(0, 1, 1);
    private static final BlockPos RIGHT = new BlockPos(2, 1, 1);
    private static final BlockPos EXTRA = new BlockPos(1, 1, 2);

    public void chemicalStructureChecksCountsAndMinimumTiers(GameTestHelper helper) {
        var fixture = fixture(helper, "chemical", PortRequirementSpec.builder()
                        .range("chemical_input_hatch", 1, 1).range("chemical_output_hatch", 1, 1).build(),
                PortTierRequirementSpec.builder().minChemicalInput(PortTiers.ChemicalTier.ADVANCED)
                        .minChemicalOutput(PortTiers.ChemicalTier.ELITE).build());
        port(helper, LEFT, "chemical_input_hatch_basic");
        port(helper, RIGHT, "chemical_output_hatch_elite");
        helper.assertFalse(fixture.form(), "Basic input cannot satisfy advanced minimum");
        helper.assertValueEqual("chemical_input_hatch>=advanced", fixture.failure().portId(), "Input minimum failure identifies its grade");

        port(helper, LEFT, "chemical_input_hatch_advanced");
        port(helper, RIGHT, "chemical_output_hatch_basic");
        helper.assertFalse(fixture.form(), "Basic output cannot satisfy elite minimum");
        helper.assertValueEqual("chemical_output_hatch>=elite", fixture.failure().portId(), "Output minimum failure identifies its grade");
        port(helper, RIGHT, "chemical_output_hatch_elite");
        helper.assertTrue(fixture.form(), "Exact grades form the compiled structure");
        fixture.invalidate();
        port(helper, LEFT, "chemical_input_hatch_ultimate");
        port(helper, RIGHT, "chemical_output_hatch_ultimate");
        helper.assertTrue(fixture.form(), "Higher grades satisfy both minimums");
        fixture.invalidate();

        port(helper, EXTRA, "chemical_input_hatch_basic");
        helper.assertFalse(fixture.form(), "Different chemical grades share a family count maximum");
        helper.assertValueEqual(PortRequirementSpec.FailureReason.TOO_MANY, fixture.failure().reason(), "Extra input exceeds maximum");
        casing(helper, EXTRA);
        casing(helper, LEFT);
        helper.assertFalse(fixture.form(), "Missing input fails its count minimum");
        helper.assertValueEqual("chemical_input_hatch", fixture.failure().portId(), "Missing input reports its family alias");
        helper.succeed();
    }

    public void singleTierStructureChecksCountsAndDirection(GameTestHelper helper, String inputId, String outputId, PortTiers tiers) {
        var spec = PortTierRequirementSpec.from(tiers);
        var fixture = fixture(helper, inputId + "_presence", PortRequirementSpec.none(), spec);
        helper.assertFalse(fixture.form(), "Single-tier requirements reject missing interfaces");
        port(helper, LEFT, outputId);
        helper.assertFalse(fixture.form(), "An output cannot satisfy an input presence requirement");
        port(helper, LEFT, inputId);
        port(helper, RIGHT, inputId);
        helper.assertFalse(fixture.form(), "An input cannot satisfy an output presence requirement");
        helper.assertValueEqual(spec.requirements().get(1).id(), fixture.failure().portId(), "Direction failure identifies the missing output");
        port(helper, RIGHT, "energy_output_hatch_ultimate");
        helper.assertFalse(fixture.form(), "Another resource family cannot satisfy output presence");
        port(helper, RIGHT, outputId);
        helper.assertTrue(fixture.form(), "Both matching directions form the structure");
        fixture.invalidate();

        String inputAlias = spec.requirements().get(0).id().split(">=", 2)[0];
        String outputAlias = spec.requirements().get(1).id().split(">=", 2)[0];
        fixture = fixture(helper, inputId + "_counts", PortRequirementSpec.builder()
                .range(inputAlias, 1, 1).range(outputAlias, 1, 1).build(), spec);
        port(helper, LEFT, inputId);
        port(helper, RIGHT, inputId);
        helper.assertFalse(fixture.form(), "Wrong-direction interfaces cannot supply the output count");
        port(helper, RIGHT, outputId);
        helper.assertTrue(fixture.form(), "Each direction contributes exactly one count");
        fixture.invalidate();
        port(helper, EXTRA, inputId);
        helper.assertFalse(fixture.form(), "An extra input exceeds the family maximum");
        helper.assertValueEqual(inputAlias, fixture.failure().portId(), "Input count uses the family alias");
        helper.assertValueEqual(PortRequirementSpec.FailureReason.TOO_MANY, fixture.failure().reason(), "Extra input reports the maximum");
        port(helper, EXTRA, outputId);
        helper.assertFalse(fixture.form(), "An extra output exceeds the family maximum");
        helper.assertValueEqual(outputAlias, fixture.failure().portId(), "Output count uses the family alias");
        casing(helper, EXTRA);
        casing(helper, LEFT);
        helper.assertFalse(fixture.form(), "Removing the input fails its minimum count");
        helper.assertValueEqual(PortRequirementSpec.FailureReason.MISSING, fixture.failure().reason(), "Missing input reports a minimum");
        helper.succeed();
    }

    public void radioactiveAndHeatFamiliesStaySeparate(GameTestHelper helper) {
        var fixture = fixture(helper, "radioactive_heat", PortRequirementSpec.none(),
                PortTierRequirementSpec.builder().anyRadioactiveChemicalInput().anyHeatOutput().build());
        port(helper, LEFT, "chemical_input_hatch_ultimate");
        port(helper, RIGHT, "heat_output_hatch");
        helper.assertFalse(fixture.form(), "Normal chemical input cannot satisfy a radioactive family requirement");
        port(helper, LEFT, "radioactive_chemical_output_hatch");
        helper.assertFalse(fixture.form(), "Radioactive output cannot satisfy radioactive input");
        port(helper, LEFT, "radioactive_chemical_input_hatch");
        port(helper, RIGHT, "heat_input_hatch");
        helper.assertFalse(fixture.form(), "Heat input cannot satisfy heat output");
        helper.assertValueEqual("heat_output_hatch", fixture.failure().portId(), "Presence failure has no fabricated grade");
        port(helper, RIGHT, "heat_output_hatch");
        helper.assertTrue(fixture.form(), "Matching radioactive and heat directions form");
        fixture.invalidate();
        helper.succeed();
    }

    public void multiResourceInterfacesContributeEachFamilyOnce(GameTestHelper helper) {
        boolean chemicals = ModList.get().isLoaded("appmek");
        var kinds = PortKinds.all().stream().filter(kind -> kind.id().startsWith("ae2_me_")
                || kind.id().startsWith("eae_me_") || kind.id().startsWith("eaep_me_")).toList();
        helper.assertTrue(!kinds.isEmpty(), "AE2 test exercises registered interfaces");
        for (var kind : kinds) {
            boolean input = kind.id().contains("_input_") || kind.id().contains("_pattern_");
            boolean output = kind.id().contains("_output_") || kind.id().contains("_pattern_");
            var counts = PortRequirementSpec.builder();
            var tiers = PortTiers.builder();
            if (input) resourceConstraints(counts, tiers, IOType.INPUT, chemicals);
            if (output) resourceConstraints(counts, tiers, IOType.OUTPUT, chemicals);
            var fixture = fixture(helper, kind.id(), counts.build(), PortTierRequirementSpec.from(tiers.build()));
            helper.assertFalse(fixture.form(), "Missing multi-family interface fails its constraints: " + kind.id());
            port(helper, LEFT, kind.id());
            helper.assertTrue(fixture.form(), "One physical interface satisfies all expected family minimums and exact counts: " + kind.id());
            fixture.invalidate();
            port(helper, EXTRA, kind.id());
            helper.assertFalse(fixture.form(), "Two physical interfaces exceed exact family maxima: " + kind.id());
            helper.assertValueEqual(PortRequirementSpec.FailureReason.TOO_MANY, fixture.failure().reason(), "Multi-family excess reports a maximum");
            casing(helper, EXTRA);
            port(helper, LEFT, input && !output ? "ae2_me_output_interface" : "ae2_me_input_interface");
            helper.assertFalse(fixture.form(), "Opposite or incomplete directions cannot supply every required family: " + kind.id());
        }
        helper.succeed();
    }

    public void sharedChemicalBindingKeepsIndependentCountAliases(GameTestHelper helper) {
        var kinds = PortKinds.all().stream().filter(kind -> kind.id().startsWith("ae2_me_")
                        || kind.id().startsWith("eae_me_") || kind.id().startsWith("eaep_me_"))
                .filter(kind -> kind.id().contains("_input_") || kind.id().contains("_pattern_")).toList();
        helper.assertTrue(!kinds.isEmpty(), "AppMek test exercises real registered multi-family inputs");
        for (var kind : kinds) {
            var fixture = fixture(helper, kind.id() + "_radioactive_count", PortRequirementSpec.builder()
                            .range("chemical_input_hatch", 1, 2).range("radioactive_chemical_input_hatch", 1, 1).build(),
                    PortTierRequirementSpec.builder().minChemicalInput(PortTiers.ChemicalTier.ULTIMATE)
                            .anyRadioactiveChemicalInput().build());
            port(helper, LEFT, kind.id());
            helper.assertTrue(fixture.form(), "Shared chemical binding contributes to both independent count aliases: " + kind.id());
            fixture.invalidate();
            port(helper, EXTRA, kind.id());
            helper.assertFalse(fixture.form(), "Radioactive maximum is enforced while the ordinary chemical count remains allowed");
            helper.assertValueEqual("radioactive_chemical_input_hatch", fixture.failure().portId(), "Radioactive alias keeps its own maximum");
            helper.assertValueEqual(PortRequirementSpec.FailureReason.TOO_MANY, fixture.failure().reason(), "Radioactive excess reports maximum");
        }
        helper.succeed();
    }

    public void highTierEnergyPortsCheckCountsAndDirection(GameTestHelper helper, String inputId, String outputId) {
        var fixture = fixture(helper, inputId, PortRequirementSpec.builder()
                        .range("energy_input_hatch", 1, 1).range("energy_output_hatch", 1, 1).build(),
                PortTierRequirementSpec.builder().minEnergyInput(EnergyHatchSize.ULTIMATE)
                        .minEnergyOutput(EnergyHatchSize.ULTIMATE).build());
        port(helper, LEFT, inputId);
        port(helper, RIGHT, outputId);
        helper.assertTrue(fixture.form(), "High-tier energy interfaces satisfy ultimate minimums");
        fixture.invalidate();
        port(helper, LEFT, "energy_input_hatch_tiny");
        helper.assertFalse(fixture.form(), "A lower ordinary hatch fails the same compiled minimum");
        helper.assertValueEqual("energy_input_hatch>=ultimate", fixture.failure().portId(), "Energy grade failure identifies its minimum");
        port(helper, LEFT, inputId);
        port(helper, EXTRA, inputId);
        helper.assertFalse(fixture.form(), "Extra high-tier interface exceeds the common energy family count");
        helper.assertValueEqual(PortRequirementSpec.FailureReason.TOO_MANY, fixture.failure().reason(), "Energy excess reports maximum");
        casing(helper, EXTRA);
        port(helper, RIGHT, inputId);
        helper.assertFalse(fixture.form(), "Two inputs cannot replace the required energy output");
        helper.succeed();
    }

    public void combinedAndExtendedPortsKeepIndependentFamilies(GameTestHelper helper) {
        var counts = PortRequirementSpec.builder().range("item_input_bus", 1, 1).range("fluid_input_hatch", 1, 1).build();
        var ordinary = fixture(helper, "ordinary_combined", counts,
                PortTierRequirementSpec.builder().minItemInput(ItemBusSize.HUGE).minFluidInput(FluidHatchSize.VACUUM).build());
        port(helper, LEFT, "combined_input_ultimate");
        helper.assertTrue(ordinary.form(), "Ordinary combined port satisfies independent item and fluid grades and counts");
        ordinary.invalidate();
        var extended = fixture(helper, "extended_combined", counts,
                PortTierRequirementSpec.builder().minItemInput(ItemBusSize.LUDICROUS).minFluidInput(FluidHatchSize.VACUUM).build());
        port(helper, LEFT, "combined_input_ultimate");
        helper.assertFalse(extended.form(), "Ordinary combined ultimate does not inherit the highest item grade");
        for (String id : List.of("extended_combined_input_advanced", "extended_combined_input_reinforced", "extended_combined_input_ultimate")) {
            port(helper, LEFT, id);
            helper.assertTrue(extended.form(), "Extended combined port satisfies both highest minima once: " + id);
            extended.invalidate();
            port(helper, EXTRA, id);
            helper.assertFalse(extended.form(), "Extra combined interface exceeds each shared family maximum");
            helper.assertValueEqual(PortRequirementSpec.FailureReason.TOO_MANY, extended.failure().reason(), "Combined excess reports maximum");
            casing(helper, EXTRA);
        }
        port(helper, LEFT, "extended_combined_output_ultimate");
        helper.assertFalse(extended.form(), "Extended output cannot supply either input family");
        helper.succeed();
    }

    private static void resourceConstraints(PortRequirementSpec.Builder counts, PortTiers.Builder tiers, IOType io, boolean chemicals) {
        String direction = io.getSerializedName();
        counts.range("item_" + direction + "_bus", 1, 1).range("fluid_" + direction + "_hatch", 1, 1);
        if (io == IOType.INPUT) tiers.minItemInput(PortTiers.ItemTier.LUDICROUS).minFluidInput(PortTiers.FluidTier.VACUUM);
        else tiers.minItemOutput(PortTiers.ItemTier.LUDICROUS).minFluidOutput(PortTiers.FluidTier.VACUUM);
        if (chemicals) {
            counts.range("chemical_" + direction + "_hatch", 1, 1).range("radioactive_chemical_" + direction + "_hatch", 1, 1);
            if (io == IOType.INPUT) tiers.minChemicalInput(PortTiers.ChemicalTier.ULTIMATE).anyRadioactiveChemicalInput();
            else tiers.minChemicalOutput(PortTiers.ChemicalTier.ULTIMATE).anyRadioactiveChemicalOutput();
        }
    }

    private static Fixture fixture(GameTestHelper helper, String name, PortRequirementSpec counts, PortTierRequirementSpec tiers) {
        ResourceLocation id = MMCR.id("port_constraints_" + name);
        var machine = new DynamicMachine(id, "machine.mmcr_test." + id.getPath(),
                new BlockArray(Map.of(new BlockPos(1, 0, 0), new BlockPredicate.Any(),
                        new BlockPos(-1, 0, 0), new BlockPredicate.Any(), new BlockPos(0, 0, 1), new BlockPredicate.Any())),
                MachineControllerSpec.defaultsFor(id), MachineAppearanceSpec.defaults(), counts, tiers,
                List.of(), Map.of(), 1, false, false, 1);
        if (!MachineRegistry.containsStatic(id)) MachineRegistry.register(machine);
        helper.assertTrue(!MachineRegistry.getCompiledStages(id).isEmpty(), "Fixture validates a compiled stage");
        casing(helper, LEFT);
        casing(helper, RIGHT);
        casing(helper, EXTRA);
        BlockPos controllerPos = new BlockPos(1, 1, 1);
        helper.setBlock(controllerPos, ModBlocks.controllerFor(MMCR.id("test_cube")).get().defaultBlockState()
                .setValue(MachineControllerBlock.FACING, Direction.SOUTH));
        MachineControllerBlockEntity controller = helper.getBlockEntity(controllerPos);
        controller.setMachine(machine);
        return new Fixture(controller, machine);
    }

    private static void port(GameTestHelper helper, BlockPos pos, String id) {
        helper.setBlock(pos, ModBlocks.BLOCKS.get(id).get().defaultBlockState());
    }

    private static void casing(GameTestHelper helper, BlockPos pos) { helper.setBlock(pos, ModBlocks.CASING.get().defaultBlockState()); }

    /** Drives formation immediately without assertions on elapsed server ticks.
     * @author howxu <dev@howxu.cn>
     */
    private record Fixture(MachineControllerBlockEntity controller, Machine machine) {
        private boolean form() {
            try {
                Method method = MachineControllerBlockEntity.class.getDeclaredMethod("tryFormMachine", Machine.class, Direction.class);
                method.setAccessible(true);
                return (boolean) method.invoke(controller, machine, Direction.SOUTH);
            } catch (ReflectiveOperationException exception) {
                throw new AssertionError("Unable to validate port-constrained structure", exception);
            }
        }

        private void invalidate() { controller.invalidateFormedStructure(); }

        private PortRequirementSpec.Failure failure() { return controller.currentRuntimeSnapshot().structure().lastFormationFailure(); }
    }
}
