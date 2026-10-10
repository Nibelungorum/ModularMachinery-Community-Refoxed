package cn.howxu.mmcr.api.capability.transfer;

import cn.howxu.mmcr.internal.storage.LongFluidStorage;
import cn.howxu.mmcr.test.TestBootstrap;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies directed container transfers and participation in the caller's transaction.
 *
 * @author howxu <dev@howxu.cn>
 */
class ContainerResourceTransferTest {
    @BeforeAll
    static void setup() throws Exception {
        TestBootstrap.bootstrap();
    }

    @Test
    void input_moves_the_containers_contents_into_the_port_on_commit() {
        var container = new LongFluidStorage(4_000, () -> {});
        var port = new LongFluidStorage(4_000, () -> {});
        var water = FluidResource.of(Fluids.WATER);
        container.setContents(water, 2_000);
        try (Transaction transaction = Transaction.openRoot()) {
            assertThat(ContainerResourceTransfer.transfer(IOType.INPUT,
                    container, port, transaction)).isEqualTo(2_000);
            transaction.commit();
        }
        assertThat(container.amount(0)).isZero();
        assertThat(container.resource(0)).isEqualTo(FluidResource.EMPTY);
        assertThat(port.amount(0)).isEqualTo(2_000);
        assertThat(port.resource(0)).isEqualTo(water);
    }

    @Test
    void input_does_not_extract_from_the_port_into_an_empty_container() {
        var container = new LongFluidStorage(4_000, () -> {});
        var port = new LongFluidStorage(4_000, () -> {});
        var water = FluidResource.of(Fluids.WATER);
        port.setContents(water, 2_000);
        try (Transaction transaction = Transaction.openRoot()) {
            assertThat(ContainerResourceTransfer.transfer(IOType.INPUT,
                    container, port, transaction)).isZero();
            transaction.commit();
        }
        assertThat(container.amount(0)).isZero();
        assertThat(port.amount(0)).isEqualTo(2_000);
    }

    @Test
    void a_parent_transaction_rollback_restores_both_sides() {
        var container = new LongFluidStorage(4_000, () -> {});
        var port = new LongFluidStorage(4_000, () -> {});
        var water = FluidResource.of(Fluids.WATER);
        container.setContents(water, 2_000);
        try (Transaction transaction = Transaction.openRoot()) {
            assertThat(ContainerResourceTransfer.transfer(IOType.INPUT,
                    container, port, transaction)).isEqualTo(2_000);
        }
        assertThat(container.amount(0)).isEqualTo(2_000);
        assertThat(container.resource(0)).isEqualTo(water);
        assertThat(port.amount(0)).isZero();
        assertThat(port.resource(0)).isEqualTo(FluidResource.EMPTY);
    }

    @Test
    void output_moves_the_ports_contents_into_the_container_on_commit() {
        var container = new LongFluidStorage(4_000, () -> {});
        var port = new LongFluidStorage(4_000, () -> {});
        var water = FluidResource.of(Fluids.WATER);
        port.setContents(water, 2_000);
        try (Transaction transaction = Transaction.openRoot()) {
            assertThat(ContainerResourceTransfer.transfer(IOType.OUTPUT,
                    container, port, transaction)).isEqualTo(2_000);
            transaction.commit();
        }
        assertThat(container.amount(0)).isEqualTo(2_000);
        assertThat(container.resource(0)).isEqualTo(water);
        assertThat(port.amount(0)).isZero();
        assertThat(port.resource(0)).isEqualTo(FluidResource.EMPTY);
    }

    @Test
    void output_does_not_insert_the_containers_contents_into_the_port() {
        var container = new LongFluidStorage(4_000, () -> {});
        var port = new LongFluidStorage(4_000, () -> {});
        container.setContents(FluidResource.of(Fluids.WATER), 2_000);
        try (Transaction transaction = Transaction.openRoot()) {
            assertThat(ContainerResourceTransfer.transfer(IOType.OUTPUT,
                    container, port, transaction)).isZero();
            transaction.commit();
        }
        assertThat(container.amount(0)).isEqualTo(2_000);
        assertThat(port.amount(0)).isZero();
    }

    @Test
    void input_moves_only_what_the_port_can_accept_and_retains_the_remainder() {
        var container = new LongFluidStorage(4_000, () -> {});
        var port = new LongFluidStorage(4_000, () -> {});
        var water = FluidResource.of(Fluids.WATER);
        container.setContents(water, 2_000);
        port.setContents(water, 3_000);
        try (Transaction transaction = Transaction.openRoot()) {
            assertThat(ContainerResourceTransfer.transfer(IOType.INPUT,
                    container, port, transaction)).isEqualTo(1_000);
            transaction.commit();
        }
        assertThat(container.amount(0)).isEqualTo(1_000);
        assertThat(container.resource(0)).isEqualTo(water);
        assertThat(port.amount(0)).isEqualTo(4_000);
        assertThat(port.resource(0)).isEqualTo(water);
    }

    @Test
    void incompatible_resources_leave_both_sides_unchanged() {
        var container = new LongFluidStorage(4_000, () -> {});
        var port = new LongFluidStorage(4_000, () -> {});
        var water = FluidResource.of(Fluids.WATER);
        var lava = FluidResource.of(Fluids.LAVA);
        container.setContents(water, 2_000);
        port.setContents(lava, 1_000);
        try (Transaction transaction = Transaction.openRoot()) {
            assertThat(ContainerResourceTransfer.transfer(IOType.INPUT,
                    container, port, transaction)).isZero();
            assertThat(ContainerResourceTransfer.transfer(IOType.OUTPUT,
                    container, port, transaction)).isZero();
            transaction.commit();
        }
        assertThat(container.amount(0)).isEqualTo(2_000);
        assertThat(container.resource(0)).isEqualTo(water);
        assertThat(port.amount(0)).isEqualTo(1_000);
        assertThat(port.resource(0)).isEqualTo(lava);
    }

    @Test
    void different_components_on_the_same_fluid_leave_both_sides_unchanged() {
        var container = new LongFluidStorage(4_000, () -> {});
        var port = new LongFluidStorage(4_000, () -> {});
        var containerWater = FluidResource.of(Fluids.WATER, DataComponentPatch.builder()
                .set(DataComponents.CUSTOM_NAME, Component.literal("Container water")).build());
        var portWater = FluidResource.of(Fluids.WATER, DataComponentPatch.builder()
                .set(DataComponents.CUSTOM_NAME, Component.literal("Port water")).build());
        assertThat(containerWater.getFluid()).isSameAs(portWater.getFluid());
        assertThat(containerWater).isNotEqualTo(portWater);
        container.setContents(containerWater, 2_000);
        port.setContents(portWater, 1_000);
        for (IOType direction : new IOType[]{IOType.INPUT, IOType.OUTPUT}) {
            try (Transaction transaction = Transaction.openRoot()) {
                assertThat(ContainerResourceTransfer.transfer(direction,
                        container, port, transaction)).isZero();
                transaction.commit();
            }
            assertThat(container.amount(0)).isEqualTo(2_000);
            assertThat(container.resource(0)).isEqualTo(containerWater);
            assertThat(port.amount(0)).isEqualTo(1_000);
            assertThat(port.resource(0)).isEqualTo(portWater);
        }
    }

    @Test
    void missing_direction_or_handler_leaves_existing_contents_unchanged() {
        var container = new LongFluidStorage(4_000, () -> {});
        var port = new LongFluidStorage(4_000, () -> {});
        var water = FluidResource.of(Fluids.WATER);
        container.setContents(water, 2_000);
        port.setContents(water, 1_000);
        try (Transaction transaction = Transaction.openRoot()) {
            assertThat(ContainerResourceTransfer.transfer(null,
                    container, port, transaction)).isZero();
            assertThat(ContainerResourceTransfer.transfer(IOType.INPUT,
                    null, port, transaction)).isZero();
            assertThat(ContainerResourceTransfer.transfer(IOType.OUTPUT,
                    container, null, transaction)).isZero();
            transaction.commit();
        }
        assertThat(container.amount(0)).isEqualTo(2_000);
        assertThat(container.resource(0)).isEqualTo(water);
        assertThat(port.amount(0)).isEqualTo(1_000);
        assertThat(port.resource(0)).isEqualTo(water);
    }
}
