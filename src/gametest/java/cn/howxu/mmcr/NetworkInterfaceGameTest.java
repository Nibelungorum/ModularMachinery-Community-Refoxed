package cn.howxu.mmcr;

import cn.howxu.mmcr.api.network.MachineReference;
import cn.howxu.mmcr.internal.tile.NetworkInterfaceBlockEntity;
import cn.howxu.mmcr.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;

/**
 * World-level lifecycle coverage for network interfaces.
 *
 * @author howxu <dev@howxu.cn>
 */
public class NetworkInterfaceGameTest {
    public void sameBlockStateReplacementPreservesConnections(GameTestHelper helper) {
        BlockPos firstPos = new BlockPos(0, 1, 0);
        BlockPos secondPos = new BlockPos(1, 1, 0);
        NetworkInterfaceBlockEntity second = placeConnectedInterfaces(helper, firstPos, secondPos);

        helper.getLevel().setBlock(helper.absolutePos(firstPos), ModBlocks.NETWORK_INTERFACE.get().defaultBlockState(), 3);

        helper.assertTrue(second.connections().size() == 1,
                "Replacing a network interface with its own state preserves peer connections");
        helper.succeed();
    }

    public void blockReplacementClearsPeerConnections(GameTestHelper helper) {
        BlockPos firstPos = new BlockPos(0, 1, 0);
        BlockPos secondPos = new BlockPos(1, 1, 0);
        NetworkInterfaceBlockEntity second = placeConnectedInterfaces(helper, firstPos, secondPos);

        helper.getLevel().setBlock(helper.absolutePos(firstPos), Blocks.STONE.defaultBlockState(), 3);

        helper.assertTrue(second.connections().isEmpty(),
                "Replacing a network interface removes its endpoint from loaded peers");
        helper.succeed();
    }

    private static NetworkInterfaceBlockEntity placeConnectedInterfaces(GameTestHelper helper, BlockPos firstPos,
                                                                          BlockPos secondPos) {
        helper.setBlock(firstPos, ModBlocks.NETWORK_INTERFACE.get().defaultBlockState());
        helper.setBlock(secondPos, ModBlocks.NETWORK_INTERFACE.get().defaultBlockState());
        NetworkInterfaceBlockEntity first = helper.getBlockEntity(firstPos, NetworkInterfaceBlockEntity.class);
        NetworkInterfaceBlockEntity second = helper.getBlockEntity(secondPos, NetworkInterfaceBlockEntity.class);
        GlobalPos firstEndpoint = GlobalPos.of(helper.getLevel().dimension(), first.getBlockPos());
        GlobalPos secondEndpoint = GlobalPos.of(helper.getLevel().dimension(), second.getBlockPos());
        MachineReference machine = new MachineReference(MMCR.id("network_interface_lifecycle"), 1L);
        first.addConnection(new NetworkInterfaceBlockEntity.Connection(secondEndpoint, machine, 0L));
        second.addConnection(new NetworkInterfaceBlockEntity.Connection(firstEndpoint, machine, 0L));
        return second;
    }
}
