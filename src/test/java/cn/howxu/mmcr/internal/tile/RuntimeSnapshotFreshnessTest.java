package cn.howxu.mmcr.internal.tile;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.capability.CapabilityDirections;
import cn.howxu.mmcr.api.capability.CapabilityHost;
import cn.howxu.mmcr.api.capability.CapabilityRequest;
import cn.howxu.mmcr.api.capability.CapabilitySnapshot;
import cn.howxu.mmcr.api.capability.CapabilityType;
import cn.howxu.mmcr.api.capability.CapabilityView;
import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.facet.ItemHandlerFacet;
import cn.howxu.mmcr.api.capability.plan.CapabilityOperation;
import cn.howxu.mmcr.api.capability.plan.CapabilityResult;
import cn.howxu.mmcr.api.recipe.helper.ProcessingComponent;
import cn.howxu.mmcr.internal.capability.CapabilityFactories;
import cn.howxu.mmcr.internal.runtime.ControllerRuntimeSnapshot;
import cn.howxu.mmcr.internal.storage.LongItemStorage;
import cn.howxu.mmcr.registry.ModBlockEntities;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.test.RuntimeTestFixtures;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies controller cache freshness when a storage commits while its old amount is captured.
 *
 * @author howxu <dev@howxu.cn>
 */
class RuntimeSnapshotFreshnessTest {
    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    @ParameterizedTest
    @CsvSource({"true,true", "true,false", "false,true", "false,false"})
    void reentrant_commit_is_visible_on_the_next_query_without_manual_dirty(
            boolean publishedQuery, boolean reuseStaticState) {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controllerEntity(MMCR.id("test_cube"), BlockPos.ZERO);
        BlockPos source = new BlockPos(7, 20, 30);
        ReentrantItemStorage items = new ReentrantItemStorage(
                () -> controller.componentRuntime().markCapabilitySourceChanged(source));
        CapabilityOwner owner = new CapabilityOwner(source, new ItemCapability(items));
        ControllerRuntimeSnapshot before = query(controller, publishedQuery);
        controller.componentRuntime().replaceComponents(List.of(
                new ProcessingComponent(null, owner, source, BlockPos.ZERO, List.of("source"), null)));
        if (reuseStaticState) before = query(controller, publishedQuery);
        items.addItems(10L);
        items.afterRead = () -> items.addItems(10L);

        ControllerRuntimeSnapshot intermediate = query(controller, publishedQuery);
        assertThat(intermediate.capabilityPresentations().getFirst().amount()).isEqualTo(10L);
        assertThat(items.amount(0)).isEqualTo(20L);

        ControllerRuntimeSnapshot current = query(controller, publishedQuery);
        assertThat(current.capabilityPresentations().getFirst().amount()).isEqualTo(20L);
        assertThat(query(controller, publishedQuery)).isSameAs(current);
        assertThat(query(controller, !publishedQuery)).isSameAs(current);
        assertThat(intermediate.capabilityPresentations().getFirst().amount()).isEqualTo(10L);
        assertThat(intermediate.capabilityPresentations().getFirst().slots().getFirst().amount()).isEqualTo(10L);
        if (reuseStaticState) {
            assertThat(before.capabilityPresentations().getFirst().amount()).isZero();
            assertThat(before.capabilityPresentations().getFirst().slots().getFirst().amount()).isZero();
        } else {
            assertThat(before.capabilityPresentations()).isEmpty();
        }
    }

    private static ControllerRuntimeSnapshot query(MachineControllerBlockEntity controller, boolean published) {
        return published ? controller.runtimeSnapshot() : controller.currentRuntimeSnapshot();
    }

    /** @author howxu <dev@howxu.cn> */
    private static final class ReentrantItemStorage extends LongItemStorage {
        private Runnable afterRead;

        private ReentrantItemStorage(Runnable onChange) {
            super(1, 100L, onChange);
        }

        private void addItems(long amount) {
            assertThat(forceInsert(0, new ItemStack(Items.IRON_INGOT), amount, false)).isEqualTo(amount);
        }

        @Override
        public long amount(int slot) {
            long amount = super.amount(slot);
            Runnable action = afterRead;
            afterRead = null;
            if (action != null) action.run();
            return amount;
        }
    }

    /** @author howxu <dev@howxu.cn> */
    private record ItemCapability(ReentrantItemStorage storage) implements MachineCapability, ItemHandlerFacet {
        @Override public ReentrantItemStorage itemHandler() { return storage; }

        @Override
        public CapabilityType type() { return new CapabilityType(MMCR.id("reentrant_snapshot_test")); }

        @Override
        public CapabilityDirections directions() { return CapabilityDirections.input(); }

        @Override
        public CapabilityView view() {
            return CapabilityFactories.view(type(), directions(), Set.of(ItemHandlerFacet.class));
        }

        @Override
        public CapabilityOperation prepare(CapabilityRequest request) {
            return () -> CapabilityResult.successful();
        }
    }

    /** @author howxu <dev@howxu.cn> */
    private static final class CapabilityOwner extends BlockEntity implements CapabilityHost {
        private final List<MachineCapability> capabilities;

        private CapabilityOwner(BlockPos pos, MachineCapability capability) {
            super(ModBlockEntities.BES.get("item_input_bus").get(), pos,
                    ModBlocks.BLOCKS.get("item_input_bus").get().defaultBlockState());
            capabilities = List.of(capability);
        }

        @Override
        public CapabilitySnapshot capabilitySnapshot() { return new CapabilitySnapshot(capabilities); }

        @Override
        public List<MachineCapability> capabilities() { return capabilities; }
    }
}
