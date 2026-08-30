package cn.howxu.mmcr.compat.jade;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.internal.runtime.JadeTextState;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies Jade custom text NBT serialization.
 *
 * @author howxu <dev@howxu.cn>
 */
class JadeTextCodecTest {

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    @Test
    void roundTripPreservesOrderAndComponentContent() {
        JadeTextState state = new JadeTextState();
        state.append(MMCR.id("literal"), Component.literal("hello"));
        state.append(MMCR.id("translated"), Component.translatable("block.mmcr.machine_controller"));
        CompoundTag data = new CompoundTag();

        JadeTextCodec.write(data, state.snapshot());

        assertThat(JadeTextCodec.read(data))
                .containsExactly(Component.literal("hello"),
                        Component.translatable("block.mmcr.machine_controller"));
    }

    @Test
    void malformedLinesAreIgnored() {
        CompoundTag data = new CompoundTag();
        ListTag lines = new ListTag();
        CompoundTag malformed = new CompoundTag();
        malformed.putString("id", "mmcr:broken");
        lines.add(malformed);
        data.put("mmcr_jade_text", lines);

        assertThat(JadeTextCodec.read(data)).isEmpty();
    }
}
