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
import cn.howxu.mmcr.internal.event.ModCapabilities;
import cn.howxu.mmcr.internal.tile.ExtendedCombinedPortBlockEntity;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import java.util.List;
import java.util.Map;

/**
 * World-level coverage for extended combined ports.
 *
 * @author howxu <dev@howxu.cn>
 */
public class ExtendedPortGameTest {

    public void extendedCombinedPortTransfersBeyondIntegerRange(GameTestHelper helper) {
        BlockPos portPos = new BlockPos(0, 1, 0);
        helper.setBlock(portPos, ModBlocks.BLOCKS.get("extended_combined_input_advanced").get().defaultBlockState());
        ExtendedCombinedPortBlockEntity port = helper.getBlockEntity(portPos, ExtendedCombinedPortBlockEntity.class);
        BlockPos worldPos = helper.absolutePos(portPos);
        BlockEntity blockEntity = helper.getLevel().getBlockEntity(worldPos);

        ResourceHandler<ItemResource> itemHandler = ModCapabilities.ITEM_BLOCK.getCapability(
                helper.getLevel(), worldPos, helper.getLevel().getBlockState(worldPos), blockEntity, Direction.EAST);
        ResourceHandler<FluidResource> fluidHandler = ModCapabilities.FLUID_BLOCK.getCapability(
                helper.getLevel(), worldPos, helper.getLevel().getBlockState(worldPos), blockEntity, Direction.WEST);
        helper.assertTrue(itemHandler != null, "Extended combined item handler is exposed");
        helper.assertTrue(fluidHandler != null, "Extended combined fluid handler is exposed");

        try (Transaction transaction = Transaction.openRoot()) {
            itemHandler.insert(0, ItemResource.of(Items.IRON_INGOT), Integer.MAX_VALUE, transaction);
            itemHandler.insert(0, ItemResource.of(Items.IRON_INGOT), Integer.MAX_VALUE, transaction);
            fluidHandler.insert(0, FluidResource.of(Fluids.WATER), Integer.MAX_VALUE, transaction);
            fluidHandler.insert(0, FluidResource.of(Fluids.WATER), Integer.MAX_VALUE, transaction);
            transaction.commit();
        }

        helper.assertTrue(port.itemStorage().amount(0) > Integer.MAX_VALUE,
                "Extended item storage preserves cumulative amounts above int range");
        helper.assertTrue(port.fluidStorage().amount(0) > Integer.MAX_VALUE,
                "Extended fluid storage preserves cumulative amounts above int range");
        helper.succeed();
    }

    public void extendedCombinedPortPublishesFormedAppearance(GameTestHelper helper) {
        BlockPos controllerPos = new BlockPos(0, 1, 0);
        BlockPos portPos = controllerPos.relative(Direction.EAST);
        var controllerBlock = ModBlocks.controllerFor(MMCR.id("test_cube")).get();
        var portBlock = ModBlocks.BLOCKS.get("extended_combined_input_advanced").get();
        helper.setBlock(controllerPos, controllerBlock.defaultBlockState().setValue(MachineControllerBlock.FACING, Direction.SOUTH));
        helper.setBlock(portPos, portBlock.defaultBlockState());

        MachineControllerBlockEntity controller = helper.getBlockEntity(controllerPos, MachineControllerBlockEntity.class);
        ExtendedCombinedPortBlockEntity port = helper.getBlockEntity(portPos, ExtendedCombinedPortBlockEntity.class);
        try (Transaction transaction = Transaction.openRoot()) {
            port.fluidStorage().insert(0, FluidResource.of(Fluids.WATER), 1L, transaction);
            transaction.commit();
        }
        Identifier texture = MMCR.id("block/extended_combined_test_casing");
        DynamicMachine machine = new DynamicMachine(
                MMCR.id("extended_combined_appearance_test"),
                "extended combined appearance test",
                new BlockArray(Map.of(portPos.subtract(controllerPos), new BlockPredicate.OfBlock(portBlock))),
                MachineControllerSpec.defaultsFor(MMCR.id("test_cube")),
                new MachineAppearanceSpec(MMCR.id("basic_casing"), MMCR.id("block/basic_casing"), texture),
                PortRequirementSpec.none(), PortTierRequirementSpec.none(), List.of(), Map.of());
        controller.setMachine(machine);
        helper.runAtTickTime(20, () -> {
            helper.assertTrue(controller.structureSnapshot().formed(), "Extended combined port controller forms");
            helper.assertTrue(controller.runtimeSnapshot().linkedPortPositions().contains(port.getBlockPos()),
                    "Formed controller links the extended combined port");
            helper.assertTrue(port.appearanceBaseTexture().equals(texture),
                    "Extended combined port receives formed appearance texture");
            helper.assertTrue(port.getModelData().get(MachineModelDataKeys.PORT_BASE_TEXTURE).equals(texture),
                    "Extended combined port model data exposes formed appearance texture");
            helper.succeed();
        });
    }
}
