package cn.howxu.mmcr;

import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.api.machine.DynamicMachine;
import cn.howxu.mmcr.api.machine.MachineAppearanceSpec;
import cn.howxu.mmcr.api.machine.MachineControllerSpec;
import cn.howxu.mmcr.api.machine.PortRequirementSpec;
import cn.howxu.mmcr.api.machine.PortTierRequirementSpec;
import cn.howxu.mmcr.client.model.MachineModelDataKeys;
import cn.howxu.mmcr.internal.block.MachineControllerBlock;
import cn.howxu.mmcr.internal.tile.CombinedPortBlockEntity;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Map;

/**
 * World-level coverage for ordinary combined ports.
 *
 * @author howxu <dev@howxu.cn>
 */
public class CombinedPortGameTest {

    public void combinedPortPublishesFormedAppearance(GameTestHelper helper) {
        BlockPos controllerPos = new BlockPos(0, 1, 0);
        BlockPos portPos = controllerPos.relative(Direction.EAST);
        var controllerBlock = ModBlocks.controllerFor(MMCR.id("test_cube")).get();
        var portBlock = ModBlocks.BLOCKS.get("combined_input_basic").get();
        helper.setBlock(controllerPos, controllerBlock.defaultBlockState().setValue(MachineControllerBlock.FACING, Direction.SOUTH));
        helper.setBlock(portPos, portBlock.defaultBlockState());

        MachineControllerBlockEntity controller = helper.getBlockEntity(controllerPos, MachineControllerBlockEntity.class);
        CombinedPortBlockEntity port = helper.getBlockEntity(portPos, CombinedPortBlockEntity.class);
        Identifier texture = MMCR.id("block/combined_test_casing");
        DynamicMachine machine = new DynamicMachine(
                MMCR.id("combined_appearance_test"),
                "combined appearance test",
                new BlockArray(Map.of(portPos.subtract(controllerPos), new BlockPredicate.OfBlock(portBlock))),
                MachineControllerSpec.defaultsFor(MMCR.id("test_cube")),
                new MachineAppearanceSpec(MMCR.id("basic_casing"), MMCR.id("block/basic_casing"), texture),
                PortRequirementSpec.none(), PortTierRequirementSpec.none(), List.of(), Map.of());
        controller.setMachine(machine);
        helper.runAtTickTime(20, () -> {
            helper.assertTrue(controller.structureSnapshot().formed(), "Combined port controller forms");
            helper.assertTrue(controller.runtimeSnapshot().linkedPortPositions().contains(port.getBlockPos()),
                    "Formed controller links the combined port");
            helper.assertTrue(port.appearanceBaseTexture().equals(texture),
                    "Combined port receives formed appearance texture");
            helper.assertTrue(port.getModelData().get(MachineModelDataKeys.PORT_BASE_TEXTURE).equals(texture),
                    "Combined port model data exposes formed appearance texture");
            helper.succeed();
        });
    }
}
