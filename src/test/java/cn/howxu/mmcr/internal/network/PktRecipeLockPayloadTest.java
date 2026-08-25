package cn.howxu.mmcr.internal.network;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.recipe.helper.CraftingStatus;
import cn.howxu.mmcr.internal.runtime.FactoryRuntime;
import cn.howxu.mmcr.test.RuntimeTestFixtures;
import cn.howxu.mmcr.test.TestBootstrap;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.neoforged.neoforge.network.connection.ConnectionType;
import net.neoforged.neoforge.fluids.FluidStack;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;

import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests the final server-owned recipe-lock payload boundary.
 *
 * @author howxu <dev@howxu.cn>
 */
class PktRecipeLockPayloadTest {
    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    @Test
    void payload_round_trips_controller_position_and_thread_index() {
        PktRecipeLockPayload payload = new PktRecipeLockPayload(new BlockPos(3, 4, 5), 7);
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY,
                ConnectionType.NEOFORGE);

        PktRecipeLockPayload.STREAM_CODEC.encode(buffer, payload);
        PktRecipeLockPayload decoded = PktRecipeLockPayload.STREAM_CODEC.decode(buffer);

        assertThat(decoded).isEqualTo(payload);
        buffer.release();
    }

    @Test
    void server_handler_rejects_a_missing_player_or_payload() {
        assertThat(PktRecipeLockPayload.toggleOnServer(null, null)).isFalse();
        assertThat(PktRecipeLockPayload.toggleOnServer(null,
                new PktRecipeLockPayload(BlockPos.ZERO, 0))).isFalse();
    }

    @Test
    void invalid_thread_index_does_not_toggle_the_base_lane() {
        FactoryRuntime runtime = new FactoryRuntime();
        runtime.ensureBaseLane(RuntimeTestFixtures.controller(MMCR.id("test_cube")));

        assertThat(runtime.toggleRecipeLock(-1)).isFalse();
        assertThat(runtime.toggleRecipeLock(999)).isFalse();
        assertThat(runtime.threadSnapshots().getFirst().locked()).isFalse();
    }

    @Test
    void machine_state_payload_preserves_locked_recipe_id_and_detects_lock_changes() {
        String recipeId = "other_namespace:recipe_with_a_long_id";
        PktMachineStatePayload locked = machineState(true, recipeId);
        PktMachineStatePayload unlocked = machineState(false, "");

        assertThat(locked.lockedRecipeId()).isEqualTo(recipeId);
        assertThat(PktMachineStatePayload.stateChanged(locked, unlocked)).isTrue();
    }

    private static PktMachineStatePayload machineState(boolean locked, String recipeId) {
        return new PktMachineStatePayload(new BlockPos(1, 2, 3), "", true, false,
                List.of(), locked, recipeId, "", 0, 0, false, "", CraftingStatus.Status.IDLE, "", null,
                true, false, 0, 0, 1, 1, false, 0, 0, 0, 0, 0, 0,
                FluidStack.EMPTY, FluidStack.EMPTY);
    }
}
