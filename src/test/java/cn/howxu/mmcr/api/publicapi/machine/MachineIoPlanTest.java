package cn.howxu.mmcr.api.publicapi.machine;

import cn.howxu.mmcr.api.capability.CapabilitySnapshot;
import cn.howxu.mmcr.api.capability.CapabilityDirections;
import cn.howxu.mmcr.api.capability.CapabilityRequest;
import cn.howxu.mmcr.api.capability.CapabilityType;
import cn.howxu.mmcr.api.capability.CapabilityView;
import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.plan.CapabilityResult;
import cn.howxu.mmcr.api.capability.plan.CapabilityOperation;
import cn.howxu.mmcr.api.capability.plan.OutputFit;
import cn.howxu.mmcr.api.capability.plan.OutputSimulation;
import cn.howxu.mmcr.api.capability.facet.CapabilityFacet;
import cn.howxu.mmcr.api.capability.facet.EnergyStorageFacet;
import cn.howxu.mmcr.api.capability.facet.FluidHandlerFacet;
import cn.howxu.mmcr.api.capability.facet.ItemHandlerFacet;
import cn.howxu.mmcr.api.publicapi.recipe.EnergyRequirement;
import cn.howxu.mmcr.api.publicapi.recipe.ItemRequirement;
import cn.howxu.mmcr.api.publicapi.recipe.RecipeIo;
import cn.howxu.mmcr.api.publicapi.recipe.RecipeRequirement;
import cn.howxu.mmcr.api.publicapi.recipe.component.DataComponentPredicateSet;
import cn.howxu.mmcr.internal.capability.ItemBusCapability;
import cn.howxu.mmcr.internal.capability.EnergyHatchCapability;
import cn.howxu.mmcr.internal.storage.LongEnergyStorage;
import cn.howxu.mmcr.internal.storage.LongFluidStorage;
import cn.howxu.mmcr.internal.storage.LongItemStorage;
import cn.howxu.mmcr.internal.tile.ExtendedItemBusBlockEntity;
import cn.howxu.mmcr.registry.ModBlockEntities;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.test.TestBootstrap;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
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

    @BeforeEach
    void bootstrapCapabilities() throws Exception {
        TestBootstrap.bootstrapCapabilities();
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
        ItemStack iron = new ItemStack(Items.IRON_INGOT);
        LongItemStorage firstItems = itemStorage(2);
        LongItemStorage secondItems = itemStorage(2);
        insert(firstItems, 0, iron, 1L);
        insert(secondItems, 0, iron, 2L);

        LongFluidStorage firstFluids = new LongFluidStorage(2, 3_000L, null);
        LongFluidStorage secondFluids = new LongFluidStorage(2, 3_000L, null);
        firstFluids.setContents(0, new FluidStack(Fluids.WATER, 1), 1_000L);
        secondFluids.setContents(0, new FluidStack(Fluids.WATER, 1), 1_500L);

        MachineIoView view = view(
                capability(firstItems, IOType.INPUT, List.of("primary")),
                capability(secondItems, IOType.INPUT, List.of("primary")),
                capability(firstFluids, IOType.INPUT, List.of("secondary")),
                capability(secondFluids, IOType.INPUT, List.of("secondary")),
                capability(itemStorage(1), IOType.OUTPUT, List.of("primary")));

        assertThat(view.itemInputs()).singleElement().satisfies(input -> {
            assertThat(input.resource().is(Items.IRON_INGOT)).isTrue();
            assertThat(input.amount()).isEqualTo(3L);
        });
        assertThat(view.fluidInputs()).singleElement().satisfies(input -> {
            assertThat(input.resource().is(Fluids.WATER)).isTrue();
            assertThat(input.amount()).isEqualTo(2_500L);
        });
        assertThat(view.itemAmount(Ingredient.of(Items.IRON_INGOT))).isEqualTo(3L);
        assertThat(view.fluidAmount(FluidIngredient.of(Fluids.WATER))).isEqualTo(2_500L);
        assertThat(view.forTags(Set.of("primary")).itemInputs()).hasSize(1);
        assertThat(view.forTags(Set.of("missing")).itemInputs()).isEmpty();
    }

    @Test
    void aggregates_energy_inputs_and_uses_only_output_capabilities_for_capacity() {
        LongEnergyStorage first = new LongEnergyStorage(100L, 100L, null);
        LongEnergyStorage second = new LongEnergyStorage(200L, 100L, null);
        first.setAmount(30L);
        second.setAmount(40L);
        LongItemStorage inputItems = itemStorage(1);
        LongItemStorage outputItems = itemStorage(1);
        LongEnergyStorage outputEnergy = new LongEnergyStorage(500L, 100L, null);
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
        ItemStack normal = new ItemStack(Items.IRON_INGOT);
        ItemStack componentStack = new ItemStack(Items.IRON_INGOT);
        componentStack.set(DataComponents.MAX_STACK_SIZE, 16);
        ItemStack componentResource = componentStack.copyWithCount(1);
        LongItemStorage first = itemStorage(1);
        LongItemStorage second = itemStorage(1);
        insert(first, 0, normal, 1L);
        insert(second, 0, componentResource, 2L);

        MachineIoView view = view(
                capability(first, IOType.INPUT, List.of()),
                capability(second, IOType.INPUT, List.of()));

        assertThat(view.itemInputs()).hasSize(2);
        assertThat(view.itemInputs()).anySatisfy(input -> {
            assertThat(ItemStack.isSameItemSameComponents(input.resource(), normal)).isTrue();
            assertThat(input.amount()).isEqualTo(1L);
        }).anySatisfy(input -> {
            assertThat(ItemStack.isSameItemSameComponents(input.resource(), componentResource)).isTrue();
            assertThat(input.amount()).isEqualTo(2L);
        });
        assertThat(view.itemAmount(Ingredient.of(Items.IRON_INGOT))).isEqualTo(3L);
    }

    @Test
    void counts_only_valid_output_slots_and_respects_item_stack_size() {
        RejectingItemStorage storage = new RejectingItemStorage(2, 100L);
        ItemStack gold = stack(Items.GOLD_NUGGET, 64);
        ItemStack iron = stack(Items.IRON_INGOT, 64);
        insert(storage, 0, gold, 10L);

        MachineIoView view = view(capability(storage, IOType.OUTPUT, List.of()));

        assertThat(view.itemOutputCapacity(gold)).isEqualTo(54L);
        assertThat(view.itemOutputCapacity(iron)).isZero();
    }

    @Test
    void extended_item_output_capacity_ignores_vanilla_stack_size_for_data_bearing_items() {
        ExtendedItemBusBlockEntity bus = (ExtendedItemBusBlockEntity) ModBlockEntities.BES
                .get("extended_item_output_bus_basic").get().create(
                        BlockPos.ZERO,
                        ModBlocks.BLOCKS.get("extended_item_output_bus_basic").get().defaultBlockState());
        ItemStack output = new ItemStack(Items.IRON_INGOT, 96);
        output.set(DataComponents.CUSTOM_NAME, Component.literal("data output"));

        MachineIoView view = new MachineIoView(bus.capabilitySnapshot());

        assertThat(view.itemOutputCapacity(output)).isEqualTo(Long.MAX_VALUE);
    }

    @Test
    void output_capacity_requires_matching_resource_for_non_empty_zero_quantity_slots() {
        ItemStack ironStack = stack(Items.IRON_INGOT, 64);
        ItemStack iron = ironStack.copyWithCount(1);
        ZeroQuantityItemStorage storage = new ZeroQuantityItemStorage(iron);

        MachineIoView view = view(capability(storage, IOType.OUTPUT, List.of()));

        assertThat(view.itemOutputCapacity(new ItemStack(Items.GOLD_NUGGET))).isZero();
        assertThat(view.itemOutputCapacity(ironStack)).isEqualTo(64L);
    }

    @Test
    void counts_same_fluid_slots_and_empty_slots_but_not_different_fluids() {
        LongFluidStorage storage = new LongFluidStorage(3, 2_000L, null);
        storage.setContents(0, new FluidStack(Fluids.WATER, 1), 500L);
        storage.setContents(1, new FluidStack(Fluids.LAVA, 1), 500L);

        MachineIoView view = view(capability(storage, IOType.OUTPUT, List.of()));

        assertThat(view.fluidOutputCapacity(new FluidStack(Fluids.WATER, 1_000))).isEqualTo(3_500L);
        assertThat(view.fluidOutputCapacity(new FluidStack(Fluids.LAVA, 1_000))).isEqualTo(3_500L);
    }

    @Test
    void empty_storage_returns_empty_inputs_and_zero_output_capacity() {
        LongItemStorage items = itemStorage(2);
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
        LongItemStorage storage = itemStorage(1);
        ItemStack iron = new ItemStack(Items.IRON_INGOT);
        insert(storage, 0, iron, 1L);
        MachineIoView view = view(capability(storage, IOType.INPUT, List.of()));

        assertThatThrownBy(() -> view.itemInputs().clear())
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(view.itemInputs().getFirst().resource()).isNotSameAs(iron);
        assertThat(ItemStack.isSameItemSameComponents(view.itemInputs().getFirst().resource(), iron)).isTrue();
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
        assertThatThrownBy(() -> new MachineIoView.ResourceAmount<ItemStack>(null, 0L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MachineIoView.ResourceAmount<>(ItemStack.EMPTY, -1L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void simulate_is_read_only_and_commit_requires_a_successful_simulation() {
        LongEnergyStorage energy = new LongEnergyStorage(100L, 100L, null);
        energy.setAmount(10L);
        MachineIoPlan notSimulated = new MachineIoPlan(new CapabilitySnapshot(List.of(
                new EnergyHatchCapability(energy, IOType.INPUT))));
        notSimulated.addInput(new EnergyRequirement(4));

        assertThat(notSimulated.commit().successful()).isFalse();
        assertThat(energy.getAmountAsLong()).isEqualTo(10L);
        MachineIoPlan plan = new MachineIoPlan(new CapabilitySnapshot(List.of(
                new EnergyHatchCapability(energy, IOType.INPUT))));
        plan.addInput(new EnergyRequirement(4));
        assertThat(plan.simulate().energySatisfied()).isTrue();
        assertThat(energy.getAmountAsLong()).isEqualTo(10L);
        assertThat(plan.commit().successful()).isTrue();
        assertThat(plan.commit().successful()).isFalse();
        assertThat(energy.getAmountAsLong()).isEqualTo(6L);
    }

    @Test
    void output_requests_aggregate_across_capabilities_and_require_full_blocks_commit() {
        LongItemStorage first = itemStorage(1);
        LongItemStorage second = itemStorage(1);
        ItemStack output = stack(Items.GOLD_NUGGET, 64);
        output.setCount(4);
        ItemStack gold = output.copyWithCount(1);
        insert(first, 0, gold, 63L);
        insert(second, 0, gold, 63L);
        MachineIoPlan plan = new MachineIoPlan(new CapabilitySnapshot(List.of(
                new ItemBusCapability(first, IOType.OUTPUT),
                new ItemBusCapability(second, IOType.OUTPUT))));
        plan.addOutput(itemOutput(output),
                OutputPolicy.REQUIRE_FULL);

        MachineIoPlan.Simulation simulation = plan.simulate();
        assertThat(simulation.outputs()).containsExactly(new OutputSimulation(4L, 2L, OutputFit.PARTIAL));
        assertThat(simulation.failure()).isNotNull();
        assertThat(plan.commit().successful()).isFalse();
        assertThat(first.amount(0)).isEqualTo(63L);
        assertThat(second.amount(0)).isEqualTo(63L);
    }

    @Test
    void allow_partial_commits_the_accepted_output_amount() {
        LongItemStorage first = itemStorage(1);
        ItemStack output = stack(Items.GOLD_NUGGET, 64);
        output.setCount(4);
        ItemStack gold = output.copyWithCount(1);
        insert(first, 0, gold, 63L);
        MachineIoPlan plan = new MachineIoPlan(new CapabilitySnapshot(List.of(
                new ItemBusCapability(first, IOType.OUTPUT))));
        plan.addOutput(itemOutput(output),
                OutputPolicy.ALLOW_PARTIAL);

        assertThat(plan.simulate().outputs())
                .containsExactly(new OutputSimulation(4L, 1L, OutputFit.PARTIAL));
        assertThat(plan.commit().successful()).isTrue();
        assertThat(first.amount(0)).isEqualTo(64L);
    }

    @Test
    void reverse_addition_keeps_inputs_before_outputs_and_preserves_output_policy() {
        LongItemStorage inputStorage = itemStorage(1);
        LongItemStorage outputStorage = itemStorage(1);
        ItemStack outputStack = stack(Items.GOLD_NUGGET, 64);
        outputStack.setCount(4);
        insert(inputStorage, 0, new ItemStack(Items.IRON_INGOT), 1L);
        insert(outputStorage, 0, outputStack, 63L);
        LongEnergyStorage energyStorage = new LongEnergyStorage(100L, 100L, null);
        energyStorage.setAmount(4L);

        RecipeRequirement itemInput = new ItemRequirement(RecipeIo.INPUT, Ingredient.of(Items.IRON_INGOT), 1,
                ItemStack.EMPTY, 1F, DataComponentPredicateSet.EMPTY, 1F);
        RecipeRequirement energyInput = new EnergyRequirement(4);
        RecipeRequirement output = itemOutput(outputStack);
        MachineIoPlan plan = new MachineIoPlan(new CapabilitySnapshot(List.of(
                new ItemBusCapability(outputStorage, IOType.OUTPUT),
                new ItemBusCapability(inputStorage, IOType.INPUT),
                new EnergyHatchCapability(energyStorage, IOType.INPUT))));

        plan.addOutput(output, OutputPolicy.ALLOW_PARTIAL)
                .addInput(itemInput)
                .addInput(energyInput);

        assertThat(plan.requirements()).containsExactly(itemInput, energyInput, output);
        assertThat(plan.simulate())
                .satisfies(simulation -> {
                    assertThat(simulation.inputsSatisfied()).isTrue();
                    assertThat(simulation.energySatisfied()).isTrue();
                    assertThat(simulation.outputs()).containsExactly(
                            new OutputSimulation(4L, 1L, OutputFit.PARTIAL));
                    assertThat(simulation.failure()).isNull();
                });
        assertThat(plan.commit().successful()).isTrue();
        assertThat(inputStorage.amount(0)).isZero();
        assertThat(energyStorage.getAmountAsLong()).isZero();
        assertThat(outputStorage.amount(0)).isEqualTo(64L);
    }

    @Test
    void reports_full_and_none_output_fits() {
        ItemStack output = stack(Items.GOLD_NUGGET, 64);
        output.setCount(4);
        LongItemStorage fullStorage = itemStorage(1);
        MachineIoPlan fullPlan = new MachineIoPlan(new CapabilitySnapshot(List.of(
                new ItemBusCapability(fullStorage, IOType.OUTPUT))));
        fullPlan.addOutput(itemOutput(output), OutputPolicy.REQUIRE_FULL);

        assertThat(fullPlan.simulate().outputs())
                .containsExactly(new OutputSimulation(4L, 4L, OutputFit.FULL));
        assertThat(fullPlan.simulate().failure()).isNull();

        LongItemStorage noneStorage = itemStorage(1);
        insert(noneStorage, 0, new ItemStack(Items.COBBLESTONE), 64L);
        MachineIoPlan nonePlan = new MachineIoPlan(new CapabilitySnapshot(List.of(
                new ItemBusCapability(noneStorage, IOType.OUTPUT))));
        nonePlan.addOutput(itemOutput(output), OutputPolicy.REQUIRE_FULL);

        assertThat(nonePlan.simulate().outputs())
                .containsExactly(new OutputSimulation(4L, 0L, OutputFit.NONE));
        assertThat(nonePlan.simulate().failure()).isNotNull();
        assertThat(nonePlan.commit().successful()).isFalse();
    }

    @Test
    void add_input_and_output_reject_the_wrong_direction() {
        MachineIoPlan plan = new MachineIoPlan(new CapabilitySnapshot(List.of()));

        assertThatThrownBy(() -> plan.addInput(itemOutput(new ItemStack(Items.IRON_INGOT))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> plan.addOutput(new EnergyRequirement(1), OutputPolicy.REQUIRE_FULL))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static MachineIoView view(MachineCapability... capabilities) {
        return new MachineIoView(new CapabilitySnapshot(List.of(capabilities)));
    }

    private static ItemRequirement itemOutput(ItemStack stack) {
        return new ItemRequirement(RecipeIo.OUTPUT, null, 0, stack, 1F, DataComponentPredicateSet.EMPTY, 1F);
    }

    private static MachineCapability capability(Object storage, IOType ioType, List<String> tags) {
        return new TestCapability(storage, ioType, tags);
    }

    private static LongItemStorage itemStorage(int slots) {
        return new LongItemStorage(slots, 100L, () -> {});
    }

    private static ItemStack stack(Item item, int maxStackSize) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.MAX_STACK_SIZE, maxStackSize);
        return stack;
    }

    private static void insert(LongItemStorage storage, int slot, ItemStack resource, long amount) {
        storage.forceInsert(slot, resource, amount, false);
    }

    private record TestCapability(Object value, IOType direction, List<String> tags)
            implements MachineCapability, ItemHandlerFacet, FluidHandlerFacet, EnergyStorageFacet {
        private TestCapability {
            tags = List.copyOf(tags);
        }

        @Override
        public CapabilityType type() {
            return new CapabilityType(ResourceLocation.fromNamespaceAndPath("mmcr_test", "machine_io"));
        }

        @Override
        public CapabilityDirections directions() {
            return CapabilityDirections.of(direction);
        }

        @Override
        public CapabilityView view() {
            return new CapabilityView() {
                @Override
                public CapabilityType type() {
                    return TestCapability.this.type();
                }

                @Override
                public CapabilityDirections directions() {
                    return TestCapability.this.directions();
                }

                @Override
                public List<String> tags() {
                    return TestCapability.this.tags();
                }

                @Override
                public Set<Class<? extends CapabilityFacet>> facets() {
                    if (value instanceof IItemHandler) return Set.of(ItemHandlerFacet.class);
                    if (value instanceof IFluidHandler) return Set.of(FluidHandlerFacet.class);
                    if (value instanceof IEnergyStorage) return Set.of(EnergyStorageFacet.class);
                    return Set.of();
                }
            };
        }

        @Override
        public IItemHandler itemHandler() { return (IItemHandler) value; }

        @Override
        public IFluidHandler fluidHandler() { return (IFluidHandler) value; }

        @Override
        public IEnergyStorage energyStorage() { return (IEnergyStorage) value; }

        @Override
        public CapabilityOperation prepare(CapabilityRequest request) {
            return CapabilityResult::successful;
        }
    }

    private static final class RejectingItemStorage extends LongItemStorage {
        private RejectingItemStorage(int slots, long capacity) {
            super(slots, capacity, () -> {});
        }

        @Override
        public boolean isItemValid(int slot, ItemStack resource) {
            return slot != 1 && super.isItemValid(slot, resource);
        }
    }

    private static final class ZeroQuantityItemStorage extends LongItemStorage {
        private final ItemStack slotResource;

        private ZeroQuantityItemStorage(ItemStack slotResource) {
            super(1, 64L, () -> {});
            this.slotResource = slotResource;
        }

        @Override
        public ItemStack resource(int slot) {
            return slot == 0 ? slotResource : super.resource(slot);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack resource) {
            return true;
        }
    }

}
