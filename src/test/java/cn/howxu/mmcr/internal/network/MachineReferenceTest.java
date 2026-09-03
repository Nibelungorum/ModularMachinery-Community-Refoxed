package cn.howxu.mmcr.internal.network;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.network.MachineReference;
import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.api.machine.DynamicMachine;
import cn.howxu.mmcr.api.machine.MachineControllerSpec;
import cn.howxu.mmcr.test.RuntimeTestFixtures;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Verifies stable machine identity hashing and value semantics.
 *
 * @author howxu <dev@howxu.cn>
 */
class MachineReferenceTest {
    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    @Test
    void sameMachineInputsProduceStableIdentity() {
        Identifier dimension = Identifier.parse("minecraft:overworld");
        Identifier type = Identifier.parse("mmcr:assembler");
        BlockPos pos = new BlockPos(12, 64, -8);

        assertEquals(MachineReferenceHasher.hash(dimension, type, pos),
                MachineReferenceHasher.hash(dimension, type, pos));
        assertEquals(new MachineReference(type, MachineReferenceHasher.hash(dimension, type, pos)),
                new MachineReference(type, MachineReferenceHasher.hash(dimension, type, pos)));
    }

    @Test
    void eachMachineInputChangesTheIdentity() {
        Identifier dimension = Identifier.parse("minecraft:overworld");
        Identifier type = Identifier.parse("mmcr:assembler");
        BlockPos pos = new BlockPos(12, 64, -8);
        long original = MachineReferenceHasher.hash(dimension, type, pos);

        assertNotEquals(original, MachineReferenceHasher.hash(Identifier.parse("minecraft:the_nether"), type, pos));
        assertNotEquals(MachineReferenceHasher.hash(Identifier.parse("a:bc"), type, pos),
                MachineReferenceHasher.hash(Identifier.parse("ab:c"), type, pos));
        assertNotEquals(original, MachineReferenceHasher.hash(dimension, Identifier.parse("mmcr:foundry"), pos));
        assertNotEquals(original, MachineReferenceHasher.hash(dimension, type, new BlockPos(13, 64, -8)));
        assertNotEquals(original, MachineReferenceHasher.hash(dimension, type, new BlockPos(12, 65, -8)));
        assertNotEquals(original, MachineReferenceHasher.hash(dimension, type, new BlockPos(12, 64, -7)));
    }

    @Test
    void machineReferenceRequiresItsType() {
        assertThrows(NullPointerException.class, () -> new MachineReference(null, 1L));
    }

    @Test
    void hasherRejectsNullInputs() {
        Identifier dimension = Identifier.parse("minecraft:overworld");
        Identifier type = Identifier.parse("mmcr:assembler");
        BlockPos pos = new BlockPos(12, 64, -8);

        assertThrows(NullPointerException.class, () -> MachineReferenceHasher.hash(null, type, pos));
        assertThrows(NullPointerException.class, () -> MachineReferenceHasher.hash(dimension, null, pos));
        assertThrows(NullPointerException.class, () -> MachineReferenceHasher.hash(dimension, type, null));
    }

    @Test
    void formedControllerCachesAndResetsItsMachineReference() {
        Identifier machineType = MMCR.id("machine_reference_test");
        BlockPos controllerPos = new BlockPos(12, 64, -8);
        DynamicMachine machine = new DynamicMachine(machineType, "Machine Reference Test",
                new BlockArray(Map.of(new BlockPos(1, 0, 0), new BlockPredicate.Any())),
                MachineControllerSpec.defaultsFor(machineType));
        var controller = RuntimeTestFixtures.controllerEntity(MMCR.id("test_cube"), controllerPos);

        assertNull(controller.machineReference());

        RuntimeTestFixtures.formStructure(controller, machine);
        MachineReference reference = controller.machineReference();
        assertNotNull(reference);
        assertSame(reference, controller.machineReference());
        assertEquals(machineType, reference.type());
        assertEquals(MachineReferenceHasher.hash(Identifier.parse("minecraft:overworld"), machineType, controllerPos),
                reference.hash());

        controller.requestImmediateStructureCheck();
        assertNotSame(reference, controller.machineReference());

        controller.invalidateFormedStructure();
        assertNull(controller.machineReference());
    }
}
