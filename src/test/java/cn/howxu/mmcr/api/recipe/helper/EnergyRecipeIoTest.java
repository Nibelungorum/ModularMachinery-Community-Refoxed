package cn.howxu.mmcr.api.recipe.helper;

import cn.howxu.mmcr.internal.storage.LongEnergyStorage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EnergyRecipeIoTest {

    @Test
    void singleInputHatchWithEnoughEnergyConsumesExactlyRequiredFe() {
        LongEnergyStorage hatch = chargedStorage(1_000, 500, 300);

        boolean consumed = EnergyRecipeIo.consumeInputs(List.of(hatch), 120, 1);

        assertThat(consumed).isTrue();
        assertThat(hatch.getAmountAsLong()).isEqualTo(180);
    }

    @Test
    void multipleInputHatchesCanSatisfyOneTickTogether() {
        LongEnergyStorage first = chargedStorage(1_000, 500, 80);
        LongEnergyStorage second = chargedStorage(1_000, 500, 200);

        boolean consumed = EnergyRecipeIo.consumeInputs(List.of(first, second), 150, 1);

        assertThat(consumed).isTrue();
        assertThat(first.getAmountAsLong()).isEqualTo(0);
        assertThat(second.getAmountAsLong()).isEqualTo(130);
    }

    @Test
    void insufficientCombinedEnergyFailsWithoutMutation() {
        LongEnergyStorage first = chargedStorage(1_000, 500, 60);
        LongEnergyStorage second = chargedStorage(1_000, 500, 70);

        boolean consumed = EnergyRecipeIo.consumeInputs(List.of(first, second), 150, 1);

        assertThat(consumed).isFalse();
        assertThat(first.getAmountAsLong()).isEqualTo(60);
        assertThat(second.getAmountAsLong()).isEqualTo(70);
    }

    @Test
    void insufficientTransferLimitFailsWithoutPartialMutation() {
        LongEnergyStorage first = chargedStorage(1_000, 40, 500);
        LongEnergyStorage second = chargedStorage(1_000, 40, 500);

        boolean consumed = EnergyRecipeIo.consumeInputs(List.of(first, second), 100, 1);

        assertThat(consumed).isFalse();
        assertThat(first.getAmountAsLong()).isEqualTo(500);
        assertThat(second.getAmountAsLong()).isEqualTo(500);
    }

    @Test
    void produceOutputsSingleHatchWithCapacityReceivesRequiredFe() {
        LongEnergyStorage hatch = emptyStorage(1_000, 500);

        boolean produced = EnergyRecipeIo.produceOutputs(List.of(hatch), 200, 1);

        assertThat(produced).isTrue();
        assertThat(hatch.getAmountAsLong()).isEqualTo(200);
    }

    @Test
    void produceOutputsMultipleHatchesCanSatisfyOneTickTogether() {
        LongEnergyStorage first = emptyStorage(150, 150);
        LongEnergyStorage second = emptyStorage(150, 150);

        boolean produced = EnergyRecipeIo.produceOutputs(List.of(first, second), 200, 1);

        assertThat(produced).isTrue();
        assertThat(first.getAmountAsLong()).isEqualTo(150);
        assertThat(second.getAmountAsLong()).isEqualTo(50);
    }

    @Test
    void insufficientOutputCapacityFailsWithoutMutation() {
        LongEnergyStorage first = emptyStorage(50, 50);
        LongEnergyStorage second = emptyStorage(50, 50);

        boolean produced = EnergyRecipeIo.produceOutputs(List.of(first, second), 200, 1);

        assertThat(produced).isFalse();
        assertThat(first.getAmountAsLong()).isZero();
        assertThat(second.getAmountAsLong()).isZero();
    }

    @Test
    void canProduceOutputsDoesNotMutateStorage() {
        LongEnergyStorage hatch = emptyStorage(1_000, 500);

        boolean canProduce = EnergyRecipeIo.canProduceOutputs(List.of(hatch), 200, 1);

        assertThat(canProduce).isTrue();
        assertThat(hatch.getAmountAsLong()).isZero();
    }

    @Test
    void produceOutputsMultiplierScalesRequiredEnergy() {
        LongEnergyStorage first = emptyStorage(500, 500);
        LongEnergyStorage second = emptyStorage(500, 500);

        boolean produced = EnergyRecipeIo.produceOutputs(List.of(first, second), 200, 3);

        assertThat(produced).isTrue();
        assertThat(first.getAmountAsLong() + second.getAmountAsLong()).isEqualTo(600);
    }

    private static LongEnergyStorage chargedStorage(int capacity, int maxExtract, int inserted) {
        LongEnergyStorage storage = new LongEnergyStorage(capacity, maxExtract, () -> {});
        storage.forceInsert(inserted, false);
        assertThat(storage.getAmountAsLong()).isEqualTo(inserted);
        return storage;
    }

    private static LongEnergyStorage emptyStorage(int capacity, int maxReceive) {
        return new LongEnergyStorage(capacity, maxReceive, () -> {});
    }
}
