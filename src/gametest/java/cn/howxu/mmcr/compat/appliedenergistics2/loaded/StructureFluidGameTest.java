package cn.howxu.mmcr.compat.appliedenergistics2.loaded;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * Verifies on-demand fluid extraction and compensation against a controllable ME inventory.
 *
 * @author howxu <dev@howxu.cn>
 */
public class StructureFluidGameTest {
    public void failedPlacementsPreserveFluidStorage(GameTestHelper helper) {
        MemoryStorage storage = new MemoryStorage();
        boolean[] connected = {true};
        var source = new AE2NetworkStructureItemStorage(() -> connected[0] ? storage : null, IActionSource.empty());
        AEFluidKey water = AEFluidKey.of(Fluids.WATER);
        long initial = 2L * AEFluidKey.AMOUNT_BLOCK + AEFluidKey.AMOUNT_BLOCK / 2;
        storage.amounts.add(water, initial);
        var snapshot = source.copyFluidSources();
        snapshot.clear();
        helper.assertTrue(source.copyFluidSources().get(Fluids.WATER) == 2L,
                "Snapshot is independent of storage and counts only complete source blocks");

        helper.assertFalse(source.placeFluidSource(Fluids.WATER, () -> false), "Rejected placement fails");
        helper.assertTrue(storage.amounts.get(water) == initial, "Rejected placement restores every extracted unit");
        boolean threw = false;
        try {
            source.placeFluidSource(Fluids.WATER, () -> { throw new IllegalArgumentException("placement failed"); });
        } catch (IllegalArgumentException expected) {
            threw = true;
        }
        helper.assertTrue(threw && storage.amounts.get(water) == initial, "Placement exception propagates after compensation");

        storage.extractionLimit = AEFluidKey.AMOUNT_BLOCK / 2;
        helper.assertFalse(source.placeFluidSource(Fluids.WATER, () -> { throw new AssertionError("Partial extraction cannot place"); }),
                "A partial real extraction does not invoke placement");
        helper.assertTrue(storage.amounts.get(water) == initial, "Partial extraction is completely compensated");
        storage.extractionLimit = Long.MAX_VALUE;
        helper.assertTrue(source.placeFluidSource(Fluids.WATER, () -> true), "Successful placement consumes one source");
        helper.assertTrue(storage.amounts.get(water) == initial - AEFluidKey.AMOUNT_BLOCK,
                "Successful placement consumes exactly one block of fluid");

        connected[0] = false;
        helper.assertTrue(source.copyFluidSources().isEmpty(), "Unavailable network has no visible source blocks");
        helper.assertFalse(source.placeFluidSource(Fluids.WATER, () -> { throw new AssertionError("Disconnected network cannot place"); }),
                "Disconnected network cannot consume or place");
        connected[0] = true;
        storage.amounts.reset();
        storage.amounts.add(water, AEFluidKey.AMOUNT_BLOCK - 1L);
        helper.assertFalse(source.placeFluidSource(Fluids.WATER, () -> { throw new AssertionError("Insufficient fluid cannot place"); }),
                "Fluid depletion during execution skips the target");
        helper.assertTrue(storage.amounts.get(water) == AEFluidKey.AMOUNT_BLOCK - 1L, "Insufficient fluid is not partially consumed");

        FluidStack named = new FluidStack(Fluids.WATER, AEFluidKey.AMOUNT_BLOCK);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("component-bearing fluid"));
        storage.amounts.add(AEFluidKey.of(named), AEFluidKey.AMOUNT_BLOCK);
        helper.assertTrue(source.copyFluidSources().isEmpty(), "Patched fluid components are not discarded to create a plain source");
        storage.amounts.reset();
        storage.capacity = AEFluidKey.AMOUNT_BLOCK - 1L;
        helper.assertFalse(source.recoverFluidSource(Fluids.WATER,
                        () -> { throw new AssertionError("Full storage cannot remove a source"); }),
                "Less than a whole bucket of free space prevents source removal");
        helper.assertTrue(storage.amounts.get(water) == 0L, "Rejected recovery inserts no partial fluid");
        storage.capacity = Long.MAX_VALUE;
        helper.assertFalse(source.recoverFluidSource(Fluids.WATER, () -> false), "Failed source removal is reported");
        helper.assertTrue(storage.amounts.get(water) == 0L, "Failed removal rolls back its fluid insertion");
        threw = false;
        try {
            source.recoverFluidSource(Fluids.WATER, () -> { throw new IllegalArgumentException("removal failed"); });
        } catch (IllegalArgumentException expected) {
            threw = true;
        }
        helper.assertTrue(threw && storage.amounts.get(water) == 0L, "Removal exception propagates after insertion rollback");
        storage.insertionLimit = AEFluidKey.AMOUNT_BLOCK / 2;
        helper.assertFalse(source.recoverFluidSource(Fluids.WATER,
                        () -> { throw new AssertionError("Partial insertion cannot remove a source"); }),
                "Partial real insertion leaves the block intact");
        helper.assertTrue(storage.amounts.get(water) == 0L, "Partial recovery insertion is compensated");
        storage.insertionLimit = Long.MAX_VALUE;
        helper.assertTrue(source.recoverFluidSource(Fluids.WATER, () -> true), "Successful removal recovers fluid");
        helper.assertTrue(storage.amounts.get(water) == AEFluidKey.AMOUNT_BLOCK, "A removed source adds exactly one bucket");
        helper.succeed();
    }

    /**
     * Controllable inventory for testing changes between simulation and modulation.
     * @author howxu <dev@howxu.cn>
     */
    private static final class MemoryStorage implements MEStorage {
        private final KeyCounter amounts = new KeyCounter();
        private long extractionLimit = Long.MAX_VALUE;
        private long insertionLimit = Long.MAX_VALUE;
        private long capacity = Long.MAX_VALUE;

        @Override
        public long insert(AEKey key, long amount, Actionable mode, IActionSource source) {
            long inserted = Math.min(amount, capacity - amounts.get(key));
            if (mode == Actionable.MODULATE) {
                inserted = Math.min(inserted, insertionLimit);
                amounts.add(key, inserted);
            }
            return inserted;
        }

        @Override
        public long extract(AEKey key, long amount, Actionable mode, IActionSource source) {
            long extracted = Math.min(amount, amounts.get(key));
            if (mode == Actionable.MODULATE) {
                extracted = Math.min(extracted, extractionLimit);
                amounts.add(key, -extracted);
            }
            return extracted;
        }

        @Override
        public void getAvailableStacks(KeyCounter out) { out.addAll(amounts); }

        @Override
        public Component getDescription() { return Component.empty(); }
    }
}
