package cn.howxu.mmcr.compat.fluxnetworks;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.definition.BlockPredicate;
import cn.howxu.mmcr.api.machine.definition.InterfacePredicates;
import cn.howxu.mmcr.client.model.MachineModelDataKeys;
import cn.howxu.mmcr.compat.fluxnetworks.loaded.FluxNetworkInputBlockEntity;
import cn.howxu.mmcr.compat.fluxnetworks.loaded.FluxNetworkInterfaceBlockEntity;
import cn.howxu.mmcr.compat.kubejs.KubeJSInterfaceHelpers;
import cn.howxu.mmcr.internal.api.facade.structure.StructureAdapters;
import cn.howxu.mmcr.internal.tile.EnergyHatchBlockEntity;
import cn.howxu.mmcr.publicapi.structure.BlockConditions;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ForcedChunksSavedData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import sonar.fluxnetworks.api.FluxCapabilities;
import sonar.fluxnetworks.api.FluxConstants;
import sonar.fluxnetworks.api.FluxDataComponents;
import sonar.fluxnetworks.api.network.SecurityLevel;
import sonar.fluxnetworks.common.connection.FluxMenu;
import sonar.fluxnetworks.common.connection.FluxNetwork;
import sonar.fluxnetworks.common.connection.PhantomFluxDevice;
import sonar.fluxnetworks.common.device.TileFluxDevice;
import sonar.fluxnetworks.common.device.TileFluxStorage;
import sonar.fluxnetworks.common.item.ItemFluxConfigurator;
import sonar.fluxnetworks.common.item.FluxDeviceItem;
import sonar.fluxnetworks.common.level.FluxChunkLoading;
import sonar.fluxnetworks.register.RegistryItems;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static cn.howxu.mmcr.compat.fluxnetworks.FluxNetworksGameTestFixtures.*;

/** Native menu, registry discovery, configuration, dimension and block lifecycle contracts.
 * @author howxu <dev@howxu.cn>
 */
public final class FluxNetworksDeviceGameTest {
    public void nativeMenuAndCapabilities(GameTestHelper helper) {
        ServerPlayer owner = player(helper);
        ServerPlayer visitor = player(helper);
        FluxNetwork network = createNetwork(helper, owner);
        FluxNetwork other = null;
        FluxNetworkInputBlockEntity reloaded = null;
        MachineRig rig = null;
        try {
            other = createNetwork(helper, player(helper));
            rig = machine(helper);
            connectAndCycle(rig.input(), network);
            connectAndCycle(rig.output(), network);
            helper.assertTrue(network.getLogicalDevices(FluxNetwork.POINT).contains(rig.input())
                            && !network.getLogicalDevices(FluxNetwork.PLUG).contains(rig.input())
                            && network.getLogicalDevices(FluxNetwork.PLUG).contains(rig.output())
                            && !network.getLogicalDevices(FluxNetwork.POINT).contains(rig.output()),
                    "Actual MMCR BE types register in the native Point/Plug logical lists");
            rig.input().setOwnerUUID(owner.getUUID());
            rig.input().onPlayerInteract(owner);
            helper.assertTrue(owner.containerMenu instanceof FluxMenu menu && menu.mProvider == rig.input()
                            && menu.stillValid(owner), "Native interaction opens FluxMenu with the actual device provider");
            owner.closeContainer();
            rig.output().onPlayerInteract(owner);
            helper.assertTrue(owner.containerMenu instanceof FluxMenu menu && menu.mProvider == rig.output()
                            && menu.stillValid(owner), "Registered Plug opens the native FluxMenu with its own provider");
            owner.closeContainer();
            network.setSecurityLevel(SecurityLevel.PRIVATE);
            helper.assertTrue(rig.input().canPlayerAccess(owner) && !rig.input().canPlayerAccess(visitor),
                    "Native device owner bypass and private-network visitor denial are preserved");
            rig.input().onPlayerInteract(visitor);
            helper.assertTrue(visitor.containerMenu == visitor.inventoryMenu, "Denied visitor cannot open native menu");
            network.setSecurityLevel(SecurityLevel.PUBLIC);
            rig.input().onPlayerInteract(visitor);
            helper.assertTrue(visitor.containerMenu instanceof FluxMenu menu && menu.mProvider == rig.input(),
                    "Public-network visitor opens the same native provider after the owner's menu closes");
            visitor.closeContainer();

            structureHelpers(helper, rig.input().getBlockState().getBlock(), rig.output().getBlockState().getBlock());
            TileFluxStorage supply = storage(helper, STORAGE, 500L);
            connectAndCycle(supply, network);
            // Association metadata alone must not admit energy before the controller confirms its snapshot.
            rig.input().onMachineFormed(rig.controller().getBlockPos());
            rig.output().onMachineFormed(rig.controller().getBlockPos());
            rig.input().getTransferHandler().requestWarmup(100L);
            network.onEndServerTick();
            helper.assertTrue(rig.input().getTransferBuffer() == 0L && supply.getTransferBuffer() == 500L
                            && rig.output().getTransferHandler().admissionAt(0L) == 0L,
                    "Unconfirmed controller associations admit neither Point demand nor Plug recipe output");
            form(helper, rig.controller());
            tickDevice(rig.input());
            tickDevice(rig.output());
            for (FluxNetworkInterfaceBlockEntity port : List.of(rig.input(), rig.output())) {
                helper.assertTrue(Boolean.TRUE.equals(port.getModelData().get(MachineModelDataKeys.PORT_LINKED))
                                && port.appearanceSource().equals(rig.controller().currentStructureSnapshot()
                                .machine().appearance().formedPortTextureSource())
                                && port.getBlockState().getBlock().getAppearance(port.getBlockState(), helper.getLevel(),
                                port.getBlockPos(), Direction.UP, port.getBlockState(), port.getBlockPos()).is(Blocks.GOLD_BLOCK),
                        "Confirmed formation publishes linked ModelData and the machine's full-block dynamic appearance");
                for (Direction side : Direction.values()) assertNoExternalEnergy(helper, port, side);
                assertNoExternalEnergy(helper, port, null);
            }
            BlockPos adjacent = CONTROLLER.west().north();
            helper.setBlock(adjacent, ModBlocks.BLOCKS.get("energy_output_hatch").get().defaultBlockState());
            EnergyHatchBlockEntity adjacentHatch = helper.getBlockEntity(adjacent);
            adjacentHatch.energyStorage().forceInsert(200L, false);
            var adjacentEnergy = helper.getLevel().getCapability(Capabilities.EnergyStorage.BLOCK,
                    helper.absolutePos(adjacent), Direction.SOUTH);
            helper.assertTrue(adjacentEnergy != null && adjacentEnergy.extractEnergy(200, true) == 200,
                    "Adjacent ordinary energy port really holds transferable FE");
            BlockPos adjacentSink = CONTROLLER.east().north();
            helper.setBlock(adjacentSink, ModBlocks.BLOCKS.get("energy_input_hatch").get().defaultBlockState());
            var sinkEnergy = helper.getLevel().getCapability(Capabilities.EnergyStorage.BLOCK,
                    helper.absolutePos(adjacentSink), Direction.SOUTH);
            helper.assertTrue(sinkEnergy != null && sinkEnergy.receiveEnergy(30, true) == 30,
                    "Adjacent ordinary input really has room for transferable FE");
            tickDevice(rig.input());
            tickDevice(rig.output());
            rig.input().getTransferHandler().requestWarmup(100L);
            network.onEndServerTick();
            helper.assertTrue(rig.input().getTransferBuffer() == 100L && supply.getTransferBuffer() == 400L
                            && adjacentEnergy.getEnergyStored() == 200,
                    "Native cycle transfers only network energy and never extracts adjacent ordinary FE");
            helper.assertTrue(rig.output().getTransferHandler().acceptRecipeEnergy(30L),
                    "Confirmed real Plug admits committed energy against actual network demand");
            tickDevice(rig.output());
            helper.assertTrue(rig.output().getTransferBuffer() == 30L && sinkEnergy.getEnergyStored() == 0,
                    "Explicit native device ticker never exports committed recipe output to adjacent FE");
            network.onEndServerTick();
            helper.assertTrue(rig.output().getTransferBuffer() == 0L && supply.getTransferBuffer() == 430L
                            && adjacentEnergy.getEnergyStored() == 200 && sinkEnergy.getEnergyStored() == 0,
                    "Committed Plug output drains only through its real network and conserves total energy");

            // A real native configurator paste changes a live first-ticked device's object identity as well as its ID.
            connectAndCycle(rig.output(), other);
            pasteFrom(helper, owner, rig.output(), rig.input());
            network.onEndServerTick();
            other.onEndServerTick();
            CompoundTag update = rig.input().getUpdateTag(helper.getLevel().registryAccess());
            helper.assertTrue(rig.input().getNetwork() == other && rig.input().getNetworkID() == other.getNetworkID()
                            && update.getInt(FluxConstants.NETWORK_ID) == other.getNetworkID()
                            && !network.getLogicalDevices(FluxNetwork.POINT).contains(rig.input())
                            && other.getLogicalDevices(FluxNetwork.POINT).contains(rig.input())
                            && rig.input().getTransferBuffer() == 100L && rig.input().getTransferHandler().reserved() == 0L,
                    "Live configuration paste synchronizes native connection queues and update tag without copying source energy");

            CompoundTag saved = rig.input().saveWithFullMetadata(helper.getLevel().registryAccess());
            rig.input().onChunkUnloaded();
            other.onEndServerTick();
            helper.assertTrue(!other.getLogicalDevices(FluxNetwork.POINT).contains(rig.input())
                            && other.getConnectionByPos(rig.input().getGlobalPos()) instanceof PhantomFluxDevice phantom
                            && !phantom.isChunkLoaded(), "Native unload retains only this device's unloaded phantom");
            helper.getLevel().removeBlockEntity(rig.input().getBlockPos());
            BlockEntity loaded = BlockEntity.loadStatic(rig.input().getBlockPos(), rig.input().getBlockState(),
                    saved, helper.getLevel().registryAccess());
            helper.assertTrue(loaded instanceof FluxNetworkInputBlockEntity && loaded.getType() == rig.input().getType(),
                    "Native disk reload resolves the MMCR Point's actual registered block entity factory");
            reloaded = (FluxNetworkInputBlockEntity) loaded;
            helper.getLevel().setBlockEntity(reloaded);
            reloaded.onLoad();
            helper.assertTrue(!reloaded.getNetwork().isValid() && reloaded.getNetworkID() == other.getNetworkID(),
                    "Reload preserves saved network ID but does not bypass native first-tick connection");
            tickDevice(reloaded);
            other.onEndServerTick();
            helper.assertTrue(reloaded.getNetwork() == other && other.getConnectionByPos(reloaded.getGlobalPos()) == reloaded
                            && other.getLogicalDevices(FluxNetwork.POINT).contains(reloaded),
                    "Native first tick replaces the phantom with the reloaded registered device");
            removalReleasesTicket(helper, reloaded, other);
            rig.controller().invalidateFormedStructure();
            helper.assertTrue(!Boolean.TRUE.equals(rig.output().getModelData().get(MachineModelDataKeys.PORT_LINKED)),
                    "Unforming immediately clears the surviving port's dynamic linked appearance");
        } finally {
            try {
                owner.closeContainer();
                visitor.closeContainer();
                if (rig != null) rig.controller().invalidateFormedStructure();
            } finally {
                try { if (reloaded != null) releaseTicket(reloaded); }
                finally {
                    try { closeNetwork(network); }
                    finally { if (other != null) closeNetwork(other); }
                }
            }
        }
        helper.succeed();
    }

    public void crossDimensionNativeTransfer(GameTestHelper helper) {
        FluxNetwork network = createNetwork(helper, player(helper));
        ServerLevel nether = helper.getLevel().getServer().getLevel(Level.NETHER);
        BlockPos remote = new BlockPos(helper.absolutePos(STORAGE).getX() + 1_000_000, 64,
                helper.absolutePos(STORAGE).getZ() + 1_000_000);
        BlockState previous = null;
        CompoundTag previousEntity = null;
        MachineRig rig = null;
        try {
            helper.assertTrue(nether != null && nether != helper.getLevel(), "Second server dimension is available");
            previous = nether.getBlockState(remote);
            BlockEntity entity = nether.getBlockEntity(remote);
            previousEntity = entity == null ? null : entity.saveWithFullMetadata(nether.registryAccess());
            rig = machine(helper);
            form(helper, rig.controller());
            TileFluxStorage supply = storage(nether, remote, 250L);
            connectAndCycle(supply, network);
            connectAndCycle(rig.input(), network);
            rig.input().getTransferHandler().requestWarmup(100L);
            network.onEndServerTick();
            helper.assertTrue(!supply.getGlobalPos().dimension().equals(rig.input().getGlobalPos().dimension())
                            && network.getConnectionByPos(supply.getGlobalPos()) == supply
                            && network.getConnectionByPos(rig.input().getGlobalPos()) == rig.input()
                            && supply.getTransferBuffer() == 150L && rig.input().getTransferBuffer() == 100L,
                    "Real network allocates conserved energy across dimensions using native GlobalPos identities");
        } finally {
            try {
                try { if (rig != null) rig.controller().invalidateFormedStructure(); }
                finally { closeNetwork(network); }
            }
            finally {
                if (previous != null) {
                    nether.removeBlock(remote, false);
                    nether.setBlock(remote, previous, 3);
                    if (previousEntity != null) {
                        BlockEntity restored = BlockEntity.loadStatic(remote, previous, previousEntity, nether.registryAccess());
                        if (restored != null) { nether.setBlockEntity(restored); restored.onLoad(); }
                    }
                }
            }
        }
        helper.succeed();
    }

    public void dropAndReplacePreserveEnergy(GameTestHelper helper) {
        ServerPlayer owner = player(helper);
        FluxNetwork network = createNetwork(helper, owner);
        MachineRig rig = null;
        try {
            rig = machine(helper);
            form(helper, rig.controller());
            TileFluxStorage supply = storage(helper, STORAGE, 400L);
            connectAndCycle(supply, network);
            connectAndCycle(rig.input(), network);
            connectAndCycle(rig.output(), network);
            rig.input().setOwnerUUID(owner.getUUID());
            rig.output().setOwnerUUID(owner.getUUID());
            configure(rig.input(), "saved-point", 17, 200L, true);
            configure(rig.output(), "saved-plug", -23, 120L, false);
            rig.input().getTransferHandler().requestWarmup(100L);
            network.onEndServerTick();
            helper.assertTrue(rig.input().getTransferHandler().reserveCandidate(60L)
                            && rig.input().getTransferHandler().commitCandidate(60L)
                            && rig.output().getTransferHandler().acceptRecipeEnergy(40L),
                    "Drop fixtures hold real native received energy, committed reservation and committed Plug output");
            // Inspect committed Plug output before a removal cycle can drain it to native Storage.
            for (FluxNetworkInterfaceBlockEntity port : List.of(rig.output(), rig.input())) {
                long energy = port.getTransferBuffer();
                var config = port.collectComponents().get(FluxDataComponents.FLUX_CONFIG);
                ItemStack drop = Block.getDrops(port.getBlockState(), helper.getLevel(), port.getBlockPos(), port,
                        owner, ItemStack.EMPTY).stream().filter(stack -> stack.is(port.getBlockState().getBlock().asItem()))
                        .findFirst().orElseThrow(() -> new AssertionError("Generated conditional loot drops the registered Flux block"));
                helper.assertTrue(drop.has(FluxDataComponents.FLUX_CONFIG)
                                && config.equals(drop.get(FluxDataComponents.FLUX_CONFIG))
                                && drop.getOrDefault(FluxDataComponents.STORED_ENERGY, -1L) == energy,
                        "Actual loot copies native Flux configuration and stored energy DataComponents");
                BlockPos pos = port.getBlockPos();
                BlockState state = port.getBlockState();
                if (port == rig.input()) {
                    ItemStack wrench = BuiltInRegistries.ITEM.getTag(Tags.Items.TOOLS_WRENCH).orElseThrow()
                            .stream().map(holder -> new ItemStack(holder.value())).findFirst().orElseThrow();
                    owner.setItemInHand(InteractionHand.MAIN_HAND, wrench);
                    owner.setShiftKeyDown(true);
                    PlayerInteractEvent.RightClickBlock event = new PlayerInteractEvent.RightClickBlock(owner,
                            InteractionHand.MAIN_HAND, pos, hit(pos));
                    NeoForge.EVENT_BUS.post(event);
                    helper.assertTrue(event.isCanceled() && helper.getLevel().getBlockState(pos).isAir(),
                            "Real crouch-wrench event dismantles the registered Flux interface");
                    drop = owner.getInventory().items.stream().filter(stack -> stack.is(state.getBlock().asItem()))
                            .findFirst().orElseThrow(() -> new AssertionError("Wrench returns native component-preserving loot"));
                    helper.assertTrue(config.equals(drop.get(FluxDataComponents.FLUX_CONFIG))
                                    && drop.getOrDefault(FluxDataComponents.STORED_ENERGY, -1L) == energy,
                            "Wrench path uses the same component-aware conditional loot");
                    owner.setShiftKeyDown(false);
                } else helper.getLevel().removeBlock(pos, false);
                network.onEndServerTick();
                helper.getLevel().setBlock(pos, state, 3);
                state.getBlock().setPlacedBy(helper.getLevel(), pos, state, owner, drop);
                FluxNetworkInterfaceBlockEntity placed = (FluxNetworkInterfaceBlockEntity) helper.getLevel().getBlockEntity(pos);
                helper.assertTrue(placed.getTransferBuffer() == energy && placed.getNetworkID() == network.getNetworkID()
                                && config.equals(placed.collectComponents().get(FluxDataComponents.FLUX_CONFIG))
                                && placed.getOwnerUUID().equals(owner.getUUID())
                                && !Boolean.TRUE.equals(placed.getModelData().get(MachineModelDataKeys.PORT_LINKED))
                                && (!(placed instanceof FluxNetworkInputBlockEntity input) || input.getTransferHandler().reserved() == 0L),
                        "Real placement restores config/energy/placer ownership, never controller ownership or recipe reservations");
                tickDevice(placed);
                network.onEndServerTick();
                helper.assertTrue(placed.getNetwork() == network && network.getConnectionByPos(placed.getGlobalPos()) == placed,
                        "Replaced block joins through native first-tick lifecycle without losing its identity");
            }
        } finally {
            try { if (rig != null) rig.controller().invalidateFormedStructure(); }
            finally { closeNetwork(network); }
        }
        helper.succeed();
    }

    public void nativeItemCraftingRecipes(GameTestHelper helper) {
        FluxNetwork network = createNetwork(helper, player(helper));
        MachineRig rig = null;
        try {
            rig = machine(helper);
            form(helper, rig.controller());
            connectAndCycle(storage(helper, STORAGE, 300L), network);
            connectAndCycle(rig.input(), network);
            connectAndCycle(rig.output(), network);
            rig.input().getTransferHandler().requestWarmup(100L);
            network.onEndServerTick();
            helper.assertTrue(rig.input().getTransferBuffer() == 100L
                            && rig.output().getTransferHandler().acceptRecipeEnergy(40L),
                    "Crafting fixtures obtain real network energy and commit real Plug output");
            ItemStack point = new ItemStack(RegistryItems.FLUX_POINT.get());
            ItemStack plug = new ItemStack(RegistryItems.FLUX_PLUG.get());
            helper.assertTrue(point.getItem() instanceof FluxDeviceItem && plug.getItem() instanceof FluxDeviceItem,
                    "Ingredients are the actual registered native Point/Plug items, never ordinary item stand-ins");
            point.set(FluxDataComponents.FLUX_CONFIG, rig.input().collectComponents().get(FluxDataComponents.FLUX_CONFIG));
            point.set(FluxDataComponents.STORED_ENERGY, rig.input().getTransferBuffer());
            plug.set(FluxDataComponents.FLUX_CONFIG, rig.output().collectComponents().get(FluxDataComponents.FLUX_CONFIG));
            plug.set(FluxDataComponents.STORED_ENERGY, rig.output().getTransferBuffer());
            assertNativeCrafting(helper, FluxNetworksIds.INPUT, FluxNetworksIds.OUTPUT, point);
            assertNativeCrafting(helper, FluxNetworksIds.OUTPUT, FluxNetworksIds.INPUT, plug);
        } finally {
            try { if (rig != null) rig.controller().invalidateFormedStructure(); }
            finally { closeNetwork(network); }
        }
        helper.succeed();
    }

    private static void assertNativeCrafting(GameTestHelper helper, String matchingId, String oppositeId, ItemStack nativeItem) {
        var manager = helper.getLevel().getRecipeManager();
        var matching = manager.byKey(MMCR.id(matchingId)).orElseThrow(
                () -> new AssertionError("Generated conditional crafting recipe must actually load: " + matchingId));
        var opposite = manager.byKey(MMCR.id(oppositeId)).orElseThrow(
                () -> new AssertionError("Generated opposite crafting recipe must actually load: " + oppositeId));
        helper.assertTrue(matching.value() instanceof ShapelessRecipe && opposite.value() instanceof ShapelessRecipe,
                "Both DataGen recipes load as their actual registered shapeless recipe type");
        ShapelessRecipe recipe = (ShapelessRecipe) matching.value();
        ItemStack before = nativeItem.copy();
        CraftingInput input = CraftingInput.of(3, 1, List.of(new ItemStack(ModItems.MODULARIUM.get()),
                nativeItem, new ItemStack(ModBlocks.BASIC_CASING.get())));
        helper.assertTrue(nativeItem.has(FluxDataComponents.FLUX_CONFIG) && nativeItem.has(FluxDataComponents.STORED_ENERGY)
                        && recipe.matches(input, helper.getLevel())
                        && !((ShapelessRecipe) opposite.value()).matches(input, helper.getLevel()),
                "Actual component-bearing native item matches only its own direction despite shapeless ingredient order");
        ItemStack assembled = recipe.assemble(input, helper.getLevel().registryAccess());
        helper.assertTrue(assembled.is(ModBlocks.BLOCKS.get(matchingId).get().asItem()) && assembled.getCount() == 1
                        && !assembled.has(FluxDataComponents.FLUX_CONFIG) && !assembled.has(FluxDataComponents.STORED_ENERGY)
                        && ItemStack.matches(before, nativeItem),
                "Actual assemble returns the declared MMCR interface without copying native energy/config or mutating ingredients");
        helper.assertTrue(!recipe.matches(CraftingInput.of(2, 1, List.of(nativeItem,
                        new ItemStack(ModItems.MODULARIUM.get()))), helper.getLevel())
                        && !recipe.matches(CraftingInput.of(2, 1, List.of(nativeItem,
                        new ItemStack(ModBlocks.BASIC_CASING.get()))), helper.getLevel()),
                "Real matching rejects missing casing and missing modularium");
    }

    public void pendingConnectionsAndRemoval(GameTestHelper helper) {
        ServerPlayer owner = player(helper);
        FluxNetwork a = createNetwork(helper, owner);
        FluxNetwork b = null;
        FluxNetwork c = null;
        MachineRig rig = null;
        try {
            b = createNetwork(helper, player(helper));
            c = createNetwork(helper, player(helper));
            rig = machine(helper);
            form(helper, rig.controller());
            TileFluxStorage sinkA = storage(helper, STORAGE, 0L);
            TileFluxStorage sinkB = storage(helper, STORAGE.east(), 0L);
            TileFluxStorage sinkC = storage(helper, STORAGE.east(2), 0L);
            connectAndCycle(sinkA, a);
            connectAndCycle(sinkB, b);
            connectAndCycle(sinkC, c);
            connectAndCycle(rig.input(), a);
            connectAndCycle(rig.output(), a);
            helper.assertTrue(rig.output().getTransferHandler().acceptRecipeEnergy(40L),
                    "Real formed Plug commits energy before switching to a pending native connection");
            helper.assertTrue(rig.output().connect(b), "Native connect queues B without running any transfer cycle");
            var outputItem = rig.output().getDisplayStack().getItem();
            ItemStack drop = Block.getDrops(rig.output().getBlockState(), helper.getLevel(), rig.output().getBlockPos(),
                    rig.output(), owner, ItemStack.EMPTY).stream().filter(stack -> stack.is(outputItem))
                    .findFirst().orElseThrow();
            helper.assertTrue(drop.getOrDefault(FluxDataComponents.STORED_ENERGY, -1L) == 40L,
                    "Actual loot snapshots the committed buffer before pending-connection removal");
            helper.getLevel().removeBlock(rig.output().getBlockPos(), false);
            a.onEndServerTick();
            b.onEndServerTick();
            helper.assertTrue(rig.output().isRemoved() && !a.getLogicalDevices(FluxNetwork.ANY).contains(rig.output())
                            && !b.getLogicalDevices(FluxNetwork.ANY).contains(rig.output())
                            && a.getConnectionByPos(rig.output().getGlobalPos()) == null
                            && b.getConnectionByPos(rig.output().getGlobalPos()) == null
                            && sinkA.getTransferBuffer() == 0L && sinkB.getTransferBuffer() == 0L
                            && rig.output().getTransferBuffer() == 40L,
                    "Pending B removal leaves no logical/map device and cannot spend the energy already copied into loot");

            var output = output(helper, CONTROLLER.east());
            form(helper, rig.controller());
            connectAndCycle(output, a);
            helper.assertTrue(output.getTransferHandler().acceptRecipeEnergy(40L), "Replacement Plug commits its own output");
            helper.assertTrue(rig.input().connect(b) && output.connect(b)
                            && rig.input().connect(c) && output.connect(c),
                    "Actual Point and Plug move A to pending B to pending C in the same server call, without intermediate cycles");
            a.onEndServerTick();
            b.onEndServerTick();
            c.onEndServerTick();
            for (TileFluxDevice device : List.of(rig.input(), output)) {
                helper.assertTrue(device.getNetwork() == c && c.getConnectionByPos(device.getGlobalPos()) == device
                                && c.getLogicalDevices(FluxNetwork.ANY).contains(device)
                                && !a.getLogicalDevices(FluxNetwork.ANY).contains(device)
                                && !b.getLogicalDevices(FluxNetwork.ANY).contains(device)
                                && a.getConnectionByPos(device.getGlobalPos()) == null && b.getConnectionByPos(device.getGlobalPos()) == null,
                        "Only final network C owns the actual device after repeated pending switches");
            }
            helper.assertTrue(c.getLogicalDevices(FluxNetwork.POINT).contains(rig.input())
                            && c.getLogicalDevices(FluxNetwork.PLUG).contains(output)
                            && sinkA.getTransferBuffer() == 0L && sinkB.getTransferBuffer() == 0L
                            && sinkC.getTransferBuffer() == 40L && output.getTransferBuffer() == 0L,
                    "Only C transfers committed energy, conserving one buffer without cross-network ghosts");
        } finally {
            try { if (rig != null) rig.controller().invalidateFormedStructure(); }
            finally {
                try { closeNetwork(a); }
                finally {
                    try { if (b != null) closeNetwork(b); }
                    finally { if (c != null) closeNetwork(c); }
                }
            }
        }
        helper.succeed();
    }

    public void sameNetworkPasteSortsNativeLists(GameTestHelper helper) {
        ServerPlayer owner = player(helper);
        FluxNetwork network = createNetwork(helper, owner);
        MachineRig rig = null;
        try {
            rig = machine(helper);
            var secondInput = input(helper, new BlockPos(4, 1, 1));
            var secondOutput = output(helper, new BlockPos(4, 1, 2));
            TileFluxStorage donor = storage(helper, STORAGE, 0L);
            configure(secondInput, "second-point", 10, 100L, false);
            configure(secondOutput, "second-plug", 10, 100L, false);
            for (TileFluxDevice device : List.of(donor, rig.input(), rig.output(), secondInput, secondOutput)) {
                connectAndCycle(device, network);
            }
            configure(donor, "priority-donor", 20, 100L, false);
            network.onEndServerTick(); // Clear GUI settings' sort flag before testing the independent paste path.
            assertOrder(helper, network, FluxNetwork.POINT, secondInput, rig.input());
            assertOrder(helper, network, FluxNetwork.PLUG, secondOutput, rig.output());
            pasteFrom(helper, owner, donor, rig.input());
            network.onEndServerTick();
            assertOrder(helper, network, FluxNetwork.POINT, rig.input(), secondInput);
            pasteFrom(helper, owner, donor, rig.output());
            network.onEndServerTick();
            assertOrder(helper, network, FluxNetwork.PLUG, rig.output(), secondOutput);
            configure(donor, "surge-donor", -20, 100L, true);
            network.onEndServerTick(); // Donor changes must not accidentally supply the paste's missing sort notification.
            assertOrder(helper, network, FluxNetwork.POINT, rig.input(), secondInput);
            assertOrder(helper, network, FluxNetwork.PLUG, rig.output(), secondOutput);
            pasteFrom(helper, owner, donor, secondInput);
            network.onEndServerTick();
            assertOrder(helper, network, FluxNetwork.POINT, secondInput, rig.input());
            pasteFrom(helper, owner, donor, secondOutput);
            network.onEndServerTick();
            assertOrder(helper, network, FluxNetwork.PLUG, secondOutput, rig.output());
            for (TileFluxDevice device : List.of(rig.input(), rig.output(), secondInput, secondOutput)) {
                helper.assertTrue(device.getNetwork() == network && network.getConnectionByPos(device.getGlobalPos()) == device,
                        "Priority/surge paste sorts real native lists while preserving same-network object identity");
            }
        } finally {
            try { if (rig != null) rig.controller().invalidateFormedStructure(); }
            finally { closeNetwork(network); }
        }
        helper.succeed();
    }

    private static void assertOrder(GameTestHelper helper, FluxNetwork network, int type,
                                    TileFluxDevice first, TileFluxDevice second) {
        var devices = network.getLogicalDevices(type);
        helper.assertTrue(devices.indexOf(first) >= 0 && devices.indexOf(first) < devices.indexOf(second),
                "Native logical list actually follows pasted priority/surge, ahead of the previously preferred device");
    }

    static void pasteFrom(GameTestHelper helper, ServerPlayer player, TileFluxDevice source, TileFluxDevice target) {
        ItemStack configurator = new ItemStack(RegistryItems.FLUX_CONFIGURATOR.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, configurator);
        ItemFluxConfigurator item = RegistryItems.FLUX_CONFIGURATOR.get();
        player.setShiftKeyDown(true);
        helper.assertTrue(item.onItemUseFirst(configurator, new UseOnContext(player, InteractionHand.MAIN_HAND,
                hit(source.getBlockPos()))) == InteractionResult.SUCCESS && configurator.has(FluxDataComponents.FLUX_CONFIG),
                "Native configurator copies the source device's actual configuration component");
        player.setShiftKeyDown(false);
        helper.assertTrue(item.onItemUseFirst(configurator, new UseOnContext(player, InteractionHand.MAIN_HAND,
                hit(target.getBlockPos()))) == InteractionResult.SUCCESS, "Native configurator pastes into the live MMCR device");
    }

    private static BlockHitResult hit(BlockPos pos) {
        return new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
    }

    static void configure(TileFluxDevice device, String name, int priority, long limit, boolean surge) {
        CompoundTag settings = new CompoundTag();
        settings.putString(FluxConstants.CUSTOM_NAME, name);
        settings.putInt(FluxConstants.PRIORITY, priority);
        settings.putLong(FluxConstants.LIMIT, limit);
        settings.putBoolean(FluxConstants.SURGE_MODE, surge);
        device.readCustomTag(settings, FluxConstants.NBT_TILE_SETTINGS);
    }

    private static void assertNoExternalEnergy(GameTestHelper helper, TileFluxDevice device, Direction side) {
        helper.assertTrue(helper.getLevel().getCapability(Capabilities.EnergyStorage.BLOCK, device.getBlockPos(), side) == null
                        && helper.getLevel().getCapability(FluxCapabilities.BLOCK, device.getBlockPos(), side) == null,
                "Registered recipe-only Flux device exposes neither FE nor FN on side " + side);
    }

    private static void structureHelpers(GameTestHelper helper, Block input, Block output) {
        List<BlockPredicate> inputs = List.of(InterfacePredicates.anyOfEnergyInput(),
                StructureAdapters.unwrap(BlockConditions.energyInput()));
        List<BlockPredicate> outputs = List.of(InterfacePredicates.anyOfEnergyOutput(),
                StructureAdapters.unwrap(BlockConditions.energyOutput()));
        for (BlockPredicate predicate : inputs) helper.assertTrue(blocks(predicate).contains(input) && !blocks(predicate).contains(output),
                "Core/public energy-input helpers include actual Flux Point and reject actual Plug");
        for (BlockPredicate predicate : outputs) helper.assertTrue(blocks(predicate).contains(output) && !blocks(predicate).contains(input),
                "Core/public energy-output helpers include actual Flux Plug and reject actual Point");
        helper.assertTrue(KubeJSInterfaceHelpers.anyOfEnergyInput().matches(input.defaultBlockState())
                        && !KubeJSInterfaceHelpers.anyOfEnergyInput().matches(output.defaultBlockState())
                        && KubeJSInterfaceHelpers.anyOfEnergyOutput().matches(output.defaultBlockState())
                        && !KubeJSInterfaceHelpers.anyOfEnergyOutput().matches(input.defaultBlockState()),
                "Existing KubeJS direction helpers match the real Flux states in both positive and negative directions");
        for (BlockPredicate ports : List.of(InterfacePredicates.anyOfEnergyPorts(), StructureAdapters.unwrap(BlockConditions.energyPorts()))) {
            helper.assertTrue(blocks(ports).containsAll(List.of(input, output, ModBlocks.BLOCKS.get("energy_input_hatch").get(),
                    ModBlocks.BLOCKS.get("energy_output_hatch").get())), "Bidirectional core/public helpers retain Flux and ordinary energy ports");
        }
        helper.assertTrue(KubeJSInterfaceHelpers.anyOfEnergyPorts().matches(input.defaultBlockState())
                        && KubeJSInterfaceHelpers.anyOfEnergyPorts().matches(output.defaultBlockState())
                        && KubeJSInterfaceHelpers.anyOfEnergyPorts().matches(ModBlocks.BLOCKS.get("energy_input_hatch").get().defaultBlockState())
                        && KubeJSInterfaceHelpers.anyOfEnergyPorts().matches(ModBlocks.BLOCKS.get("energy_output_hatch").get().defaultBlockState()),
                "Bidirectional KubeJS helper includes both actual Flux devices and retains ordinary energy ports");
    }

    private static List<Block> blocks(BlockPredicate predicate) {
        if (predicate.blockSupplier().isPresent()) return List.of(predicate.blockSupplier().orElseThrow().get());
        if (predicate.block().isPresent()) return List.of(predicate.block().orElseThrow());
        return predicate.alternatives().stream().flatMap(child -> blocks(child).stream()).toList();
    }

    private static void removalReleasesTicket(GameTestHelper helper, TileFluxDevice device, FluxNetwork network) {
        ServerLevel level = helper.getLevel();
        var data = level.getDataStorage().computeIfAbsent(ForcedChunksSavedData.factory(), ForcedChunksSavedData.FILE_ID);
        Set<?> before = new HashSet<>(data.getBlockForcedChunks().getTickingChunks().keySet());
        ChunkPos chunk = new ChunkPos(device.getBlockPos());
        try {
            helper.assertTrue(FluxChunkLoading.CONTROLLER.forceChunk(level, device.getBlockPos(), chunk.x, chunk.z, true, true),
                    "Registered native Flux ticket controller acquires this device's single ticking chunk ticket");
            device.setForcedLoading(true); // Native ticket validation uses this same device marker.
            Set<?> actualTickets = data.getBlockForcedChunks().getTickingChunks().keySet();
            Object ownTicket = actualTickets.stream()
                    .filter(key -> !before.contains(key)).findFirst().orElseThrow();
            level.removeBlock(device.getBlockPos(), false);
            network.onEndServerTick();
            helper.assertTrue(device.isRemoved() && !network.getLogicalDevices(FluxNetwork.ANY).contains(device)
                            && network.getConnectionByPos(device.getGlobalPos()) == null
                            && !data.getBlockForcedChunks().getTickingChunks().containsKey(ownTicket),
                    "Native removal deletes its logical/map connection and releases its own actual chunk ticket");
        } finally { releaseTicket(device); }
    }

    private static void releaseTicket(TileFluxDevice device) {
        ChunkPos chunk = new ChunkPos(device.getBlockPos());
        FluxChunkLoading.CONTROLLER.forceChunk((ServerLevel) device.getLevel(), device.getBlockPos(), chunk.x, chunk.z, false, true);
    }
}
