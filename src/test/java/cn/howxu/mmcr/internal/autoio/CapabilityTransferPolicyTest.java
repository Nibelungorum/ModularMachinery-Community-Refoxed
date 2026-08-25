package cn.howxu.mmcr.internal.autoio;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.capability.CapabilityType;
import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.plan.CapabilityOperation;
import cn.howxu.mmcr.api.capability.storage.LongValueStorage;
import cn.howxu.mmcr.api.capability.transfer.TransferResult;
import cn.howxu.mmcr.internal.tile.EnergyInputHatchBlockEntity;
import cn.howxu.mmcr.internal.tile.FluidInputHatchBlockEntity;
import cn.howxu.mmcr.internal.tile.ItemInputBusBlockEntity;
import cn.howxu.mmcr.internal.tile.ItemOutputBusBlockEntity;
import cn.howxu.mmcr.test.RuntimeTestFixtures;
import cn.howxu.mmcr.test.TestBootstrap;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Capability identity and automatic transfer policy tests.
 *
 * @author howxu <dev@howxu.cn>
 */
class CapabilityTransferPolicyTest {
    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    @Test
    void builtInPoliciesAreSelectedByItemFluidAndEnergyCapabilityIdentity() {
        ItemInputBusBlockEntity item = RuntimeTestFixtures.itemInput(BlockPos.ZERO);
        FluidInputHatchBlockEntity fluid = RuntimeTestFixtures.fluidInput(new BlockPos(1, 0, 0));
        EnergyInputHatchBlockEntity energy = RuntimeTestFixtures.energyInput(new BlockPos(2, 0, 0));

        assertThat(CapabilityTransferPolicies.policyFor(item.capabilitySnapshot().capabilities().getFirst())).isPresent();
        assertThat(CapabilityTransferPolicies.policyFor(fluid.capabilitySnapshot().capabilities().getFirst())).isPresent();
        assertThat(CapabilityTransferPolicies.policyFor(energy.capabilitySnapshot().capabilities().getFirst())).isPresent();
    }

    @Test
    void inputAndOutputWorkPoliciesReflectStoredContents() {
        ItemInputBusBlockEntity input = RuntimeTestFixtures.itemInput(BlockPos.ZERO);
        ItemOutputBusBlockEntity output = RuntimeTestFixtures.itemOutput(new BlockPos(1, 0, 0));
        var inputCapability = input.capabilitySnapshot().capabilities().getFirst();
        var outputCapability = output.capabilitySnapshot().capabilities().getFirst();
        var inputPolicy = CapabilityTransferPolicies.policyFor(inputCapability).orElseThrow();
        var outputPolicy = CapabilityTransferPolicies.policyFor(outputCapability).orElseThrow();

        assertThat(inputPolicy.hasWork(inputCapability)).isTrue();
        assertThat(outputPolicy.hasWork(outputCapability)).isFalse();
    }

    @Test
    void noTargetAndNoWorkReturnStructuredBlockedResults() {
        ItemInputBusBlockEntity input = RuntimeTestFixtures.itemInput(BlockPos.ZERO);
        ItemOutputBusBlockEntity output = RuntimeTestFixtures.itemOutput(new BlockPos(1, 0, 0));
        var inputCapability = input.capabilitySnapshot().capabilities().getFirst();
        var outputCapability = output.capabilitySnapshot().capabilities().getFirst();
        var inputPolicy = CapabilityTransferPolicies.policyFor(inputCapability).orElseThrow();
        var outputPolicy = CapabilityTransferPolicies.policyFor(outputCapability).orElseThrow();

        TransferResult noTarget = inputPolicy.transfer(inputCapability, Direction.NORTH);
        TransferResult noWork = outputPolicy.transfer(outputCapability, Direction.NORTH);

        assertThat(noTarget.successful()).isFalse();
        assertThat(noTarget.amount()).isZero();
        assertThat(noTarget.failure().details()).containsEntry("reason", "no_target");
        assertThat(noWork.failure().details()).containsEntry("reason", "no_work");
    }

    @Test
    void invalidCapabilityAndSideAreBlockedWithoutMutatingStorage() {
        MachineCapability unknown = new MachineCapability() {
            private final CapabilityType capabilityType = new CapabilityType(MMCR.id("unknown"));

            @Override public CapabilityType type() { return capabilityType; }
            @Override public IOType ioType() { return IOType.INPUT; }
            @Override public cn.howxu.mmcr.api.capability.CapabilityView view() {
                return new cn.howxu.mmcr.api.capability.CapabilityView() {
                    @Override public CapabilityType type() { return capabilityType; }
                    @Override public IOType ioType() { return IOType.INPUT; }
                };
            }
            @Override public CapabilityOperation prepare(cn.howxu.mmcr.api.capability.CapabilityRequest request) { return null; }
        };

        assertThat(CapabilityTransferPolicies.policyFor(unknown)).isEmpty();
        assertThat(CapabilityTransferPolicies.policyFor(null)).isEmpty();
    }

    @Test
    void longValueStorageKeepsLongAmountsAndHonorsTransactionRollback() {
        LongValueStorage storage = new LongValueStorage(Long.MAX_VALUE, Long.MAX_VALUE, () -> {});
        long amount = (long) Integer.MAX_VALUE + 17L;

        try (Transaction transaction = Transaction.openRoot()) {
            storage.updateSnapshots(transaction);
            assertThat(storage.insert(amount, false)).isEqualTo(amount);
        }

        assertThat(storage.amount()).isZero();
        assertThat(new TransferResult(true, amount, null).amount()).isEqualTo(amount);
    }
}
