package cn.howxu.mmcr.internal.network;

import cn.howxu.mmcr.internal.recipe.FactoryRecipeScheduler;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.neoforged.neoforge.network.connection.ConnectionType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PktRecipeLockPayloadTest {
    @Test
    void payload_round_trips_controller_position_and_thread_index() {
        PktRecipeLockPayload original = new PktRecipeLockPayload(new BlockPos(4, 5, 6), 3);
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY,
                ConnectionType.NEOFORGE);

        PktRecipeLockPayload.STREAM_CODEC.encode(buffer, original);

        assertThat(PktRecipeLockPayload.STREAM_CODEC.decode(buffer)).isEqualTo(original);
    }

    @Test
    void invalid_thread_index_does_not_toggle_another_thread() {
        FactoryRecipeScheduler scheduler = new FactoryRecipeScheduler(1);

        assertThat(scheduler.toggleRecipeLock(-1)).isFalse();
        assertThat(scheduler.toggleRecipeLock(999)).isFalse();
        assertThat(scheduler.allThreads().getFirst().isRecipeLocked()).isFalse();
    }

    @Test
    void machine_state_payload_preserves_the_complete_locked_recipe_id() {
        PktMachineStatePayload payload = new PktMachineStatePayload(new BlockPos(1, 2, 3), "", true, false,
                java.util.List.of(), true, "other_namespace:recipe_with_a_long_id");

        assertThat(payload.lockedRecipeId()).isEqualTo("other_namespace:recipe_with_a_long_id");
    }

    @Test
    void lock_state_change_is_not_suppressed_by_ordinary_state_deduplication() {
        assertThat(PktMachineStatePayload.stateChanged(true, false, true,
                "other_namespace:recipe_with_a_long_id", true, false, false, "")).isTrue();
    }
}
