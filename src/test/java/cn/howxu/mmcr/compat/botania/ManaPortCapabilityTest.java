package cn.howxu.mmcr.compat.botania;

import cn.howxu.mmcr.api.capability.facet.AsyncPlanningFacet;
import cn.howxu.mmcr.api.capability.facet.EnergyStorageFacet;
import cn.howxu.mmcr.api.capability.facet.OperationFacet;
import cn.howxu.mmcr.api.capability.facet.ScalarFacet;
import cn.howxu.mmcr.api.capability.facet.SyncFacet;
import cn.howxu.mmcr.api.capability.facet.TransferFacet;
import cn.howxu.mmcr.api.capability.facet.ValueFacet;
import cn.howxu.mmcr.api.capability.plan.CapabilityRequests;
import cn.howxu.mmcr.api.capability.presentation.CapabilityDisplay;
import cn.howxu.mmcr.api.capability.status.BuiltinFailureReasons;
import cn.howxu.mmcr.api.capability.status.FailurePhase;
import cn.howxu.mmcr.api.compat.botania.ManaViewFacet;
import cn.howxu.mmcr.internal.capability.BuiltinCapabilityDefinitions;
import cn.howxu.mmcr.test.TestBootstrap;
import cn.howxu.mmcr.util.IOType;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies real storage facets, bounded commit and validated synchronization.
 *
 * @author howxu <dev@howxu.cn>
 */
class ManaPortCapabilityTest {
    @BeforeAll
    static void bootstrap() throws Exception { TestBootstrap.bootstrap(); }

    @Test
    void facetsExposeRealSharedStorageAndExcludeEnergyAndAutoIo() {
        ManaStorage storage = new ManaStorage(() -> {});
        storage.setAmount(700L);
        ManaPortCapability input = new ManaPortCapability(null, storage, IOType.INPUT);
        ManaPortCapability output = new ManaPortCapability(null, storage, IOType.OUTPUT);
        assertThat(input.view().facets()).contains(ManaViewFacet.class, ScalarFacet.class, OperationFacet.class,
                SyncFacet.class, AsyncPlanningFacet.class)
                .doesNotContain(EnergyStorageFacet.class, ValueFacet.class, TransferFacet.class);
        assertThat(input.facet(ManaViewFacet.class)).contains(input);
        assertThat(input.queryIdentity()).isSameAs(storage.identity()).isSameAs(output.queryIdentity());
        storage.move(100L, false, false);
        assertThat(input.amount()).isEqualTo(output.amount()).isEqualTo(600L);
        assertThat(input.displays(input.view())).containsExactly(new CapabilityDisplay(
                BotaniaManaIds.MANA.toString(), "600", "", Optional.empty()));
    }

    @Test
    void prepareIsPureAndAllocatedTotalsCommitWithoutAnotherParallelMultiplier() {
        AtomicInteger changes = new AtomicInteger();
        ManaStorage storage = new ManaStorage(changes::incrementAndGet);
        storage.setAmount(800L);
        changes.set(0);
        ManaPortCapability input = new ManaPortCapability(null, storage, IOType.INPUT);
        var operation = input.prepare(new CapabilityRequests.ValueRequest(
                BotaniaManaIds.TYPE, IOType.INPUT, 3L, 600L, false));
        assertThat(storage.amount()).isEqualTo(800);
        assertThat(changes).hasValue(0);
        assertThat(operation.commit().success()).isTrue();
        assertThat(storage.amount()).isEqualTo(200);
        ManaPortCapability output = new ManaPortCapability(null, storage, IOType.OUTPUT);
        assertThat(output.prepareScalar(new CapabilityRequests.ValueRequest(
                BotaniaManaIds.TYPE, IOType.OUTPUT, 5L, 300L, true)).commit().success()).isTrue();
        assertThat(storage.amount()).isEqualTo(500);
        assertThat(changes).hasValue(2);
    }

    @Test
    void wrongTypeDirectionOrInsertFlagNeverChangesStorage() {
        ManaStorage storage = new ManaStorage(() -> {});
        storage.setAmount(500L);
        ManaPortCapability input = new ManaPortCapability(null, storage, IOType.INPUT);
        for (var request : List.of(
                new CapabilityRequests.ValueRequest(BuiltinCapabilityDefinitions.ENERGY_TYPE, IOType.INPUT, 1L, 100L, false),
                new CapabilityRequests.ValueRequest(BotaniaManaIds.TYPE, IOType.OUTPUT, 1L, 100L, true),
                new CapabilityRequests.ValueRequest(BotaniaManaIds.TYPE, IOType.INPUT, 1L, 100L, true))) {
            var result = input.prepareOperation(request).commit();
            assertThat(result.success()).isFalse();
            assertThat(result.status().reason()).isEqualTo(BuiltinFailureReasons.UNSUPPORTED_REQUEST);
        }
        assertThat(input.prepareOperation(new CapabilityRequests.ResourceRequest<>(BotaniaManaIds.TYPE,
                IOType.INPUT, 1L, List.of())).commit().status().reason())
                .isEqualTo(BuiltinFailureReasons.UNSUPPORTED_REQUEST);
        assertThat(storage.amount()).isEqualTo(500);
    }

    @Test
    void externalChangeAfterPreparationRejectsWholeTransferWithTypedDetails() {
        ManaStorage storage = new ManaStorage(() -> {});
        storage.setAmount(700L);
        ManaPortCapability input = new ManaPortCapability(null, storage, IOType.INPUT);
        var operation = input.prepare(new CapabilityRequests.ValueRequest(
                BotaniaManaIds.TYPE, IOType.INPUT, 2L, 600L, false));
        storage.move(300L, false, false);
        var result = operation.commit();
        assertThat(result.success()).isFalse();
        assertThat(result.status().reason()).isEqualTo(ManaFailureReasons.INPUT_MISSING);
        assertThat(result.status().failure().trace().frames().getFirst().phase())
                .isEqualTo(FailurePhase.CAPABILITY_COMMIT);
        assertThat(result.status().details()).containsEntry("required", "600")
                .containsEntry("available", "400").containsEntry("shortfall", "200");
        assertThat(storage.amount()).isEqualTo(400);
        storage.setAmount(storage.capacity() - 400L);
        ManaPortCapability output = new ManaPortCapability(null, storage, IOType.OUTPUT);
        var huge = output.prepare(new CapabilityRequests.ValueRequest(
                BotaniaManaIds.TYPE, IOType.OUTPUT, 1L, Long.MAX_VALUE, true)).commit();
        assertThat(huge.success()).isFalse();
        assertThat(huge.status().reason()).isEqualTo(ManaFailureReasons.OUTPUT_BLOCKED);
        assertThat(huge.status().details()).containsEntry("required", Long.toString(Long.MAX_VALUE))
                .containsEntry("available", "400");
        assertThat(storage.amount()).isEqualTo(storage.capacity() - 400);
    }

    @Test
    void synchronizationUpdatesSharedStorageAndRejectsInvalidStateBeforeMutation() {
        ManaStorage source = new ManaStorage(() -> {});
        source.setAmount(850L);
        AtomicInteger changes = new AtomicInteger();
        ManaStorage target = new ManaStorage(changes::incrementAndGet);
        ManaPortCapability sender = new ManaPortCapability(null, source, IOType.INPUT);
        ManaPortCapability receiver = new ManaPortCapability(null, target, IOType.INPUT);
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            sender.encode(buffer);
            receiver.decode(buffer);
            assertThat(target.amount()).isEqualTo(850);
            buffer.readerIndex(0);
            receiver.decode(buffer);
            assertThat(changes).hasValue(1);
            for (int[] state : new int[][]{{-1, target.capacity()}, {target.capacity() + 1, target.capacity()},
                    {850, target.capacity() - 1}}) {
                buffer.clear();
                buffer.writeInt(state[0]);
                buffer.writeInt(state[1]);
                assertThatThrownBy(() -> receiver.decode(buffer)).isInstanceOf(IllegalArgumentException.class);
                assertThat(target.amount()).isEqualTo(850);
                assertThat(changes).hasValue(1);
            }
        } finally {
            buffer.release();
        }
    }
}
