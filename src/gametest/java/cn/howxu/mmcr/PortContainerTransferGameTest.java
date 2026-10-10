package cn.howxu.mmcr;

import cn.howxu.mmcr.api.capability.transfer.ContainerResourceTransfer;
import cn.howxu.mmcr.internal.menu.CombinedPortMenu;
import cn.howxu.mmcr.internal.menu.FluidHatchMenu;
import cn.howxu.mmcr.internal.network.PktPortContainerTransferPayload;
import cn.howxu.mmcr.internal.tile.CombinedPortBlockEntity;
import cn.howxu.mmcr.internal.tile.FluidHatchBlockEntity;
import cn.howxu.mmcr.internal.tile.IOPortBlockEntity;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.util.IOType;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Real bucket exchanges through the server-side ordinary port menu boundary.
 * @author howxu <dev@howxu.cn> */
public class PortContainerTransferGameTest {
    public void bucketsFollowPortDirection(GameTestHelper helper) {
        BlockPos inputPos = new BlockPos(0, 1, 0);
        BlockPos outputPos = new BlockPos(2, 1, 0);
        helper.setBlock(inputPos, ModBlocks.BLOCKS.get("fluid_input_hatch").get().defaultBlockState());
        helper.setBlock(outputPos, ModBlocks.BLOCKS.get("fluid_output_hatch").get().defaultBlockState());
        FluidHatchBlockEntity input = helper.getBlockEntity(inputPos);
        FluidHatchBlockEntity output = helper.getBlockEntity(outputPos);
        ServerPlayer player = player(helper, input);
        player.containerMenu = new FluidHatchMenu(17, player.getInventory(), input);
        player.containerMenu.setCarried(new ItemStack(Items.WATER_BUCKET));
        helper.assertTrue(click(player, 0) == 1_000
                        && ItemStack.matches(player.containerMenu.getCarried(), new ItemStack(Items.BUCKET))
                        && input.fluidHandler(null).resource(0).is(Fluids.WATER)
                        && input.fluidHandler(null).amount(0) == 1_000,
                "Input receives bucket contents and exchanges the cursor item");
        helper.assertTrue(click(player, 0) == 0 && input.fluidHandler(null).amount(0) == 1_000
                        && player.containerMenu.getCarried().is(Items.BUCKET),
                "Input cannot refill an empty cursor bucket");
        output.fluidHandler(null).setFluid(new FluidStack(Fluids.WATER, 1_000));
        player.setPos(output.getBlockPos().getCenter());
        player.containerMenu = new FluidHatchMenu(18, player.getInventory(), output);
        player.containerMenu.setCarried(new ItemStack(Items.WATER_BUCKET));
        helper.assertTrue(click(player, 0) == 0 && output.fluidHandler(null).amount(0) == 1_000,
                "Output cannot receive a filled bucket");
        player.containerMenu.setCarried(new ItemStack(Items.BUCKET));
        helper.assertTrue(click(player, 0) == 1_000 && output.fluidHandler(null).amount(0) == 0
                        && ItemStack.matches(player.containerMenu.getCarried(), new ItemStack(Items.WATER_BUCKET)),
                "Output fills the cursor bucket and consumes its contents");
        helper.succeed();
    }

    public void partialBucketsRollbackBothSides(GameTestHelper helper) {
        BlockPos pos = new BlockPos(0, 1, 0);
        helper.setBlock(pos, ModBlocks.BLOCKS.get("fluid_input_hatch").get().defaultBlockState());
        FluidHatchBlockEntity port = helper.getBlockEntity(pos);
        long before = port.fluidHandler(null).capacity(0) - 500;
        port.fluidHandler(null).setContents(0, new FluidStack(Fluids.WATER, 1), before);
        ServerPlayer player = player(helper, port);
        player.containerMenu = new FluidHatchMenu(17, player.getInventory(), port);
        player.containerMenu.setCarried(new ItemStack(Items.WATER_BUCKET));
        helper.assertTrue(click(player, 0) == 0 && port.fluidHandler(null).amount(0) == before
                        && port.fluidHandler(null).resource(0).is(Fluids.WATER)
                        && ItemStack.matches(player.containerMenu.getCarried(), new ItemStack(Items.WATER_BUCKET)),
                "A bucket cannot be partially emptied and both sides remain unchanged");
        helper.setBlock(pos, ModBlocks.BLOCKS.get("fluid_output_hatch").get().defaultBlockState());
        port = helper.getBlockEntity(pos);
        port.fluidHandler(null).setFluid(new FluidStack(Fluids.WATER, 500));
        player.containerMenu = new FluidHatchMenu(18, player.getInventory(), port);
        player.containerMenu.setCarried(new ItemStack(Items.BUCKET));
        helper.assertTrue(click(player, 0) == 0 && port.fluidHandler(null).amount(0) == 500
                        && ItemStack.matches(player.containerMenu.getCarried(), new ItemStack(Items.BUCKET)),
                "A bucket cannot be partially filled and source extraction is never committed");
        helper.succeed();
    }

    public void combinedSecondTankIsIndependent(GameTestHelper helper) {
        BlockPos pos = new BlockPos(0, 1, 0);
        for (String direction : List.of("input", "output")) {
            helper.setBlock(pos, ModBlocks.BLOCKS.get("combined_" + direction + "_reinforced").get().defaultBlockState());
            CombinedPortBlockEntity port = helper.getBlockEntity(pos);
            port.itemHandler().insertItem(0, new ItemStack(Items.STONE, 7), false);
            port.fluidHandler(null).setContents(0, new FluidStack(Fluids.LAVA, 1), 2_000);
            boolean input = direction.equals("input");
            if (!input) port.fluidHandler(null).setContents(1, new FluidStack(Fluids.WATER, 1), 1_000);
            ServerPlayer player = player(helper, port);
            player.containerMenu = new CombinedPortMenu(17, player.getInventory(), port);
            ItemStack carried = new ItemStack(input ? Items.WATER_BUCKET : Items.BUCKET);
            player.containerMenu.setCarried(carried);
            helper.assertTrue(click(player, -1) == 0 && click(player, 2) == 0
                            && ItemStack.matches(player.containerMenu.getCarried(), carried),
                    "Combined tank indices are checked before transfer");
            helper.assertTrue(click(player, 1) == 1_000
                            && port.fluidHandler(null).amount(0) == 2_000
                            && port.fluidHandler(null).resource(0).is(Fluids.LAVA)
                            && port.fluidHandler(null).amount(1) == (input ? 1_000 : 0)
                            && port.itemHandler().amount(0) == 7
                            && port.itemHandler().getStackInSlot(0).is(Items.STONE)
                            && player.containerMenu.getCarried().is(input ? Items.BUCKET : Items.WATER_BUCKET),
                    "Only the clicked second tank changes; items and the first tank stay intact");
        }
        helper.succeed();
    }

    public void stackedBucketsRespectInventoryCapacity(GameTestHelper helper) {
        BlockPos pos = new BlockPos(0, 1, 0);
        helper.setBlock(pos, ModBlocks.BLOCKS.get("fluid_output_hatch").get().defaultBlockState());
        FluidHatchBlockEntity port = helper.getBlockEntity(pos);
        ServerPlayer player = player(helper, port);
        player.containerMenu = new FluidHatchMenu(17, player.getInventory(), port);
        player.getInventory().clearContent();
        int entitiesBefore = nearbyDrops(helper, player).size();
        int dropsBefore = droppedWaterBuckets(helper, player);
        port.fluidHandler(null).setFluid(new FluidStack(Fluids.WATER, 1_000));
        player.containerMenu.setCarried(new ItemStack(Items.BUCKET, 2));
        helper.assertTrue(click(player, 0) == 0 && port.fluidHandler(null).amount(0) == 1_000
                        && ItemStack.matches(player.containerMenu.getCarried(), new ItemStack(Items.BUCKET, 2))
                        && player.getInventory().isEmpty() && nearbyDrops(helper, player).size() == entitiesBefore,
                "Insufficient water rejects the whole stack without changing cursor, inventory or world");
        port.fluidHandler(null).setFluid(new FluidStack(Fluids.WATER, 2_000));
        helper.assertTrue(click(player, 0) == 2_000 && port.fluidHandler(null).isEmpty()
                        && ItemStack.matches(player.containerMenu.getCarried(), new ItemStack(Items.WATER_BUCKET))
                        && player.getInventory().items.stream().filter(stack -> stack.is(Items.WATER_BUCKET))
                        .mapToInt(ItemStack::getCount).sum() == 1
                        && player.getInventory().items.stream().noneMatch(stack -> stack.is(Items.BUCKET))
                        && nearbyDrops(helper, player).size() == entitiesBefore,
                "Whole-stack conversion places one filled bucket on cursor and the overflow in inventory");
        player.getInventory().clearContent();
        for (int index = 0; index < player.getInventory().items.size(); index++) {
            player.getInventory().setItem(index, new ItemStack(Items.STONE, 64));
        }
        List<ItemStack> inventoryBefore = new ArrayList<>();
        for (int index = 0; index < player.getInventory().getContainerSize(); index++) {
            inventoryBefore.add(player.getInventory().getItem(index).copy());
        }
        player.containerMenu.setCarried(new ItemStack(Items.BUCKET, 2));
        port.fluidHandler(null).setFluid(new FluidStack(Fluids.WATER, 500));
        helper.assertTrue(click(player, 0) == 0 && port.fluidHandler(null).amount(0) == 500
                        && ItemStack.matches(player.containerMenu.getCarried(), new ItemStack(Items.BUCKET, 2))
                        && nearbyDrops(helper, player).size() == entitiesBefore,
                "Insufficient fill is also rejected with a full inventory");
        assertInventoryUnchanged(helper, player, inventoryBefore);
        port.fluidHandler(null).setFluid(new FluidStack(Fluids.WATER, 2_000));
        var simulated = ContainerResourceTransfer.transferCarried(IOType.OUTPUT,
                player.containerMenu.getCarried(), port.fluidHandler(null), true);
        helper.assertTrue(simulated.amount() == 2_000 && port.fluidHandler(null).amount(0) == 2_000
                        && ItemStack.matches(player.containerMenu.getCarried(), new ItemStack(Items.BUCKET, 2))
                        && nearbyDrops(helper, player).size() == entitiesBefore,
                "Simulation prepares the entire exchange without consuming water or spawning overflow");
        assertInventoryUnchanged(helper, player, inventoryBefore);
        helper.assertTrue(click(player, 0) == 2_000 && port.fluidHandler(null).isEmpty()
                        && ItemStack.matches(player.containerMenu.getCarried(), new ItemStack(Items.WATER_BUCKET))
                        && droppedWaterBuckets(helper, player) == dropsBefore + 1
                        && nearbyDrops(helper, player).size() == entitiesBefore + 1,
                "Committed whole-stack fill drops exactly one overflow bucket when inventory is full");
        assertInventoryUnchanged(helper, player, inventoryBefore);
        helper.succeed();
    }

    public void invalidRequestsCannotMutateStorage(GameTestHelper helper) {
        BlockPos pos = new BlockPos(0, 1, 0);
        helper.setBlock(pos, ModBlocks.BLOCKS.get("fluid_input_hatch").get().defaultBlockState());
        FluidHatchBlockEntity port = helper.getBlockEntity(pos);
        ServerPlayer player = player(helper, port);
        player.containerMenu = new FluidHatchMenu(17, player.getInventory(), port);
        helper.assertTrue(click(player, 0) == 0, "Empty cursor cannot transfer resources");
        player.containerMenu.setCarried(new ItemStack(Items.STONE, 2));
        helper.assertTrue(click(player, 0) == 0
                        && ItemStack.matches(player.containerMenu.getCarried(), new ItemStack(Items.STONE, 2)),
                "Non-container items retain their identity and count");
        player.containerMenu.setCarried(new ItemStack(Items.WATER_BUCKET));
        helper.assertTrue(PktPortContainerTransferPayload.interactOnServer(player, null) == 0
                        && PktPortContainerTransferPayload.interactOnServer(player, new PktPortContainerTransferPayload(16, 0)) == 0
                        && click(player, -1) == 0 && click(player, 1) == 0 && click(player, Integer.MAX_VALUE) == 0,
                "Missing payload, stale menu and invalid tank indices are rejected");
        player.setPos(port.getBlockPos().getCenter().add(32, 0, 0));
        helper.assertTrue(click(player, 0) == 0, "Out-of-range player cannot transfer");
        player.setPos(port.getBlockPos().getCenter());
        player.containerMenu = new FluidHatchMenu(17, player.getInventory(), port.getBlockPos());
        player.containerMenu.setCarried(new ItemStack(Items.WATER_BUCKET));
        helper.assertTrue(click(player, 0) == 0, "Position-only menu cannot substitute the server owner");
        player.containerMenu = new FluidHatchMenu(17, player.getInventory(), port);
        player.containerMenu.setCarried(new ItemStack(Items.WATER_BUCKET));
        helper.setBlock(pos, ModBlocks.CASING.get().defaultBlockState());
        helper.assertTrue(click(player, 0) == 0 && port.fluidHandler(null).isEmpty(),
                "Detached owner is not modified after block replacement");
        helper.setBlock(pos, ModBlocks.BLOCKS.get("extended_fluid_input_hatch_basic").get().defaultBlockState());
        FluidHatchBlockEntity extended = helper.getBlockEntity(pos);
        player.containerMenu = new FluidHatchMenu(18, player.getInventory(), extended);
        player.containerMenu.setCarried(new ItemStack(Items.WATER_BUCKET));
        helper.assertTrue(click(player, 0) == 0, "Forged ordinary menu cannot expose an extended port");
        AbstractContainerMenu previousMenu = player.containerMenu;
        player.containerMenu = player.inventoryMenu;
        player.containerMenu.setCarried(new ItemStack(Items.WATER_BUCKET));
        helper.assertTrue(!PktPortContainerTransferPayload.validTarget(player, previousMenu, extended)
                        && click(player, 0) == 0 && extended.fluidHandler(null).isEmpty()
                        && ItemStack.matches(player.containerMenu.getCarried(), new ItemStack(Items.WATER_BUCKET)),
                "Only the current real port menu may access storage");
        helper.succeed();
    }

    private static ServerPlayer player(GameTestHelper helper, IOPortBlockEntity port) {
        ServerPlayer player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "mmcr-container"), ClientInformation.createDefault());
        player.setPos(port.getBlockPos().getCenter());
        return player;
    }

    private static int click(ServerPlayer player, int tankIndex) {
        return PktPortContainerTransferPayload.interactOnServer(player,
                new PktPortContainerTransferPayload(player.containerMenu.containerId, tankIndex));
    }

    private static List<ItemEntity> nearbyDrops(GameTestHelper helper, ServerPlayer player) {
        return helper.getLevel().getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(3));
    }

    private static int droppedWaterBuckets(GameTestHelper helper, ServerPlayer player) {
        return nearbyDrops(helper, player).stream().filter(entity -> entity.getItem().is(Items.WATER_BUCKET))
                .mapToInt(entity -> entity.getItem().getCount()).sum();
    }

    private static void assertInventoryUnchanged(GameTestHelper helper, ServerPlayer player, List<ItemStack> before) {
        for (int index = 0; index < before.size(); index++) {
            helper.assertTrue(ItemStack.matches(player.getInventory().getItem(index), before.get(index)),
                    "Full inventory retains original contents in slot " + index);
        }
    }
}
