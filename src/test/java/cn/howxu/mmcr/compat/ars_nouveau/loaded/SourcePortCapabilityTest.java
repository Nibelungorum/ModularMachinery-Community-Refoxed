package cn.howxu.mmcr.compat.ars_nouveau.loaded;

import cn.howxu.mmcr.api.capability.CapabilityDirections;
import cn.howxu.mmcr.api.capability.facet.EnergyStorageFacet;
import cn.howxu.mmcr.api.capability.facet.OperationFacet;
import cn.howxu.mmcr.api.capability.facet.PresentationFacet;
import cn.howxu.mmcr.api.capability.facet.ScalarFacet;
import cn.howxu.mmcr.api.capability.facet.SyncFacet;
import cn.howxu.mmcr.api.capability.facet.TransferFacet;
import cn.howxu.mmcr.api.capability.facet.ValueFacet;
import cn.howxu.mmcr.api.capability.plan.CapabilityOperation;
import cn.howxu.mmcr.api.capability.plan.CapabilityRequests;
import cn.howxu.mmcr.api.capability.plan.CapabilityResult;
import cn.howxu.mmcr.api.capability.presentation.CapabilityDisplay;
import cn.howxu.mmcr.api.capability.status.BuiltinFailureReasons;
import cn.howxu.mmcr.api.capability.status.FailurePhase;
import cn.howxu.mmcr.api.compat.ars_nouveau.SourceViewFacet;
import cn.howxu.mmcr.compat.ars_nouveau.ArsSourceIds;
import cn.howxu.mmcr.compat.ars_nouveau.SourceFailureReasons;
import cn.howxu.mmcr.internal.capability.BuiltinCapabilityDefinitions;
import cn.howxu.mmcr.test.TestBootstrap;
import cn.howxu.mmcr.util.IOType;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies native transfer, facet and sync contracts without a game world.
 *
 * @author howxu <dev@howxu.cn>
 */
class SourcePortCapabilityTest {
    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    @Test
    void exposesSourceFacetsAndSharedPhysicalIdentityWithoutEnergyOrAutoIo() {
        SourcePortStorage storage = new SourcePortStorage(() -> {});
        storage.setAmount(700L);
        SourcePortCapability input = new SourcePortCapability(null, storage, IOType.INPUT);
        SourcePortCapability output = new SourcePortCapability(null, storage, IOType.OUTPUT);

        assertThat(input.type()).isEqualTo(ArsSourceIds.TYPE);
        assertThat(input.directions()).isEqualTo(CapabilityDirections.input());
        assertThat(output.directions()).isEqualTo(CapabilityDirections.output());
        assertThat(input.view().facets()).contains(SourceViewFacet.class, ScalarFacet.class, OperationFacet.class,
                PresentationFacet.class, SyncFacet.class)
                .doesNotContain(EnergyStorageFacet.class, ValueFacet.class, TransferFacet.class);
        assertThat(input.facet(SourceViewFacet.class)).contains(input);
        assertThat(input.queryIdentity()).isSameAs(storage.identity()).isSameAs(output.queryIdentity());
        storage.move(100L, false, false);
        assertThat(input.amount()).isEqualTo(output.amount()).isEqualTo(600L);
        assertThat(input.displays(input.view())).containsExactly(new CapabilityDisplay(
                ArsSourceIds.SOURCE.toString(), "600", "", Optional.empty()));
    }

    @Test
    void preparationDoesNotMoveSourceAndAllocatedAmountCommitsOnceRegardlessOfParallelism() {
        AtomicInteger changed = new AtomicInteger();
        SourcePortStorage storage = new SourcePortStorage(changed::incrementAndGet);
        storage.setAmount(800L);
        changed.set(0);
        SourcePortCapability input = new SourcePortCapability(null, storage, IOType.INPUT);
        CapabilityOperation extract = input.prepare(new CapabilityRequests.ValueRequest(
                ArsSourceIds.TYPE, IOType.INPUT, 3L, 600L, false));

        assertThat(input.amount()).isEqualTo(800L);
        assertThat(changed).hasValue(0);
        assertThat(extract.commit().success()).isTrue();
        assertThat(input.amount()).isEqualTo(200L);
        assertThat(changed).hasValue(1);
        SourcePortCapability output = new SourcePortCapability(null, storage, IOType.OUTPUT);
        assertThat(output.prepareScalar(new CapabilityRequests.ValueRequest(
                ArsSourceIds.TYPE, IOType.OUTPUT, 5L, 300L, true)).commit().success()).isTrue();
        assertThat(output.amount()).isEqualTo(500L);
        assertThat(changed).hasValue(2);
    }

    @Test
    void unsupportedTypeDirectionAndInsertFlagDoNotTouchStorage() {
        SourcePortStorage storage = new SourcePortStorage(() -> {});
        storage.setAmount(500L);
        SourcePortCapability input = new SourcePortCapability(null, storage, IOType.INPUT);
        List<CapabilityRequests.ValueRequest> requests = List.of(
                new CapabilityRequests.ValueRequest(BuiltinCapabilityDefinitions.ENERGY_TYPE,
                        IOType.INPUT, 1L, 100L, false),
                new CapabilityRequests.ValueRequest(ArsSourceIds.TYPE, IOType.OUTPUT, 1L, 100L, true),
                new CapabilityRequests.ValueRequest(ArsSourceIds.TYPE, IOType.INPUT, 1L, 100L, true));

        for (CapabilityRequests.ValueRequest request : requests) {
            CapabilityResult result = input.prepareOperation(request).commit();
            assertThat(result.success()).isFalse();
            assertThat(result.status().reason()).isEqualTo(BuiltinFailureReasons.UNSUPPORTED_REQUEST);
            assertThat(result.status().failure().trace().frames().getFirst().phase()).isEqualTo(FailurePhase.CAPABILITY_COMMIT);
        }
        assertThat(input.prepareOperation(new CapabilityRequests.ResourceRequest<>(ArsSourceIds.TYPE,
                IOType.INPUT, 1L, List.of())).commit().status().reason())
                .isEqualTo(BuiltinFailureReasons.UNSUPPORTED_REQUEST);
        assertThatThrownBy(() -> input.prepare(requests.getFirst())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> input.prepare(requests.get(1))).isInstanceOf(IllegalArgumentException.class);
        assertThat(input.amount()).isEqualTo(500L);
    }

    @Test
    void changedStorageReportsTypedCommitFailureWithoutPartialTransfer() {
        SourcePortStorage storage = new SourcePortStorage(() -> {});
        storage.setAmount(700L);
        SourcePortCapability input = new SourcePortCapability(null, storage, IOType.INPUT);
        CapabilityOperation operation = input.prepare(new CapabilityRequests.ValueRequest(
                ArsSourceIds.TYPE, IOType.INPUT, 1L, 600L, false));
        storage.move(300L, false, false);

        CapabilityResult result = operation.commit();

        assertThat(result.success()).isFalse();
        assertThat(result.status().reason()).isEqualTo(SourceFailureReasons.INPUT_MISSING);
        assertThat(result.status().failure().trace().frames().getFirst().phase()).isEqualTo(FailurePhase.CAPABILITY_COMMIT);
        assertThat(result.status().details()).containsAllEntriesOf(Map.of(
                "required", "600", "available", "400", "shortfall", "200"));
        assertThat(input.amount()).isEqualTo(400L);
    }

    @Test
    void outputCapacityChangesReportTypedFailureAndLongRequestsAreNotTruncated() {
        SourcePortStorage storage = new SourcePortStorage(() -> {});
        storage.setAmount(storage.capacity() - 700L);
        SourcePortCapability output = new SourcePortCapability(null, storage, IOType.OUTPUT);
        CapabilityOperation operation = output.prepare(new CapabilityRequests.ValueRequest(
                ArsSourceIds.TYPE, IOType.OUTPUT, 1L, 600L, true));
        storage.move(300L, true, false);

        CapabilityResult result = operation.commit();

        assertThat(result.success()).isFalse();
        assertThat(result.status().reason()).isEqualTo(SourceFailureReasons.OUTPUT_BLOCKED);
        assertThat(result.status().details()).containsAllEntriesOf(Map.of(
                "required", "600", "available", "400", "shortfall", "200"));
        assertThat(output.amount()).isEqualTo(storage.capacity() - 400L);
        CapabilityResult huge = output.prepare(new CapabilityRequests.ValueRequest(
                ArsSourceIds.TYPE, IOType.OUTPUT, 1L, Long.MAX_VALUE, true)).commit();
        assertThat(huge.success()).isFalse();
        assertThat(huge.status().details()).containsEntry("required", Long.toString(Long.MAX_VALUE))
                .containsEntry("available", "400")
                .containsEntry("shortfall", Long.toString(Long.MAX_VALUE - 400L));
        assertThat(output.amount()).isEqualTo(storage.capacity() - 400L);
    }

    @Test
    void syncRoundTripUpdatesTheSharedStoreAndOnlyNotifiesActualChanges() {
        SourcePortStorage source = new SourcePortStorage(() -> {});
        source.setAmount(850L);
        AtomicInteger changed = new AtomicInteger();
        SourcePortStorage target = new SourcePortStorage(changed::incrementAndGet);
        SourcePortCapability sender = new SourcePortCapability(null, source, IOType.INPUT);
        SourcePortCapability receiver = new SourcePortCapability(null, target, IOType.INPUT);
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            sender.encode(buffer);
            receiver.decode(buffer);
            assertThat(target.amount()).isEqualTo(source.amount());
            assertThat(changed).hasValue(1);
            buffer.readerIndex(0);
            receiver.decode(buffer);
            assertThat(changed).hasValue(1);
            assertThat(buffer.readableBytes()).isZero();
        } finally {
            buffer.release();
        }
    }

    @Test
    void invalidSyncStateIsRejectedBeforeChangingStorage() {
        AtomicInteger changed = new AtomicInteger();
        SourcePortStorage storage = new SourcePortStorage(changed::incrementAndGet);
        storage.setAmount(500L);
        changed.set(0);
        SourcePortCapability capability = new SourcePortCapability(null, storage, IOType.INPUT);
        for (int[] state : new int[][]{{-1, storage.capacity()}, {storage.capacity() + 1, storage.capacity()},
                {500, storage.capacity() - 1}}) {
            RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
            try {
                buffer.writeInt(state[0]);
                buffer.writeInt(state[1]);
                assertThatThrownBy(() -> capability.decode(buffer)).isInstanceOf(IllegalArgumentException.class);
                assertThat(capability.amount()).isEqualTo(500L);
                assertThat(changed).hasValue(0);
            } finally {
                buffer.release();
            }
        }
    }
}
