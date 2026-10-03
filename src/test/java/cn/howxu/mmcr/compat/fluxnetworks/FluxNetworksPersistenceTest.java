package cn.howxu.mmcr.compat.fluxnetworks;

import cn.howxu.mmcr.compat.fluxnetworks.loaded.FluxNetworkPlugHandler;
import cn.howxu.mmcr.compat.fluxnetworks.loaded.FluxNetworkPointHandler;
import io.netty.buffer.Unpooled;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import sonar.fluxnetworks.api.FluxConstants;
import sonar.fluxnetworks.common.data.FluxDeviceConfigComponent;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/** @author howxu <dev@howxu.cn> */
class FluxNetworksPersistenceTest {
    @BeforeAll
    static void bootstrap() throws Exception {
        FluxNetworksTestBootstrap.bootstrap();
    }

    @Test
    void savedReservationRebindDoesNotReserveOrWithdrawTwice() {
        FluxNetworkPointHandler original = filledPoint(50L);
        assertThat(original.reserveCandidate(50L)).isTrue();
        assertThat(original.commitCandidate(50L)).isTrue();
        assertThat(original.consumeLocal(10L)).isTrue();
        CompoundTag saved = new CompoundTag();
        original.writeCustomTag(saved, FluxConstants.NBT_SAVE_ALL);

        FluxNetworkPointHandler restored = new FluxNetworkPointHandler(() -> false, () -> 1L, () -> {});
        var mirror = restored.storage();
        restored.readCustomTag(saved, FluxConstants.NBT_SAVE_ALL);
        restored.restoreCandidateOrSavedReservation(40L);
        assertThat(restored.getBuffer()).isEqualTo(40L);
        assertThat(restored.reserved()).isEqualTo(40L);
        assertThat(mirror.amount()).isEqualTo(40L);
        assertThat(restored.getRequest()).isZero();
        assertThat(restored.consumeLocal(10L)).isTrue();
        assertThat(restored.getBuffer()).isEqualTo(30L);
        assertThat(restored.releaseReserved(30L)).isEqualTo(30L);
        assertThat(restored.availableForPrefetch()).isEqualTo(30L);
    }

    @Test
    void saveDiscardsTentativePlansAndClampsLegacyReservationMetadata() {
        FluxNetworkPointHandler original = filledPoint(50L);
        assertThat(original.reserveCandidate(20L)).isTrue();
        CompoundTag saved = new CompoundTag();
        original.writeCustomTag(saved, FluxConstants.NBT_SAVE_ALL);
        FluxNetworkPointHandler restored = new FluxNetworkPointHandler(() -> true, () -> 1L, () -> {});
        restored.readCustomTag(saved, FluxConstants.NBT_SAVE_ALL);
        assertThat(restored.reserved()).isZero();
        assertThat(restored.availableForPrefetch()).isEqualTo(50L);
        assertThat(restored.getRequest()).isZero();

        saved.getCompound("mmcr_prefetch").putLong("reserved", Long.MAX_VALUE);
        restored.readCustomTag(saved, FluxConstants.NBT_SAVE_ALL);
        assertThat(restored.reserved()).isEqualTo(50L);
        saved.getCompound("mmcr_prefetch").putLong("reserved", -1L);
        restored.readCustomTag(saved, FluxConstants.NBT_SAVE_ALL);
        assertThat(restored.reserved()).isZero();
        saved.remove("mmcr_prefetch");
        restored.readCustomTag(saved, FluxConstants.NBT_SAVE_ALL);
        assertThat(restored.getBuffer()).isEqualTo(50L);
        assertThat(restored.reserved()).isZero();
    }

    @Test
    void networkChangeClearsPointDemandButKeepsCommittedInventory() {
        FluxNetworkPointHandler handler = filledPoint(50L);
        assertThat(handler.reserveCandidate(30L)).isTrue();
        assertThat(handler.commitCandidate(30L)).isTrue();
        handler.requestWarmup(50L);
        handler.onCycleStart();
        assertThat(handler.getRequest()).isPositive();
        handler.onNetworkChanged();
        assertThat(handler.getRequest()).isZero();
        assertThat(handler.getBuffer()).isEqualTo(50L);
        assertThat(handler.reserved()).isEqualTo(30L);
        assertThat(handler.releaseReserved(30L)).isEqualTo(30L);
        assertThat(handler.availableForPrefetch()).isEqualTo(50L);
    }

    @Test
    void nativeConfigurationAndGuiSyncRefreshPointMirrorWithoutServerNotifications() {
        AtomicInteger changes = new AtomicInteger();
        FluxNetworkPointHandler handler = new FluxNetworkPointHandler(() -> true, () -> 1L, changes::incrementAndGet);
        var mirror = handler.storage();
        handler.applyConfiguration(FluxDeviceConfigComponent.EMPTY, 50L);
        assertThat(handler.reserveCandidate(20L)).isTrue();
        assertThat(handler.commitCandidate(20L)).isTrue();
        changes.set(0);
        handler.applyConfiguration(FluxDeviceConfigComponent.EMPTY, 30L);
        assertThat(handler.reserved()).isZero();
        assertThat(handler.availableForPrefetch()).isEqualTo(30L);
        assertThat(mirror.amount()).isEqualTo(30L);
        FriendlyByteBuf packet = new FriendlyByteBuf(Unpooled.buffer());
        try {
            packet.writeLong(-4L).writeLong(26L);
            handler.readPacketBuffer(packet, FluxConstants.DEVICE_S2C_GUI_SYNC);
            assertThat(handler.getChange()).isEqualTo(-4L);
            assertThat(mirror.amount()).isEqualTo(26L);
        } finally {
            packet.release();
        }
        assertThat(changes.get()).isZero();
    }

    @Test
    void settingsOnlyConfigurationPreservesCommittedReservationAndCurrentReceiveBudget() {
        FluxNetworkPointHandler handler = filledPoint(80L);
        assertThat(handler.reserveCandidate(50L)).isTrue();
        assertThat(handler.commitCandidate(50L)).isTrue();
        handler.applyConfiguration(FluxDeviceConfigComponent.EMPTY, 80L);
        assertThat(handler.reserved()).isEqualTo(50L);
        assertThat(handler.availableForPrefetch()).isEqualTo(30L);
        handler.setLimit(100L);
        handler.requestWarmup(100L);
        handler.onCycleStart();
        assertThat(handler.getRequest()).isEqualTo(20L);
    }

    @Test
    void reconciliationKeepsAllRestoredOwnersAndReleasesOnlyOrphanedMetadata() {
        FluxNetworkPointHandler original = filledPoint(80L);
        assertThat(original.reserveCandidate(80L)).isTrue();
        assertThat(original.commitCandidate(80L)).isTrue();
        CompoundTag saved = new CompoundTag();
        original.writeCustomTag(saved, FluxConstants.NBT_SAVE_ALL);
        FluxNetworkPointHandler restored = new FluxNetworkPointHandler(() -> true, () -> 1L, () -> {});
        restored.readCustomTag(saved, FluxConstants.NBT_SAVE_ALL);
        Object firstLane = new Object();
        Object secondLane = new Object();
        restored.updateRecipeOwner(firstLane, 30L, () -> {});
        restored.updateRecipeOwner(secondLane, 20L, () -> {});
        assertThat(restored.reserved()).isEqualTo(80L);
        restored.reconcileRecipeOwners();
        assertThat(restored.getBuffer()).isEqualTo(80L);
        assertThat(restored.reserved()).isEqualTo(50L);
        restored.reconcileRecipeOwners();
        assertThat(restored.reserved()).isEqualTo(50L);
    }

    @Test
    void configurationPasteDoesNotReopenPointReceiveBudgetInTheSameTick() {
        FluxNetworkPointHandler handler = filledPoint(80L);
        assertThat(handler.consumeLocal(80L)).isTrue();
        handler.applyConfiguration(FluxDeviceConfigComponent.EMPTY, 0L);
        handler.requestWarmup(50L);
        handler.onCycleStart();
        assertThat(handler.getRequest()).isEqualTo(20L);
    }

    @Test
    void plugReloadConfigurationAndGuiSyncKeepNativeBufferAndMirrorTogether() {
        AtomicInteger changes = new AtomicInteger();
        FluxNetworkPlugHandler original = new FluxNetworkPlugHandler(() -> true, () -> 100L, () -> {});
        original.setLimit(100L);
        assertThat(original.acceptRecipeEnergy(40L)).isTrue();
        CompoundTag saved = new CompoundTag();
        original.writeCustomTag(saved, FluxConstants.NBT_SAVE_ALL);
        FluxNetworkPlugHandler restored = new FluxNetworkPlugHandler(() -> false, () -> 100L, changes::incrementAndGet);
        restored.readCustomTag(saved, FluxConstants.NBT_SAVE_ALL);
        assertThat(restored.storage().amount()).isEqualTo(40L);
        assertThat(restored.acceptRecipeEnergy(1L)).isFalse();
        restored.onNetworkChanged();
        assertThat(restored.getBuffer()).isEqualTo(40L);
        restored.applyConfiguration(FluxDeviceConfigComponent.EMPTY, 20L);
        assertThat(restored.storage().amount()).isEqualTo(20L);
        FriendlyByteBuf packet = new FriendlyByteBuf(Unpooled.buffer());
        try {
            packet.writeLong(7L).writeLong(13L);
            restored.readPacketBuffer(packet, FluxConstants.DEVICE_S2C_GUI_SYNC);
            assertThat(restored.getChange()).isEqualTo(7L);
            assertThat(restored.storage().amount()).isEqualTo(13L);
        } finally {
            packet.release();
        }
        assertThat(changes.get()).isZero();
    }

    private static FluxNetworkPointHandler filledPoint(long amount) {
        FluxNetworkPointHandler handler = new FluxNetworkPointHandler(() -> true, () -> 1L, () -> {});
        handler.setLimit(100L);
        handler.requestWarmup(amount);
        handler.onCycleStart();
        handler.addToBuffer(handler.getRequest());
        return handler;
    }
}
