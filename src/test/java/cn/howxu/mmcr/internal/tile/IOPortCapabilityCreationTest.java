package cn.howxu.mmcr.internal.tile;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.capability.CapabilityDirections;
import cn.howxu.mmcr.api.capability.CapabilityRequest;
import cn.howxu.mmcr.api.capability.CapabilitySnapshot;
import cn.howxu.mmcr.api.capability.CapabilityType;
import cn.howxu.mmcr.api.capability.CapabilityView;
import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.plan.CapabilityOperation;
import cn.howxu.mmcr.api.capability.plan.CapabilityResult;
import cn.howxu.mmcr.api.capability.type.CapabilityBinding;
import cn.howxu.mmcr.api.port.PortDefinition;
import cn.howxu.mmcr.api.port.PortTierPolicy;
import cn.howxu.mmcr.internal.port.IOPortKind;
import cn.howxu.mmcr.registry.ModBlockEntities;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.test.RuntimeTestFixtures;
import cn.howxu.mmcr.test.TestBootstrap;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies binding directions are available while hosted capabilities are created.
 *
 * @author howxu <dev@howxu.cn>
 */
class IOPortCapabilityCreationTest {
    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    @Test
    void custom_binding_factory_receives_its_bidirectional_declaration_while_builtin_port_stays_physical() {
        AtomicReference<CapabilityDirections> receivedDirections = new AtomicReference<>();
        CapabilityBinding binding = new CapabilityBinding(new CapabilityType(MMCR.id("bidirectional_factory_test")),
                CapabilityDirections.bidirectional(), context -> {
                    receivedDirections.set(context.directions());
                    return new TestCapability(context.directions());
                }, PortTierPolicy.always());
        TestPort port = new TestPort(binding);

        MachineCapability created = port.create(binding);
        MachineCapability builtIn = RuntimeTestFixtures.itemInput(BlockPos.ZERO)
                .capabilitySnapshot().capabilities().getFirst();

        assertThat(receivedDirections.get()).isEqualTo(CapabilityDirections.bidirectional());
        assertThat(created.directions()).isEqualTo(CapabilityDirections.bidirectional());
        assertThat(builtIn.directions()).isEqualTo(CapabilityDirections.input());
    }

    private static final class TestPort extends IOPortBlockEntity {
        private final IOPortKind kind;

        private TestPort(CapabilityBinding binding) {
            super(ModBlockEntities.BES.get("item_input_bus").get(), BlockPos.ZERO,
                    ModBlocks.BLOCKS.get("item_input_bus").get().defaultBlockState());
            kind = new IOPortKind() {
                @Override public String id() { return "bidirectional_factory_test"; }
                @Override public IOType ioType() { return IOType.INPUT; }
                @Override public BlockEntityType.BlockEntitySupplier<? extends IOPortBlockEntity> entityFactory() { return null; }
                @Override public PortDefinition definition() { return PortDefinition.of(MMCR.id("bidirectional_factory_test"), binding); }
            };
        }

        @Override public IOType ioType() { return IOType.INPUT; }
        @Override public IOPortKind kind() { return kind; }
        @Override public CapabilitySnapshot capabilitySnapshot() { return new CapabilitySnapshot(List.of()); }
        private MachineCapability create(CapabilityBinding binding) { return createCapability(binding); }
    }

    private record TestCapability(CapabilityDirections directions) implements MachineCapability {
        @Override public CapabilityType type() { return new CapabilityType(MMCR.id("bidirectional_factory_test")); }
        @Override public CapabilityView view() { return new CapabilityView() {
            @Override public CapabilityType type() { return TestCapability.this.type(); }
            @Override public CapabilityDirections directions() { return TestCapability.this.directions(); }
        }; }
        @Override public CapabilityOperation prepare(CapabilityRequest request) {
            return () -> CapabilityResult.successful();
        }
    }
}
