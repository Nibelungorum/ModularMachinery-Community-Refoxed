package cn.howxu.mmcr;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.execution.CraftingCpuHelper;
import cn.howxu.mmcr.api.recipe.ParallelTier;
import cn.howxu.mmcr.api.recipe.RecipeRegistry;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.pattern.PatternBatchDetails;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Items;

import java.util.Locale;
import java.util.LinkedHashMap;

import static cn.howxu.mmcr.PatternBatchNativeCpuFixture.INT_MAX;

/** Real paid owners are recreated together; only level ticks advance production.
 * Cross-chunk cases require mmcr:pattern_batch_cross_chunk, rotation NONE.
 * @author howxu <dev@howxu.cn>
 */
public final class PatternBatchPersistenceGameTest {
    private static final BlockPos REMOTE_CPU = new BlockPos(20, 1, 3);
    private static final AEItemKey BLOCKER = AEItemKey.of(Items.COBBLESTONE);

    public void reloadCpuMachineAndInterfaceDuringPaidProcessing(GameTestHelper helper) { reload(helper, SavePoint.PROCESSING); }
    public void reloadCpuMachineAndInterfaceAfterFinalTickPaid(GameTestHelper helper) { reload(helper, SavePoint.FINAL_TICK_PAID); }
    public void reloadCpuMachineAndInterfaceWithOutputWaitingForNetwork(GameTestHelper helper) { reload(helper, SavePoint.OUTPUT_WAITING); }

    public void removedRecipeRestoresNativeClusterAndCancelsExactInputs(GameTestHelper helper) {
        var fixture = new PatternBatchNativeCpuFixture(helper, "removed_recipe", false, 2, 530L,
                ParallelTier.PRO, PatternBatchNativeCpuFixture.CPU, true, 2L, true);
        fixture.calculate().thenExecute(() -> {
            fixture.submit();
            var recipes = new LinkedHashMap<>(RecipeRegistry.dynamicSnapshot());
            recipes.remove(fixture.recipeId);
            RecipeRegistry.replaceDynamic(recipes);
            helper.assertTrue(RecipeRegistry.getRecipe(fixture.recipeId) == null, "The live recipe is genuinely removed");
            fixture.rebuildPaidOwners();
        }).thenWaitUntil(() -> helper.assertTrue(fixture.host.getMainNode().getNode() != null
                        && fixture.energy.getMainNode().getNode() != null
                        && fixture.cpuBlock().getMainNode().getNode() != null && fixture.cluster() != null,
                "Actual native core previousState decoding reforms the cluster without the removed recipe"))
                .thenExecute(() -> {
                    helper.assertTrue(fixture.cpu().hasJob()
                                    && PatternBatchCodecGameTest.taskCounts(fixture.cpu()).equals(fixture.plan.patternTimes())
                                    && fixture.cpu().getStored(fixture.input) == fixture.total
                                    && fixture.cpu().getPendingOutputs(fixture.output) == fixture.total,
                            "Cluster reconstruction retains every full/tail definition, task and CPU-held material");
                    fixture.cpu().setJobSuspended(true);
                    fixture.connectNetwork(true);
                }).thenWaitUntil(() -> helper.assertTrue(fixture.cluster().isActive() && fixture.host.getMainNode().isActive(),
                        "The restored native CPU and source are online"))
                .thenExecute(() -> {
                    fixture.power.delegate = fixture.grid().getEnergyService();
                    for (var pattern : fixture.plan.patternTimes().keySet()) {
                        var holders = CraftingCpuHelper.extractPatternInputs(pattern, fixture.cpu().getInventory(), helper.getLevel(),
                                new KeyCounter(), new KeyCounter());
                        helper.assertTrue(holders != null, "Restored definitions still extract their exact native inputs");
                        var before = new KeyCounter();
                        for (var holder : holders) before.addAll(holder);
                        helper.assertFalse(fixture.host.craftingMachine().pushPattern(pattern, holders, null),
                                "Missing live binding refuses the saved batch instead of unscaling it");
                        var after = new KeyCounter();
                        for (var holder : holders) after.addAll(holder);
                        helper.assertTrue(after.get(fixture.input) == before.get(fixture.input),
                                "Live refusal leaves every extracted CPU holder intact");
                        CraftingCpuHelper.reinjectPatternInputs(fixture.cpu().getInventory(), holders);
                    }
                    helper.assertTrue(fixture.dispatch() == 0 && fixture.cpu().getStored(fixture.input) == fixture.total
                                    && fixture.cpu().getWaitingFor(fixture.output) == 0 && fixture.provider.accepted == 0
                                    && fixture.power.charges == 0
                                    && PatternBatchCodecGameTest.taskCounts(fixture.cpu()).equals(fixture.plan.patternTimes()),
                            "Native dispatch refuses without consuming tasks, inputs, AE energy or machine ownership");
                    fixture.cpu().cancel();
                }).thenWaitUntil(() -> helper.assertTrue(!fixture.cpu().hasJob()
                                && fixture.cpu().getStored(fixture.input) == 0 && fixture.stored(fixture.input) == fixture.total
                                && fixture.stored(fixture.output) == 0 && fixture.host.getLogic().getReturnInv().isEmpty(),
                        "Native cancellation refunds every unpaid input after real cluster reconstruction"))
                .thenSucceed();
    }

    private static void reload(GameTestHelper helper, SavePoint point) {
        var fixture = new PatternBatchNativeCpuFixture(helper, "reload_" + point.name().toLowerCase(Locale.ROOT),
                false, 2, INT_MAX, ParallelTier.ULTIMATE, REMOTE_CPU, true);
        long[] paid = new long[1];
        long[] energy = new long[1];
        var sequence = fixture.calculate().thenExecute(() -> {
            PatternBatchLargeAmountGameTest.assertSingleHugePlan(helper, fixture);
            fixture.submit();
            fixture.addSpareMaterial();
            helper.assertTrue(fixture.dispatch() == 1 && fixture.provider.accepted == 1,
                    "Native CPU pays and starts the actual giant batch before saving");
            helper.assertTrue(fixture.energyHatch().energyStorage().getAmountAsLong() == fixture.feBudget,
                    "Admission checks the complete batch's FE without spending any of the forty-tick budget");
            if (point == SavePoint.FINAL_TICK_PAID) fixture.fillReturnSlots(BLOCKER);
            if (point == SavePoint.OUTPUT_WAITING) fixture.disconnectHost();
        }).thenWaitUntil(() -> {
            var lane = patternLane(fixture);
            var payment = lane.getCompound("execution_commit");
            var recipe = lane.getCompound("recipe");
            if (point == SavePoint.PROCESSING) {
                helper.assertTrue(lane.getBoolean("active") && recipe.getInt("tick") > 0
                                && recipe.getInt("tick") < 39 && payment.getLong("paid_attempts") > 0,
                        "Real ticks reach actual paid processing, not synthetic execution state");
            } else if (point == SavePoint.FINAL_TICK_PAID) {
                helper.assertTrue(lane.getBoolean("active") && recipe.getBoolean("finishPending")
                                && payment.getBoolean("final_tick_paid") && !payment.getString("phase").equals("TERMINAL")
                                && fixture.returnAmount(fixture.output) == 0 && fixture.stored(fixture.output) == 0,
                        "Final tick is paid while unrelated full return slots prevent output commit");
            } else {
                helper.assertTrue(!lane.getBoolean("active") && fixture.returnAmount(fixture.output) == fixture.total
                                && fixture.cpu().getWaitingFor(fixture.output) == fixture.total && fixture.stored(fixture.output) == 0,
                        "Actual committed output is local while the disconnected native CPU still awaits it");
            }
        }).thenExecute(() -> {
            var lane = patternLane(fixture);
            paid[0] = lane.getCompound("execution_commit").getLong("paid_attempts");
            energy[0] = fixture.energyHatch().energyStorage().getAmountAsLong();
            helper.assertTrue(fixture.cpu().hasJob() && fixture.cpu().getStored(fixture.input) == 0
                            && fixture.cpu().getWaitingFor(fixture.output) == fixture.total && fixture.stored(fixture.input) == fixture.total,
                    "CPU, machine, interface and spare material have distinct real owners at save time");
            fixture.rebuildPaidOwners();
            // Pending topology-dependent runtime NBT is also observable through the real BE save path.
            var restored = patternLane(fixture);
            helper.assertTrue((point == SavePoint.OUTPUT_WAITING ? !restored.getBoolean("active")
                            : restored.getCompound("execution_commit").getLong("paid_attempts") == paid[0])
                            && fixture.energyHatch().energyStorage().getAmountAsLong() == energy[0]
                            && restored.getBoolean("requires_pattern_request"),
                    "New controller and FE owner retain the exact payment ledger, FE and request-only source restriction");
            if (point == SavePoint.FINAL_TICK_PAID) helper.assertTrue(
                    restored.getCompound("execution_commit").getBoolean("final_tick_paid")
                            && restored.getCompound("recipe").getBoolean("finishPending"),
                    "The restored native runtime retains final-tick payment and pending output before any new tick");
            if (point == SavePoint.OUTPUT_WAITING) helper.assertTrue(fixture.returnAmount(fixture.output) == fixture.total,
                    "The new interface, not fabricated CPU insertion, owns the complete output");
        }).thenWaitUntil(() -> helper.assertTrue(fixture.host.getMainNode().getNode() != null
                        && fixture.energy.getMainNode().getNode() != null && fixture.cpuBlock().getMainNode().getNode() != null
                        && fixture.cluster() != null, "Recreated native nodes initialize and the CPU cluster reforms"))
                .thenExecute(() -> {
                    helper.assertTrue(fixture.cpu().hasJob() && fixture.cpu().getWaitingFor(fixture.output) == fixture.total
                                    && fixture.cpu().getStored(fixture.input) == 0,
                            "Actual native core NBT restores the waiting job before owners reconnect");
                    fixture.connectNetwork(point != SavePoint.OUTPUT_WAITING);
                }).thenWaitUntil(() -> helper.assertTrue(fixture.cluster().isActive()
                                && fixture.controller.structureSnapshot().formed(), "Recreated CPU and machine become available"));
        if (point == SavePoint.FINAL_TICK_PAID) {
            sequence.thenExecuteFor(5, () -> {
                var lane = patternLane(fixture);
                helper.assertTrue(lane.getCompound("execution_commit").getBoolean("final_tick_paid")
                                && lane.getCompound("recipe").getBoolean("finishPending")
                                && lane.getCompound("execution_commit").getLong("paid_attempts") == paid[0]
                                && fixture.energyHatch().energyStorage().getAmountAsLong() == energy[0]
                                && fixture.returnAmount(fixture.output) == 0,
                        "Repeated native load and blocked finish retries never repay the last tick");
            }).thenExecute(() -> fixture.clearReturnBlocker(BLOCKER));
        }
        if (point == SavePoint.OUTPUT_WAITING) {
            sequence.thenExecuteFor(5, () -> helper.assertTrue(fixture.returnAmount(fixture.output) == fixture.total
                            && fixture.stored(fixture.output) == 0 && fixture.energyHatch().energyStorage().getAmountAsLong() == energy[0],
                    "Rebuilt disconnected backlog stays single-owned without production or payment replay"))
                    .thenExecute(fixture::reconnectHost);
        }
        sequence.thenWaitUntil(() -> fixture.assertSettled(1)).thenExecute(() -> {
            helper.assertTrue(fixture.energyHatch().energyStorage().getAmountAsLong() == 0
                            && fixture.provider.accepted == 1 && fixture.power.charges == 1,
                    "Only forty real ticks spend the original exact FE budget; only one CPU push is paid");
            fixture.energyHatch().energyStorage().setAmount(fixture.feBudget);
        }).thenExecuteFor(85, () -> {
            fixture.assertSettled(1);
            helper.assertTrue(fixture.energyHatch().energyStorage().getAmountAsLong() == fixture.feBudget,
                    "Spare network inputs and restored FE cannot autonomously restart a remembered external order");
        }).thenSucceed();
    }

    public void hugeBatchRejectsMissingMachineFeWithoutTakingCpuInputs(GameTestHelper helper) { blockedAdmission(helper, true); }
    public void hugeBatchRejectsFullOutputWithoutTakingCpuInputs(GameTestHelper helper) { blockedAdmission(helper, false); }

    private static void blockedAdmission(GameTestHelper helper, boolean missingFe) {
        var fixture = new PatternBatchNativeCpuFixture(helper, missingFe ? "no_machine_fe" : "full_return",
                false, 2, INT_MAX, ParallelTier.ULTIMATE, PatternBatchNativeCpuFixture.CPU, true);
        fixture.calculate().thenExecute(() -> {
            PatternBatchLargeAmountGameTest.assertSingleHugePlan(helper, fixture);
            fixture.submit();
            if (missingFe) fixture.energyHatch().energyStorage().setAmount(0);
            else fixture.fillReturnSlots(BLOCKER);
            long fe = fixture.energyHatch().energyStorage().getAmountAsLong();
            helper.assertTrue(fixture.dispatch() == 0 && fixture.cpu().getStored(fixture.input) == fixture.total
                            && fixture.cpu().getWaitingFor(fixture.output) == 0 && fixture.cpu().getPendingOutputs(fixture.output) == fixture.total
                            && fixture.provider.attempts == 1 && fixture.provider.accepted == 0 && fixture.power.charges == 0
                            && fixture.energyHatch().energyStorage().getAmountAsLong() == fe
                            && fixture.controller.runtimeSnapshot().factory().activeLaneCount() == 0,
                    "Unavailable machine FE or output space refuses before paying any resource owner");
            helper.assertTrue(fixture.returnAmount(fixture.output) == 0 && (missingFe
                            ? fixture.host.getLogic().getReturnInv().isEmpty()
                            : fixture.returnAmount(BLOCKER) == fixture.host.getLogic().getReturnInv().size()),
                    "Refusal neither manufactures output nor spends unrelated blockers");
            if (missingFe) fixture.energyHatch().energyStorage().setAmount(fixture.feBudget);
            else fixture.clearReturnBlocker(BLOCKER);
            helper.assertTrue(fixture.dispatch() == 1 && fixture.provider.accepted == 1 && fixture.power.charges == 1,
                    "Restoring the blocked resource admits the unchanged entire batch once");
            helper.assertTrue(fixture.energyHatch().energyStorage().getAmountAsLong() == fixture.feBudget,
                    "Successful retry checks machine FE without charging a startup fee");
        }).thenWaitUntil(() -> fixture.assertSettled(1)).thenExecute(() -> helper.assertTrue(
                fixture.energyHatch().energyStorage().getAmountAsLong() == 0,
                "No FE was lost to refusal or duplicate start; the original exact budget completes production")).thenSucceed();
    }

    public void overflowingBatchDefinitionRejectsBeforeAnyCpuPayment(GameTestHelper helper) {
        var fixture = new PatternBatchNativeCpuFixture(helper, "checked_overflow", false, 2, INT_MAX, ParallelTier.ULTIMATE);
        fixture.calculate().thenExecute(() -> {
            PatternBatchLargeAmountGameTest.assertSingleHugePlan(helper, fixture);
            fixture.submit();
            var batch = (PatternBatchDetails) fixture.plan.patternTimes().keySet().iterator().next();
            boolean rejected = false;
            try { batch.scaled(Long.MAX_VALUE / 2 + 1); }
            catch (ArithmeticException exception) { rejected = true; }
            helper.assertTrue(rejected && fixture.cpu().getStored(fixture.input) == fixture.total
                            && fixture.cpu().getWaitingFor(fixture.output) == 0 && fixture.cpu().getPendingOutputs(fixture.output) == fixture.total
                            && fixture.provider.attempts == 0 && fixture.power.charges == 0
                            && fixture.host.getLogic().getReturnInv().isEmpty()
                            && fixture.controller.runtimeSnapshot().factory().activeLaneCount() == 0,
                    "Checked two-unit definition overflow refuses before reservation, dispatch or payment");
            helper.assertTrue(fixture.dispatch() == 1, "Valid original native plan remains executable after overflow refusal");
        }).thenWaitUntil(() -> fixture.assertSettled(1)).thenSucceed();
    }

    private static CompoundTag patternLane(PatternBatchNativeCpuFixture fixture) {
        return fixture.savedLanes().stream().filter(lane -> lane.getBoolean("requires_pattern_request"))
                .findFirst().orElseThrow(() -> new GameTestAssertException("Expected a persisted pattern-source machine lane"));
    }

    /** @author howxu <dev@howxu.cn> */
    private enum SavePoint { PROCESSING, FINAL_TICK_PAID, OUTPUT_WAITING }
}
