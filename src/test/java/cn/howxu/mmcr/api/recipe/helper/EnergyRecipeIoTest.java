package cn.howxu.mmcr.api.recipe.helper;

import net.neoforged.neoforge.energy.EnergyStorage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EnergyRecipeIoTest {

    @Test
    void singleInputHatchWithEnoughEnergyConsumesExactlyRequiredFe() {
        EnergyStorage hatch = chargedStorage(1_000, 500, 300);

        boolean consumed = EnergyRecipeIo.consumeInputs(List.of(hatch), 120, 1);

        assertThat(consumed).isTrue();
        assertThat(hatch.getEnergyStored()).isEqualTo(180);
    }

    @Test
    void multipleInputHatchesCanSatisfyOneTickTogether() {
        EnergyStorage first = chargedStorage(1_000, 500, 80);
        EnergyStorage second = chargedStorage(1_000, 500, 200);

        boolean consumed = EnergyRecipeIo.consumeInputs(List.of(first, second), 150, 1);

        assertThat(consumed).isTrue();
        assertThat(first.getEnergyStored()).isEqualTo(0);
        assertThat(second.getEnergyStored()).isEqualTo(130);
    }

    @Test
    void insufficientCombinedEnergyFailsWithoutMutation() {
        EnergyStorage first = chargedStorage(1_000, 500, 60);
        EnergyStorage second = chargedStorage(1_000, 500, 70);

        boolean consumed = EnergyRecipeIo.consumeInputs(List.of(first, second), 150, 1);

        assertThat(consumed).isFalse();
        assertThat(first.getEnergyStored()).isEqualTo(60);
        assertThat(second.getEnergyStored()).isEqualTo(70);
    }

    @Test
    void insufficientTransferLimitFailsWithoutPartialMutation() {
        EnergyStorage first = chargedStorage(1_000, 40, 500);
        EnergyStorage second = chargedStorage(1_000, 40, 500);

        boolean consumed = EnergyRecipeIo.consumeInputs(List.of(first, second), 100, 1);

        assertThat(consumed).isFalse();
        assertThat(first.getEnergyStored()).isEqualTo(500);
        assertThat(second.getEnergyStored()).isEqualTo(500);
    }

    private static EnergyStorage chargedStorage(int capacity, int maxExtract, int inserted) {
        EnergyStorage storage = new EnergyStorage(capacity, capacity, maxExtract);
        storage.receiveEnergy(inserted, false);
        assertThat(storage.getEnergyStored()).isEqualTo(inserted);
        return storage;
    }
}
