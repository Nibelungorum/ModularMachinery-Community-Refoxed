package cn.howxu.mmcr.api.publicapi.machine;

import cn.howxu.mmcr.api.capability.CapabilitySnapshot;
import cn.howxu.mmcr.api.capability.CapabilityRequest;
import cn.howxu.mmcr.api.capability.CapabilityType;
import cn.howxu.mmcr.api.capability.CapabilityView;
import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.plan.CapabilityResult;
import cn.howxu.mmcr.api.capability.plan.CapabilityOperation;
import cn.howxu.mmcr.api.capability.plan.OutputFit;
import cn.howxu.mmcr.api.capability.plan.OutputPolicy;
import cn.howxu.mmcr.api.capability.plan.OutputSimulation;
import cn.howxu.mmcr.api.capability.storage.CapabilityStorage;
import cn.howxu.mmcr.api.capability.storage.LongValueStorage;
import cn.howxu.mmcr.api.capability.storage.ResourceStorage;
import cn.howxu.mmcr.internal.storage.LongFluidStorage;
import cn.howxu.mmcr.internal.storage.LongResourceStorage;
import cn.howxu.mmcr.test.TestBootstrap;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies the read-only machine I/O view and output simulation metadata.
 *
 * @author howxu <dev@howxu.cn>
 */
class MachineIoPlanTest {
    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    @Test
    void output_simulation_validates_metadata_and_machine_view_is_constructible() {
        assertThat(new OutputSimulation(4L, 2L, OutputFit.PARTIAL))
                .isEqualTo(new OutputSimulation(4L, 2L, OutputFit.PARTIAL));
        assertThat(OutputPolicy.values()).containsExactly(OutputPolicy.REQUIRE_FULL, OutputPolicy.ALLOW_PARTIAL);
        assertThat(new MachineIoView(new CapabilitySnapshot(List.of()))).isNotNull();
    }

    @Test
    void aggregates_inputs_across_independent_capabilities_and_filters_direction() {
        ItemResource iron = ItemResource.of(Items.IRON_INGOT);
        LongResourceStorage<ItemResource> firstItems = itemStorage(2);
        LongResourceStorage<ItemResource> secondItems = itemStorage(2);
        insert(firstItems, 0, iron, 1L);
        insert(secondItems, 0, iron, 2L);

        LongFluidStorage firstFluids = new LongFluidStorage(2, 3_000L, null);
        LongFluidStorage secondFluids = new LongFluidStorage(2, 3_000L, null);
        firstFluids.setContents(0, FluidResource.of(Fluids.WATER), 1_000L);
        secondFluids.setContents(0, FluidResource.of(Fluids.WATER), 1_500L);

        MachineIoView view = view(
                capability(firstItems, IOType.INPUT, List.of("primary")),
                capability(secondItems, IOType.INPUT, List.of("primary")),
                capability(firstFluids, IOType.INPUT, List.of("secondary")),
                capability(secondFluids, IOType.INPUT, List.of("secondary")),
                capability(itemStorage(1), IOType.OUTPUT, List.of("primary")));

        assertThat(view.itemInputs()).containsExactly(new MachineIoView.ResourceAmount<>(iron, 3L));
        assertThat(view.fluidInputs()).containsExactly(new MachineIoView.ResourceAmount<>(
                FluidResource.of(Fluids.WATER), 2_500L));
        assertThat(view.itemAmount(Ingredient.of(Items.IRON_INGOT))).isEqualTo(3L);
        assertThat(view.fluidAmount(FluidIngredient.of(Fluids.WATER))).isEqualTo(2_500L);
        assertThat(view.forTags(Set.of("primary")).itemInputs()).hasSize(1);
        assertThat(view.forTags(Set.of("missing")).itemInputs()).isEmpty();
    }

    @Test
    void aggregates_energy_inputs_and_uses_only_output_capabilities_for_capacity() {
        LongValueStorage first = new LongValueStorage(100L, 100L, null);
        LongValueStorage second = new LongValueStorage(200L, 100L, null);
        first.setAmount(30L);
        second.setAmount(40L);
        LongResourceStorage<ItemResource> inputItems = itemStorage(1);
        LongResourceStorage<ItemResource> outputItems = itemStorage(1);
        LongValueStorage outputEnergy = new LongValueStorage(500L, 100L, null);
        outputEnergy.setAmount(125L);
        ItemStack gold = stack(Items.GOLD_NUGGET, 64);

        MachineIoView view = view(
                capability(first, IOType.INPUT, List.of()),
                capability(second, IOType.INPUT, List.of()),
                capability(inputItems, IOType.INPUT, List.of()),
                capability(outputItems, IOType.OUTPUT, List.of()),
                capability(outputEnergy, IOType.OUTPUT, List.of()));

        assertThat(view.energyInput()).isEqualTo(70L);
        assertThat(view.energyOutputCapacity()).isEqualTo(375L);
        assertThat(view.itemOutputCapacity(gold)).isEqualTo(64L);
        assertThat(view.forTags(Set.of("primary")).energyInput()).isZero();
    }

    @Test
    void keeps_item_resources_with_different_components_separate() {
        ItemResource normal = ItemResource.of(Items.IRON_INGOT);
        ItemStack componentStack = new ItemStack(Items.IRON_INGOT);
        componentStack.set(DataComponents.MAX_STACK_SIZE, 16);
        ItemResource componentResource = ItemResource.of(componentStack);
        LongResourceStorage<ItemResource> first = itemStorage(1);
        LongResourceStorage<ItemResource> second = itemStorage(1);
        insert(first, 0, normal, 1L);
        insert(second, 0, componentResource, 2L);

        MachineIoView view = view(
                capability(first, IOType.INPUT, List.of()),
                capability(second, IOType.INPUT, List.of()));

        assertThat(view.itemInputs()).containsExactlyInAnyOrder(
                new MachineIoView.ResourceAmount<>(normal, 1L),
                new MachineIoView.ResourceAmount<>(componentResource, 2L));
        assertThat(view.itemAmount(Ingredient.of(Items.IRON_INGOT))).isEqualTo(3L);
    }

    @Test
    void counts_only_valid_output_slots_and_respects_item_stack_size() {
        RejectingItemStorage storage = new RejectingItemStorage(2, 100L);
        ItemStack gold = stack(Items.GOLD_NUGGET, 64);
        ItemStack iron = stack(Items.IRON_INGOT, 64);
        insert(storage, 0, ItemResource.of(gold), 10L);

        MachineIoView view = view(capability(storage, IOType.OUTPUT, List.of()));

        assertThat(view.itemOutputCapacity(gold)).isEqualTo(54L);
        assertThat(view.itemOutputCapacity(iron)).isZero();
    }

    @Test
    void counts_same_fluid_slots_and_empty_slots_but_not_different_fluids() {
        LongFluidStorage storage = new LongFluidStorage(3, 2_000L, null);
        storage.setContents(0, FluidResource.of(Fluids.WATER), 500L);
        storage.setContents(1, FluidResource.of(Fluids.LAVA), 500L);

        MachineIoView view = view(capability(storage, IOType.OUTPUT, List.of()));

        assertThat(view.fluidOutputCapacity(new FluidStack(Fluids.WATER, 1_000))).isEqualTo(3_500L);
        assertThat(view.fluidOutputCapacity(new FluidStack(Fluids.LAVA, 1_000))).isEqualTo(3_500L);
    }

    @Test
    void empty_storage_returns_empty_inputs_and_zero_output_capacity() {
        LongResourceStorage<ItemResource> items = itemStorage(2);
        LongFluidStorage fluids = new LongFluidStorage(2, 2_000L, null);

        MachineIoView view = view(
                capability(items, IOType.INPUT, List.of()),
                capability(fluids, IOType.INPUT, List.of()));

        assertThat(view.itemInputs()).isEmpty();
        assertThat(view.fluidInputs()).isEmpty();
        assertThat(view.itemOutputCapacity(new ItemStack(Items.IRON_INGOT))).isZero();
        assertThat(view.fluidOutputCapacity(new FluidStack(Fluids.WATER, 1_000))).isZero();
    }

    @Test
    void returned_lists_and_resource_amounts_are_immutable() {
        LongResourceStorage<ItemResource> storage = itemStorage(1);
        ItemResource iron = ItemResource.of(Items.IRON_INGOT);
        insert(storage, 0, iron, 1L);
        MachineIoView view = view(capability(storage, IOType.INPUT, List.of()));

        assertThatThrownBy(() -> view.itemInputs().clear())
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(view.itemInputs().getFirst().resource()).isSameAs(iron);
    }

    @Test
    void output_simulation_rejects_invalid_ranges() {
        assertThatThrownBy(() -> new OutputSimulation(-1L, 0L, OutputFit.NONE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new OutputSimulation(1L, 2L, OutputFit.PARTIAL))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new OutputSimulation(1L, 0L, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void resource_amount_rejects_null_resources_and_negative_amounts() {
        assertThatThrownBy(() -> new MachineIoView.ResourceAmount<ItemResource>(null, 0L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MachineIoView.ResourceAmount<>(ItemResource.EMPTY, -1L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static MachineIoView view(MachineCapability... capabilities) {
        return new MachineIoView(new CapabilitySnapshot(List.of(capabilities)));
    }

    private static MachineCapability capability(Object storage, IOType ioType, List<String> tags) {
        return new TestCapability(storage, ioType, tags);
    }

    private static LongResourceStorage<ItemResource> itemStorage(int slots) {
        return new LongResourceStorage<>(ItemResource.class, slots, 100L,
                ItemResource::isEmpty, () -> {});
    }

    private static ItemStack stack(Item item, int maxStackSize) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.MAX_STACK_SIZE, maxStackSize);
        return stack;
    }

    private static void insert(ResourceStorage<ItemResource> storage, int slot,
                               ItemResource resource, long amount) {
        try (Transaction transaction = Transaction.openRoot()) {
            storage.insert(slot, resource, amount, transaction);
            transaction.commit();
        }
    }

    private record TestCapability(Object value, IOType ioType, List<String> tags) implements MachineCapability {
        private TestCapability {
            tags = List.copyOf(tags);
        }

        @Override
        public CapabilityType type() {
            return new CapabilityType(Identifier.fromNamespaceAndPath("mmcr_test", "machine_io"));
        }

        @Override
        public CapabilityView view() {
            return new CapabilityView() {
                @Override
                public CapabilityType type() {
                    return TestCapability.this.type();
                }

                @Override
                public IOType ioType() {
                    return TestCapability.this.ioType();
                }

                @Override
                public List<String> tags() {
                    return TestCapability.this.tags();
                }
            };
        }

        @Override
        public CapabilityStorage storage() {
            return (CapabilityStorage) value;
        }

        @Override
        public CapabilityOperation prepare(CapabilityRequest request) {
            return transaction -> CapabilityResult.successful();
        }
    }

    private static final class RejectingItemStorage extends LongResourceStorage<ItemResource> {
        private RejectingItemStorage(int slots, long capacity) {
            super(ItemResource.class, slots, capacity, ItemResource::isEmpty, () -> {});
        }

        @Override
        public boolean isValid(int slot, ItemResource resource) {
            return slot != 1 && super.isValid(slot, resource);
        }
    }

}
