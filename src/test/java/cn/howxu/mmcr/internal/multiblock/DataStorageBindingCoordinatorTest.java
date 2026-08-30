package cn.howxu.mmcr.internal.multiblock;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.api.machine.DynamicMachine;
import cn.howxu.mmcr.api.machine.Machine;
import cn.howxu.mmcr.api.machine.MachineAppearanceSpec;
import cn.howxu.mmcr.api.machine.MachineControllerSpec;
import cn.howxu.mmcr.api.machine.MachineRegistry;
import cn.howxu.mmcr.api.machine.PortRequirementSpec;
import cn.howxu.mmcr.api.machine.PortTierRequirementSpec;
import cn.howxu.mmcr.internal.tile.DataStorageBlockEntity;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.registry.ModBlockEntities;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.test.RuntimeTestFixtures;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Verifies one-controller ownership for independent data-storage blocks.
 * @author howxu <dev@howxu.cn>
 */
class DataStorageBindingCoordinatorTest {
    private static final BlockPos STORAGE_POS = new BlockPos(1, 0, 0);

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    @BeforeEach
    void clearMachineRegistry() {
        MachineRegistry.clearForTesting();
    }

    @Test
    void reconcile_binds_storage_and_unbind_removes_only_that_controller() {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controllerEntity(MMCR.id("test_cube"), BlockPos.ZERO);
        DataStorageBlockEntity storage = storage(STORAGE_POS);
        Machine machine = machine();
        RuntimeTestFixtures.formStructureWithComponents(controller, machine, storage);

        DataStorageBindingCoordinator coordinator = new DataStorageBindingCoordinator();
        coordinator.reconcile(controller, List.of(storage));

        assertThat(storage.controllerPosition()).contains(BlockPos.ZERO);
        assertThat(storage.appearanceBaseTexture()).isEqualTo(MMCR.id("block/storage_casing"));

        coordinator.unbind(controller, List.of(storage));

        assertThat(storage.controllerPosition()).isEmpty();
        assertThat(storage.appearanceBaseTexture()).isEqualTo(MMCR.id("block/basic_casing"));
    }

    @Test
    void formed_controller_context_exposes_bound_storage() {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controllerEntity(MMCR.id("test_cube"), BlockPos.ZERO);
        DataStorageBlockEntity storage = storage(STORAGE_POS);
        RuntimeTestFixtures.formStructureWithComponents(controller, machine(), storage);

        assertThat(controller.structureSnapshot().formed()).isTrue();
        assertThat(controller.structureSnapshot().pattern().pattern()).containsKey(STORAGE_POS);
        assertThat(controller.runtimeSnapshot().linkedPortPositions()).contains(STORAGE_POS);
        assertThat(storage.controllerPosition()).contains(BlockPos.ZERO);
        assertThat(controller.behaviorContext().dataStorage()).isSameAs(storage.storage());
    }

    @Test
    void unbind_missing_keeps_current_storage_binding_and_releases_only_removed_storage() {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controllerEntity(MMCR.id("test_cube"), BlockPos.ZERO);
        DataStorageBlockEntity retained = storage(STORAGE_POS);
        DataStorageBlockEntity removed = storage(new BlockPos(0, 1, 0));
        RuntimeTestFixtures.formStructureWithComponents(controller, machine(), retained);

        DataStorageBindingCoordinator coordinator = new DataStorageBindingCoordinator();
        coordinator.reconcile(controller, List.of(retained, removed));
        coordinator.unbindMissing(controller, List.of(retained, removed), List.of(retained));

        assertThat(retained.controllerPosition()).contains(BlockPos.ZERO);
        assertThat(removed.controllerPosition()).isEmpty();
    }

    @Test
    void reconcile_binds_only_the_first_storage_by_position() {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controllerEntity(
                MMCR.id("test_cube"), BlockPos.ZERO);
        DataStorageBlockEntity lower = storage(new BlockPos(-1, 0, 0));
        DataStorageBlockEntity higher = storage(new BlockPos(1, 0, 0));

        RuntimeTestFixtures.formStructureWithComponents(controller, machine(), higher, lower);

        assertThat(lower.controllerPosition()).contains(BlockPos.ZERO);
        assertThat(higher.controllerPosition()).isEmpty();
        assertThat(controller.behaviorContext().dataStorage()).isSameAs(lower.storage());
    }

    private static DataStorageBlockEntity storage(BlockPos pos) {
        return (DataStorageBlockEntity) ModBlockEntities.DATA_STORAGE.get().create(
                pos, ModBlocks.DATA_STORAGE.get().defaultBlockState());
    }

    private static Machine machine() {
        BlockPredicate storage = new BlockPredicate.OfBlock(ModBlocks.DATA_STORAGE.get());
        return new DynamicMachine(MMCR.id("storage_binding_machine"), "Storage Binding",
                new BlockArray(Map.of(
                        new BlockPos(1, 0, 0), storage,
                        new BlockPos(0, 0, 1), storage,
                        new BlockPos(-1, 0, 0), storage,
                        new BlockPos(0, 0, -1), storage)),
                MachineControllerSpec.defaultsFor(MMCR.id("storage_binding_machine")),
                new MachineAppearanceSpec(MMCR.id("block/basic_casing"), MMCR.id("block/basic_casing"),
                        MMCR.id("block/storage_casing")),
                PortRequirementSpec.none(), PortTierRequirementSpec.none(), List.of(), Map.of());
    }
}
