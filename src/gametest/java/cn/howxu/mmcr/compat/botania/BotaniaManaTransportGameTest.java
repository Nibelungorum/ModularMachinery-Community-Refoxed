package cn.howxu.mmcr.compat.botania;

import appeng.core.definitions.AEItems;
import cn.howxu.mmcr.api.compat.botania.ManaViewFacet;
import cn.howxu.mmcr.api.machine.MachineAppearanceSpec;
import cn.howxu.mmcr.client.model.MachineModelDataKeys;
import cn.howxu.mmcr.compat.botania.loaded.ManaPortBlockEntity;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Unit;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.Tags;
import vazkii.botania.api.block.Wandable;
import vazkii.botania.api.mana.ManaItem;
import vazkii.botania.api.mana.ManaPool;
import vazkii.botania.api.mana.ManaReceiver;
import vazkii.botania.api.mana.spark.ManaSparkAttachable;
import vazkii.botania.api.mana.spark.ManaSparkHelper;
import vazkii.botania.common.block.block_entity.mana.ManaPoolBlockEntity;
import vazkii.botania.common.block.block_entity.mana.ManaSpreaderBlockEntity;
import vazkii.botania.common.component.BotaniaDataComponents;
import vazkii.botania.common.entity.ManaBurstEntity;
import vazkii.botania.common.entity.ManaSparkEntity;
import vazkii.botania.common.item.BotaniaItems;
import vazkii.botania.common.item.ManaTabletItem;

import java.util.ArrayList;
import java.util.List;

/** Real Botania transport, item, wand and persistence paths. @author howxu <dev@howxu.cn> */
public final class BotaniaManaTransportGameTest {
    public void capabilitiesWandAndPersistence(GameTestHelper helper) {
        ServerPlayer player = BotaniaManaGameTestFixtures.wandPlayer(helper);
        for (IOType io : List.of(IOType.INPUT, IOType.OUTPUT)) {
            BlockPos pos = new BlockPos(io == IOType.INPUT ? 1 : 3, 1, 1);
            ManaPortBlockEntity port = BotaniaManaGameTestFixtures.port(helper, pos, io);
            port.storage().setAmount(12_345);
            ManaReceiver handler = ManaReceiver.LOOKUP.find(helper.getLevel(), helper.absolutePos(pos), null);
            helper.assertTrue(handler == port.externalHandler(), "Null side exposes the stable native pool host");
            for (Direction side : Direction.values()) {
                helper.assertTrue(ManaReceiver.LOOKUP.find(helper.getLevel(), helper.absolutePos(pos), side) == handler,
                        "Every side exposes the same native handler");
                helper.assertTrue(Wandable.LOOKUP.find(helper.getLevel(), helper.absolutePos(pos), side) == port,
                        "Every side exposes native wand behavior");
            }
            helper.assertTrue(ManaSparkAttachable.LOOKUP.find(helper.getLevel(), helper.absolutePos(pos)) == port,
                    "Spark capability resolves the pool itself");
            port.receiveMana(io == IOType.INPUT ? -500 : 500);
            helper.assertTrue(port.storage().amount() == 12_345, "Wrong native receive direction cannot change real inventory");
            if (io == IOType.OUTPUT) {
                port.receiveMana(Integer.MIN_VALUE);
                helper.assertTrue(port.storage().amount() == 0, "Minimum int extraction widens before negation and drains safely");
            } else {
                port.storage().setAmount(port.storage().capacity() - 7);
                port.receiveMana(Integer.MAX_VALUE);
                helper.assertTrue(port.storage().amount() == port.storage().capacity(),
                        "Oversized native reception accepts only available input space");
            }
            port.storage().setAmount(12_345);
            var fixedKind = port.kind();
            var expectedBlock = port.getBlockState().getBlock();
            for (var wand : List.of(BotaniaItems.WAND_OF_THE_FOREST, BotaniaItems.WAND_OF_THE_ELVEN_FOREST)) {
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(wand));
                helper.assertTrue(player.getMainHandItem().is(Tags.Items.TOOLS_WRENCH),
                        "Native forest and elven wands exercise the general wrench interception boundary");
                for (boolean sneaking : List.of(false, true)) {
                    String interaction = io + " " + wand + " sneaking=" + sneaking;
                    port.storage().move(1, io == IOType.INPUT, false);
                    int before = port.storage().amount();
                    helper.assertTrue(port.isMarkedForSync(), interaction + " starts with a real pending inventory change");
                    player.setShiftKeyDown(sneaking);
                    BotaniaManaGameTestFixtures.use(helper, player, pos);
                    helper.assertTrue(helper.getLevel().getBlockState(port.getBlockPos()).is(expectedBlock),
                            interaction + " leaves the expected pool block in the world");
                    helper.assertTrue(helper.getLevel().getBlockEntity(port.getBlockPos()) == port && !port.isRemoved(),
                            interaction + " keeps the same live world block entity");
                    helper.assertTrue(port.storage().amount() == before, interaction + " preserves real mana inventory");
                    helper.assertTrue(port.kind() == fixedKind && port.ioType() == io, interaction + " keeps the fixed pool kind");
                    helper.assertTrue(player.containerMenu == player.inventoryMenu, interaction + " does not open a menu");
                    helper.assertTrue(!port.isMarkedForSync(), interaction + " clears its own pending marker through native wand sync");
                }
                BlockPos casingPos = new BlockPos(4, 1, 1);
                helper.setBlock(casingPos, ModBlocks.CASING.get().defaultBlockState());
                BotaniaManaGameTestFixtures.use(helper, player, casingPos);
                helper.assertTrue(helper.getLevel().getBlockState(helper.absolutePos(casingPos)).isAir(),
                        "Sneaking " + wand + " still dismantles unrelated MMCR casing; only modular mana pools are exempt");
            }
            player.setShiftKeyDown(false);
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            BotaniaManaGameTestFixtures.use(helper, player, pos);
            helper.assertTrue(player.containerMenu == player.inventoryMenu, "Empty-hand interaction has no menu");
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(BotaniaItems.WAND_OF_THE_FOREST));
            port.storage().move(1, io == IOType.INPUT, false);
            helper.assertTrue(port.isMarkedForSync(), "Real inventory changes mark a potential visual synchronization");
            player.setShiftKeyDown(false);
            BotaniaManaGameTestFixtures.use(helper, player, pos);
            helper.assertTrue(!port.isMarkedForSync(), "Wand use clears the pending marker through native immediate sync");
            port.storage().setAmount(12_345);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(BotaniaItems.MANA_SPARK));
            BotaniaManaGameTestFixtures.use(helper, player, pos);
            ManaSparkEntity attached = (ManaSparkEntity) ManaSparkHelper.getAttachedSpark(helper.getLevel(), helper.absolutePos(pos));
            helper.assertTrue(attached != null && player.containerMenu == player.inventoryMenu,
                    "Real spark useOn reaches attachment through the independent block interaction path");
            attached.discard();
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(BotaniaItems.WAND_OF_THE_FOREST));

            BlockPos linkedController = helper.absolutePos(new BlockPos(4, 1, 4));
            port.linkControllerAppearanceSource(linkedController,
                    new MachineAppearanceSpec.TextureSource(ResourceLocation.withDefaultNamespace("stone"), null));
            helper.assertTrue(port.getBlockState().getAppearance(helper.getLevel(), port.getBlockPos(), Direction.UP,
                            null, null).is(Blocks.STONE), "Independent pool resolves the linked casing appearance");
            CompoundTag saved = port.saveWithFullMetadata(helper.getLevel().registryAccess());
            CompoundTag update = port.getUpdateTag(helper.getLevel().registryAccess());
            helper.assertTrue(update.getCompound("mana").getInt("amount") == 12_345,
                    "Initial/update packet data contains real input and output inventory");
            Object originalSnapshot = port.capabilitySnapshot();
            helper.assertTrue(originalSnapshot == port.capabilitySnapshot(), "Hosted capability snapshot is stable");
            helper.setBlock(pos, Blocks.AIR);
            ManaPortBlockEntity restored = BotaniaManaGameTestFixtures.port(helper, pos, io);
            restored.loadWithComponents(saved, helper.getLevel().registryAccess());
            helper.assertTrue(restored.linkedControllerPositions().contains(linkedController)
                            && restored.getBlockState().getAppearance(helper.getLevel(), restored.getBlockPos(), Direction.UP,
                            null, null).is(Blocks.STONE), "Saving/restoring mana also preserves the linked appearance lifecycle");
            ManaViewFacet facet = restored.capabilitySnapshot().capabilities().getFirst()
                    .facet(ManaViewFacet.class).orElseThrow();
            helper.assertTrue(restored.storage().amount() == 12_345 && facet.amount() == 12_345
                            && facet.queryIdentity() == restored.storage().identity(),
                    "NBT restores one physical store and the truthful query facet");
            helper.assertTrue(restored.getCurrentMana() == (io == IOType.INPUT ? 0 : 12_345),
                    "Restored native projection stays fixed to its IO direction");
            handler.receiveMana(io == IOType.INPUT ? 500 : -500);
            helper.assertTrue(port.storage().amount() == 12_345 && restored.storage().amount() == 12_345
                            && handler.getCurrentMana() == 0 && handler.isFull(),
                    "Removed old native reference cannot mutate or supply either store");
            restored.onChunkUnloaded();
            restored.receiveMana(io == IOType.INPUT ? 500 : -500);
            helper.assertTrue(restored.storage().amount() == 12_345 && restored.getCurrentMana() == 0,
                    "Chunk unload immediately invalidates retained native references");
            restored.onLoad();
            restored.setRemoved();
            restored.receiveMana(io == IOType.INPUT ? 500 : -500);
            helper.assertTrue(restored.storage().amount() == 12_345, "Unloaded/removed reference cannot transfer mana");
            helper.setBlock(pos, Blocks.AIR);

            BlockPos wrenchPos = new BlockPos(io == IOType.INPUT ? 1 : 3, 1, 3);
            ManaPortBlockEntity dismantled = BotaniaManaGameTestFixtures.port(helper, wrenchPos, io);
            var dismantledBlock = dismantled.getBlockState().getBlock();
            player.setItemInHand(InteractionHand.MAIN_HAND, AEItems.CERTUS_QUARTZ_WRENCH.stack());
            helper.assertTrue(player.getMainHandItem().is(Tags.Items.TOOLS_WRENCH), "Ordinary wrench uses the real wrench tag");
            player.setShiftKeyDown(true);
            BotaniaManaGameTestFixtures.use(helper, player, wrenchPos);
            helper.assertTrue(helper.getLevel().getBlockState(dismantled.getBlockPos()).isAir() && dismantled.isRemoved(),
                    "Ordinary wrench still dismantles the " + io + " modular pool through real gameMode interaction");
            helper.assertTrue(player.getInventory().contains(new ItemStack(dismantledBlock)),
                    "Ordinary wrench returns the " + io + " pool to player inventory");
            player.setShiftKeyDown(false);
        }
        helper.succeed();
    }

    public void updateTagOverwritesRealManaAndPreservesAppearance(GameTestHelper helper) {
        for (IOType io : List.of(IOType.INPUT, IOType.OUTPUT)) {
            int z = io == IOType.INPUT ? 1 : 3;
            ManaPortBlockEntity source = BotaniaManaGameTestFixtures.port(helper, new BlockPos(1, 1, z), io);
            ManaPortBlockEntity target = BotaniaManaGameTestFixtures.port(helper, new BlockPos(3, 1, z), io);
            int synchronizedMana = io == IOType.INPUT ? 42_321 : 54_321;
            int previousMana = io == IOType.INPUT ? 80_000 : 123;
            source.storage().setAmount(synchronizedMana);
            target.storage().setAmount(previousMana);
            BlockPos linkedController = helper.absolutePos(new BlockPos(4, 1, 4));
            var appearance = new MachineAppearanceSpec.TextureSource(ResourceLocation.withDefaultNamespace("stone"), null);
            source.linkControllerAppearanceSource(linkedController, appearance);
            target.linkControllerAppearanceSource(helper.absolutePos(new BlockPos(4, 1, 3)),
                    new MachineAppearanceSpec.TextureSource(ResourceLocation.withDefaultNamespace("dirt"),
                            ResourceLocation.withDefaultNamespace("block/dirt")));
            var fixedKind = target.kind();
            var snapshot = target.capabilitySnapshot();
            ManaViewFacet facet = snapshot.capabilities().getFirst().facet(ManaViewFacet.class).orElseThrow();
            Object identity = target.storage().identity();
            helper.assertTrue(facet.amount() == previousMana && facet.queryIdentity() == identity,
                    io + " update target starts with a different nonzero physical inventory and live facet");
            CompoundTag update = source.getUpdateTag(helper.getLevel().registryAccess());
            helper.assertTrue(update.getCompound("mana").getInt("amount") == synchronizedMana,
                    io + " real update tag carries nonzero source inventory");

            target.handleUpdateTag(update, helper.getLevel().registryAccess());

            helper.assertTrue(target.storage().amount() == synchronizedMana,
                    io + " handleUpdateTag overwrites inventory rather than applying directional receiveMana");
            helper.assertTrue(target.capabilitySnapshot() == snapshot && facet.amount() == synchronizedMana
                            && facet.queryIdentity() == identity && target.storage().identity() == identity,
                    io + " update refreshes the existing query facet without replacing physical storage");
            helper.assertTrue(target.kind() == fixedKind && target.ioType() == io,
                    io + " update keeps the target pool kind and direction fixed");
            helper.assertTrue(target.getCurrentMana() == (io == IOType.INPUT ? 0 : synchronizedMana),
                    io + " native projection stays directional while real synchronized inventory is nonzero");
            helper.assertTrue(helper.getLevel().getBlockEntity(target.getBlockPos()) == target && !target.isRemoved(),
                    io + " update keeps the same live registered target host");
            helper.assertTrue(target.linkedControllerPositions().equals(source.linkedControllerPositions())
                            && target.appearanceSource().equals(appearance),
                    io + " update overwrites stale controller links and preserves source appearance");
            var modelData = target.getModelData();
            helper.assertTrue(appearance.equals(modelData.get(MachineModelDataKeys.PORT_TEXTURE_SOURCE))
                            && Boolean.TRUE.equals(modelData.get(MachineModelDataKeys.PORT_LINKED))
                            && modelData.get(MachineModelDataKeys.PORT_BASE_TEXTURE) == null,
                    io + " update publishes linked appearance ModelData and clears the stale texture override");
            helper.assertTrue(target.getBlockState().getAppearance(helper.getLevel(), target.getBlockPos(), Direction.UP,
                            null, null).is(Blocks.STONE),
                    io + " synchronized appearance still resolves through the independent pool block");
        }
        helper.succeed();
    }

    public void nativeSpreaderPullAndBurstCollision(GameTestHelper helper) {
        BlockPos outputPos = new BlockPos(1, 1, 1);
        BlockPos inputPos = new BlockPos(1, 1, 3);
        ManaPortBlockEntity output = BotaniaManaGameTestFixtures.port(helper, outputPos, IOType.OUTPUT);
        ManaPortBlockEntity input = BotaniaManaGameTestFixtures.port(helper, inputPos, IOType.INPUT);
        output.storage().setAmount(1_600);
        input.storage().setAmount(1_600);
        BlockPos spreaderPos = new BlockPos(2, 1, 1);
        ManaSpreaderBlockEntity spreader = BotaniaManaGameTestFixtures.spreader(helper, spreaderPos);
        helper.setBlock(spreaderPos, spreader.getBlockState().setValue(BlockStateProperties.POWERED, true));
        tickSpreader(helper, spreader);
        helper.assertTrue(spreader.getCurrentMana() > 0 && output.storage().amount() + spreader.getCurrentMana() == 1_600,
                "Real commonTick pulls only the output projection and conserves mana");
        helper.setBlock(outputPos, Blocks.AIR);
        helper.setBlock(spreaderPos, Blocks.AIR);
        spreaderPos = new BlockPos(2, 1, 3);
        spreader = BotaniaManaGameTestFixtures.spreader(helper, spreaderPos);
        helper.setBlock(spreaderPos, spreader.getBlockState().setValue(BlockStateProperties.POWERED, true));
        tickSpreader(helper, spreader);
        helper.assertTrue(spreader.getCurrentMana() == 0 && input.storage().amount() == 1_600,
                "Real commonTick cannot pull real input inventory");

        // Use the native beam simulation and native shooting routine, then tick the actual spawned burst.
        ServerPlayer player = BotaniaManaGameTestFixtures.wandPlayer(helper);
        spreader.bindTo(player, player.getMainHandItem(), helper.absolutePos(inputPos), Direction.UP);
        helper.assertTrue(helper.absolutePos(inputPos).equals(spreader.getBinding()), "Native beam simulation finds the input pool");
        int payload = spreader.getSpreaderBlock().getDefaultBurstProperties().maxMana;
        spreader.receiveMana(payload);
        helper.setBlock(spreaderPos, spreader.getBlockState().setValue(BlockStateProperties.POWERED, false));
        int before = input.storage().amount();
        tickSpreader(helper, spreader);
        var shooterIdentity = spreader.getIdentifier();
        List<ManaBurstEntity> fired = helper.getLevel().getEntitiesOfClass(ManaBurstEntity.class,
                new AABB(spreader.getBlockPos()).inflate(1), burst -> shooterIdentity.equals(burst.getShooterUUID()));
        helper.assertTrue(!fired.isEmpty(), "Real spreader shooting spawns a real mana burst");
        for (ManaBurstEntity burst : fired) tickUntilCollision(burst);
        helper.assertTrue(input.storage().amount() == before + payload && spreader.getCurrentMana() == 0,
                "Real spawned burst collision deposits into input without creating mana");
        helper.setBlock(spreaderPos, Blocks.AIR);

        output = BotaniaManaGameTestFixtures.port(helper, outputPos, IOType.OUTPUT);
        output.storage().setAmount(300);
        spreaderPos = new BlockPos(2, 1, 1);
        spreader = BotaniaManaGameTestFixtures.spreader(helper, spreaderPos);
        spreader.bindTo(player, player.getMainHandItem(), helper.absolutePos(outputPos), Direction.UP);
        spreader.receiveMana(spreader.getMaxMana());
        tickSpreader(helper, spreader);
        helper.assertTrue(spreader.getCurrentMana() == spreader.getMaxMana() && output.storage().amount() == 300,
                "Output rejects firing even when a native beam intersects it");
        ManaBurstEntity rejected = verticalBurst(helper, outputPos, 160);
        tickUntilCollision(rejected);
        helper.assertTrue(rejected.isRemoved() && output.storage().amount() == 300,
                "A real forced burst collision cannot insert into output");
        ManaBurstEntity vertical = verticalBurst(helper, inputPos, 160);
        before = input.storage().amount();
        tickUntilCollision(vertical);
        helper.assertTrue(vertical.isRemoved() && input.storage().amount() == before + 160,
                "Full interaction shape intercepts vertical bursts above the pool cavity");
        helper.succeed();
    }

    public void nativeSparkDirectionsAndLifecycle(GameTestHelper helper) {
        List<ManaSparkEntity> sparks = new ArrayList<>();
        try {
            BlockPos outputPos = new BlockPos(1, 1, 1);
            BlockPos inputPos = new BlockPos(3, 1, 1);
            BlockPos nativePos = new BlockPos(2, 1, 3);
            ManaPortBlockEntity output = BotaniaManaGameTestFixtures.port(helper, outputPos, IOType.OUTPUT);
            ManaPortBlockEntity input = BotaniaManaGameTestFixtures.port(helper, inputPos, IOType.INPUT);
            ManaPool nativePool = BotaniaManaGameTestFixtures.nativePool(helper, nativePos);
            ManaSparkEntity source = attach(helper, outputPos, sparks);
            ManaSparkEntity target = attach(helper, inputPos, sparks);
            ManaSparkEntity ordinary = attach(helper, nativePos, sparks);
            source.setUpgrade(new ItemStack(BotaniaItems.SPARK_AUGMENT_RECESSIVE));
            ordinary.setNetwork(DyeColor.CYAN);
            output.storage().setAmount(2_500);
            source.tick();
            helper.assertTrue(input.storage().amount() > 0 && input.storage().amount() + output.storage().amount() == 2_500,
                    "Native recessive spark pushes from output to input and conserves mana");
            helper.assertTrue(nativePool.getCurrentMana() == 0, "Different colored network cannot receive a transfer");
            input.storage().setAmount(input.storage().capacity());
            int outputBefore = output.storage().amount();
            source.tick();
            helper.assertTrue(output.storage().amount() == outputBefore, "Full input stops native spark extraction");
            input.storage().setAmount(0);
            output.storage().setAmount(0);
            source.tick();
            helper.assertTrue(input.storage().amount() == 0, "Empty output cannot supply sparks");

            source.setUpgrade(ItemStack.EMPTY);
            target.setUpgrade(new ItemStack(BotaniaItems.SPARK_AUGMENT_DOMINANT));
            output.storage().setAmount(2_500);
            target.tick();
            helper.assertTrue(input.storage().amount() > 0 && input.storage().amount() + output.storage().amount() == 2_500,
                    "Native dominant spark pulls from output into the real input store");
            target.setUpgrade(new ItemStack(BotaniaItems.SPARK_AUGMENT_RECESSIVE));
            source.setUpgrade(new ItemStack(BotaniaItems.SPARK_AUGMENT_DOMINANT));
            input.storage().setAmount(2_000);
            output.storage().setAmount(2_000);
            target.tick();
            source.tick();
            helper.assertTrue(input.storage().amount() == 2_000 && output.storage().amount() == 2_000,
                    "Wrong upgrade directions cannot extract input or fill output");

            source.setUpgrade(new ItemStack(BotaniaItems.SPARK_AUGMENT_RECESSIVE));
            target.setUpgrade(ItemStack.EMPTY);
            target.setNetwork(DyeColor.CYAN);
            source.tick();
            helper.assertTrue(input.storage().amount() == 2_000 && output.storage().amount() == 2_000,
                    "Recoloring invalidates old spark transfers immediately");
            target.setNetwork(DyeColor.MAGENTA);
            source.tick();
            helper.assertTrue(input.storage().amount() > 2_000, "Restored color rejoins the native network");
            target.discard();
            outputBefore = output.storage().amount();
            source.tick();
            helper.assertTrue(output.storage().amount() == outputBefore, "Removed spark no longer receives from old connections");

            ordinary.setNetwork(DyeColor.MAGENTA);
            nativePool.receiveMana(1_500);
            source.setUpgrade(new ItemStack(BotaniaItems.SPARK_AUGMENT_DOMINANT));
            source.tick();
            helper.assertTrue(nativePool.getCurrentMana() == 1_500, "Dominant output cannot pull native pool mana");
            source.setUpgrade(new ItemStack(BotaniaItems.SPARK_AUGMENT_RECESSIVE));
            output.storage().setAmount(2_000);
            source.tick();
            helper.assertTrue(nativePool.getCurrentMana() > 1_500
                            && nativePool.getCurrentMana() + output.storage().amount() == 3_500,
                    "Output supplies an ordinary native pool through the actual spark network");
            helper.setBlock(outputPos, Blocks.AIR);
            int nativeBefore = nativePool.getCurrentMana();
            source.tick();
            helper.assertTrue(source.isRemoved() && nativePool.getCurrentMana() == nativeBefore,
                    "Breaking the host kills its spark without supplying stale inventory");

            // A native source can supply input through its ordinary receiver capability too.
            ManaSparkEntity replacement = attach(helper, inputPos, sparks);
            ordinary.setUpgrade(new ItemStack(BotaniaItems.SPARK_AUGMENT_RECESSIVE));
            input.storage().setAmount(0);
            nativeBefore = nativePool.getCurrentMana();
            ordinary.tick();
            helper.assertTrue(input.storage().amount() > 0 && input.storage().amount() + nativePool.getCurrentMana() == nativeBefore,
                    "Native pool recessive network supplies the modular input pool");
            replacement.setUpgrade(new ItemStack(BotaniaItems.SPARK_AUGMENT_RECESSIVE));
            ordinary.setUpgrade(ItemStack.EMPTY);
            nativeBefore = nativePool.getCurrentMana();
            replacement.tick();
            helper.assertTrue(nativePool.getCurrentMana() == nativeBefore, "Input cannot supply an ordinary native pool");
        } finally {
            sparks.forEach(ManaSparkEntity::discard);
        }
        helper.succeed();
    }

    public void droppedItemsPermissionsAndBoundaries(GameTestHelper helper) {
        ManaPortBlockEntity input = BotaniaManaGameTestFixtures.port(helper, new BlockPos(1, 1, 1), IOType.INPUT);
        ManaPortBlockEntity output = BotaniaManaGameTestFixtures.port(helper, new BlockPos(3, 1, 1), IOType.OUTPUT);
        for (var itemType : List.of(BotaniaItems.MANA_TABLET, BotaniaItems.BAND_OF_MANA)) {
            ItemEntity entity = BotaniaManaGameTestFixtures.item(helper, new BlockPos(1, 1, 1), new ItemStack(itemType), 3_000);
            ManaItem item = ManaItem.LOOKUP.find(entity.getItem());
            input.storage().setAmount(0);
            helper.assertTrue(input.transferManaItem(entity), "Input drains the actual tablet/ring");
            helper.assertTrue(input.storage().amount() + item.getMana() == 3_000 && input.storage().amount() > 0,
                    "Finite native item drain conserves mana");
            output.storage().setAmount(3_000);
            int itemBefore = item.getMana();
            helper.assertTrue(output.transferManaItem(entity), "Output charges the actual tablet/ring");
            helper.assertTrue(output.storage().amount() + item.getMana() == 3_000 + itemBefore,
                    "Finite native item charge conserves mana");
            input.storage().setAmount(input.storage().capacity() - 7);
            itemBefore = item.getMana();
            input.transferManaItem(entity);
            helper.assertTrue(input.storage().amount() == input.storage().capacity() && item.getMana() == itemBefore - 7,
                    "Near-full input accepts only remaining space");
            itemBefore = item.getMana();
            helper.assertTrue(!input.transferManaItem(entity) && item.getMana() == itemBefore,
                    "Full input stops draining the finite item");
            item.addMana(item.getMaxMana() - item.getMana() - 9);
            output.storage().setAmount(1_000);
            output.transferManaItem(entity);
            helper.assertTrue(item.getMana() == item.getMaxMana() && output.storage().amount() == 991,
                    "Near-full native item accepts only its remaining space");

            entity.getItem().remove(BotaniaDataComponents.CAN_DRAIN_MANA_TO_POOL);
            input.storage().setAmount(0);
            helper.assertTrue(!input.transferManaItem(entity) && input.storage().amount() == 0,
                    "Actual drain permission component forbids item-to-input transfer");
            item.addMana(-100);
            helper.assertTrue(output.transferManaItem(entity), "Drain denial does not accidentally deny receiving");
            entity.getItem().set(BotaniaDataComponents.CAN_DRAIN_MANA_TO_POOL, Unit.INSTANCE);
            entity.getItem().remove(BotaniaDataComponents.CAN_RECEIVE_MANA_FROM_POOL);
            item.addMana(-100);
            int poolBefore = output.storage().amount();
            helper.assertTrue(!output.transferManaItem(entity) && output.storage().amount() == poolBefore,
                    "Actual receive permission component forbids output-to-item transfer");
            helper.assertTrue(input.transferManaItem(entity), "Receive denial does not accidentally deny draining");
            entity.discard();
        }

        ItemEntity empty = BotaniaManaGameTestFixtures.item(helper, new BlockPos(1, 1, 1), new ItemStack(BotaniaItems.MANA_TABLET), 0);
        input.storage().setAmount(2_000);
        helper.assertTrue(!input.transferManaItem(empty) && ManaItem.LOOKUP.find(empty.getItem()).getMana() == 0,
                "Input never charges empty items from its real inventory");
        output.storage().setAmount(0);
        ManaItem.LOOKUP.find(empty.getItem()).addMana(2_000);
        helper.assertTrue(!output.transferManaItem(empty) && ManaItem.LOOKUP.find(empty.getItem()).getMana() == 2_000,
                "Output never drains items even when its store is empty");
        empty.discard();

        ItemStack creative = new ItemStack(BotaniaItems.MANA_TABLET);
        ManaTabletItem.setStackCreative(creative);
        ItemEntity infinite = BotaniaManaGameTestFixtures.item(helper, new BlockPos(1, 1, 1), creative, 0);
        input.storage().setAmount(0);
        int creativeBefore = ManaItem.LOOKUP.find(creative).getMana();
        helper.assertTrue(input.transferManaItem(infinite) && input.storage().amount() > 0
                        && ManaItem.LOOKUP.find(creative).getMana() == creativeBefore,
                "Creative tablet retains native infinite supply semantics");
        output.storage().setAmount(2_000);
        helper.assertTrue(!output.transferManaItem(infinite) && output.storage().amount() == 2_000,
                "Creative tablet is already full and cannot waste output inventory");
        infinite.discard();
        helper.succeed();
    }

    @SuppressWarnings("unchecked")
    public void localTickerDoesNotInfuseOrDoubleTransfer(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        ManaPortBlockEntity input = BotaniaManaGameTestFixtures.port(helper, pos, IOType.INPUT);
        ItemEntity tablet = BotaniaManaGameTestFixtures.item(helper, pos, new ItemStack(BotaniaItems.MANA_TABLET), 4_000);
        ItemEntity lotus = BotaniaManaGameTestFixtures.item(helper, pos, new ItemStack(BotaniaItems.BLACK_LOTUS), 0);
        ItemEntity iron = BotaniaManaGameTestFixtures.item(helper, pos, new ItemStack(Items.IRON_INGOT), 0);
        ItemEntity outside = BotaniaManaGameTestFixtures.item(helper, new BlockPos(3, 1, 1), new ItemStack(BotaniaItems.MANA_TABLET), 4_000);
        BlockEntityTicker<ManaPortBlockEntity> ticker = input.getBlockState().getTicker(helper.getLevel(),
                (BlockEntityType<ManaPortBlockEntity>) input.getType());
        helper.assertTrue(ticker != null, "Independent pool exposes its server ticker");
        ticker.tick(helper.getLevel(), input.getBlockPos(), input.getBlockState(), input);
        helper.assertTrue(input.storage().amount() == ManaPoolBlockEntity.TRANSFER_BASE_RATE
                        && ManaItem.LOOKUP.find(tablet.getItem()).getMana() == 4_000 - input.storage().amount(),
                "Real block ticker routes exactly one finite item transfer");
        helper.assertTrue(lotus.isAlive() && lotus.getItem().is(BotaniaItems.BLACK_LOTUS) && lotus.getItem().getCount() == 1
                        && iron.isAlive() && iron.getItem().is(Items.IRON_INGOT) && iron.getItem().getCount() == 1,
                "Local ticker does not dissolve lotus or run native infusion recipes");
        helper.assertTrue(ManaItem.LOOKUP.find(outside.getItem()).getMana() == 4_000,
                "Local ticker does not scan neighboring pools or ground items");
        ManaPortBlockEntity output = BotaniaManaGameTestFixtures.port(helper, new BlockPos(3, 1, 1), IOType.OUTPUT);
        output.storage().setAmount(2_000);
        BlockEntityTicker<ManaPortBlockEntity> outputTicker = output.getBlockState().getTicker(helper.getLevel(),
                (BlockEntityType<ManaPortBlockEntity>) output.getType());
        helper.assertTrue(outputTicker != null, "Output pool exposes the same independent ticker route");
        outputTicker.tick(helper.getLevel(), output.getBlockPos(), output.getBlockState(), output);
        helper.assertTrue(output.storage().amount() == 2_000 - ManaPoolBlockEntity.TRANSFER_BASE_RATE
                        && output.storage().amount() + ManaItem.LOOKUP.find(outside.getItem()).getMana() == 6_000,
                "Real output ticker charges a ground item once and conserves real inventory");
        tablet.discard();
        lotus.discard();
        iron.discard();
        outside.discard();
        helper.succeed();
    }

    public void dispersiveSparkUsesOnlyOutputMana(GameTestHelper helper) {
        BlockPos inputPos = new BlockPos(1, 1, 1);
        BlockPos outputPos = new BlockPos(3, 1, 1);
        ManaPortBlockEntity input = BotaniaManaGameTestFixtures.port(helper, inputPos, IOType.INPUT);
        ManaPortBlockEntity output = BotaniaManaGameTestFixtures.port(helper, outputPos, IOType.OUTPUT);
        input.storage().setAmount(2_000);
        output.storage().setAmount(2_000);
        ServerPlayer player = BotaniaManaGameTestFixtures.wandPlayer(helper);
        player.setPos(helper.absolutePos(new BlockPos(2, 1, 1)).getCenter());
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(BotaniaItems.MANA_TABLET));
        helper.getLevel().addNewPlayer(player);
        ManaSparkEntity in = BotaniaManaGameTestFixtures.spark(helper, inputPos, new ItemStack(Items.LIME_DYE));
        ManaSparkEntity out = BotaniaManaGameTestFixtures.spark(helper, outputPos, new ItemStack(Items.LIME_DYE));
        try {
            in.setUpgrade(new ItemStack(BotaniaItems.SPARK_AUGMENT_DISPERSIVE));
            out.setUpgrade(new ItemStack(BotaniaItems.SPARK_AUGMENT_DISPERSIVE));
            ManaItem item = ManaItem.LOOKUP.find(player.getMainHandItem());
            in.tick();
            helper.assertTrue(item.getMana() == 0 && input.storage().amount() == 2_000,
                    "Actual dispersive input spark cannot charge player items");
            out.tick();
            helper.assertTrue(item.getMana() > 0 && item.getMana() + output.storage().amount() == 2_000,
                    "Actual dispersive output spark charges player items from real inventory");
        } finally {
            in.discard();
            out.discard();
            player.discard();
        }
        helper.succeed();
    }

    private static ManaSparkEntity attach(GameTestHelper helper, BlockPos pos, List<ManaSparkEntity> sparks) {
        ManaSparkEntity spark = BotaniaManaGameTestFixtures.spark(helper, pos, new ItemStack(Items.MAGENTA_DYE));
        sparks.add(spark);
        return spark;
    }

    private static void tickSpreader(GameTestHelper helper, ManaSpreaderBlockEntity spreader) {
        ManaSpreaderBlockEntity.commonTick(helper.getLevel(), spreader.getBlockPos(), spreader.getBlockState(), spreader);
    }

    private static ManaBurstEntity verticalBurst(GameTestHelper helper, BlockPos pool, int mana) {
        ManaBurstEntity burst = new ManaBurstEntity(helper.getLevel(), helper.absolutePos(pool.above(2)), 0, -90, false);
        burst.setMana(mana);
        burst.setStartingMana(mana);
        burst.setMinManaLoss(100);
        burst.setManaLossPerTick(0);
        burst.setGravity(0);
        helper.getLevel().addFreshEntity(burst);
        return burst;
    }

    private static void tickUntilCollision(ManaBurstEntity burst) {
        // Bounded entity simulation; assertions are about collision outcomes, never an exact server tick.
        for (int step = 0; step < 80 && !burst.isRemoved(); step++) burst.tick();
    }
}
