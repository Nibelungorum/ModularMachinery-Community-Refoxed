package cn.howxu.mmcr.internal.menu;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.controller.ui.ControllerUiSnapshot.Kind;
import cn.howxu.mmcr.api.controller.ui.ControllerUiSnapshot.Role;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Opening metadata has one wire layout for both controller menus.
 * @author howxu <dev@howxu.cn>
 */
class ControllerMenuOpenDataTest {
    @Test
    void round_trip_preserves_identity_roles_and_capabilities_and_consumes_all_fields() {
        for (Kind kind : Kind.values()) {
            for (Role role : Role.values()) {
                var value = new ControllerMenuOpenData(UUID.randomUUID(), Level.NETHER,
                        new BlockPos(3, 7, -2), MMCR.id("machine"), kind, role, true, 2,
                        Optional.of(MMCR.id("host")), List.of(
                        new ControllerMenuOpenData.Capability(MMCR.id("request"), 1, false),
                        new ControllerMenuOpenData.Capability(MMCR.id("state"), 3, true)));
                roundTrip(value);
            }
        }
        roundTrip(new ControllerMenuOpenData(UUID.randomUUID(), Level.OVERWORLD, BlockPos.ZERO,
                MMCR.id("unformed"), Kind.NORMAL, Role.NORMAL, false, 0, Optional.empty(), List.of()));
    }

    @Test
    void metadata_owns_its_position_and_capability_list() {
        var pos = new BlockPos.MutableBlockPos(1, 2, 3);
        var capabilities = new ArrayList<ControllerMenuOpenData.Capability>();
        capabilities.add(new ControllerMenuOpenData.Capability(MMCR.id("state"), 1, true));
        var value = new ControllerMenuOpenData(UUID.randomUUID(), Level.OVERWORLD, pos,
                MMCR.id("machine"), Kind.NORMAL, Role.NORMAL, false, 0, Optional.empty(), capabilities);
        pos.set(8, 9, 10);
        capabilities.clear();
        assertThat(value.pos()).isEqualTo(new BlockPos(1, 2, 3));
        assertThat(value.capabilities()).hasSize(1);
        assertThatThrownBy(() -> value.capabilities().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    private static void roundTrip(ControllerMenuOpenData value) {
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            ControllerMenuOpenData.write(buffer, value);
            assertThat(ControllerMenuOpenData.read(buffer)).isEqualTo(value);
            assertThat(buffer.isReadable()).isFalse();
        } finally {
            buffer.release();
        }
    }
}
