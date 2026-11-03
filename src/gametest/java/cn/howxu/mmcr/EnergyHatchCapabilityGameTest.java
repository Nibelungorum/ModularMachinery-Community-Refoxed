package cn.howxu.mmcr;

import cn.howxu.mmcr.api.capability.async.AsyncCapabilityOperation;
import cn.howxu.mmcr.api.capability.async.AsyncCapabilityRequest;
import cn.howxu.mmcr.api.capability.facet.AsyncPlanningFacet;
import cn.howxu.mmcr.api.capability.storage.LongValueStorage;
import cn.howxu.mmcr.internal.capability.EnergyHatchCapability;
import cn.howxu.mmcr.internal.tile.EnergyHatchBlockEntity;
import cn.howxu.mmcr.internal.event.ModCapabilities;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandlerUtil;
import net.neoforged.neoforge.transfer.energy.InfiniteEnergyHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;

public class EnergyHatchCapabilityGameTest {

    public void bulkAsyncRecipeEnergyCommitsWithoutPerBatchOperations(GameTestHelper helper) {
        for (long parallelism : new long[] {1L, 3L, Integer.MAX_VALUE}) {
            long amount = 1_000_000_000L * parallelism;
            int[] changes = {0};
            LongValueStorage storage = new LongValueStorage(Long.MAX_VALUE, 1_000_000_000L, () -> changes[0]++);
            storage.setAmount(amount);
            changes[0] = 0;
            EnergyHatchCapability capability = new EnergyHatchCapability(storage, IOType.INPUT);
            AsyncPlanningFacet facet = capability.facet(AsyncPlanningFacet.class).orElseThrow();
            var request = new AsyncCapabilityRequest.Scalar(capability.type().id(), parallelism, amount, false);
            var operation = facet.workerPlanner().plan(facet.captureSnapshot(), request).orElseThrow();
            helper.assertTrue(operation instanceof AsyncCapabilityOperation.Scalar scalar
                            && scalar.amount() == amount && scalar.parallelism() == parallelism,
                    "Async planning retains one scalar and its complete parallel transfer budget");
            try (Transaction transaction = Transaction.openRoot()) {
                helper.assertTrue(facet.commit(operation, transaction).success(), "Bulk async native energy commit succeeds");
                helper.assertTrue(storage.amount() == 0L && changes[0] == 0,
                        "The caller owns the transaction and no change notification is sent before commit");
            }
            helper.assertTrue(storage.amount() == amount && changes[0] == 0, "Aborted debit restores all energy silently");
            try (Transaction transaction = Transaction.openRoot()) {
                helper.assertTrue(facet.commit(operation, transaction).success(), "Bulk async native energy commit succeeds");
                transaction.commit();
            }
            helper.assertTrue(storage.amount() == 0L && changes[0] == 1,
                    "Bulk async commit debits the exact amount with one change notification");
        }
        LongValueStorage storage = new LongValueStorage(Long.MAX_VALUE, 1_000_000_000L, null);
        storage.setAmount(3_000_000_000L);
        EnergyHatchCapability capability = new EnergyHatchCapability(storage, IOType.INPUT);
        AsyncPlanningFacet facet = capability.facet(AsyncPlanningFacet.class).orElseThrow();
        try (Transaction transaction = Transaction.openRoot()) {
            helper.assertFalse(facet.commit(new AsyncCapabilityOperation.Scalar(capability.type().id(),
                            3_000_000_000L, false), transaction).success(),
                    "A legacy one-budget scalar cannot bypass the energy transfer budget");
            transaction.commit();
        }
        helper.assertTrue(storage.amount() == 3_000_000_000L, "Rejected scalar leaves energy unchanged");
        helper.succeed();
    }

    public void energyHatchStoresFE(GameTestHelper helper) {
        BlockPos inputPos = new BlockPos(0, 1, 0);
        BlockPos outputPos = new BlockPos(0, 2, 0);
        helper.setBlock(inputPos, ModBlocks.BLOCKS.get("energy_input_hatch").get().defaultBlockState());
        helper.setBlock(outputPos, ModBlocks.BLOCKS.get("energy_output_hatch").get().defaultBlockState());

        BlockPos inputWorldPos = helper.absolutePos(inputPos);
        BlockPos outputWorldPos = helper.absolutePos(outputPos);
        BlockEntity inputBe = helper.getLevel().getBlockEntity(inputWorldPos);
        BlockEntity outputBe = helper.getLevel().getBlockEntity(outputWorldPos);

        EnergyHatchBlockEntity inputHatch = helper.getBlockEntity(inputPos, EnergyHatchBlockEntity.class);
        EnergyHatchBlockEntity outputHatch = helper.getBlockEntity(outputPos, EnergyHatchBlockEntity.class);

        var inputCapability = inputHatch.capabilitySnapshot().capabilities().getFirst();
        var outputCapability = outputHatch.capabilitySnapshot().capabilities().getFirst();
        helper.assertTrue(inputCapability.directions().supports(IOType.INPUT), "Input capability accepts INPUT");
        helper.assertTrue(outputCapability.directions().supports(IOType.OUTPUT), "Output capability accepts OUTPUT");

        EnergyHandler input = ModCapabilities.ENERGY_BLOCK.getCapability(
                helper.getLevel(), inputWorldPos, helper.getLevel().getBlockState(inputWorldPos), inputBe, Direction.UP);
        EnergyHandler output = ModCapabilities.ENERGY_BLOCK.getCapability(
                helper.getLevel(), outputWorldPos, helper.getLevel().getBlockState(outputWorldPos), outputBe, Direction.UP);

        helper.assertTrue(input != null, "Input energy capability is present");
        helper.assertTrue(output != null, "Output energy capability is present");

        int movedToInput = EnergyHandlerUtil.move(InfiniteEnergyHandler.INSTANCE, input, 500, null);
        helper.assertTrue(movedToInput == 500, "Input energy capability receives from transfer energy source");
        helper.assertTrue(inputHatch.getEnergyHandler(null).getAmountAsLong() == 500, "Input hatch stores transferred energy");

        try (Transaction tx = Transaction.openRoot()) {
            int extracted = input.extract(200, tx);
            helper.assertTrue(extracted == 0, "Input energy capability rejects extracting");
            tx.commit();
        }

        var outputStorage = outputHatch.energyStorage();
        while (outputStorage.forceInsert(10000, false) > 0) {}

        try (Transaction tx = Transaction.openRoot()) {
            int inserted = output.insert(200, tx);
            int extracted = output.extract(700, tx);
            helper.assertTrue(inserted == 0, "Output energy capability rejects receiving");
            helper.assertTrue(extracted > 0, "Output energy capability extracts stored energy");
            tx.commit();
        }

        helper.succeed();
    }
}
