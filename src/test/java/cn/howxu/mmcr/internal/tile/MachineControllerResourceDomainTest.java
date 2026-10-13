package cn.howxu.mmcr.internal.tile;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.DynamicMachine;
import cn.howxu.mmcr.config.ServerConfig;
import cn.howxu.mmcr.internal.event.SharedIoEvents;
import cn.howxu.mmcr.internal.multiblock.ComponentClaimPolicy;
import cn.howxu.mmcr.internal.multiblock.StructureClaimRegistry;
import cn.howxu.mmcr.test.RuntimeTestFixtures;
import cn.howxu.mmcr.test.ServerThreadTestScope;
import cn.howxu.mmcr.test.TestBootstrap;
import com.electronwill.nightconfig.core.CommentedConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.fml.config.IConfigSpec;
import net.neoforged.neoforge.event.level.LevelEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Controller registry caching preserves live ownership and lifecycle boundaries.
 *
 * @author howxu <dev@howxu.cn>
 */
class MachineControllerResourceDomainTest {
    private final List<ServerLevel> levels = new ArrayList<>();
    private ServerThreadTestScope serverThreadScope;

    @BeforeAll
    static void bootstrap() throws Exception {
        TestBootstrap.bootstrap();
        CommentedConfig config = CommentedConfig.inMemory();
        ServerConfig.SPEC.correct(config);
        var constructor = Class.forName("net.neoforged.fml.config.LoadedConfig")
                .getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        ServerConfig.SPEC.acceptConfig((IConfigSpec.ILoadedConfig) constructor.newInstance(config, null, null));
    }

    @BeforeEach
    void openServerScope() throws Exception {
        serverThreadScope = ServerThreadTestScope.open();
        TestBootstrap.bootstrapCapabilities();
    }

    @AfterEach
    void cleanup() throws Exception {
        try {
            for (ServerLevel level : levels) SharedIoEvents.onLevelUnload(new LevelEvent.Unload(level));
        } finally {
            levels.clear();
            if (serverThreadScope != null) serverThreadScope.close();
        }
    }

    @Test
    void registry_replacement_on_the_same_level_does_not_reuse_old_claims() {
        MachineControllerBlockEntity controller = controller();
        ServerLevel level = (ServerLevel) controller.getLevel();
        StructureClaimRegistry old = StructureClaimRegistry.get(level);
        old.claim(controller.getBlockPos(), List.of());
        StructureClaimRegistry.ResourceDomain original = controller.resourceDomain();
        assertThat(original).isNotNull().isSameAs(old.domainFor(controller.getBlockPos()));
        assertThat(old.isDiscarded()).isFalse();

        StructureClaimRegistry.discard(level);
        assertThat(old.isDiscarded()).isTrue();
        assertThat(old.domainFor(controller.getBlockPos())).isSameAs(original);
        assertThat(controller.resourceDomain()).isNull();
        StructureClaimRegistry replacement = StructureClaimRegistry.get(level);
        assertThat(replacement).isNotSameAs(old);
        assertThat(replacement.isDiscarded()).isFalse();
        replacement.claim(controller.getBlockPos(), List.of());
        assertThat(controller.resourceDomain()).isNotNull()
                .isSameAs(replacement.domainFor(controller.getBlockPos()));
    }

    @Test
    void cached_registry_keeps_live_merge_split_release_and_unrelated_domain_changes() {
        MachineControllerBlockEntity controller = controller();
        StructureClaimRegistry registry = StructureClaimRegistry.get((ServerLevel) controller.getLevel());
        BlockPos owner = controller.getBlockPos();
        BlockPos second = new BlockPos(10, 0, 0);
        BlockPos unrelated = new BlockPos(20, 0, 0);
        BlockPos shared = new BlockPos(5, 0, 0);
        var claim = new StructureClaimRegistry.Claim(shared, ComponentClaimPolicy.SHARED_SERIALIZED);
        registry.claim(owner, List.of(claim));
        StructureClaimRegistry.ResourceDomain original = controller.resourceDomain();
        assertThat(original).isNotNull();
        registry.claim(unrelated, List.of());
        assertThat(controller.resourceDomain()).isSameAs(original);

        registry.claim(second, List.of(claim));
        StructureClaimRegistry.ResourceDomain merged = controller.resourceDomain();
        assertThat(merged).isNotNull().isNotSameAs(original).isSameAs(registry.domainFor(owner));
        assertThat(merged.controllers()).containsExactlyInAnyOrder(owner, second);
        assertThat(original.controllers()).containsExactly(owner);

        registry.release(second);
        StructureClaimRegistry.ResourceDomain split = controller.resourceDomain();
        assertThat(split).isNotNull().isNotSameAs(merged).isSameAs(registry.domainFor(owner));
        assertThat(split.controllers()).containsExactly(owner);
        assertThat(merged.controllers()).containsExactlyInAnyOrder(owner, second);
        registry.release(unrelated);
        assertThat(controller.resourceDomain()).isSameAs(split);
        registry.release(owner);
        assertThat(controller.resourceDomain()).isNull();
    }

    @Test
    void changing_level_rebinds_the_registry_for_the_same_controller_position() {
        MachineControllerBlockEntity controller = controller();
        MachineControllerBlockEntity other = controller();
        ServerLevel oldLevel = (ServerLevel) controller.getLevel();
        ServerLevel newLevel = (ServerLevel) other.getLevel();
        assertThat(newLevel).isNotSameAs(oldLevel);
        StructureClaimRegistry old = StructureClaimRegistry.get(oldLevel);
        StructureClaimRegistry next = StructureClaimRegistry.get(newLevel);
        old.claim(controller.getBlockPos(), List.of());
        next.claim(controller.getBlockPos(), List.of());
        assertThat(controller.resourceDomain()).isNotNull().isSameAs(old.domainFor(controller.getBlockPos()));

        controller.setLevel(newLevel);
        assertThat(controller.resourceDomain()).isNotNull().isSameAs(next.domainFor(controller.getBlockPos()));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void removal_and_chunk_unload_drop_the_reference_before_controller_reuse(boolean chunkUnload)
            throws Exception {
        MachineControllerBlockEntity controller = controller();
        ServerLevel level = (ServerLevel) controller.getLevel();
        StructureClaimRegistry registry = StructureClaimRegistry.get(level);
        registry.claim(controller.getBlockPos(), List.of());
        assertThat(controller.resourceDomain()).isNotNull().isSameAs(registry.domainFor(controller.getBlockPos()));
        var cached = MachineControllerBlockEntity.class.getDeclaredField("cachedStructureClaimRegistry");
        cached.setAccessible(true);
        assertThat(cached.get(controller)).isSameAs(registry);

        if (chunkUnload) controller.onChunkUnloaded();
        else controller.setRemoved();
        assertThat(cached.get(controller)).isNull();
        assertThat(StructureClaimRegistry.get(level)).isSameAs(registry);
        assertThat(registry.isDiscarded()).isFalse();

        controller.clearRemoved();
        registry.claim(controller.getBlockPos(), List.of());
        assertThat(controller.resourceDomain()).isNotNull().isSameAs(registry.domainFor(controller.getBlockPos()));
        assertThat(cached.get(controller)).isSameAs(registry);
    }

    @Test
    void assigning_the_same_level_preserves_the_live_registry_reference() throws Exception {
        MachineControllerBlockEntity controller = controller();
        ServerLevel level = (ServerLevel) controller.getLevel();
        StructureClaimRegistry registry = StructureClaimRegistry.get(level);
        registry.claim(controller.getBlockPos(), List.of());
        assertThat(controller.resourceDomain()).isNotNull().isSameAs(registry.domainFor(controller.getBlockPos()));
        var cached = MachineControllerBlockEntity.class.getDeclaredField("cachedStructureClaimRegistry");
        cached.setAccessible(true);

        controller.setLevel(level);
        assertThat(cached.get(controller)).isSameAs(registry);
        assertThat(controller.resourceDomain()).isSameAs(registry.domainFor(controller.getBlockPos()));
    }

    private MachineControllerBlockEntity controller() {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controllerEntity(
                MMCR.id("test_cube"), BlockPos.ZERO);
        DynamicMachine machine = new DynamicMachine(MMCR.id("test_cube"), "domain cache fixture",
                new BlockArray(Map.of()));
        RuntimeTestFixtures.formStructure(controller, machine);
        levels.add((ServerLevel) controller.getLevel());
        return controller;
    }
}
