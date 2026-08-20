package cn.howxu.mmcr.client.preview;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.api.machine.DynamicMachine;
import cn.howxu.mmcr.client.RuntimeContentClientApplier;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.Executor;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import cn.howxu.mmcr.test.TestBootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies lazy structure preview compilation state.
 *
 * @author howxu <dev@howxu.cn>
 */
class StructurePreviewCompilationCacheTest {
    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    @Test
    void acquireDefersCompilationUntilStartedAndReportsCompletion() {
        Queue<Runnable> queued = new ArrayDeque<>();
        Executor executor = queued::add;
        StructurePreviewCompilationCache cache = new StructurePreviewCompilationCache(new StructurePreviewSchemaFactory(), executor);
        DynamicMachine machine = new DynamicMachine(MMCR.id("cached_preview"), "machine.cached",
                new BlockArray(Map.of(BlockPos.ZERO, new BlockPredicate.OfBlock(Blocks.IRON_BLOCK))));

        StructurePreviewCompilation compilation = cache.acquire(machine);

        assertThat(compilation.progressPercent()).isZero();
        assertThat(compilation.complete()).isFalse();
        compilation.start();
        assertThat(queued).hasSize(1);
        queued.remove().run();
        assertThat(compilation.complete()).isTrue();
        assertThat(compilation.progressPercent()).isEqualTo(100);
        assertThat(compilation.schema().states()).hasSize(1);
    }

    @Test
    void acquireRebuildsWhenContentVersionChanges() {
        StructurePreviewCompilationCache cache = new StructurePreviewCompilationCache(
                new StructurePreviewSchemaFactory(), Runnable::run);
        DynamicMachine machine = new DynamicMachine(MMCR.id("versioned_preview"), "machine.versioned_preview",
                new BlockArray(Map.of(BlockPos.ZERO, new BlockPredicate.OfBlock(Blocks.IRON_BLOCK))));

        StructurePreviewCompilation first = cache.acquire(machine, 1L);
        StructurePreviewCompilation second = cache.acquire(machine, 2L);

        assertThat(second).isNotSameAs(first);
    }

    @Test
    void acquireUsesAppliedVersionAndHasIgnoresHistoricalEntries() {
        RuntimeContentClientApplier.apply(Map.of(), Map.of(), 11L);
        StructurePreviewCompilationCache cache = new StructurePreviewCompilationCache(
                new StructurePreviewSchemaFactory(), Runnable::run);
        DynamicMachine machine = new DynamicMachine(MMCR.id("applied_version_preview"), "machine.applied_version_preview",
                new BlockArray(Map.of(BlockPos.ZERO, new BlockPredicate.OfBlock(Blocks.IRON_BLOCK))));

        StructurePreviewCompilation old = cache.acquire(machine, 10L);
        StructurePreviewCompilation current = cache.acquire(machine);

        assertThat(current).isNotSameAs(old);
        assertThat(cache.has(machine.registryName())).isTrue();
        cache.clear();
        cache.acquire(machine, 10L);
        assertThat(cache.has(machine.registryName())).isFalse();
    }

    @Test
    void acquireBuildsOnlyOnceForTheSameAppliedVersion() {
        RuntimeContentClientApplier.apply(Map.of(), Map.of(), 12L);
        StructurePreviewCompilationCache cache = new StructurePreviewCompilationCache(
                new StructurePreviewSchemaFactory(), Runnable::run);
        DynamicMachine machine = new DynamicMachine(MMCR.id("same_version_preview"), "machine.same_version_preview",
                new BlockArray(Map.of(BlockPos.ZERO, new BlockPredicate.OfBlock(Blocks.IRON_BLOCK))));

        assertThat(cache.acquire(machine)).isSameAs(cache.acquire(machine));
    }

    @Test
    void acquireRebuildsAfterAppliedVersionChanges() {
        StructurePreviewCompilationCache cache = new StructurePreviewCompilationCache(
                new StructurePreviewSchemaFactory(), Runnable::run);
        DynamicMachine machine = new DynamicMachine(MMCR.id("applied_rebuild_preview"), "machine.applied_rebuild_preview",
                new BlockArray(Map.of(BlockPos.ZERO, new BlockPredicate.OfBlock(Blocks.IRON_BLOCK))));

        RuntimeContentClientApplier.apply(Map.of(), Map.of(), 13L);
        StructurePreviewCompilation first = cache.acquire(machine);
        RuntimeContentClientApplier.apply(Map.of(), Map.of(), 14L);
        StructurePreviewCompilation second = cache.acquire(machine);

        assertThat(second).isNotSameAs(first);
    }
}
