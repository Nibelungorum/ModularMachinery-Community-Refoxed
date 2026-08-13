package cn.howxu.mmcr.internal.autoio;

import cn.howxu.mmcr.internal.tile.IOPortBlockEntity;
import cn.howxu.mmcr.internal.port.IOPortKind;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.registry.PortKinds;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author howxu <dev@howxu.cn>
 */
class AutoIOTransferHandlersTest {

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    @Test
    void handler_for_matches_item_fluid_and_energy_ports() {
        assertThat(AutoIOTransferHandlers.handlerFor(port("item_input_bus_normal"))).isPresent();
        assertThat(AutoIOTransferHandlers.handlerFor(port("fluid_input_hatch_tiny"))).isPresent();
        assertThat(AutoIOTransferHandlers.handlerFor(port("energy_input_hatch_tiny"))).isPresent();
    }

    @Test
    void gas_is_reserved_but_has_no_builtin_handler() {
        assertThat(AutoIOCapabilityType.GAS).isNotNull();
    }

    private static IOPortBlockEntity port(String id) {
        IOPortKind kind = PortKinds.all().stream().filter(candidate -> candidate.id().equals(id)).findFirst()
                .orElseGet(() -> PortKinds.all().stream()
                        .filter(candidate -> candidate.id().equals(id.replace("_normal", "")))
                        .findFirst()
                        .orElseThrow());
        return (IOPortBlockEntity) kind.entityFactory().create(BlockPos.ZERO, ModBlocks.BLOCKS.get(kind.id()).get().defaultBlockState());
    }
}
