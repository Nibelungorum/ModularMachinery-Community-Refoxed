package cn.howxu.mmcr.internal.capability;

import cn.howxu.mmcr.api.capability.CapabilityHost;
import cn.howxu.mmcr.api.capability.CapabilityRequest;
import cn.howxu.mmcr.api.capability.CapabilitySnapshot;
import cn.howxu.mmcr.api.capability.CapabilityType;
import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.facet.ResourceFacet;
import cn.howxu.mmcr.api.capability.facet.ValueFacet;
import cn.howxu.mmcr.api.capability.type.CapabilityCreationContext;
import cn.howxu.mmcr.api.capability.type.CapabilityRegistry;
import cn.howxu.mmcr.api.capability.storage.LongValueStorage;
import cn.howxu.mmcr.api.capability.storage.ResourceStorage;
import cn.howxu.mmcr.api.port.PortDefinition;
import cn.howxu.mmcr.api.capability.plan.CapabilityOperation;
import cn.howxu.mmcr.api.capability.plan.CapabilityResult;
import cn.howxu.mmcr.api.capability.plan.CapabilityRequests;
import cn.howxu.mmcr.internal.port.IOPortKind;
import cn.howxu.mmcr.internal.storage.LongFluidStorage;
import cn.howxu.mmcr.internal.storage.LongResourceStorage;
import cn.howxu.mmcr.internal.tile.IOPortBlockEntity;
import cn.howxu.mmcr.registry.ModBlockEntities;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.registry.PortKinds;
import cn.howxu.mmcr.test.TestBootstrap;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.lang.reflect.Modifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies the capability host boundary and the built-in port capabilities.
 *
 * @author howxu <dev@howxu.cn>
 */
class CapabilityHostTest {
    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    @BeforeEach
    void bootstrapCapabilities() throws Exception {
        TestBootstrap.bootstrapCapabilities();
    }

    @Test
    void host_snapshot_is_immutable_and_keeps_capability_identity() {
        IOPortBlockEntity port = new MixedPort(BlockPos.ZERO,
                ModBlocks.BLOCKS.get("item_input_bus").get().defaultBlockState());
        CapabilityHost host = port;

        CapabilitySnapshot first = host.capabilitySnapshot();
        CapabilitySnapshot second = host.capabilitySnapshot();

        assertThat(first).isSameAs(second);
        assertThat(first.capabilities()).hasSize(2);
        assertThat(first.capabilities().get(0)).isSameAs(second.capabilities().get(0));
        assertThatThrownBy(() -> first.capabilities().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void port_base_declares_capability_snapshot_as_abstract() throws NoSuchMethodException {
        assertThat(Modifier.isAbstract(IOPortBlockEntity.class.getMethod("capabilitySnapshot").getModifiers())).isTrue();
    }

    @Test
    void item_capability_uses_slot_resource_storage_and_operation_contract() {
        MachineCapability capability = port("item_input_bus").capabilitySnapshot().capabilities().getFirst();
        ItemBusCapability item = (ItemBusCapability) capability;
        ResourceStorage<ItemResource> storage = item.storage();

        assertThat(storage.size()).isGreaterThan(1);
        ItemResource iron = ironResource();
        try (Transaction transaction = Transaction.openRoot()) {
            assertThat(storage.insert(0, iron, 3L, transaction))
                    .isEqualTo(3L);
            assertThat(item.prepare(request(item)).commit(transaction).success()).isTrue();
            transaction.commit();
        }

        assertThat(storage.amount(0)).isEqualTo(3L);
        assertThat(storage.resource(0)).isEqualTo(iron);
    }

    @Test
    void fluid_capability_uses_long_fluid_storage() {
        MachineCapability capability = port("fluid_input_hatch").capabilitySnapshot().capabilities().getFirst();
        FluidHatchCapability fluid = (FluidHatchCapability) capability;
        ResourceStorage<FluidResource> storage = fluid.storage();

        assertThat(fluid.storage()).isInstanceOf(cn.howxu.mmcr.internal.storage.LongFluidStorage.class);
        try (Transaction transaction = Transaction.openRoot()) {
            assertThat(storage.insert(0, FluidResource.of(net.minecraft.world.level.material.Fluids.WATER), 750L, transaction))
                    .isEqualTo(750L);
            transaction.commit();
        }

        assertThat(storage.amount(0)).isEqualTo(750L);
        assertThat(storage.resource(0)).isEqualTo(FluidResource.of(net.minecraft.world.level.material.Fluids.WATER));
    }

    @Test
    void energy_capability_uses_long_value_storage() {
        MachineCapability capability = port("energy_input_hatch_tiny").capabilitySnapshot().capabilities().getFirst();
        EnergyHatchCapability energy = (EnergyHatchCapability) capability;
        LongValueStorage storage = energy.storage();

        assertThat(storage.insert(2_000L, false)).isEqualTo(500L);
        assertThat(storage.amount()).isEqualTo(500L);
        try (Transaction transaction = Transaction.openRoot()) {
            assertThat(energy.prepare(request(energy)).commit(transaction).success()).isTrue();
            transaction.commit();
        }
    }

    @Test
    void capability_definitions_use_storage_protocols_from_the_host() {
        StorageHost host = new StorageHost();

        ItemBusCapability item = (ItemBusCapability) CapabilityRegistry.get(BuiltinCapabilityDefinitions.ITEM_TYPE)
                .factory().create(context(host));
        FluidHatchCapability fluid = (FluidHatchCapability) CapabilityRegistry.get(BuiltinCapabilityDefinitions.FLUID_TYPE)
                .factory().create(context(host));
        EnergyHatchCapability energy = (EnergyHatchCapability) CapabilityRegistry.get(BuiltinCapabilityDefinitions.ENERGY_TYPE)
                .factory().create(context(host));

        assertThat(item.storage()).isSameAs(host.itemStorage());
        assertThat(fluid.storage()).isSameAs(host.fluidStorage());
        assertThat(energy.storage()).isSameAs(host.getEnergyStorage());
        assertThat(item.ioType()).isEqualTo(IOType.INPUT);
        assertThat(fluid.ioType()).isEqualTo(IOType.INPUT);
        assertThat(energy.ioType()).isEqualTo(IOType.INPUT);
    }

    private static CapabilityRequest request(MachineCapability capability) {
        if (capability.facet(ResourceFacet.class).isPresent()) {
            return new CapabilityRequests.ResourceRequest<>(capability.type(), capability.ioType(), 1, List.of());
        }
        if (capability.facet(ValueFacet.class).map(ValueFacet::storage).filter(LongValueStorage.class::isInstance).isPresent()) {
            return new CapabilityRequests.ValueRequest(capability.type(), capability.ioType(), 1, 1, false);
        }
        return new TestRequest(capability.type(), capability.ioType(), 1);
    }

    private static ItemResource ironResource() {
        ItemStack stack = Items.IRON_INGOT.getDefaultInstance();
        stack.set(DataComponents.MAX_STACK_SIZE, 64);
        return ItemResource.of(stack);
    }

    private static IOPortBlockEntity port(String id) {
        IOPortKind kind = PortKinds.all().stream().filter(candidate -> candidate.id().equals(id)).findFirst().orElseThrow();
        BlockState state = ModBlocks.BLOCKS.get(id).get().defaultBlockState();
        return kind.entityFactory().create(BlockPos.ZERO, state);
    }

    private record TestRequest(CapabilityType type, IOType ioType, long parallelism) implements CapabilityRequest {}

    private static final class MixedPort extends IOPortBlockEntity {
        private static final IOPortKind KIND = new IOPortKind() {
            @Override public String id() { return "mixed_test"; }
            @Override public IOType ioType() { return IOType.INPUT; }
            @Override public net.minecraft.world.level.block.entity.BlockEntityType.BlockEntitySupplier<? extends IOPortBlockEntity> entityFactory() {
                return MixedPort::new;
            }
            @Override public PortDefinition definition() {
                return PortDefinition.of(cn.howxu.mmcr.MMCR.id("mixed_test"));
            }
        };

        private MixedPort(BlockPos pos, BlockState state) {
            super(ModBlockEntities.BES.get("item_input_bus").get(), pos,
                    state);
        }

        private CapabilitySnapshot capabilitySnapshot;

        @Override public IOType ioType() { return IOType.INPUT; }
        @Override public IOPortKind kind() { return KIND; }
        @Override public CapabilitySnapshot capabilitySnapshot() {
            if (capabilitySnapshot == null) {
                capabilitySnapshot = new CapabilitySnapshot(List.of(new TestCapability("first"), new TestCapability("second")));
            }
            return capabilitySnapshot;
        }
    }

    private static CapabilityCreationContext context(CapabilityHost host) {
        return new CapabilityCreationContext() {
            @Override public CapabilityHost host() { return host; }
            @Override public IOType ioType() { return IOType.INPUT; }
            @Override public <T> java.util.Optional<T> service(Class<T> serviceType) {
                return serviceType.isInstance(host) ? java.util.Optional.of(serviceType.cast(host)) : java.util.Optional.empty();
            }
            @Override public Runnable onChanged() { return () -> {}; }
        };
    }

    private static final class StorageHost extends IOPortBlockEntity {
        private final ResourceStorage<ItemResource> itemStorage = new LongResourceStorage<>(
                ItemResource.class, 2, 100L, resource -> resource.isEmpty(), () -> {});
        private final ResourceStorage<FluidResource> fluidStorage = new LongFluidStorage(2, 100L, () -> {});
        private final LongValueStorage energyStorage = new LongValueStorage(100L, 20L, () -> {});

        private StorageHost() {
            super(ModBlockEntities.BES.get("item_input_bus").get(), BlockPos.ZERO,
                    ModBlocks.BLOCKS.get("item_input_bus").get().defaultBlockState());
        }

        @Override public IOType ioType() { return IOType.INPUT; }
        @Override public IOPortKind kind() { return PortKinds.ITEM_INPUT; }
        @Override public CapabilitySnapshot capabilitySnapshot() { return new CapabilitySnapshot(List.of()); }
        @Override public ResourceStorage<ItemResource> itemStorage() { return itemStorage; }
        @Override public ResourceStorage<FluidResource> fluidStorage() { return fluidStorage; }
        @Override public LongValueStorage getEnergyStorage() { return energyStorage; }
    }

    private record TestCapability(String id) implements MachineCapability {
        @Override public CapabilityType type() { return new CapabilityType(Identifier.fromNamespaceAndPath("mmcr_test", id)); }
        @Override public IOType ioType() { return IOType.INPUT; }
        @Override public cn.howxu.mmcr.api.capability.CapabilityView view() { return new cn.howxu.mmcr.api.capability.CapabilityView() {
            @Override public CapabilityType type() { return TestCapability.this.type(); }
            @Override public IOType ioType() { return TestCapability.this.ioType(); }
        }; }
        @Override public CapabilityOperation prepare(CapabilityRequest request) {
            return transaction -> CapabilityResult.successful();
        }
    }
}
