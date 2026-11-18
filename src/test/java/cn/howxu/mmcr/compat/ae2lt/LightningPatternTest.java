package cn.howxu.mmcr.compat.ae2lt;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.AEKeyTypes;
import appeng.api.stacks.AEKeyTypesInternal;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.helpers.patternprovider.PatternProviderReturnInventory;
import cn.howxu.mmcr.api.compat.ae2lt.LightningTier;
import cn.howxu.mmcr.api.capability.storage.ResourceStorage;
import cn.howxu.mmcr.api.recipe.OutputRegistry;
import cn.howxu.mmcr.compat.ae2lt.loaded.LoadedLightningBridge;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.adapter.AE2ResourceFamilies;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.adapter.AE2ResourceFamily;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.storage.PatternRequestState;
import cn.howxu.mmcr.test.TestBootstrap;
import com.mojang.serialization.Lifecycle;
import net.minecraft.core.BlockPos;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static cn.howxu.mmcr.api.compat.ae2lt.LightningTier.EXTREME_HIGH_VOLTAGE;
import static cn.howxu.mmcr.api.compat.ae2lt.LightningTier.HIGH_VOLTAGE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** @author howxu <dev@howxu.cn> */
class LightningPatternTest {
    @BeforeAll
    static void bootstrap() throws Exception {
        TestBootstrap.bootstrap();
        try {
            AEKeyTypes.getAll();
        } catch (IllegalStateException ignored) {
            var registry = new MappedRegistry<AEKeyType>(AEKeyType.REGISTRY_KEY, Lifecycle.stable());
            AEKeyTypesInternal.setRegistry(registry);
            Registry.register(registry, AEKeyType.items().getId(), AEKeyType.items());
            Registry.register(registry, AEKeyType.fluids().getId(), AEKeyType.fluids());
            registry.freeze();
        }
    }

    @BeforeEach
    void loadBridge() {
        LightningBridgeBootstrap.installForTesting(new LoadedLightningBridge());
    }

    @AfterEach
    void resetBridge() {
        LightningBridgeBootstrap.resetForTesting();
    }

    @Test
    void mixedPatternConsumesAndReturnsEachTierExactlyOnce() {
        var family = LoadedLightningBridge.FAMILY;
        var iron = ItemResource.of(Items.IRON_INGOT);
        var water = FluidResource.of(Fluids.WATER);
        var first = new KeyCounter();
        first.add(AEItemKey.of(iron), 8L);
        first.add(AEFluidKey.of(water), 1_000L);
        first.add(family.adapter().toKey(HIGH_VOLTAGE), 11L);
        first.add(family.adapter().toKey(EXTREME_HIGH_VOLTAGE), 13L);
        var second = new KeyCounter();
        second.add(family.adapter().toKey(HIGH_VOLTAGE), 7L);
        var state = new PatternRequestState(new KeyCounter[]{first, second}, AE2ResourceFamilies.all().size());
        var items = AE2ResourceFamilies.ITEM.patternRequestView(state);
        var fluids = AE2ResourceFamilies.FLUID.patternRequestView(state);
        var lightning = family.patternRequestView(state);
        int highSlot = resourceSlot(lightning, HIGH_VOLTAGE);
        int extremeSlot = resourceSlot(lightning, EXTREME_HIGH_VOLTAGE);
        var returns = new TestReturnInventory();
        for (boolean commit : new boolean[]{false, true}) {
            try (var transaction = Transaction.openRoot()) {
                assertThat(items.extract(0, iron, 2L, transaction)).isEqualTo(2L);
                assertThat(fluids.extract(0, water, 200L, transaction)).isEqualTo(200L);
                assertThat(lightning.extract(highSlot, HIGH_VOLTAGE, 3L, transaction)).isEqualTo(3L);
                assertThat(lightning.extract(extremeSlot, EXTREME_HIGH_VOLTAGE, 5L, transaction)).isEqualTo(5L);
                assertThat(items.accept(AE2ResourceFamilies.ITEM.patternReturnView(returns), transaction)).isTrue();
                assertThat(fluids.accept(AE2ResourceFamilies.FLUID.patternReturnView(returns), transaction)).isTrue();
                assertThat(lightning.accept(family.patternReturnView(returns), transaction)).isTrue();
                if (commit) transaction.commit();
            }
            if (!commit) {
                assertThat(returns.isEmpty()).isTrue();
                assertThat(first.get(family.adapter().toKey(HIGH_VOLTAGE))).isEqualTo(11L);
                assertThat(second.get(family.adapter().toKey(HIGH_VOLTAGE))).isEqualTo(7L);
                assertThat(lightning.amount(highSlot)).isEqualTo(18L);
                assertThat(lightning.amount(extremeSlot)).isEqualTo(13L);
            }
        }
        var received = returns.getAvailableStacks();
        assertThat(received.get(AEItemKey.of(iron))).isEqualTo(6L);
        assertThat(received.get(AEFluidKey.of(water))).isEqualTo(800L);
        assertThat(received.get(family.adapter().toKey(HIGH_VOLTAGE))).isEqualTo(15L);
        assertThat(received.get(family.adapter().toKey(EXTREME_HIGH_VOLTAGE))).isEqualTo(8L);
        assertThat(first).allSatisfy(entry -> assertThat(entry.getLongValue()).isZero());
        assertThat(second).allSatisfy(entry -> assertThat(entry.getLongValue()).isZero());
        assertThatThrownBy(lightning::size).isInstanceOf(IllegalStateException.class);
    }

    private static <R> int resourceSlot(ResourceStorage<R> storage, R resource) {
        for (int slot = 0; slot < storage.size(); slot++) {
            if (resource.equals(storage.resource(slot))) return slot;
        }
        throw new AssertionError("Missing request resource: " + resource);
    }

    @Test
    void returnCapacityFailureRollsBackAllFamilies() {
        var family = LoadedLightningBridge.FAMILY;
        var iron = AEItemKey.of(Items.IRON_INGOT);
        var water = AEFluidKey.of(Fluids.WATER);
        var high = family.adapter().toKey(HIGH_VOLTAGE);
        var holders = new KeyCounter();
        holders.add(iron, 2L);
        holders.add(water, 2L);
        holders.add(high, 5L);
        var state = new PatternRequestState(new KeyCounter[]{holders}, 3);
        var items = AE2ResourceFamilies.ITEM.patternRequestView(state);
        var fluids = AE2ResourceFamilies.FLUID.patternRequestView(state);
        var lightning = family.patternRequestView(state);
        var returns = new TestReturnInventory();
        returns.setStack(0, new GenericStack(iron, 1L));
        returns.setStack(1, new GenericStack(water, 1L));
        for (int slot = 2; slot < returns.size(); slot++) {
            returns.setStack(slot, new GenericStack(AEItemKey.of(Items.GOLD_INGOT), 64L));
        }
        try (var transaction = Transaction.openRoot()) {
            assertThat(items.accept(AE2ResourceFamilies.ITEM.patternReturnView(returns), transaction)).isTrue();
            assertThat(fluids.accept(AE2ResourceFamilies.FLUID.patternReturnView(returns), transaction)).isTrue();
            assertThat(lightning.accept(family.patternReturnView(returns), transaction)).isFalse();
        }
        assertThat(holders.get(iron)).isEqualTo(2L);
        assertThat(holders.get(water)).isEqualTo(2L);
        assertThat(holders.get(high)).isEqualTo(5L);
        assertThat(returns.getAmount(0)).isEqualTo(1L);
        assertThat(returns.getAmount(1)).isEqualTo(1L);
        assertThat(lightning.amount(0)).isEqualTo(5L);
    }

    @Test
    void emptyFamilyViewsStillParticipateInRequestLifetime() {
        var high = LoadedLightningBridge.FAMILY.adapter().toKey(HIGH_VOLTAGE);
        var holders = new KeyCounter();
        holders.add(high, 10L);
        var state = new PatternRequestState(new KeyCounter[]{holders}, AE2ResourceFamilies.all().size());
        var returns = new TestReturnInventory();
        try (var transaction = Transaction.openRoot()) {
            for (var family : AE2ResourceFamilies.all()) acceptEmptyOrFull(family, state, returns, transaction);
            transaction.commit();
        }
        assertThat(holders.get(high)).isZero();
        assertThat(returns.getAvailableStacks().get(high)).isEqualTo(10L);
    }

    private static <R> void acceptEmptyOrFull(AE2ResourceFamily<R> family, PatternRequestState state,
                                             TestReturnInventory returns, Transaction transaction) {
        assertThat(family.patternRequestView(state).accept(family.patternReturnView(returns), transaction)).isTrue();
    }

    @Test
    void unknownKeyRejectsWithoutMutatingHoldersEvenWhenItClaimsTheItemType() {
        var unknown = new UnsupportedKey();
        var holders = new KeyCounter();
        holders.add(unknown, 1L);
        holders.add(LoadedLightningBridge.FAMILY.adapter().toKey(HIGH_VOLTAGE), 7L);
        assertThat(AE2ResourceFamilies.supportsKey(unknown)).isFalse();
        assertThat(AE2ResourceFamilies.patternOutput(new GenericStack(unknown, 1L))).isEmpty();
        assertThatThrownBy(() -> LoadedLightningBridge.FAMILY.patternRequestView(new KeyCounter[]{holders}))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(holders.get(unknown)).isEqualTo(1L);
        assertThat(holders.get(LoadedLightningBridge.FAMILY.adapter().toKey(HIGH_VOLTAGE))).isEqualTo(7L);
    }

    @Test
    void partialLightningRequestPreservesLongAmountsAndRollsBack() {
        var high = LoadedLightningBridge.FAMILY.adapter().toKey(HIGH_VOLTAGE);
        var extreme = LoadedLightningBridge.FAMILY.adapter().toKey(EXTREME_HIGH_VOLTAGE);
        var holders = new KeyCounter();
        holders.add(high, 4_294_967_294L);
        holders.add(extreme, 8_589_934_588L);
        var request = LoadedLightningBridge.FAMILY.patternRequestView(new KeyCounter[]{holders});
        int highSlot = request.resource(0) == HIGH_VOLTAGE ? 0 : 1;
        int extremeSlot = 1 - highSlot;
        try (var transaction = Transaction.openRoot()) {
            assertThat(request.extract(highSlot, HIGH_VOLTAGE, 2_147_483_647L, transaction)).isEqualTo(2_147_483_647L);
            assertThat(request.amount(highSlot)).isEqualTo(2_147_483_647L);
        }
        assertThat(request.amount(highSlot)).isEqualTo(4_294_967_294L);
        assertThat(request.amount(extremeSlot)).isEqualTo(8_589_934_588L);
        assertThat(holders.get(high)).isEqualTo(4_294_967_294L);
    }

    @Test
    void lightningOutputPatternMatchesOnlySameTierAndAmount() {
        var expected = new LightningOutput(HIGH_VOLTAGE, 17L, 1F);
        assertThat(expected.matchesPatternOutput(new LightningOutput(HIGH_VOLTAGE, 17L, 0.25F))).isTrue();
        assertThat(expected.matchesPatternOutput(new LightningOutput(EXTREME_HIGH_VOLTAGE, 17L, 1F))).isFalse();
        assertThat(expected.matchesPatternOutput(new LightningOutput(HIGH_VOLTAGE, 18L, 1F))).isFalse();
        var high = LoadedLightningBridge.FAMILY.adapter().toKey(HIGH_VOLTAGE);
        assertThat(AE2ResourceFamilies.patternOutput(new GenericStack(high, 17L))).contains(expected);
        assertThat(AE2ResourceFamilies.patternOutput(new GenericStack(high, 2_147_483_648L))).isEmpty();
    }

    @Test
    void factoryAggregationSeparatesTiersAndSumsSameTier() {
        try (var scope = OutputRegistry.openTestScope()) {
            for (LightningTier tier : LightningTier.values()) OutputRegistry.register(LightningRecipeTypes.outputType(tier));
            var controller = TestBootstrap.newController();
            TestBootstrap.bindFactoryWithOutputs(controller, List.of(
                    new LightningOutput(HIGH_VOLTAGE, Integer.MAX_VALUE, 1F),
                    new LightningOutput(EXTREME_HIGH_VOLTAGE, 7L, 1F)), 2L);
            TestBootstrap.bindFactoryWithOutputs(controller, List.of(new LightningOutput(HIGH_VOLTAGE, 11L, 1F)), 3L);
            var merged = controller.recipeOutputs();
            assertThat(merged).hasSize(2);
            assertThat(merged.get(0).output().outputType()).isSameAs(LightningRecipeTypes.outputType(HIGH_VOLTAGE));
            assertThat(merged.get(0).amount()).isEqualTo(4_294_967_327L);
            assertThat(merged.get(1).output().outputType()).isSameAs(LightningRecipeTypes.outputType(EXTREME_HIGH_VOLTAGE));
            assertThat(merged.get(1).amount()).isEqualTo(14L);
        }
    }

    /** Explicit test storage support, without mutating AE2's global native-key registry.
     * @author howxu <dev@howxu.cn> */
    private static final class TestReturnInventory extends PatternProviderReturnInventory {
        TestReturnInventory() {
            super(() -> {});
        }

        @Override
        public boolean isSupportedType(AEKeyType type) {
            return type == AEKeyType.items() || type == AEKeyType.fluids()
                    || type == LoadedLightningBridge.FAMILY.adapter().keyType();
        }
    }

    /** @author howxu <dev@howxu.cn> */
    private static final class UnsupportedKey extends AEKey {
        @Override public AEKeyType getType() { return AEKeyType.items(); }
        @Override public AEKey dropSecondary() { return this; }
        @Override public void toTag(ValueOutput output) {}
        @Override public Object getPrimaryKey() { return this; }
        @Override public Identifier getId() { return Identifier.fromNamespaceAndPath("test", "unsupported"); }
        @Override public void writeToPacket(RegistryFriendlyByteBuf data) {}
        @Override protected Component computeDisplayName() { return Component.empty(); }
        @Override public void addDrops(long amount, List<ItemStack> drops, Level level, BlockPos pos) {}
        @Override public boolean hasComponents() { return false; }
    }
}
