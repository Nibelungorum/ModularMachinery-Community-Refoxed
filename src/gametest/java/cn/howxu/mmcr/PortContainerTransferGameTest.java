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
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Exercises real bucket exchanges through the server-side ordinary port menu boundary.
 *
 * @author howxu <dev@howxu.cn>
 */
public class PortContainerTransferGameTest {
    public void bucketsFollowPortDirection(GameTestHelper helper) {
        BlockPos inputPos = new BlockPos(0, 1, 0);
        BlockPos outputPos = new BlockPos(2, 1, 0);
        helper.setBlock(inputPos, ModBlocks.BLOCKS.get("fluid_input_hatch").get().defaultBlockState());
        helper.setBlock(outputPos, ModBlocks.BLOCKS.get("fluid_output_hatch").get().defaultBlockState());
        var input = helper.getBlockEntity(inputPos, FluidHatchBlockEntity.class);
        var output = helper.getBlockEntity(outputPos, FluidHatchBlockEntity.class);
        ServerPlayer player = player(helper, input);
        player.containerMenu = new FluidHatchMenu(17, player.getInventory(), input);
        player.containerMenu.setCarried(new ItemStack(Items.WATER_BUCKET));
        helper.assertTrue(click(player, 0) == 1_000
                        && player.containerMenu.getCarried().is(Items.BUCKET)
                        && player.containerMenu.getCarried().getCount() == 1
                        && input.fluidStorage().resource(0).is(Fluids.WATER)
                        && input.fluidStorage().amount(0) == 1_000,
                "Input receives the bucket contents and exchanges the cursor item");
        helper.assertTrue(click(player, 0) == 0 && input.fluidStorage().amount(0) == 1_000
                        && player.containerMenu.getCarried().is(Items.BUCKET),
                "Input cannot fill the empty cursor bucket");
        output.fluidStorage().setFluid(new FluidStack(Fluids.WATER, 1_000));
        player.setPos(output.getBlockPos().getCenter());
        player.containerMenu = new FluidHatchMenu(18, player.getInventory(), output);
        player.containerMenu.setCarried(new ItemStack(Items.WATER_BUCKET));
        helper.assertTrue(click(player, 0) == 0 && output.fluidStorage().amount(0) == 1_000
                        && player.containerMenu.getCarried().is(Items.WATER_BUCKET),
                "Output cannot receive a filled cursor bucket");
        player.containerMenu.setCarried(new ItemStack(Items.BUCKET));
        helper.assertTrue(click(player, 0) == 1_000
                        && player.containerMenu.getCarried().is(Items.WATER_BUCKET)
                        && player.containerMenu.getCarried().getCount() == 1
                        && output.fluidStorage().amount(0) == 0,
                "Output fills the cursor bucket and consumes its contents");
        helper.succeed();
    }

    public void partialBucketsRollbackBothSides(GameTestHelper helper) {
        BlockPos pos = new BlockPos(0, 1, 0);
        helper.setBlock(pos, ModBlocks.BLOCKS.get("fluid_input_hatch").get().defaultBlockState());
        var port = helper.getBlockEntity(pos, FluidHatchBlockEntity.class);
        var water = FluidResource.of(Fluids.WATER);
        long before = port.fluidStorage().capacity(0, water) - 500;
        port.fluidStorage().setContents(water, before);
        ServerPlayer player = player(helper, port);
        player.containerMenu = new FluidHatchMenu(17, player.getInventory(), port);
        player.containerMenu.setCarried(new ItemStack(Items.WATER_BUCKET));
        helper.assertTrue(click(player, 0) == 0
                        && port.fluidStorage().amount(0) == before
                        && port.fluidStorage().resource(0).equals(water)
                        && player.containerMenu.getCarried().is(Items.WATER_BUCKET)
                        && player.containerMenu.getCarried().getCount() == 1,
                "A bucket cannot be partially emptied and target insertion is rolled back");
        helper.setBlock(pos, ModBlocks.BLOCKS.get("fluid_output_hatch").get().defaultBlockState());
        port = helper.getBlockEntity(pos, FluidHatchBlockEntity.class);
        port.fluidStorage().setFluid(new FluidStack(Fluids.WATER, 500));
        player.containerMenu = new FluidHatchMenu(18, player.getInventory(), port);
        player.containerMenu.setCarried(new ItemStack(Items.BUCKET));
        helper.assertTrue(click(player, 0) == 0 && port.fluidStorage().amount(0) == 500
                        && port.fluidStorage().resource(0).equals(water)
                        && player.containerMenu.getCarried().is(Items.BUCKET)
                        && player.containerMenu.getCarried().getCount() == 1,
                "A bucket cannot be partially filled and source extraction is rolled back");
        helper.succeed();
    }

    public void combinedSecondTankIsIndependent(GameTestHelper helper) {
        BlockPos pos = new BlockPos(0, 1, 0);
        helper.setBlock(pos, ModBlocks.BLOCKS.get("combined_input_reinforced").get().defaultBlockState());
        var port = helper.getBlockEntity(pos, CombinedPortBlockEntity.class);
        var stone = ItemResource.of(new ItemStack(Items.STONE));
        try (Transaction transaction = Transaction.openRoot()) {
            helper.assertTrue(port.itemStorage().insert(0, stone, 7, transaction) == 7,
                    "Combined input item fixture can hold the initial contents");
            transaction.commit();
        }
        port.fluidStorage().setContents(0, FluidResource.of(Fluids.LAVA), 2_000);
        ServerPlayer player = player(helper, port);
        player.containerMenu = new CombinedPortMenu(17, player.getInventory(), port);
        player.containerMenu.setCarried(new ItemStack(Items.WATER_BUCKET));
        helper.assertTrue(click(player, -1) == 0 && click(player, 2) == 0
                        && port.fluidStorage().amount(1) == 0
                        && player.containerMenu.getCarried().is(Items.WATER_BUCKET),
                "Combined tank indices are validated before any transfer");
        helper.assertTrue(click(player, 1) == 1_000
                        && player.containerMenu.getCarried().is(Items.BUCKET)
                        && port.fluidStorage().amount(0) == 2_000
                        && port.fluidStorage().resource(0).is(Fluids.LAVA)
                        && port.fluidStorage().amount(1) == 1_000
                        && port.fluidStorage().resource(1).is(Fluids.WATER)
                        && port.itemStorage().amount(0) == 7
                        && port.itemStorage().resource(0).equals(stone),
                "Input targets only the clicked second tank, leaving items and the first tank intact");
        helper.setBlock(pos, ModBlocks.BLOCKS.get("combined_output_reinforced").get().defaultBlockState());
        port = helper.getBlockEntity(pos, CombinedPortBlockEntity.class);
        try (Transaction transaction = Transaction.openRoot()) {
            helper.assertTrue(port.itemStorage().insert(0, stone, 7, transaction) == 7,
                    "Combined output item fixture can hold the initial contents");
            transaction.commit();
        }
        port.fluidStorage().setContents(0, FluidResource.of(Fluids.LAVA), 2_000);
        port.fluidStorage().setContents(1, FluidResource.of(Fluids.WATER), 1_000);
        player.containerMenu = new CombinedPortMenu(18, player.getInventory(), port);
        player.containerMenu.setCarried(new ItemStack(Items.BUCKET));
        helper.assertTrue(click(player, 1) == 1_000
                        && player.containerMenu.getCarried().is(Items.WATER_BUCKET)
                        && port.fluidStorage().amount(0) == 2_000
                        && port.fluidStorage().resource(0).is(Fluids.LAVA)
                        && port.fluidStorage().amount(1) == 0
                        && port.itemStorage().amount(0) == 7
                        && port.itemStorage().resource(0).equals(stone),
                "Output extracts only the clicked second tank, leaving items and the first tank intact");
        helper.succeed();
    }

    public void stackedBucketsRespectInventoryCapacity(GameTestHelper helper) {
        BlockPos pos = new BlockPos(0, 1, 0);
        helper.setBlock(pos, ModBlocks.BLOCKS.get("fluid_output_hatch").get().defaultBlockState());
        var port = helper.getBlockEntity(pos, FluidHatchBlockEntity.class);
        ServerPlayer player = player(helper, port);
        player.containerMenu = new FluidHatchMenu(17, player.getInventory(), port);
        player.getInventory().clearContent();
        int entitiesBefore = nearbyDrops(helper, player).size();
        int dropsBefore = droppedWaterBuckets(helper, player);
        port.fluidStorage().setFluid(new FluidStack(Fluids.WATER, 1_000));
        player.containerMenu.setCarried(new ItemStack(Items.BUCKET, 2));
        int moved = click(player, 0);
        helper.assertTrue(moved == 0, "Insufficient water cannot fill the whole native cursor stack");
        helper.assertTrue(port.fluidStorage().amount(0) == 1_000
                        && port.fluidStorage().resource(0).is(Fluids.WATER),
                "Whole-stack rejection preserves the original port contents");
        helper.assertTrue(ItemStack.matches(player.containerMenu.getCarried(), new ItemStack(Items.BUCKET, 2)),
                "Whole-stack rejection preserves both empty cursor buckets");
        helper.assertTrue(player.getInventory().isEmpty() && nearbyDrops(helper, player).size() == entitiesBefore,
                "Whole-stack rejection leaves inventory and world drops unchanged");
        port.fluidStorage().setFluid(new FluidStack(Fluids.WATER, 2_000));
        moved = click(player, 0);
        helper.assertTrue(moved == 2_000, "Enough water fills both native cursor buckets together");
        helper.assertTrue(port.fluidStorage().amount(0) == 0 && port.fluidStorage().resource(0).isEmpty(),
                "Whole-stack conversion consumes all port water");
        helper.assertTrue(ItemStack.matches(player.containerMenu.getCarried(), new ItemStack(Items.WATER_BUCKET)),
                "Native exchange puts one filled bucket on the cursor first");
        helper.assertTrue(player.getInventory().getNonEquipmentItems().stream()
                        .filter(stack -> stack.is(Items.WATER_BUCKET)).mapToInt(ItemStack::getCount).sum() == 1
                        && player.getInventory().getNonEquipmentItems().stream()
                        .noneMatch(stack -> stack.is(Items.BUCKET)),
                "Exactly one filled bucket overflows into inventory, with no empty buckets remaining");
        helper.assertTrue(nearbyDrops(helper, player).size() == entitiesBefore,
                "Inventory accepts overflow without any world drop");
        player.getInventory().clearContent();
        for (int index = 0; index < player.getInventory().getNonEquipmentItems().size(); index++) {
            player.getInventory().setItem(index, new ItemStack(Items.STONE, 64));
        }
        List<ItemStack> inventoryBefore = new ArrayList<>();
        for (int index = 0; index < player.getInventory().getContainerSize(); index++) {
            inventoryBefore.add(player.getInventory().getItem(index).copy());
        }
        player.containerMenu.setCarried(new ItemStack(Items.BUCKET, 2));
        port.fluidStorage().setFluid(new FluidStack(Fluids.WATER, 500));
        moved = click(player, 0);
        helper.assertTrue(moved == 0, "Insufficient fill is rejected even with a full inventory");
        helper.assertTrue(port.fluidStorage().amount(0) == 500
                        && port.fluidStorage().resource(0).is(Fluids.WATER),
                "Rejected fill preserves port water");
        helper.assertTrue(ItemStack.matches(player.containerMenu.getCarried(), new ItemStack(Items.BUCKET, 2)),
                "Rejected fill preserves the stacked cursor");
        assertInventoryUnchanged(helper, player, inventoryBefore);
        helper.assertTrue(nearbyDrops(helper, player).size() == entitiesBefore,
                "Rejected fill creates no world drops");

        port.fluidStorage().setFluid(new FluidStack(Fluids.WATER, 2_000));
        var container = ItemAccess.forPlayerCursor(player, player.containerMenu).getCapability(Capabilities.Fluid.ITEM);
        helper.assertTrue(container != null, "Stacked cursor buckets expose their native fluid capability");
        try (Transaction transaction = Transaction.openRoot()) {
            moved = ContainerResourceTransfer.transfer(IOType.OUTPUT, container,
                    port.getResourceHandler(null), transaction);
            helper.assertTrue(moved == 2_000, "Uncommitted transfer succeeds and queues full-inventory overflow");
            helper.assertTrue(port.fluidStorage().amount(0) == 0 && port.fluidStorage().resource(0).isEmpty(),
                    "Uncommitted transfer tentatively consumes all port water");
            helper.assertTrue(ItemStack.matches(player.containerMenu.getCarried(), new ItemStack(Items.WATER_BUCKET)),
                    "Uncommitted exchange tentatively converts the cursor");
            assertInventoryUnchanged(helper, player, inventoryBefore);
            helper.assertTrue(nearbyDrops(helper, player).size() == entitiesBefore,
                    "Queued overflow is not a world entity before root commit");
        }
        helper.assertTrue(port.fluidStorage().amount(0) == 2_000
                        && port.fluidStorage().resource(0).equals(FluidResource.of(Fluids.WATER)),
                "Root rollback restores original port identity and amount");
        helper.assertTrue(ItemStack.matches(player.containerMenu.getCarried(), new ItemStack(Items.BUCKET, 2)),
                "Root rollback restores both original cursor buckets");
        assertInventoryUnchanged(helper, player, inventoryBefore);
        helper.assertTrue(nearbyDrops(helper, player).size() == entitiesBefore
                        && droppedWaterBuckets(helper, player) == dropsBefore,
                "Root rollback cancels already queued overflow without a world drop");

        moved = click(player, 0);
        helper.assertTrue(moved == 2_000, "A separate packet transfer commits the full stacked fill");
        helper.assertTrue(port.fluidStorage().amount(0) == 0 && port.fluidStorage().resource(0).isEmpty(),
                "Committed stacked fill consumes all port water");
        helper.assertTrue(ItemStack.matches(player.containerMenu.getCarried(), new ItemStack(Items.WATER_BUCKET)),
                "Committed full-inventory exchange keeps one filled cursor bucket");
        assertInventoryUnchanged(helper, player, inventoryBefore);
        helper.assertTrue(droppedWaterBuckets(helper, player) == dropsBefore + 1
                        && nearbyDrops(helper, player).size() == entitiesBefore + 1,
                "Exactly one filled bucket entity appears only after commit, without a rolled-back duplicate");
        helper.succeed();
    }

    public void invalidRequestsCannotMutateStorage(GameTestHelper helper) {
        BlockPos pos = new BlockPos(0, 1, 0);
        helper.setBlock(pos, ModBlocks.BLOCKS.get("fluid_input_hatch").get().defaultBlockState());
        var port = helper.getBlockEntity(pos, FluidHatchBlockEntity.class);
        ServerPlayer player = player(helper, port);
        player.containerMenu = new FluidHatchMenu(17, player.getInventory(), port);
        helper.assertTrue(click(player, 0) == 0 && port.fluidStorage().amount(0) == 0,
                "An empty cursor cannot request a resource transfer");
        player.containerMenu.setCarried(new ItemStack(Items.STONE, 2));
        helper.assertTrue(click(player, 0) == 0 && port.fluidStorage().amount(0) == 0
                        && port.fluidStorage().resource(0).isEmpty()
                        && ItemStack.matches(player.containerMenu.getCarried(), new ItemStack(Items.STONE, 2)),
                "A nonempty cursor without a fluid capability leaves port and cursor unchanged");
        player.containerMenu.setCarried(new ItemStack(Items.WATER_BUCKET));
        helper.assertTrue(PktPortContainerTransferPayload.interactOnServer(player, null) == 0,
                "A missing payload is rejected");
        helper.assertTrue(PktPortContainerTransferPayload.interactOnServer(player,
                new PktPortContainerTransferPayload(16, 0)) == 0, "Stale menu ID is rejected");
        helper.assertTrue(click(player, -1) == 0 && click(player, 1) == 0
                        && click(player, Integer.MAX_VALUE) == 0,
                "Standalone tank indices are validated");
        player.setPos(port.getBlockPos().getX() + 32, port.getBlockPos().getY(), port.getBlockPos().getZ());
        helper.assertTrue(click(player, 0) == 0, "Out-of-range player cannot transfer");
        player.setPos(port.getBlockPos().getCenter());
        helper.assertTrue(port.fluidStorage().amount(0) == 0
                        && player.containerMenu.getCarried().is(Items.WATER_BUCKET)
                        && player.containerMenu.getCarried().getCount() == 1,
                "Rejected requests preserve storage and the carried container");
        player.containerMenu = new FluidHatchMenu(17, player.getInventory(), port.getBlockPos());
        player.containerMenu.setCarried(new ItemStack(Items.WATER_BUCKET));
        helper.assertTrue(click(player, 0) == 0 && port.fluidStorage().amount(0) == 0,
                "A position-only menu cannot substitute for the real server owner");
        player.containerMenu = new FluidHatchMenu(17, player.getInventory(), port);
        player.containerMenu.setCarried(new ItemStack(Items.WATER_BUCKET));
        helper.setBlock(pos, ModBlocks.CASING.get().defaultBlockState());
        helper.assertTrue(click(player, 0) == 0 && port.fluidStorage().amount(0) == 0
                        && player.containerMenu.getCarried().is(Items.WATER_BUCKET),
                "Replaced block cannot mutate the detached menu owner");
        helper.setBlock(pos, ModBlocks.BLOCKS.get("extended_fluid_input_hatch_basic").get().defaultBlockState());
        var extended = helper.getBlockEntity(pos, FluidHatchBlockEntity.class);
        player.containerMenu = new FluidHatchMenu(18, player.getInventory(), extended);
        player.containerMenu.setCarried(new ItemStack(Items.WATER_BUCKET));
        helper.assertTrue(click(player, 0) == 0 && extended.fluidStorage().amount(0) == 0
                        && player.containerMenu.getCarried().is(Items.WATER_BUCKET),
                "A forged ordinary menu does not expose an extended fluid hatch");
        AbstractContainerMenu previousMenu = player.containerMenu;
        player.containerMenu = player.inventoryMenu;
        player.containerMenu.setCarried(new ItemStack(Items.WATER_BUCKET));
        helper.assertTrue(!PktPortContainerTransferPayload.validTarget(player, previousMenu, extended),
                "Only the current menu identity may access its owner");
        helper.assertTrue(click(player, 0) == 0 && extended.fluidStorage().amount(0) == 0
                        && player.containerMenu.getCarried().is(Items.WATER_BUCKET),
                "Inventory menu is not a port transfer target");
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
                    "Full inventory preserves original item identity, components and count in slot " + index);
        }
    }
}
