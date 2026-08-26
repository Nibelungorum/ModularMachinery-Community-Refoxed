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
import cn.howxu.mmcr.internal.tile.CombinedPortBlockEntity;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.registry.ModBlocks;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * World-level coverage for ordinary combined ports.
 *
 * @author howxu <dev@howxu.cn>
 */
public class CombinedPortGameTest {

    public void combinedPortSupportsFluidContainerInteraction(GameTestHelper helper) {
        BlockPos inputPos = new BlockPos(0, 1, 0);
        BlockPos outputPos = new BlockPos(2, 1, 0);
        helper.setBlock(inputPos, ModBlocks.BLOCKS.get("combined_input_basic").get().defaultBlockState());
        helper.setBlock(outputPos, ModBlocks.BLOCKS.get("combined_output_basic").get().defaultBlockState());
        CombinedPortBlockEntity input = helper.getBlockEntity(inputPos, CombinedPortBlockEntity.class);
        CombinedPortBlockEntity output = helper.getBlockEntity(outputPos, CombinedPortBlockEntity.class);
        ServerPlayer player = player(helper);

        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WATER_BUCKET, 2));
        InteractionResult emptied = use(helper, input, player);
        helper.assertTrue(emptied.consumesAction(), "Combined input accepts a filled fluid container");
        helper.assertTrue(input.fluidStorage().getAmountAsLong() == 1_000L,
                "Combined input receives one bucket of fluid");
        helper.assertTrue(player.getInventory().contains(new ItemStack(Items.BUCKET)),
                "Combined input returns an empty bucket");

        output.fluidStorage().setFluid(new FluidStack(Fluids.WATER, 1_000));
        player.getInventory().clearContent();
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BUCKET, 2));
        InteractionResult filled = use(helper, output, player);
        helper.assertTrue(filled.consumesAction(), "Combined output fills an empty fluid container");
        helper.assertTrue(output.fluidStorage().isEmpty(), "Combined output drains one bucket");
        helper.assertTrue(player.getInventory().contains(new ItemStack(Items.WATER_BUCKET)),
                "Combined output returns a filled bucket");

        BlockPos extendedPos = new BlockPos(4, 1, 0);
        helper.setBlock(extendedPos,
                ModBlocks.BLOCKS.get("extended_combined_input_advanced").get().defaultBlockState());
        BlockPos extendedWorldPos = helper.absolutePos(extendedPos);
        BlockEntity extended = helper.getLevel().getBlockEntity(extendedWorldPos);
        helper.assertTrue(ModCapabilities.FLUID_BLOCK.getCapability(
                        helper.getLevel(), extendedWorldPos, helper.getLevel().getBlockState(extendedWorldPos),
                        extended, Direction.UP) != null,
                "Extended combined fluid capability is available");
        helper.succeed();
    }

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

    private static InteractionResult use(GameTestHelper helper, CombinedPortBlockEntity port, ServerPlayer player) {
        BlockPos position = port.getBlockPos();
        return helper.getLevel().getBlockState(position).useItemOn(
                player.getMainHandItem(), helper.getLevel(), player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(position), Direction.UP, position, false));
    }

    private static ServerPlayer player(GameTestHelper helper) {
        return new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
                new GameProfile(UUID.nameUUIDFromBytes("mmcr-combined-fluid".getBytes(StandardCharsets.UTF_8)),
                        "mmcr-combined-fluid"),
                ClientInformation.createDefault());
    }
}
