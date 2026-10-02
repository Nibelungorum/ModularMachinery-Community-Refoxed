package cn.howxu.mmcr.compat.ars_nouveau;

import cn.howxu.mmcr.compat.ars_nouveau.loaded.SourcePortBlockEntity;
import cn.howxu.mmcr.util.IOType;
import com.hollingsworth.arsnouveau.api.item.IWandable;
import com.hollingsworth.arsnouveau.api.source.ISourceCap;
import com.hollingsworth.arsnouveau.api.source.ISourceTile;
import com.hollingsworth.arsnouveau.api.source.ISpecialSourceProvider;
import com.hollingsworth.arsnouveau.api.source.SourceManager;
import com.hollingsworth.arsnouveau.api.util.SourceUtil;
import com.hollingsworth.arsnouveau.common.block.tile.RelayCollectorTile;
import com.hollingsworth.arsnouveau.common.block.tile.RelayDepositTile;
import com.hollingsworth.arsnouveau.common.block.tile.RelaySplitterTile;
import com.hollingsworth.arsnouveau.common.block.tile.RelayTile;
import com.hollingsworth.arsnouveau.setup.registry.BlockRegistry;
import com.hollingsworth.arsnouveau.setup.registry.CapabilityRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerListener;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.ServerLevelData;

import java.util.List;

/** Consolidated native transport and provider lifecycle coverage. @author howxu <dev@howxu.cn> */
public final class ArsSourceTransportGameTest {
    public void wandMenusAndNativeRelaysRespectDirections(GameTestHelper helper) {
        SourcePortBlockEntity input = ArsSourceGameTestFixtures.port(helper, new BlockPos(1, 1, 1), IOType.INPUT);
        SourcePortBlockEntity output = ArsSourceGameTestFixtures.port(helper, new BlockPos(3, 1, 1), IOType.OUTPUT);
        RelayTile relay = ArsSourceGameTestFixtures.relay(helper, new BlockPos(2, 1, 1));
        ServerPlayer player = ArsSourceGameTestFixtures.wandPlayer(helper);
        ArsSourceGameTestFixtures.use(helper, player, relay.getBlockPos());
        ArsSourceGameTestFixtures.use(helper, player, input.getBlockPos());
        helper.assertTrue(input.getBlockPos().equals(relay.getToPos()) && player.containerMenu == player.inventoryMenu,
                "Relay-to-input wand connection reaches the native item path without opening a menu");
        ArsSourceGameTestFixtures.use(helper, player, output.getBlockPos());
        helper.assertTrue(player.containerMenu == player.inventoryMenu, "Selecting output with the wand bypasses its menu");
        ArsSourceGameTestFixtures.use(helper, player, relay.getBlockPos());
        helper.assertTrue(output.getBlockPos().equals(relay.getFromPos()), "Output-to-relay connection uses native capability");
        helper.assertTrue(!((Object) input instanceof IWandable) && !((Object) output instanceof IWandable),
                "Source interfaces do not implement extra wand connection semantics");
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        for (SourcePortBlockEntity port : List.of(input, output)) {
            ArsSourceGameTestFixtures.use(helper, player, port.getBlockPos());
            helper.assertTrue(player.containerMenu instanceof SourcePortMenu menu && menu.owner() == port,
                    "Ordinary right click opens the real source menu");
            player.closeContainer();
        }

        ISourceCap in = capability(helper, input);
        ISourceCap out = capability(helper, output);
        for (Direction side : Direction.values()) {
            helper.assertTrue(helper.getLevel().getCapability(CapabilityRegistry.SOURCE_CAPABILITY,
                            input.getBlockPos(), side) == in
                            && helper.getLevel().getCapability(CapabilityRegistry.SOURCE_CAPABILITY,
                            output.getBlockPos(), side) == out,
                    "All faces expose the same stable directional capability");
        }
        output.storage().setAmount(700);
        tickNative(helper, relay);
        helper.assertTrue(input.storage().amount() == 700 && output.storage().amount() == 0 && relay.getSource() == 0,
                "Native bound relay tick resolves both wand targets and transfers through their capabilities");
        input.storage().setAmount(0);
        output.storage().setAmount(700);
        int moved = relay.transferSource(out, in);
        helper.assertTrue(moved == 700 && input.storage().amount() == 700 && output.storage().amount() == 0,
                "Native cap transfer conserves source between the interfaces");
        helper.assertTrue(relay.transferSource(in, out) == 0 && input.storage().amount() == 700,
                "Reversed native cap transfer cannot extract input or fill output");
        var jar = ArsSourceGameTestFixtures.jar(helper, new BlockPos(2, 1, 3), 300);
        helper.assertTrue(relay.transferSource(jar.getSourceStorage(), in) == 300 && jar.getSource() == 0,
                "Real jar supplies the input capability");
        output.storage().setAmount(200);
        helper.assertTrue(relay.transferSource(out, jar.getSourceStorage()) == 200 && jar.getSource() == 200,
                "Real jar accepts the output capability");

        for (var block : List.of(BlockRegistry.RELAY_SPLITTER.get(), BlockRegistry.RELAY_WARP.get())) {
            helper.setBlock(new BlockPos(2, 1, 2), block.defaultBlockState());
            RelaySplitterTile multi = helper.getBlockEntity(new BlockPos(2, 1, 2));
            input.storage().setAmount(0);
            output.storage().setAmount(500);
            multi.setTakeFrom(output.getBlockPos());
            multi.setTakeFrom(input.getBlockPos());
            multi.setSendTo(input.getBlockPos());
            multi.setSendTo(output.getBlockPos());
            multi.processFromList();
            multi.processToList();
            helper.assertTrue(input.storage().amount() == 500 && output.storage().amount() == 0 && multi.getSource() == 0,
                    "Splitter/warp lists use capability directions and conserve source");
            multi.clearPos();
            multi.setTakeFrom(input.getBlockPos());
            multi.setSendTo(output.getBlockPos());
            multi.processFromList();
            multi.processToList();
            helper.assertTrue(input.storage().amount() == 500 && output.storage().amount() == 0,
                    "Reversed splitter/warp lists cannot move source");
        }

        helper.setBlock(new BlockPos(2, 1, 2), BlockRegistry.RELAY_COLLECTOR.get().defaultBlockState());
        RelayCollectorTile collector = helper.getBlockEntity(new BlockPos(2, 1, 2));
        helper.setBlock(new BlockPos(3, 1, 2), BlockRegistry.RELAY_DEPOSIT.get().defaultBlockState());
        RelayDepositTile deposit = helper.getBlockEntity(new BlockPos(3, 1, 2));
        ISourceTile legacyInput = ArsSourceGameTestFixtures.provider(helper, input).getSource();
        ISourceTile legacyOutput = ArsSourceGameTestFixtures.provider(helper, output).getSource();
        input.storage().setAmount(100);
        output.storage().setAmount(500);
        helper.assertTrue(collector.transferSource(legacyInput, collector) == 0,
                "Collector legacy path cannot spend real input inventory");
        helper.assertTrue(collector.transferSource(legacyOutput, collector) == 500,
                "Collector legacy path extracts output");
        collector.transferSource((ISourceTile) collector, deposit);
        helper.assertTrue(deposit.transferSource(deposit, legacyOutput) == 0,
                "Deposit legacy path cannot fill output");
        helper.assertTrue(deposit.transferSource(deposit, legacyInput) == 500 && input.storage().amount() == 600
                        && output.storage().amount() == 0 && collector.getSource() == 0 && deposit.getSource() == 0,
                "Collector/deposit legacy chain conserves total source and respects directions");

        // Remove the alternate jar source/destination before native nearby scanning.
        helper.setBlock(new BlockPos(2, 1, 3), Blocks.AIR);
        relay.clearPos();
        input.storage().setAmount(100);
        output.storage().setAmount(500);
        tickNative(helper, collector);
        helper.assertTrue(collector.getSource() == 500 && output.storage().amount() == 0 && input.storage().amount() == 100,
                "Collector's actual native tick discovers the MMCR provider and extracts only output");
        collector.setSendTo(deposit.getBlockPos());
        tickNative(helper, collector);
        helper.assertTrue(collector.getSource() == 0 && deposit.getSource() == 500 && input.storage().amount() == 100,
                "Collector's bound native tick delivers discovered source to the deposit relay");
        tickNative(helper, deposit);
        helper.assertTrue(input.storage().amount() == 600 && output.storage().amount() == 0
                        && collector.getSource() == 0 && deposit.getSource() == 0,
                "Deposit's actual scanner/transfer tick discovers input, excludes output, and conserves source");
        helper.succeed();
    }

    public void discoveryRestorationReloadAndMenuUseRealStorage(GameTestHelper helper) {
        BlockPos inputPos = new BlockPos(1, 1, 1);
        BlockPos outputPos = new BlockPos(3, 1, 1);
        SourcePortBlockEntity input = ArsSourceGameTestFixtures.port(helper, inputPos, IOType.INPUT);
        SourcePortBlockEntity output = ArsSourceGameTestFixtures.port(helper, outputPos, IOType.OUTPUT);
        input.storage().setAmount(500);
        output.storage().setAmount(500);
        var level = helper.getLevel();
        var destinations = SourceUtil.canGiveSource(helper.absolutePos(new BlockPos(2, 1, 1)), level, 3);
        var sources = SourceUtil.canTakeSource(helper.absolutePos(new BlockPos(2, 1, 1)), level, 3);
        helper.assertTrue(destinations.stream().anyMatch(p -> p.getCurrentPos().equals(input.getBlockPos()))
                        && destinations.stream().noneMatch(p -> p.getCurrentPos().equals(output.getBlockPos())),
                "Nearby destinations include input but exclude output");
        helper.assertTrue(sources.stream().anyMatch(p -> p.getCurrentPos().equals(output.getBlockPos())
                        && p.getSource().getSource() == 500)
                        && sources.stream().filter(p -> p.getCurrentPos().equals(input.getBlockPos()))
                        .allMatch(p -> p.getSource().getSource() == 0),
                "Nearby extraction sees real output inventory and no spendable input inventory");
        // The centered provider is the only spendable source within this narrow range.
        helper.assertTrue(SourceUtil.takeSourceMultiple(output.getBlockPos(), level, 1, 700) == null
                        && output.storage().amount() == 500,
                "Native insufficient-source restoration leaves output unchanged");
        helper.assertTrue(!SourceUtil.hasSourceNearby(input.getBlockPos(), level, 1, 100)
                        && SourceUtil.takeSourceMultiple(input.getBlockPos(), level, 1, 100) == null
                        && input.storage().amount() == 500,
                "Native simulation and consumption cannot spend real input inventory");
        BlockPos creativePos = outputPos.south();
        helper.setBlock(creativePos, BlockRegistry.CREATIVE_SOURCE_JAR.get().defaultBlockState());
        helper.assertTrue(SourceUtil.takeSourceMultiple(output.getBlockPos(), level, 1, 700) != null
                        && output.storage().amount() == 500,
                "Creative jar leaves previously available output unchanged regardless of iteration order");
        ISourceTile legacy = ArsSourceGameTestFixtures.provider(helper, output).getSource();
        legacy.removeSource(200);
        legacy.addSource(200);
        helper.assertTrue(output.storage().amount() == 500, "Legacy restoration returns a prior extraction to output");
        helper.setBlock(creativePos, Blocks.AIR);

        ISpecialSourceProvider oldProvider = ArsSourceGameTestFixtures.provider(helper, output);
        output.onChunkUnloaded();
        helper.assertTrue(!SourceManager.INSTANCE.getSetForLevel(level).contains(oldProvider)
                        && SourceUtil.canTakeSource(output.getBlockPos(), level, 1).stream()
                        .noneMatch(p -> p.getCurrentPos().equals(output.getBlockPos())),
                "Chunk unload unregisters this provider without clearing other tests' providers");
        output.onLoad();
        output.onLoad();
        helper.assertTrue(providerCount(helper, output.getBlockPos()) == 1,
                "Repeated load registers exactly one stable provider");

        ServerPlayer player = ArsSourceGameTestFixtures.wandPlayer(helper);
        for (SourcePortBlockEntity port : List.of(input, output)) {
            CompoundTag saved = port.saveWithoutMetadata(level.registryAccess());
            BlockPos relativePos = port == input ? inputPos : outputPos;
            ISpecialSourceProvider beforeReload = ArsSourceGameTestFixtures.provider(helper, port);
            helper.setBlock(relativePos, Blocks.AIR);
            SourcePortBlockEntity reloaded = ArsSourceGameTestFixtures.port(helper, relativePos, port.ioType());
            reloaded.loadWithComponents(saved, level.registryAccess());
            reloaded.onLoad();
            helper.assertTrue(!beforeReload.isValid() && !SourceManager.INSTANCE.getSetForLevel(level).contains(beforeReload)
                            && reloaded != port && reloaded.storage().amount() == 500
                            && providerCount(helper, reloaded.getBlockPos()) == 1,
                    "Real BE replacement restores inventory, invalidates the old owner, and registers once");
            helper.assertTrue(port.ioType() == IOType.INPUT
                            ? ArsSourceGameTestFixtures.provider(helper, reloaded).getSource().getSource() == 0
                            : capability(helper, reloaded).receiveSource(100, false) == 0,
                    "Reload preserves the directional native projection");
            assertSyncedMenu(helper, player, reloaded);
            ISpecialSourceProvider removed = ArsSourceGameTestFixtures.provider(helper, reloaded);
            helper.setBlock(relativePos, Blocks.AIR);
            helper.assertTrue(!removed.isValid() && SourceUtil.canTakeSource(reloaded.getBlockPos(), level, 1).stream()
                            .noneMatch(p -> p.getCurrentPos().equals(reloaded.getBlockPos())),
                    "Removed provider is not rediscovered by native consumers");
        }
        helper.succeed();
    }

    private static long providerCount(GameTestHelper helper, BlockPos pos) {
        return SourceManager.INSTANCE.getSetForLevel(helper.getLevel()).stream()
                .filter(provider -> provider.getCurrentPos().equals(pos)).count();
    }

    private static void tickNative(GameTestHelper helper, RelayTile relay) {
        // Ars 5.11.5 gates its native entrypoint on world time, not a relay-local counter.
        // Set the due state only during this synchronous call; no other world tick runs here.
        ServerLevelData data = (ServerLevelData) helper.getLevel().getLevelData();
        long gameTime = data.getGameTime();
        try {
            data.setGameTime(gameTime - Math.floorMod(gameTime, 20L));
            relay.tick();
        } finally {
            data.setGameTime(gameTime);
        }
    }

    private static ISourceCap capability(GameTestHelper helper, SourcePortBlockEntity port) {
        ISourceCap cap = helper.getLevel().getCapability(CapabilityRegistry.SOURCE_CAPABILITY, port.getBlockPos(), null);
        helper.assertTrue(cap != null, "Registered native source capability exists");
        return cap;
    }

    private static void assertSyncedMenu(GameTestHelper helper, ServerPlayer player, SourcePortBlockEntity port) {
        SourcePortMenu server = new SourcePortMenu(1, player.getInventory(), port);
        SourcePortMenu client = new SourcePortMenu(1, player.getInventory(), port.getBlockPos());
        server.addSlotListener(new SourceMenuListener(client));
        port.storage().setAmount(731);
        server.broadcastChanges();
        helper.assertTrue(client.owner() == null && client.pos().equals(port.getBlockPos())
                        && client.storedSource() == 731 && client.sourceCapacity() == port.storage().capacity(),
                "Position-only client menu receives actual amount/capacity through native menu data slots");
    }

    /** Forwards actual menu synchronization to the position-only menu. @author howxu <dev@howxu.cn> */
    private record SourceMenuListener(SourcePortMenu client) implements ContainerListener {
        @Override public void slotChanged(AbstractContainerMenu menu, int slot, ItemStack stack) {}
        @Override public void dataChanged(AbstractContainerMenu menu, int index, int value) { client.setData(index, value); }
    }
}
