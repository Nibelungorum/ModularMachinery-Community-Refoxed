package cn.howxu.mmcr;

import cn.howxu.mmcr.compat.mekanism.loaded.ChemicalPortBlockEntity;
import cn.howxu.mmcr.compat.mekanism.loaded.ChemicalPortMenu;
import cn.howxu.mmcr.internal.menu.FluidHatchMenu;
import cn.howxu.mmcr.internal.network.PktPortContainerTransferPayload;
import cn.howxu.mmcr.internal.tile.FluidHatchBlockEntity;
import cn.howxu.mmcr.internal.tile.IOPortBlockEntity;
import cn.howxu.mmcr.registry.ModBlocks;
import com.mojang.authlib.GameProfile;
import mekanism.common.capabilities.Capabilities;
import mekanism.common.capabilities.proxy.AutomatedResourceHandler;
import mekanism.common.registries.MekanismBlocks;
import mekanism.common.registries.MekanismChemicals;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

import java.util.UUID;

/**
 * Real Mekanism tank transfers through the ordinary port menu cursor.
 *
 * @author howxu <dev@howxu.cn>
 */
public class MekanismContainerTransferGameTest {
    public void chemicalTankFollowsPortDirection(GameTestHelper helper) {
        BlockPos inputPos = new BlockPos(0, 1, 0);
        BlockPos outputPos = new BlockPos(2, 1, 0);
        helper.setBlock(inputPos, ModBlocks.BLOCKS.get("chemical_input_hatch_basic").get().defaultBlockState());
        helper.setBlock(outputPos, ModBlocks.BLOCKS.get("chemical_output_hatch_basic").get().defaultBlockState());
        var input = helper.getBlockEntity(inputPos, ChemicalPortBlockEntity.class);
        var output = helper.getBlockEntity(outputPos, ChemicalPortBlockEntity.class);
        var oxygen = MekanismChemicals.OXYGEN.asResource();
        ServerPlayer player = player(helper, input);
        player.containerMenu = new ChemicalPortMenu(17, player.getInventory(), input);
        player.containerMenu.setCarried(oxygenTank(4_000));
        var cursorHandler = ItemAccess.forPlayerCursor(player, player.containerMenu)
                .getCapability(Capabilities.CHEMICAL.item());
        try (Transaction transaction = Transaction.openRoot()) {
            helper.assertTrue(cursorHandler.extract(oxygen, 4_000, transaction) < 4_000,
                    "The unwrapped chemical tank is automatically rate limited");
        }
        helper.assertTrue(click(player) == 4_000 && input.chemicalTank().amountAsLong() == 4_000
                        && input.chemicalTank().resource().equals(oxygen),
                "Chemical input accepts carried tank contents through the manual capability");
        cursorHandler = ItemAccess.forPlayerCursor(player, player.containerMenu)
                .getCapability(Capabilities.CHEMICAL.item());
        helper.assertTrue(cursorHandler.getAmountAsLong(0) == 0,
                "Chemical cursor item lost exactly the amount inserted into the port");
        helper.assertTrue(click(player) == 0 && input.chemicalTank().amountAsLong() == 4_000,
                "Chemical input cannot refill the empty carried tank");
        output.chemicalTank().setContents(oxygen, 4_000, null);
        player.setPos(output.getBlockPos().getCenter());
        player.containerMenu = new ChemicalPortMenu(18, player.getInventory(), output);
        player.containerMenu.setCarried(oxygenTank(0));
        helper.assertTrue(click(player) == 4_000 && output.chemicalTank().amountAsLong() == 0,
                "Chemical output fills the carried tank without an automatic rate cap");
        cursorHandler = ItemAccess.forPlayerCursor(player, player.containerMenu)
                .getCapability(Capabilities.CHEMICAL.item());
        helper.assertTrue(cursorHandler.getAmountAsLong(0) == 4_000
                        && cursorHandler.getResource(0).equals(oxygen),
                "Chemical cursor item received exactly the amount and identity extracted");
        helper.assertTrue(click(player) == 0 && output.chemicalTank().amountAsLong() == 0,
                "Chemical output cannot accept the now-filled carried tank");
        helper.succeed();
    }

    public void fluidTankTransfersOnlyAvailableCapacity(GameTestHelper helper) {
        BlockPos pos = new BlockPos(0, 1, 0);
        helper.setBlock(pos, ModBlocks.BLOCKS.get("fluid_input_hatch").get().defaultBlockState());
        var port = helper.getBlockEntity(pos, FluidHatchBlockEntity.class);
        var water = FluidResource.of(Fluids.WATER);
        long before = port.fluidStorage().capacity(0, water) - 1_500;
        port.fluidStorage().setContents(water, before);
        ServerPlayer player = player(helper, port);
        player.containerMenu = new FluidHatchMenu(17, player.getInventory(), port);
        ItemStack tank = new ItemStack(MekanismBlocks.BASIC_FLUID_TANK.asItem());
        var handler = AutomatedResourceHandler.manual(
                ItemAccess.forStack(tank).getCapability(Capabilities.FLUID.item()));
        try (Transaction transaction = Transaction.openRoot()) {
            helper.assertTrue(handler.insert(water, 4_000, transaction) == 4_000,
                    "Fluid tank fixture can hold the initial contents");
            transaction.commit();
        }
        player.containerMenu.setCarried(tank);
        helper.assertTrue(click(player) == 1_500 && port.fluidStorage().amount(0) == before + 1_500,
                "The port receives only its remaining capacity");
        handler = ItemAccess.forPlayerCursor(player, player.containerMenu)
                .getCapability(Capabilities.FLUID.item());
        helper.assertTrue(handler.getAmountAsLong(0) == 2_500 && handler.getResource(0).equals(water),
                "The cursor tank retains exactly the untransferred remainder");
        helper.assertTrue(click(player) == 0 && port.fluidStorage().amount(0) == before + 1_500,
                "A full fluid input cannot reverse transfer into the partially filled cursor tank");
        helper.setBlock(pos, ModBlocks.BLOCKS.get("fluid_output_hatch").get().defaultBlockState());
        port = helper.getBlockEntity(pos, FluidHatchBlockEntity.class);
        port.fluidStorage().setContents(water, 4_000);
        tank = new ItemStack(MekanismBlocks.BASIC_FLUID_TANK.asItem());
        player.containerMenu = new FluidHatchMenu(18, player.getInventory(), port);
        player.containerMenu.setCarried(tank);
        handler = ItemAccess.forPlayerCursor(player, player.containerMenu)
                .getCapability(Capabilities.FLUID.item());
        try (Transaction transaction = Transaction.openRoot()) {
            helper.assertTrue(handler.insert(water, 4_000, transaction) < 4_000,
                    "The unwrapped fluid tank is automatically rate limited");
        }
        helper.assertTrue(click(player) == 4_000 && port.fluidStorage().amount(0) == 0,
                "Output fills the fluid tank in manual mode beyond the automatic rate");
        handler = ItemAccess.forPlayerCursor(player, player.containerMenu)
                .getCapability(Capabilities.FLUID.item());
        helper.assertTrue(handler.getAmountAsLong(0) == 4_000 && handler.getResource(0).equals(water),
                "Output depletion and cursor fluid gain agree");
        helper.assertTrue(click(player) == 0 && port.fluidStorage().amount(0) == 0,
                "Fluid output cannot accept the now-filled carried tank");
        helper.succeed();
    }

    public void chemicalTankRetainsRemainderAndRejectsMismatch(GameTestHelper helper) {
        BlockPos pos = new BlockPos(0, 1, 0);
        helper.setBlock(pos, ModBlocks.BLOCKS.get("chemical_input_hatch_basic").get().defaultBlockState());
        var port = helper.getBlockEntity(pos, ChemicalPortBlockEntity.class);
        var oxygen = MekanismChemicals.OXYGEN.asResource();
        long before = port.chemicalTank().capacityAsLong(oxygen) - 1_500;
        port.chemicalTank().setContents(oxygen, before, null);
        ServerPlayer player = player(helper, port);
        player.containerMenu = new ChemicalPortMenu(17, player.getInventory(), port);
        player.containerMenu.setCarried(oxygenTank(4_000));
        helper.assertTrue(click(player) == 1_500
                        && port.chemicalTank().amountAsLong() == before + 1_500,
                "Chemical input accepts only its free capacity");
        var handler = ItemAccess.forPlayerCursor(player, player.containerMenu)
                .getCapability(Capabilities.CHEMICAL.item());
        helper.assertTrue(handler.getAmountAsLong(0) == 2_500 && handler.getResource(0).equals(oxygen),
                "Chemical cursor tank retains the exact remainder");
        port.chemicalTank().setContents(MekanismChemicals.HYDROGEN.asResource(), 1_000, null);
        helper.assertTrue(click(player) == 0
                        && port.chemicalTank().resource().equals(MekanismChemicals.HYDROGEN.asResource())
                        && port.chemicalTank().amountAsLong() == 1_000,
                "A chemical identity mismatch cannot overwrite the port contents");
        handler = ItemAccess.forPlayerCursor(player, player.containerMenu)
                .getCapability(Capabilities.CHEMICAL.item());
        helper.assertTrue(handler.getAmountAsLong(0) == 2_500 && handler.getResource(0).equals(oxygen),
                "Rejected chemical transfer leaves the cursor contents intact");
        helper.setBlock(pos, ModBlocks.BLOCKS.get("radioactive_chemical_input_hatch").get().defaultBlockState());
        port = helper.getBlockEntity(pos, ChemicalPortBlockEntity.class);
        ItemStack remainder = player.containerMenu.getCarried().copy();
        player.containerMenu = new ChemicalPortMenu(18, player.getInventory(), port);
        player.containerMenu.setCarried(remainder);
        helper.assertTrue(click(player) == 0 && port.chemicalTank().amountAsLong() == 0,
                "Radioactive-only input rejects non-radioactive cursor chemical");
        handler = ItemAccess.forPlayerCursor(player, player.containerMenu)
                .getCapability(Capabilities.CHEMICAL.item());
        helper.assertTrue(handler.getAmountAsLong(0) == 2_500 && handler.getResource(0).equals(oxygen),
                "Radioactive filter rejection preserves the cursor chemical and remainder");
        helper.succeed();
    }

    private static ServerPlayer player(GameTestHelper helper, IOPortBlockEntity port) {
        ServerPlayer player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "mmcr-mek-tank"),
                ClientInformation.createDefault());
        player.setPos(port.getBlockPos().getCenter());
        return player;
    }

    private static int click(ServerPlayer player) {
        return PktPortContainerTransferPayload.interactOnServer(player,
                new PktPortContainerTransferPayload(player.containerMenu.containerId, 0));
    }

    private static ItemStack oxygenTank(int amount) {
        ItemStack stack = new ItemStack(MekanismBlocks.BASIC_CHEMICAL_TANK.asItem());
        var handler = AutomatedResourceHandler.manual(
                ItemAccess.forStack(stack).getCapability(Capabilities.CHEMICAL.item()));
        try (Transaction transaction = Transaction.openRoot()) {
            if (amount > 0 && handler.insert(MekanismChemicals.OXYGEN.asResource(),
                    amount, transaction) != amount) {
                throw new AssertionError("Chemical tank fixture could not accept oxygen");
            }
            transaction.commit();
        }
        return stack;
    }
}
