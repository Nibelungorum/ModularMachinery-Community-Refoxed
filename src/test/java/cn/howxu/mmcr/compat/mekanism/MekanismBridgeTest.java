package cn.howxu.mmcr.compat.mekanism;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.internal.storage.LongFluidStorage;
import java.util.Arrays;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MekanismBridgeTest {
    @AfterEach
    void resetBridge() {
        MekanismBridgeBootstrap.resetForTesting();
    }

    @Test
    void bridge_is_inert_when_mekanism_is_not_loaded() {
        MekanismBridge bridge = MekanismBridgeBootstrap.selectForTesting(false);

        assertThat(bridge.available()).isFalse();
        assertThat(bridge.supportsPortFamily(MMCR.id("mekanism_chemical"))).isFalse();
        assertThat(bridge.unavailableReason()).isEqualTo(MMCR.id("mekanism_unavailable"));
        assertThat(bridge.capabilityIdForMenu(null)).isNull();
        assertThat(bridge.isPortMenuAt(null, BlockPos.ZERO, null)).isFalse();
        var storage = new LongFluidStorage(4_000, () -> {});
        assertThat(bridge.manualFluidContainerHandler(storage)).isSameAs(storage);
        try (Transaction transaction = Transaction.openRoot()) {
            assertThat(bridge.transferChemicalContainer(null, null, 0, transaction)).isZero();
        }
        bridge.registerRecipeTypes(MMCR.id("mekanism_chemical"), MMCR.id("mekanism_heat_temperature"),
                MMCR.id("mekanism_heat"));
    }

    @Test
    void bridge_contract_has_no_mekanism_typed_public_members() {
        assertThat(Arrays.stream(MekanismBridge.class.getMethods())
                .flatMap(method -> Stream.concat(Stream.of(method.getReturnType()),
                        Arrays.stream(method.getParameterTypes())))
                .map(Class::getName))
                .noneMatch(name -> name.startsWith("mekanism."));
    }

    @Test
    void testing_bridge_can_be_installed_and_reset() {
        MekanismBridge testingBridge = new MekanismBridge() {
            @Override
            public boolean available() {
                return true;
            }

            @Override
            public boolean supportsPortFamily(Identifier familyId) {
                return true;
            }

            @Override
            public Identifier unavailableReason() {
                return MMCR.id("unused");
            }

            @Override
            public void registerRecipeTypes(Identifier chemical, Identifier heatTemperature, Identifier heat) {
            }
        };

        MekanismBridgeBootstrap.installForTesting(testingBridge);

        assertThat(MekanismBridge.get()).isSameAs(testingBridge);
        MekanismBridgeBootstrap.resetForTesting();
        assertThat(MekanismBridge.get().available()).isFalse();
    }
}
