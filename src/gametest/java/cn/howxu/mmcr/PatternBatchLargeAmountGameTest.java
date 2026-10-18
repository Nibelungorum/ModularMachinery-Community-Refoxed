package cn.howxu.mmcr;

import appeng.api.config.Actionable;
import appeng.api.stacks.GenericStack;
import appeng.crafting.execution.CraftingCpuLogic;
import cn.howxu.mmcr.api.recipe.ParallelTier;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.pattern.PatternBatchDetails;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;

import java.util.Map;

import static cn.howxu.mmcr.PatternBatchNativeCpuFixture.DOUBLE_INT_MAX;
import static cn.howxu.mmcr.PatternBatchNativeCpuFixture.INT_MAX;

/** Asynchronous native plans, CPU pushes, real factory ticks and exact long settlement.
 * @author howxu <dev@howxu.cn>
 */
public final class PatternBatchLargeAmountGameTest {
    public void nativeCpuDispatchesIntMaxParallelismAsOneBatch(GameTestHelper helper) {
        complete(helper, "int_max", false, 1);
    }
    public void hugeBatchSettlesOutputsAboveIntRangeWithoutTruncation(GameTestHelper helper) {
        complete(helper, "double_item", false, 2);
    }
    public void hugeFluidBatchSettlesOutputsAboveIntRangeWithoutTruncation(GameTestHelper helper) {
        complete(helper, "double_fluid", true, 2);
    }

    private static void complete(GameTestHelper helper, String name, boolean fluid, int amount) {
        var fixture = huge(helper, name, fluid, amount);
        fixture.calculate().thenExecute(() -> {
            assertSingleHugePlan(helper, fixture);
            fixture.submit();
            helper.assertTrue(fixture.dispatch() == 1 && fixture.provider.attempts == 1
                            && fixture.provider.accepted == 1 && fixture.provider.lastInputs.get(fixture.input) == fixture.total,
                    "One real CPU push carries the entire long batch; attempts=" + fixture.provider.attempts
                            + ", accepted=" + fixture.provider.accepted + ", inputs=" + fixture.provider.lastInputs);
            helper.assertTrue(fixture.cpu().getStored(fixture.input) == 0
                            && fixture.cpu().getWaitingFor(fixture.output) == fixture.total
                            && fixture.cpu().getPendingOutputs(fixture.output) == 0 && fixture.power.charges == 1
                            && Math.abs(fixture.power.charged - (double) fixture.total / fixture.input.getAmountPerOperation()) < 0.00001D,
                    "Native CPU inputs, waiting outputs and AE energy record the whole batch exactly once");
            helper.assertTrue(fixture.stored(fixture.output) == 0, "Push admission never manufactures output synchronously");
        }).thenWaitUntil(() -> fixture.assertOneActiveBatch(INT_MAX))
                .thenWaitUntil(() -> fixture.assertSettled(1)).thenExecute(() -> {
                    assertStoredBalanceRoundTrips(helper, fixture);
                    helper.assertTrue(fixture.provider.attempts == 1, "Exactly one standard push across the real lifecycle");
                    if (amount == 2) helper.assertTrue(fixture.stored(fixture.output) == DOUBLE_INT_MAX,
                            "Actual settled output is 4_294_967_294L without int truncation");
                }).thenSucceed();
    }

    public void hugeBatchFailsAtomicallyWhenOneUnitOrAePowerIsMissing(GameTestHelper helper) {
        refusal(helper, "atomic_item", false);
    }
    public void hugeFluidBatchFailsAtomicallyWhenOneUnitOrAePowerIsMissing(GameTestHelper helper) {
        refusal(helper, "atomic_fluid", true);
    }

    private static void refusal(GameTestHelper helper, String name, boolean fluid) {
        var fixture = huge(helper, name, fluid, 2);
        fixture.calculate().thenExecute(() -> {
            assertSingleHugePlan(helper, fixture);
            fixture.submit();
            helper.assertTrue(fixture.cpu().getInventory().extract(fixture.input, 1, Actionable.MODULATE) == 1,
                    "Temporarily remove one real CPU-held unit after planning");
            helper.assertTrue(fixture.dispatch() == 0, "One missing unit refuses the entire giant batch");
            assertUnpaid(helper, fixture, fixture.total - 1);
            helper.assertTrue(fixture.provider.attempts == 0, "Incomplete inputs never reach the provider");
            fixture.cpu().getInventory().insert(fixture.input, 1, Actionable.MODULATE);
            helper.assertTrue(fixture.cpu().getStored(fixture.input) == fixture.total,
                    "Restore that actual CPU-held unit");
            fixture.power.blocked = true;
            helper.assertTrue(fixture.dispatch() == 0, "Zero instantaneous native AE power refuses dispatch");
            assertUnpaid(helper, fixture, fixture.total);
            helper.assertTrue(fixture.provider.attempts == 0, "Energy simulation refuses before provider payment");
            fixture.power.blocked = false;
            helper.assertTrue(fixture.dispatch() == 1 && fixture.provider.accepted == 1 && fixture.power.charges == 1,
                    "Restoring power dispatches the unchanged batch and charges once");
        }).thenWaitUntil(() -> fixture.assertOneActiveBatch(INT_MAX))
                .thenWaitUntil(() -> fixture.assertSettled(1)).thenSucceed();
    }

    public void cancelUndispatchedHugeBatchReturnsEveryCpuHeldUnit(GameTestHelper helper) {
        var fixture = huge(helper, "cancel", false, 2);
        fixture.calculate().thenExecute(() -> {
            assertSingleHugePlan(helper, fixture);
            fixture.submit();
            fixture.power.blocked = true;
            helper.assertTrue(fixture.dispatch() == 0, "Unpowered batch stays unpaid");
            assertUnpaid(helper, fixture, DOUBLE_INT_MAX);
            fixture.cpu().cancel();
        }).thenWaitUntil(() -> helper.assertTrue(!fixture.cpu().hasJob() && fixture.cpu().getStored(fixture.input) == 0
                        && fixture.stored(fixture.input) == DOUBLE_INT_MAX && fixture.stored(fixture.output) == 0
                        && fixture.provider.accepted == 0 && fixture.provider.attempts == 0
                        && fixture.host.getLogic().getReturnInv().isEmpty()
                        && fixture.controller.runtimeSnapshot().factory().activeLaneCount() == 0,
                "Original native cancellation returns every unpaid unit without machine output"))
                .thenExecute(() -> assertStoredBalanceRoundTrips(helper, fixture)).thenSucceed();
    }

    public void nativeCpuReloadKeepsFullAndRemainderThenSettles(GameTestHelper helper) {
        var fixture = new PatternBatchNativeCpuFixture(helper, "cpu_reload_tail", false, 1, 10_000, ParallelTier.PRO);
        fixture.calculate().thenExecute(() -> {
            helper.assertTrue(fixture.plan.patternTimes().size() == 2, "Actual 10000/256 plan has full and tail identities");
            for (var task : fixture.plan.patternTimes().entrySet()) {
                helper.assertTrue(task.getKey() instanceof PatternBatchDetails, "Both task identities are self-describing");
                long multiplier = ((PatternBatchDetails) task.getKey()).data().multiplier();
                helper.assertTrue(multiplier == 256 && task.getValue() == 39 || multiplier == 16 && task.getValue() == 1,
                        "Native plan is precisely 256 x 39 plus 16 x 1");
            }
            fixture.submit();
            var registries = helper.getLevel().registryAccess();
            var saved = new CompoundTag();
            fixture.cpu().writeToNBT(saved, registries);
            var decoded = new CraftingCpuLogic(fixture.cluster());
            decoded.readFromNBT(saved.copy(), registries);
            helper.assertTrue(decoded.hasJob() && decoded.getStored(fixture.input) == 10_000
                            && decoded.getPendingOutputs(fixture.output) == 10_000,
                    "Native registry-aware task decoding restores all unpaid inputs and both definitions");
            fixture.cpu().readFromNBT(saved.copy(), registries);
            fixture.cpu().readFromNBT(saved.copy(), registries);
            fixture.cpu().setJobSuspended(false);
        }).thenWaitUntil(() -> fixture.assertSettled(40)).thenExecute(() -> {
            helper.assertTrue(fixture.provider.acceptedMultipliers.size() == 2
                            && fixture.provider.acceptedMultipliers.getOrDefault(256L, 0L) == 39
                            && fixture.provider.acceptedMultipliers.getOrDefault(16L, 0L) == 1,
                    "Reloaded real CPU dispatches exactly thirty-nine full batches and one tail");
            assertStoredBalanceRoundTrips(helper, fixture);
        }).thenSucceed();
    }

    public void nativeCpuDispatchesTwoCraftPatternsAndExactTail(GameTestHelper helper) {
        var fixture = new PatternBatchNativeCpuFixture(helper, "two_craft_tail", false, 2, 530L,
                ParallelTier.PRO, PatternBatchNativeCpuFixture.CPU, false, 2L, false);
        fixture.calculate().thenExecute(() -> {
            helper.assertTrue(fixture.plan.patternTimes().size() == 2, "A real k=2 order has full and tail identities");
            for (var task : fixture.plan.patternTimes().entrySet()) {
                helper.assertTrue(task.getKey() instanceof PatternBatchDetails batch
                                && batch.data().recipeCraftsPerPattern() == 2L
                                && (batch.data().multiplier() == 128L && task.getValue() == 2L
                                || batch.data().multiplier() == 9L && task.getValue() == 1L),
                        "265 physical patterns become 128x2 plus 9x1, not 256-parallel physical patterns");
            }
            fixture.submit();
            // AE continues to the next task after removing an exhausted tail even with maxPatterns=1.
            // Scope native provider availability so this observation covers exactly one actual push.
            fixture.provider.holdAfterFirstPush = true;
            try {
                int dispatched = fixture.dispatch();
                helper.assertTrue(dispatched == 1 && fixture.provider.accepted == 1L,
                        "One native push admits the entire first k*m request; dispatched=" + dispatched
                                + ", attempts=" + fixture.provider.attempts + ", accepted=" + fixture.provider.accepted
                                + ", inputs=" + fixture.provider.lastInputs + ", state=" + fixture.controller.runtimeSnapshot());
                fixture.assertOneActiveBatch(fixture.provider.acceptedMultipliers.containsKey(128L) ? 256L : 18L);
            } finally { fixture.provider.holdAfterFirstPush = false; }
            fixture.cpu().setJobSuspended(false);
        }).thenWaitUntil(() -> fixture.assertSettled(3L)).thenExecute(() -> helper.assertTrue(
                fixture.provider.acceptedMultipliers.equals(Map.of(128L, 2L, 9L, 1L)),
                "Real CPU pushes, machine production and native settlement conserve all 530 recipe crafts"))
                .thenSucceed();
    }

    private static PatternBatchNativeCpuFixture huge(GameTestHelper helper, String name, boolean fluid, int amount) {
        return new PatternBatchNativeCpuFixture(helper, name, fluid, amount, INT_MAX, ParallelTier.ULTIMATE);
    }

    static void assertSingleHugePlan(GameTestHelper helper, PatternBatchNativeCpuFixture fixture) {
        helper.assertTrue(fixture.plan.patternTimes().size() == 1, "Actual asynchronous plan has one task identity");
        var task = fixture.plan.patternTimes().entrySet().iterator().next();
        helper.assertTrue(task.getKey() instanceof PatternBatchDetails && task.getValue() == 1,
                "Actual plan contains one batch execution, not 2.1G standard executions");
        var batch = (PatternBatchDetails) task.getKey();
        helper.assertTrue(batch.data().multiplier() == INT_MAX && batch.data().recipeCraftsPerPattern() == 1
                        && batch.data().recipeId().equals(fixture.recipeId)
                        && batch.getOutputs().stream().mapToLong(GenericStack::amount).sum() == fixture.total,
                "Task binds this recipe at exact 2_147_483_647 x 1 with its complete promised output");
    }

    private static void assertUnpaid(GameTestHelper helper, PatternBatchNativeCpuFixture fixture, long held) {
        helper.assertTrue(fixture.cpu().getStored(fixture.input) == held
                        && fixture.cpu().getPendingOutputs(fixture.output) == fixture.total
                        && fixture.cpu().getWaitingFor(fixture.output) == 0 && fixture.provider.accepted == 0
                        && fixture.power.charges == 0 && fixture.host.getLogic().getReturnInv().isEmpty()
                        && fixture.controller.runtimeSnapshot().factory().activeLaneCount() == 0,
                "Refusal preserves CPU material, tasks, energy and machine ownership");
    }

    private static void assertStoredBalanceRoundTrips(GameTestHelper helper, PatternBatchNativeCpuFixture fixture) {
        var saved = new CompoundTag();
        fixture.storage.writeToChildTag(saved, "balances", helper.getLevel().registryAccess());
        var restored = new PatternBatchNativeCpuFixture.LongStorage();
        restored.readFromChildTag(saved, "balances", helper.getLevel().registryAccess());
        helper.assertTrue(restored.getAvailableStacks().get(fixture.input) == fixture.stored(fixture.input)
                        && restored.getAvailableStacks().get(fixture.output) == fixture.stored(fixture.output),
                "Native AE key/count serialization preserves actually settled or cancelled long balances");
    }
}
