package cn.howxu.mmcr.compat.appliedenergistics2.loaded.storage;

import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

/** @author HowXu {@literal <dev@howxu.cn>} */
class PatternRefundIndexTest {
    @BeforeAll static void bootstrap() throws Exception { TestBootstrap.bootstrap(); }

    @Test void requestsSurviveSaveAndNeverFollowAReplacementOrLaterBinding() {
        var master = GlobalPos.of(Level.OVERWORLD, BlockPos.ZERO);
        var other = GlobalPos.of(Level.NETHER, BlockPos.ZERO);
        var mirror = GlobalPos.of(Level.END, BlockPos.ZERO);
        var index = new PatternRefundIndex();
        var first = UUID.randomUUID();
        var replacement = UUID.randomUUID();
        index.observe(mirror, first, master, true);
        assertThat(index.request(master)).extracting(PatternRefundIndex.Member::instance).containsExactly(first);
        index.observe(mirror, first, master, true);
        var saved = index.save(new CompoundTag(), RegistryAccess.EMPTY);
        var restored = PatternRefundIndex.load(saved, RegistryAccess.EMPTY);
        assertThat(restored.member(mirror).requested()).isTrue();
        restored.observe(mirror, replacement, other, true);
        restored.forget(mirror, first);
        restored.acknowledge(mirror, first);
        assertThat(restored.member(mirror).requested()).isFalse();
        assertThat(restored.hasMembers(master)).isFalse();
        assertThat(restored.hasMembers(other)).isTrue();
        restored.request(other);
        restored.acknowledge(mirror, replacement);
        assertThat(restored.member(mirror).requested()).isFalse();
        restored.forget(mirror, replacement);
        assertThat(restored.hasMembers(other)).isFalse();
    }
}
