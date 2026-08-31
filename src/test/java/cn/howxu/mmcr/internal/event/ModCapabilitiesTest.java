package cn.howxu.mmcr.internal.event;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.capability.CapabilityType;
import cn.howxu.mmcr.api.capability.type.CapabilityBinding;
import cn.howxu.mmcr.api.port.PortDefinition;
import cn.howxu.mmcr.api.port.PortTierPolicy;
import cn.howxu.mmcr.internal.port.IOPortKind;
import cn.howxu.mmcr.internal.tile.IOPortBlockEntity;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies native capability registration consumes binding exposure declarations.
 *
 * @author howxu <dev@howxu.cn>
 */
class ModCapabilitiesTest {
    @Test
    void selects_external_exposures_from_generic_port_bindings() {
        CapabilityBinding binding = new CapabilityBinding(
                new CapabilityType(MMCR.id("external_test")), IOType.INPUT,
                context -> null, PortTierPolicy.always(),
                new CapabilityBinding.ExternalExposure<>(MMCR.id("external_test_native"), String.class,
                        (host, ioType, side) -> "exposed"));
        PortDefinition definition = PortDefinition.of(MMCR.id("external_test_port"), binding);
        IOPortKind kind = new IOPortKind() {
            @Override
            public String id() {
                return "external_test_port";
            }

            @Override
            public IOType ioType() {
                return IOType.INPUT;
            }

            @Override
            public BlockEntityType.BlockEntitySupplier<? extends IOPortBlockEntity> entityFactory() {
                return (pos, state) -> null;
            }

            @Override
            public PortDefinition definition() {
                return definition;
            }
        };

        assertThat(ModCapabilities.externalBindings(kind)).containsExactly(binding);
    }
}
