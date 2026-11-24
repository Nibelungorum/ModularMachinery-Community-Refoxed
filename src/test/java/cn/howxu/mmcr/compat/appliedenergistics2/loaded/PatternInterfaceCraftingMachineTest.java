package cn.howxu.mmcr.compat.appliedenergistics2.loaded;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.AEKeyTypes;
import appeng.api.stacks.AEKeyTypesInternal;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.core.definitions.AEItems;
import appeng.core.definitions.ItemDefinition;
import appeng.crafting.pattern.AEProcessingPattern;
import appeng.items.misc.MissingContentItem;
import cn.howxu.mmcr.LevelStub;
import cn.howxu.mmcr.test.TestBootstrap;
import com.mojang.serialization.Lifecycle;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Standard intake validates the supplied keys, not an inferred multiplier.
 * @author HowXu {@literal <dev@howxu.cn>}
 */
class PatternInterfaceCraftingMachineTest {
    @BeforeAll
    static void setup() throws Exception {
        TestBootstrap.bootstrap();
        TestBootstrap.bootstrapCapabilities();
        if (!ae2KeyTypesAreInitialized()) initializeAE2KeyTypes();
        bindMissingContentItem();
    }

    @Test
    void validatesActualSlotKeysWithoutReconstructingPatternQuantities() {
        var pattern = mixedProcessingPattern();
        var level = LevelStub.createWithBlockEntities(List.of());
        KeyCounter iron = counter(Items.IRON_INGOT, 2_147_483_648L);
        KeyCounter water = new KeyCounter();
        water.add(AEFluidKey.of(FluidResource.of(Fluids.WATER)), 1);
        assertThat(PatternInterfaceCraftingMachine.validInputs(pattern, new KeyCounter[]{iron, water}, level)).isTrue();
        assertThat(PatternInterfaceCraftingMachine.validInputs(pattern, new KeyCounter[]{iron, new KeyCounter()}, level)).isTrue();
        assertThat(PatternInterfaceCraftingMachine.validInputs(pattern, new KeyCounter[]{water, iron}, level)).isFalse();
        assertThat(PatternInterfaceCraftingMachine.validInputs(pattern, new KeyCounter[]{iron}, level)).isFalse();
        assertThat(PatternInterfaceCraftingMachine.validInputs(pattern, new KeyCounter[]{new KeyCounter(), new KeyCounter()}, level)).isFalse();
        assertThat(iron.get(AEItemKey.of(Items.IRON_INGOT))).isEqualTo(2_147_483_648L);
    }

    @Test
    void projectionsRespectPerKeyHeadroomAcrossSlotsAndNeverEditTheCallersCounters() {
        var iron = AEItemKey.of(Items.IRON_INGOT);
        var water = AEFluidKey.of(FluidResource.of(Fluids.WATER));
        var a = counter(Items.IRON_INGOT, 3);
        var b = counter(Items.IRON_INGOT, 4);
        b.add(water, 11);
        var slots = new KeyCounter[]{a, b};
        var sizing = PatternBulkSizing.from(slots, Map.of(iron, Long.MAX_VALUE - 15, water, 1L), Long.MAX_VALUE);
        assertThat(sizing.maxCopies()).isEqualTo(2);
        var projection = sizing.projected(2)[0];
        assertThat(projection.get(iron)).isEqualTo(Long.MAX_VALUE - 1);
        assertThat(projection.get(water)).isEqualTo(23);
        assertThat(a.get(iron)).isEqualTo(3);
        assertThat(b.get(iron)).isEqualTo(4);
        b.set(iron, Long.MAX_VALUE);
        assertThat(PatternBulkSizing.from(slots, Map.of(), 50).maxCopies()).isZero();
    }

    @Test
    void longOutputAmountDoesNotChangeIdentity() {
        var key = AEItemKey.of(Items.GOLD_INGOT);
        var hints = PatternInterfaceCraftingMachine.outputs(List.of(new GenericStack(key, Long.MAX_VALUE)));
        var unit = PatternInterfaceCraftingMachine.outputs(List.of(new GenericStack(key, 1)));
        assertThat(hints).singleElement().satisfies(hint -> assertThat(hint.matchesPatternResource(unit.getFirst())).isTrue());
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
    private static void bindMissingContentItem() throws ReflectiveOperationException {
        MappedRegistry<Item> registry = (MappedRegistry<Item>) BuiltInRegistries.ITEM;
        registry.unfreeze(true);
        try {
            Item item = registry.getValue(AEItems.MISSING_CONTENT.id());
            if (item == null) {
                item = new MissingContentItem(new Item.Properties());
                Registry.register(registry, AEItems.MISSING_CONTENT.id(), item);
            }
            Field itemField = ItemDefinition.class.getDeclaredField("item");
            itemField.setAccessible(true);
            DeferredItem<?> deferredItem = (DeferredItem<?>) itemField.get(AEItems.MISSING_CONTENT);
            Field holder = DeferredHolder.class.getDeclaredField("holder");
            holder.setAccessible(true);
            holder.set(deferredItem, Holder.direct(item));
        } finally {
            registry.freeze();
        }
    }

    private static AEProcessingPattern mixedProcessingPattern() {
        ItemStack definition = new ItemStack(Items.PAPER);
        AEProcessingPattern.encode(definition,
                List.of(new GenericStack(AEItemKey.of(Items.IRON_INGOT), 5L),
                        new GenericStack(AEFluidKey.of(FluidResource.of(Fluids.WATER)), 1_000L)),
                List.of(new GenericStack(AEItemKey.of(Items.IRON_NUGGET), 2L)));
        return new AEProcessingPattern(AEItemKey.of(definition));
    }

    private static KeyCounter counter(Item item, long amount) {
        KeyCounter counter = new KeyCounter();
        counter.add(AEItemKey.of(ItemResource.of(item)), amount);
        return counter;
    }

}
