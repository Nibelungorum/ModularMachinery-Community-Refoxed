package cn.howxu.mmcr.internal.capability;

import cn.howxu.mmcr.api.capability.CapabilityHost;
import cn.howxu.mmcr.api.capability.CapabilityRequest;
import cn.howxu.mmcr.api.capability.CapabilitySnapshot;
import cn.howxu.mmcr.api.capability.CapabilityType;
import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.storage.LongValueStorage;
import cn.howxu.mmcr.api.capability.storage.ResourceStorage;
import cn.howxu.mmcr.api.capability.plan.CapabilityOperation;
import cn.howxu.mmcr.api.capability.plan.CapabilityResult;
import cn.howxu.mmcr.internal.port.IOPortKind;
import cn.howxu.mmcr.internal.tile.IOPortBlockEntity;
import cn.howxu.mmcr.registry.ModBlockEntities;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.registry.PortKinds;
import cn.howxu.mmcr.test.TestBootstrap;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

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
    void item_capability_uses_slot_resource_storage_and_operation_contract() {
        MachineCapability capability = port("item_input_bus").capabilitySnapshot().capabilities().getFirst();
        ItemBusCapability item = (ItemBusCapability) capability;
        ResourceStorage<ItemResource> storage = item.storage();

        assertThat(storage.size()).isGreaterThan(1);
        try (Transaction transaction = Transaction.openRoot()) {
            assertThat(storage.insert(0, ItemResource.of(net.minecraft.world.item.Items.IRON_INGOT), 3L, transaction))
                    .isEqualTo(3L);
            assertThat(item.prepare(request(item)).commit(transaction).success()).isTrue();
            transaction.commit();
        }

        assertThat(storage.amount(0)).isEqualTo(3L);
        assertThat(storage.resource(0)).isEqualTo(ItemResource.of(net.minecraft.world.item.Items.IRON_INGOT));
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

    private static CapabilityRequest request(MachineCapability capability) {
        return new TestRequest(capability.type(), capability.ioType(), 1);
    }

    private static IOPortBlockEntity port(String id) {
        IOPortKind kind = PortKinds.all().stream().filter(candidate -> candidate.id().equals(id)).findFirst().orElseThrow();
        BlockState state = ModBlocks.BLOCKS.get(id).get().defaultBlockState();
        return kind.entityFactory().create(BlockPos.ZERO, state);
    }

    private record TestRequest(CapabilityType type, IOType ioType, int parallelism) implements CapabilityRequest {}

    private static final class MixedPort extends IOPortBlockEntity {
        private static final IOPortKind KIND = new IOPortKind() {
            @Override public String id() { return "mixed_test"; }
            @Override public IOType ioType() { return IOType.INPUT; }
            @Override public net.minecraft.world.level.block.entity.BlockEntityType.BlockEntitySupplier<? extends IOPortBlockEntity> entityFactory() {
                return MixedPort::new;
            }
            @Override public List<CapabilityFactories.CapabilityFactory> capabilityFactories() {
                return List.of(port -> new TestCapability("first"), port -> new TestCapability("second"));
            }
        };

        private MixedPort(BlockPos pos, BlockState state) {
            super(ModBlockEntities.BES.get("item_input_bus").get(), pos,
                    state);
        }

        @Override public IOType ioType() { return IOType.INPUT; }
        @Override public IOPortKind kind() { return KIND; }
        @Override public cn.howxu.mmcr.internal.autoio.AutoIOCapabilityType autoIOCapabilityType() {
            return cn.howxu.mmcr.internal.autoio.AutoIOCapabilityType.ITEM;
        }
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
