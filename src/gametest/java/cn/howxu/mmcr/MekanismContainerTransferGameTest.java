package cn.howxu.mmcr;

import cn.howxu.mmcr.compat.mekanism.MekanismBridge;
import cn.howxu.mmcr.compat.mekanism.loaded.ChemicalPortBlockEntity;
import cn.howxu.mmcr.compat.mekanism.loaded.ChemicalPortMenu;
import cn.howxu.mmcr.internal.menu.FluidHatchMenu;
import cn.howxu.mmcr.internal.network.PktPortContainerTransferPayload;
import cn.howxu.mmcr.internal.tile.FluidHatchBlockEntity;
import cn.howxu.mmcr.internal.tile.IOPortBlockEntity;
import cn.howxu.mmcr.registry.ModBlocks;
import com.mojang.authlib.GameProfile;
import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.chemical.ChemicalStack;
import mekanism.api.chemical.ChemicalUtils;
import mekanism.api.chemical.IMekanismChemicalHandler;
import mekanism.common.capabilities.Capabilities;
import mekanism.common.registries.MekanismBlocks;
import mekanism.common.registries.MekanismChemicals;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;

import java.util.UUID;

/** Real Mekanism tank transfers through the ordinary port menu cursor.
 * @author howxu <dev@howxu.cn> */
public class MekanismContainerTransferGameTest {
    public void chemicalTankFollowsPortDirection(GameTestHelper helper) {
        BlockPos inputPos = new BlockPos(0, 1, 0);
        BlockPos outputPos = new BlockPos(2, 1, 0);
        helper.setBlock(inputPos, ModBlocks.BLOCKS.get("chemical_input_hatch_basic").get().defaultBlockState());
        helper.setBlock(outputPos, ModBlocks.BLOCKS.get("chemical_output_hatch_basic").get().defaultBlockState());
        ChemicalPortBlockEntity input = helper.getBlockEntity(inputPos);
        ChemicalPortBlockEntity output = helper.getBlockEntity(outputPos);
        ServerPlayer player = player(helper, input);
        player.containerMenu = new ChemicalPortMenu(17, player.getInventory(), input);
        player.containerMenu.setCarried(oxygenTank(4_000));
        var handler = player.containerMenu.getCarried().getCapability(Capabilities.CHEMICAL.item());
        helper.assertTrue(handler.extractChemical(4_000, Action.SIMULATE).getAmount() < 4_000,
                "Native automatic chemical extraction has a rate cap");
        helper.assertTrue(click(player) == 4_000 && input.chemicalTank().getStored() == 4_000
                        && input.chemicalTank().getStack().is(MekanismChemicals.OXYGEN.get())
                        && player.containerMenu.getCarried().getCapability(Capabilities.CHEMICAL.item())
                        .getChemicalInTank(0).isEmpty(),
                "Manual input bypasses the rate cap and transfers the exact oxygen amount");
        helper.assertTrue(click(player) == 0 && input.chemicalTank().getStored() == 4_000,
                "Input cannot refill the empty carried tank");
        helper.assertTrue(click(player, true) == 4_000 && input.chemicalTank().isEmpty()
                        && player.containerMenu.getCarried().getCapability(Capabilities.CHEMICAL.item())
                        .getChemicalInTank(0).getAmount() == 4_000,
                "Shift click fills the carried chemical tank from an input port in manual mode");
        helper.assertTrue(click(player, true) == 0 && input.chemicalTank().isEmpty(),
                "Shift extraction cannot reverse into insertion when the input is empty");
        output.chemicalTank().setStack(new ChemicalStack(MekanismChemicals.OXYGEN.get(), 4_000));
        player.setPos(output.getBlockPos().getCenter());
        player.containerMenu = new ChemicalPortMenu(18, player.getInventory(), output);
        player.containerMenu.setCarried(oxygenTank(0));
        helper.assertTrue(click(player) == 4_000 && output.chemicalTank().isEmpty(),
                "Manual output fills the carried tank beyond the automatic rate");
        handler = player.containerMenu.getCarried().getCapability(Capabilities.CHEMICAL.item());
        helper.assertTrue(handler.getChemicalInTank(0).getAmount() == 4_000
                        && handler.getChemicalInTank(0).is(MekanismChemicals.OXYGEN.get())
                        && click(player) == 0 && output.chemicalTank().isEmpty(),
                "Cursor amount and identity match extraction, and output cannot reverse transfer");
        helper.succeed();
    }

    public void worldChemicalTankUsesExtractionFirst(GameTestHelper helper) {
        BlockPos pos = new BlockPos(0, 1, 0);
        helper.setBlock(pos, ModBlocks.BLOCKS.get("chemical_input_hatch_basic").get().defaultBlockState());
        ChemicalPortBlockEntity port = helper.getBlockEntity(pos);
        port.chemicalTank().setStack(new ChemicalStack(MekanismChemicals.OXYGEN.get(), 2_000));
        ServerPlayer player = player(helper, port);
        player.getAbilities().instabuild = false;
        player.setItemInHand(InteractionHand.MAIN_HAND, oxygenTank(1_000));
        helper.assertTrue(port.chemicalHandler(null).extractChemical(2_000, Action.SIMULATE).isEmpty(),
                "External automation still cannot extract from a chemical input hatch");
        helper.assertTrue(PortContainerTransferGameTest.useContainer(player, port, InteractionHand.MAIN_HAND)
                         == ItemInteractionResult.SUCCESS && port.chemicalTank().isEmpty(),
                "World chemical interaction extracts first beyond the item automation rate");
        var handler = player.getMainHandItem().getCapability(Capabilities.CHEMICAL.item());
        helper.assertTrue(handler.getChemicalInTank(0).getAmount() == 3_000
                        && handler.getChemicalInTank(0).is(MekanismChemicals.OXYGEN.get()),
                "Partly filled held chemical tank receives the exact extracted amount");
        helper.assertTrue(PortContainerTransferGameTest.useContainer(player, port, InteractionHand.MAIN_HAND)
                         == ItemInteractionResult.SUCCESS && port.chemicalTank().getStored() == 3_000,
                "Empty chemical input falls back to receiving the held tank contents");
        handler = player.getMainHandItem().getCapability(Capabilities.CHEMICAL.item());
        helper.assertTrue(handler.getChemicalInTank(0).isEmpty(),
                "Held tank loses exactly the contents inserted into the empty chemical input");
        port.chemicalTank().setStack(new ChemicalStack(MekanismChemicals.HYDROGEN.get(), 1_000));
        player.setItemInHand(InteractionHand.MAIN_HAND, oxygenTank(1_000));
        helper.assertTrue(PortContainerTransferGameTest.useContainer(player, port, InteractionHand.MAIN_HAND)
                         == ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION
                         && port.chemicalTank().getStored() == 1_000
                         && port.chemicalTank().getStack().is(MekanismChemicals.HYDROGEN.get()),
                "Both failed world directions preserve mismatched chemical identity");
        handler = player.getMainHandItem().getCapability(Capabilities.CHEMICAL.item());
        helper.assertTrue(handler.getChemicalInTank(0).getAmount() == 1_000
                        && handler.getChemicalInTank(0).is(MekanismChemicals.OXYGEN.get()),
                "Rejected world interaction preserves the held chemical contents");
        helper.succeed();
    }

    public void worldFluidTankUsesExtractionFirst(GameTestHelper helper) {
        BlockPos pos = new BlockPos(0, 1, 0);
        helper.setBlock(pos, ModBlocks.BLOCKS.get("fluid_input_hatch").get().defaultBlockState());
        FluidHatchBlockEntity port = helper.getBlockEntity(pos);
        port.fluidHandler(null).setFluid(new FluidStack(Fluids.WATER, 2_000));
        ServerPlayer player = player(helper, port);
        player.getAbilities().instabuild = false;
        ItemStack tank = new ItemStack(MekanismBlocks.BASIC_FLUID_TANK.asItem());
        var handler = MekanismBridge.get().manualFluidContainerHandler(tank.getCapability(Capabilities.FLUID.item()));
        handler.fill(new FluidStack(Fluids.WATER, 1_000), FluidAction.EXECUTE);
        player.setItemInHand(InteractionHand.MAIN_HAND, tank);
        helper.assertTrue(PortContainerTransferGameTest.useContainer(player, port, InteractionHand.MAIN_HAND)
                         == ItemInteractionResult.SUCCESS && port.fluidHandler(null).isEmpty(),
                "World fluid interaction fills a partly filled held tank before attempting insertion");
        handler = player.getMainHandItem().getCapability(Capabilities.FLUID.item());
        helper.assertTrue(handler.getFluidInTank(0).getAmount() == 3_000 && handler.getFluidInTank(0).is(Fluids.WATER),
                "Held fluid tank receives the exact amount without automatic rate limiting");
        helper.assertTrue(PortContainerTransferGameTest.useContainer(player, port, InteractionHand.MAIN_HAND)
                         == ItemInteractionResult.SUCCESS && port.fluidHandler(null).amount(0) == 3_000,
                "Empty fluid input receives the held tank contents on fallback");
        handler = player.getMainHandItem().getCapability(Capabilities.FLUID.item());
        helper.assertTrue(handler.getFluidInTank(0).isEmpty(),
                "Held fluid tank loses exactly the inserted contents");
        helper.succeed();
    }

    public void fluidTankTransfersOnlyAvailableCapacity(GameTestHelper helper) {
        BlockPos pos = new BlockPos(0, 1, 0);
        helper.setBlock(pos, ModBlocks.BLOCKS.get("fluid_input_hatch").get().defaultBlockState());
        FluidHatchBlockEntity port = helper.getBlockEntity(pos);
        long before = port.fluidHandler(null).capacity(0) - 1_500;
        port.fluidHandler(null).setContents(0, new FluidStack(Fluids.WATER, 1), before);
        ServerPlayer player = player(helper, port);
        player.containerMenu = new FluidHatchMenu(17, player.getInventory(), port);
        ItemStack tank = new ItemStack(MekanismBlocks.BASIC_FLUID_TANK.asItem());
        var handler = MekanismBridge.get().manualFluidContainerHandler(tank.getCapability(Capabilities.FLUID.item()));
        helper.assertTrue(handler.fill(new FluidStack(Fluids.WATER, 4_000), FluidAction.EXECUTE) == 4_000,
                "Manual fixture fills the real Mekanism tank");
        player.containerMenu.setCarried(tank);
        helper.assertTrue(click(player) == 1_500 && port.fluidHandler(null).amount(0) == before + 1_500,
                "Input receives only its remaining capacity");
        handler = player.containerMenu.getCarried().getCapability(Capabilities.FLUID.item());
        helper.assertTrue(handler.getFluidInTank(0).getAmount() == 2_500 && handler.getFluidInTank(0).is(Fluids.WATER)
                        && click(player) == 0 && port.fluidHandler(null).amount(0) == before + 1_500,
                "Cursor retains the exact remainder and full input cannot reverse transfer");
        helper.setBlock(pos, ModBlocks.BLOCKS.get("fluid_output_hatch").get().defaultBlockState());
        port = helper.getBlockEntity(pos);
        port.fluidHandler(null).setFluid(new FluidStack(Fluids.WATER, 4_000));
        player.containerMenu = new FluidHatchMenu(18, player.getInventory(), port);
        player.containerMenu.setCarried(new ItemStack(MekanismBlocks.BASIC_FLUID_TANK.asItem()));
        handler = player.containerMenu.getCarried().getCapability(Capabilities.FLUID.item());
        helper.assertTrue(handler.fill(new FluidStack(Fluids.WATER, 4_000), FluidAction.SIMULATE) < 4_000,
                "Native automatic fluid insertion has a rate cap");
        helper.assertTrue(click(player) == 4_000 && port.fluidHandler(null).isEmpty(),
                "Manual output bypasses the rate cap");
        handler = player.containerMenu.getCarried().getCapability(Capabilities.FLUID.item());
        helper.assertTrue(handler.getFluidInTank(0).getAmount() == 4_000 && handler.getFluidInTank(0).is(Fluids.WATER)
                        && click(player) == 0 && port.fluidHandler(null).isEmpty(),
                "Output depletion and cursor gain agree without reverse transfer");
        helper.succeed();
    }

    public void chemicalTankRetainsRemainderAndRejectsMismatch(GameTestHelper helper) {
        BlockPos pos = new BlockPos(0, 1, 0);
        helper.setBlock(pos, ModBlocks.BLOCKS.get("chemical_input_hatch_basic").get().defaultBlockState());
        ChemicalPortBlockEntity port = helper.getBlockEntity(pos);
        long before = port.chemicalTank().getCapacity() - 1_500;
        port.chemicalTank().setStack(new ChemicalStack(MekanismChemicals.OXYGEN.get(), before));
        ServerPlayer player = player(helper, port);
        player.containerMenu = new ChemicalPortMenu(17, player.getInventory(), port);
        player.containerMenu.setCarried(oxygenTank(4_000));
        helper.assertTrue(click(player) == 1_500 && port.chemicalTank().getStored() == before + 1_500,
                "Chemical input accepts only its free capacity");
        var handler = player.containerMenu.getCarried().getCapability(Capabilities.CHEMICAL.item());
        helper.assertTrue(handler.getChemicalInTank(0).getAmount() == 2_500
                        && handler.getChemicalInTank(0).is(MekanismChemicals.OXYGEN.get()),
                "Cursor retains the exact chemical remainder");
        port.chemicalTank().setStack(new ChemicalStack(MekanismChemicals.HYDROGEN.get(), 1_000));
        helper.assertTrue(click(player) == 0 && port.chemicalTank().getStored() == 1_000
                        && port.chemicalTank().getStack().is(MekanismChemicals.HYDROGEN.get()),
                "Mismatched oxygen cannot overwrite stored hydrogen");
        helper.setBlock(pos, ModBlocks.BLOCKS.get("radioactive_chemical_input_hatch").get().defaultBlockState());
        port = helper.getBlockEntity(pos);
        ItemStack remainder = player.containerMenu.getCarried().copy();
        player.containerMenu = new ChemicalPortMenu(18, player.getInventory(), port);
        player.containerMenu.setCarried(remainder);
        helper.assertTrue(click(player) == 0 && port.chemicalTank().isEmpty()
                        && ItemStack.matches(player.containerMenu.getCarried(), remainder),
                "Radioactive-only input rejects oxygen without changing cursor components");
        helper.succeed();
    }

    private static ServerPlayer player(GameTestHelper helper, IOPortBlockEntity port) {
        ServerPlayer player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "mmcr-mek-tank"), ClientInformation.createDefault());
        player.setPos(port.getBlockPos().getCenter());
        return player;
    }

    private static int click(ServerPlayer player) {
        return click(player, false);
    }

    private static int click(ServerPlayer player, boolean extract) {
        return PktPortContainerTransferPayload.interactOnServer(player,
                new PktPortContainerTransferPayload(player.containerMenu.containerId, 0, extract));
    }

    private static ItemStack oxygenTank(int amount) {
        ItemStack stack = new ItemStack(MekanismBlocks.BASIC_CHEMICAL_TANK.asItem());
        var handler = (IMekanismChemicalHandler) stack.getCapability(Capabilities.CHEMICAL.item());
        if (amount > 0 && !ChemicalUtils.insert(new ChemicalStack(MekanismChemicals.OXYGEN.get(), amount),
                null, handler::getChemicalTanks, Action.EXECUTE, AutomationType.MANUAL).isEmpty()) {
            throw new AssertionError("Chemical tank fixture could not accept oxygen");
        }
        return stack;
    }
}
