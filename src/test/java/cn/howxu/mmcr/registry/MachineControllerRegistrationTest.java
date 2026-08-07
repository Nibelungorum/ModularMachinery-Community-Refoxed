package cn.howxu.mmcr.registry;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.MachineControllerSpec;
import cn.howxu.mmcr.internal.block.MachineControllerBlock;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MachineControllerRegistrationTest {

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    @Test
    void default_blast_furnace_controller_is_registered_as_machine_specific_block_item_and_be() {
        Identifier machineId = MMCR.id("blast_furnace");
        String controllerName = MachineControllerSpec.defaultsFor(machineId).id().getPath();

        assertThat(ModBlocks.BLOCKS).containsKey(controllerName);
        assertThat(ModItems.ITEMS).containsKey(controllerName);
        assertThat(ModBlockEntities.BES).containsKey(controllerName);
        assertThat(ModBlocks.controllerFor(machineId)).isSameAs(ModBlocks.BLOCKS.get(controllerName));
        assertThat(ModBlockEntities.controllerFor(machineId)).isSameAs(ModBlockEntities.BES.get(controllerName));
    }

    @Test
    void generated_controller_keeps_legacy_controller_alias_for_existing_tests() {
        assertThat(ModBlocks.CONTROLLER).isSameAs(ModBlocks.controllerFor(MMCR.id("blast_furnace")));
    }

    @Test
    void controller_block_knows_owning_machine_id() throws Exception {
        MachineControllerBlock block = controllerBlockWithoutRunningMinecraftConstructor(MMCR.id("blast_furnace"));

        assertThat(block.machineId()).isEqualTo(MMCR.id("blast_furnace"));
        assertThat(ModBlocks.machineIdForController(block)).isEqualTo(MMCR.id("blast_furnace"));
    }

    @Test
    void controllerBlockItemSupplierReservesItemForMachine() throws Exception {
        Identifier id = MMCR.id("blast_furnace");
        String controllerName = MachineControllerSpec.defaultsFor(id).id().getPath();
        Item controllerItem = ModItems.ITEMS.get(controllerName).get();

        assertThat(ModBlocks.hasControllerFor(id)).isTrue();
        assertThat(controllerItem).isSameAs(ModBlocks.controllerFor(id).get().asItem());
        assertThat(controllerMachineIds()).containsEntry(controllerItem, id);
        assertThat(ModItems.machineIdForControllerItem(controllerItem)).isEqualTo(id);
        assertThat(ModItems.machineIdForControllerItem(Items.AIR)).isNull();
    }

    @SuppressWarnings("unchecked")
    private static Map<Item, Identifier> controllerMachineIds() throws Exception {
        Field field = ModItems.class.getDeclaredField("controllerMachineIds");
        field.setAccessible(true);
        return (Map<Item, Identifier>) field.get(null);
    }

    private static MachineControllerBlock controllerBlockWithoutRunningMinecraftConstructor(Identifier machineId) throws Exception {
        Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        sun.misc.Unsafe unsafe = (sun.misc.Unsafe) unsafeField.get(null);
        MachineControllerBlock block = (MachineControllerBlock) unsafe.allocateInstance(MachineControllerBlock.class);
        Field machineIdField = MachineControllerBlock.class.getDeclaredField("machineId");
        machineIdField.setAccessible(true);
        machineIdField.set(block, machineId);
        return block;
    }

}
