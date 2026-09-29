package cn.howxu.mmcr.compat.appliedenergistics2;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.AEKeyTypes;
import appeng.api.stacks.AEKeyTypesInternal;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.helpers.externalstorage.GenericStackInv;
import appeng.helpers.patternprovider.PatternProviderReturnInventory;
import cn.howxu.mmcr.api.capability.plan.PlanningContext;
import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.recipe.requirement.ItemRequirement;
import cn.howxu.mmcr.compat.extendedae.loaded.kind.ExtendedPatternInterfaceKind;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.adapter.AE2ResourceFamilies;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.storage.ItemResourceStorage;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.storage.PatternRequestResourceStorage;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.storage.PatternReturnResourceStorage;
import cn.howxu.mmcr.internal.capability.ItemBusCapability;
import cn.howxu.mmcr.internal.port.IOPortKind;
import cn.howxu.mmcr.internal.recipe.RequirementPlanner;
import cn.howxu.mmcr.registry.ModBlockEntities;
import cn.howxu.mmcr.test.TestBootstrap;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.registries.DeferredHolder;
import com.mojang.serialization.Lifecycle;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

/**
 * Verifies transactional request and native return views for the AE2 pattern interface.
 *
 * @author howxu <dev@howxu.cn>
 */
class PatternInterfaceResourceStorageTest {
    @BeforeAll
    static void setup() throws Exception {
        TestBootstrap.bootstrap();
        if (!ae2KeyTypesAreInitialized()) initializeAE2KeyTypes();
    }

    @Test
    void requestViewsExposeOnlyTheirPushedResourceFamilyAndRollback() {
        ItemResource iron = ItemResource.of(Items.IRON_INGOT);
        FluidResource water = FluidResource.of(net.minecraft.world.level.material.Fluids.WATER);
        KeyCounter inputs = counter(AEItemKey.of(iron), 5L,
                appeng.api.stacks.AEFluidKey.of(water), 1_000L);

        PatternRequestResourceStorage<ItemResource> items = AE2ResourceFamilies.ITEM.patternRequestView(
                new KeyCounter[]{inputs});
        PatternRequestResourceStorage<FluidResource> fluids = AE2ResourceFamilies.FLUID.patternRequestView(
                new KeyCounter[]{inputs});

        assertThat(items.size()).isOne();
        assertThat(items.resource(0)).isEqualTo(iron);
        assertThat(items.amount(0)).isEqualTo(5L);
        assertThat(fluids.size()).isOne();
        assertThat(fluids.resource(0)).isEqualTo(water);
        assertThat(fluids.amount(0)).isEqualTo(1_000L);

        try (Transaction transaction = Transaction.openRoot()) {
            assertThat(items.extract(0, iron, 2L, transaction)).isEqualTo(2L);
            assertThat(items.amount(0)).isEqualTo(3L);
        }

        assertThat(items.amount(0)).isEqualTo(5L);
        assertThat(items.insert(0, iron, 1L, Transaction.getCurrentOpenedTransaction())).isZero();
    }

    @Test
    void requestViewRejectsUnsupportedKeysBeforeExposingAnyMaterial() {
        UnsupportedKey unsupported = new UnsupportedKey();
        KeyCounter inputs = counter(unsupported, 1L);

        assertThatIllegalArgumentException().isThrownBy(() -> AE2ResourceFamilies.ITEM.patternRequestView(
                new KeyCounter[]{inputs}));
        assertThat(inputs.get(unsupported)).isEqualTo(1L);
    }

    @Test
    void requestViewRejectsNegativeAndUnrepresentablePathTotals() {
        AEItemKey iron = AEItemKey.of(Items.IRON_INGOT);
        KeyCounter negative = counter(iron, -1L);
        KeyCounter maximum = counter(iron, Long.MAX_VALUE);
        KeyCounter oneMore = counter(iron, 1L);

        assertThatIllegalArgumentException().isThrownBy(() -> AE2ResourceFamilies.ITEM.patternRequestView(
                new KeyCounter[]{negative}));
        assertThatIllegalArgumentException().isThrownBy(() -> AE2ResourceFamilies.ITEM.patternRequestView(
                new KeyCounter[]{maximum, oneMore}));
        assertThat(maximum.get(iron)).isEqualTo(Long.MAX_VALUE);
        assertThat(oneMore.get(iron)).isOne();
    }

    @Test
    void multiCounterNearMaximumRequestRejectsNativeReturnCapacityWithoutWrapping() {
        ItemResource iron = ItemResource.of(Items.IRON_INGOT);
        AEItemKey ironKey = AEItemKey.of(iron);
        KeyCounter firstPath = counter(ironKey, Long.MAX_VALUE - 2L);
        KeyCounter secondPath = counter(ironKey, 1L);
        PatternRequestResourceStorage<ItemResource> request = AE2ResourceFamilies.ITEM.patternRequestView(
                new KeyCounter[]{firstPath, secondPath});
        PatternProviderReturnInventory inventory = new PatternProviderReturnInventory(() -> {
        });

        assertThat(request.amount(0)).isEqualTo(Long.MAX_VALUE - 1L).isPositive();
        try (Transaction transaction = Transaction.openRoot()) {
            assertThat(request.accept(AE2ResourceFamilies.ITEM.patternReturnView(inventory), transaction)).isFalse();
            transaction.commit();
        }

        assertThat(firstPath.get(ironKey)).isEqualTo(Long.MAX_VALUE - 2L);
        assertThat(secondPath.get(ironKey)).isOne();
        assertThat(inventory.isEmpty()).isTrue();
        assertThatIllegalStateException().isThrownBy(request::size);
    }

    @Test
    void committedRequestConsumptionStaysNonnegativeAndRejectInvalidatesTheView() {
        ItemResource iron = ItemResource.of(Items.IRON_INGOT);
        PatternRequestResourceStorage<ItemResource> request = AE2ResourceFamilies.ITEM.patternRequestView(
                new KeyCounter[]{counter(AEItemKey.of(iron), Long.MAX_VALUE)});

        try (Transaction transaction = Transaction.openRoot()) {
            assertThat(request.extract(0, iron, Long.MAX_VALUE - 1L, transaction)).isEqualTo(Long.MAX_VALUE - 1L);
            transaction.commit();
        }

        assertThat(request.amount(0)).isOne();
        try (Transaction transaction = Transaction.openRoot()) {
            request.reject(transaction);
            transaction.commit();
        }
        assertThatIllegalStateException().isThrownBy(request::size);
    }

    @Test
    void ordinaryLinkedInputsRemainVisibleAlongsideThePatternRequestDuringPlanning() {
        ItemResource iron = ItemResource.of(Items.IRON_INGOT);
        PatternRequestResourceStorage<ItemResource> request = AE2ResourceFamilies.ITEM.patternRequestView(
                new KeyCounter[]{counter(AEItemKey.of(iron), 2L)});
        GenericStackInv linkedInventory = new GenericStackInv(Set.of(AEKeyType.items()), null,
                GenericStackInv.Mode.STORAGE, 1);
        linkedInventory.setStack(0, new GenericStack(AEItemKey.of(iron), 3L));
        ItemResourceStorage linkedInput = new ItemResourceStorage(linkedInventory);

        var result = new RequirementPlanner().plan(
                List.of(new ItemRequirement(RecipeModifier.IOType.INPUT, Ingredient.of(Items.IRON_INGOT), 5,
                        ItemStack.EMPTY)),
                List.of(new ItemBusCapability(request, IOType.INPUT), new ItemBusCapability(linkedInput, IOType.INPUT)),
                new PlanningContext(1L, 0));

        assertThat(result.successful()).isTrue();
        assertThat(result.plan()).isNotNull();
        assertThat(result.plan().commitInputs()).isTrue();
        assertThat(request.amount(0)).isZero();
        assertThat(linkedInput.amount(0)).isZero();
    }

    @Test
    void successfulRequestReturnsExactUnconsumedAmountsBeforeInvalidating() {
        ItemResource iron = ItemResource.of(Items.IRON_INGOT);
        AtomicInteger wakeCount = new AtomicInteger();
        PatternProviderReturnInventory inventory = new PatternProviderReturnInventory(wakeCount::incrementAndGet);
        PatternReturnResourceStorage<ItemResource> returns = AE2ResourceFamilies.ITEM.patternReturnView(inventory);
        PatternRequestResourceStorage<ItemResource> request = AE2ResourceFamilies.ITEM.patternRequestView(
                new KeyCounter[]{counter(AEItemKey.of(iron), 5L)});

        try (Transaction transaction = Transaction.openRoot()) {
            assertThat(request.extract(0, iron, 2L, transaction)).isEqualTo(2L);
            assertThat(request.accept(returns, transaction)).isTrue();
            assertThat(inventory.getAmount(0)).isEqualTo(3L);
        }

        assertThat(inventory.isEmpty()).isTrue();
        assertThat(request.amount(0)).isEqualTo(5L);

        try (Transaction transaction = Transaction.openRoot()) {
            assertThat(request.extract(0, iron, 2L, transaction)).isEqualTo(2L);
            assertThat(request.accept(returns, transaction)).isTrue();
            transaction.commit();
        }

        assertThat(inventory.getStack(0)).isEqualTo(new GenericStack(AEItemKey.of(iron), 3L));
        assertThat(wakeCount).hasPositiveValue();
        assertThat(returns.extract(0, iron, 1L, null)).isZero();
        assertThatIllegalStateException().isThrownBy(request::size);
    }

    @Test
    void fluidReturnViewUsesNativeInventoryForExactReturnsAndCapacityRejection() {
        FluidResource water = FluidResource.of(net.minecraft.world.level.material.Fluids.WATER);
        ItemResource iron = ItemResource.of(Items.IRON_INGOT);
        appeng.api.stacks.AEFluidKey waterKey = appeng.api.stacks.AEFluidKey.of(water);
        PatternProviderReturnInventory returnedInventory = new PatternProviderReturnInventory(() -> {
        });
        returnedInventory.setCapacity(AEKeyType.fluids(), 1_000L);
        PatternReturnResourceStorage<FluidResource> fluidReturns = AE2ResourceFamilies.FLUID.patternReturnView(
                returnedInventory);
        PatternReturnResourceStorage<ItemResource> itemReturns = AE2ResourceFamilies.ITEM.patternReturnView(
                returnedInventory);
        PatternRequestResourceStorage<FluidResource> request = AE2ResourceFamilies.FLUID.patternRequestView(
                new KeyCounter[]{counter(waterKey, 1_000L)});

        try (Transaction transaction = Transaction.openRoot()) {
            assertThat(request.extract(0, water, 400L, transaction)).isEqualTo(400L);
            assertThat(request.accept(fluidReturns, transaction)).isTrue();
            transaction.commit();
        }

        assertThat(returnedInventory.getStack(0)).isEqualTo(new GenericStack(waterKey, 600L));
        assertThat(fluidReturns.resource(0)).isEqualTo(water);
        assertThat(itemReturns.resource(0)).isNull();
        assertThat(itemReturns.isValid(0, iron)).isFalse();

        KeyCounter insufficientInput = counter(waterKey, 1L);
        PatternProviderReturnInventory fullInventory = new PatternProviderReturnInventory(() -> {
        });
        fullInventory.setCapacity(AEKeyType.fluids(), 1L);
        for (int slot = 0; slot < fullInventory.size(); slot++) {
            fullInventory.setStack(slot, new GenericStack(waterKey, 1L));
        }
        PatternRequestResourceStorage<FluidResource> insufficientRequest = AE2ResourceFamilies.FLUID.patternRequestView(
                new KeyCounter[]{insufficientInput});

        try (Transaction transaction = Transaction.openRoot()) {
            assertThat(insufficientRequest.accept(AE2ResourceFamilies.FLUID.patternReturnView(fullInventory), transaction))
                    .isFalse();
            transaction.commit();
        }

        assertThat(insufficientInput.get(waterKey)).isOne();
        for (int slot = 0; slot < fullInventory.size(); slot++) {
            assertThat(fullInventory.getStack(slot)).isEqualTo(new GenericStack(waterKey, 1L));
        }
        assertThatIllegalStateException().isThrownBy(insufficientRequest::size);
    }

    @Test
    void insufficientNativeReturnCapacityRejectsBeforeTheRequestIsAccepted() {
        ItemResource iron = ItemResource.of(Items.IRON_INGOT);
        ItemResource gold = ItemResource.of(Items.GOLD_INGOT);
        KeyCounter inputs = counter(AEItemKey.of(iron), 1L);
        PatternRequestResourceStorage<ItemResource> request = AE2ResourceFamilies.ITEM.patternRequestView(
                new KeyCounter[]{inputs});
        PatternProviderReturnInventory inventory = new PatternProviderReturnInventory(() -> {
        });
        for (int slot = 0; slot < inventory.size(); slot++) {
            inventory.setStack(slot, new GenericStack(AEItemKey.of(gold), 64L));
        }

        try (Transaction transaction = Transaction.openRoot()) {
            assertThat(request.accept(AE2ResourceFamilies.ITEM.patternReturnView(inventory), transaction)).isFalse();
            assertThatIllegalStateException().isThrownBy(request::size);
            transaction.commit();
        }

        assertThat(inputs.get(AEItemKey.of(iron))).isOne();
        for (int slot = 0; slot < inventory.size(); slot++) {
            assertThat(inventory.getStack(slot)).isEqualTo(new GenericStack(AEItemKey.of(gold), 64L));
        }
    }

    @Test
    void returnViewRejectsNegativeAmountsAndUsesTheNativeInventoryCapacity() {
        ItemResource iron = ItemResource.of(Items.IRON_INGOT);
        PatternProviderReturnInventory inventory = new PatternProviderReturnInventory(() -> {
        });
        PatternReturnResourceStorage<ItemResource> returns = AE2ResourceFamilies.ITEM.patternReturnView(inventory);

        assertThat(returns.capacity(0, iron)).isEqualTo(inventory.getMaxAmount(AEItemKey.of(iron)));
        assertThatIllegalArgumentException().isThrownBy(() -> returns.insert(0, iron, -1L, null));
    }

    @Test
    void extendedPatternKindProvidesTheNativeReturnNotificationHost() {
        bindTestEntityType(ExtendedPatternInterfaceKind.INSTANCE);
        var host = ExtendedPatternInterfaceKind.INSTANCE.entityFactory()
                .create(BlockPos.ZERO, Blocks.IRON_BLOCK.defaultBlockState());

        host.onNativeReturnInventoryDrained();

        assertThat(host.craftingMachine()).isNotNull();
    }

    private static KeyCounter counter(AEKey firstKey, long firstAmount) {
        KeyCounter counter = new KeyCounter();
        counter.add(firstKey, firstAmount);
        return counter;
    }

    private static boolean ae2KeyTypesAreInitialized() {
        try {
            return !AEKeyTypes.getAll().isEmpty();
        } catch (IllegalStateException ignored) {
            return false;
        }
    }

    private static void initializeAE2KeyTypes() {
        MappedRegistry<AEKeyType> registry = new MappedRegistry<>(AEKeyType.REGISTRY_KEY, Lifecycle.stable());
        AEKeyTypesInternal.setRegistry(registry);
        Registry.register(registry, AEKeyType.items().getId(), AEKeyType.items());
        Registry.register(registry, AEKeyType.fluids().getId(), AEKeyType.fluids());
        registry.freeze();
    }

    @SuppressWarnings("unchecked")
    private static void bindTestEntityType(IOPortKind kind) {
        ResourceLocation id = MMCR.id(kind.id());
        MappedRegistry<BlockEntityType<?>> registry = (MappedRegistry<BlockEntityType<?>>) BuiltInRegistries.BLOCK_ENTITY_TYPE;
        registry.unfreeze(true);
        try {
            if (!registry.containsKey(id)) {
                Registry.register(registry, id, new BlockEntityType<>(kind.entityFactory(), Blocks.IRON_BLOCK));
            }
        } finally {
            registry.freeze();
        }
        ModBlockEntities.BES.put(kind.id(), DeferredHolder.create(Registries.BLOCK_ENTITY_TYPE, id));
    }

    private static KeyCounter counter(AEKey firstKey, long firstAmount, AEKey secondKey, long secondAmount) {
        KeyCounter counter = counter(firstKey, firstAmount);
        counter.add(secondKey, secondAmount);
        return counter;
    }

    /**
     * Represents an AE2 key family MMCR does not support.
     *
     * @author howxu <dev@howxu.cn>
     */
    private static final class UnsupportedKey extends AEKey {
        @Override
        public AEKeyType getType() {
            return null;
        }

        @Override
        public AEKey dropSecondary() {
            return this;
        }

        @Override
        public void toTag(ValueOutput output) {
        }

        @Override
        public Object getPrimaryKey() {
            return this;
        }

        @Override
        public ResourceLocation getId() {
            return ResourceLocation.fromNamespaceAndPath("test", "unsupported");
        }

        @Override
        public void writeToPacket(RegistryFriendlyByteBuf data) {
        }

        @Override
        protected Component computeDisplayName() {
            return Component.empty();
        }

        @Override
        public void addDrops(long amount, List<ItemStack> drops, Level level, BlockPos pos) {
        }

        @Override
        public boolean hasComponents() {
            return false;
        }
    }
}
