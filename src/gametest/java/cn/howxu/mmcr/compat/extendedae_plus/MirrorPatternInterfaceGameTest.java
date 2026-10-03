package cn.howxu.mmcr.compat.extendedae_plus;

import appeng.api.AECapabilities;
import appeng.api.behaviors.GenericInternalInventory;
import appeng.api.config.Actionable;
import appeng.api.config.LockCraftingMode;
import appeng.api.config.Settings;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.ids.AEComponents;
import appeng.api.networking.GridHelper;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.blockentity.networking.CreativeEnergyCellBlockEntity;
import appeng.blockentity.storage.MEChestBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.helpers.patternprovider.PatternProviderLogic;
import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.api.machine.DynamicMachine;
import cn.howxu.mmcr.api.machine.MachineRegistry;
import cn.howxu.mmcr.api.machine.definition.InterfacePredicates;
import cn.howxu.mmcr.api.machine.definition.MachineIoView;
import cn.howxu.mmcr.api.machine.definition.PatternBuilder;
import cn.howxu.mmcr.api.recipe.MachineIngredient;
import cn.howxu.mmcr.api.recipe.MachineOutput;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.RecipeRegistry;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.compat.appliedenergistics2.AE2Bridge;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.tile.PatternInterfaceBlockEntity;
import cn.howxu.mmcr.compat.extendedae_plus.loaded.MirrorPatternInterfaceLogic;
import cn.howxu.mmcr.compat.kubejs.KubeJSInterfaceHelpers;
import cn.howxu.mmcr.internal.api.facade.structure.StructureAdapters;
import cn.howxu.mmcr.internal.block.MachineControllerBlock;
import cn.howxu.mmcr.internal.registration.MachineDefinitionConverter;
import cn.howxu.mmcr.internal.tile.ItemBusBlockEntity;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.publicapi.Structures;
import cn.howxu.mmcr.publicapi.structure.BlockConditions;
import cn.howxu.mmcr.registry.ModBlocks;
import com.extendedae_plus.content.ae2.MirrorPatternProviderBlockEntity;
import com.extendedae_plus.content.ae2.MirrorPatternProviderBlockEntity.MasterLocation;
import com.extendedae_plus.init.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Integrated mirror lifecycle, native binding tool and independent MMCR dispatch coverage.
 *
 * @author howxu <dev@howxu.cn>
 */
public class MirrorPatternInterfaceGameTest {
    private static final String MIRROR = "eaep_me_mirror_pattern_interface";
    private static final String ORDINARY = "ae2_me_pattern_interface";
    private static final String EXTENDED = "eae_me_extended_pattern_interface";

    public void syncAndPersistence(GameTestHelper helper) {
        BlockPos ordinaryPos = new BlockPos(0, 1, 0);
        BlockPos extendedPos = new BlockPos(2, 1, 0);
        BlockPos mirrorPos = new BlockPos(4, 1, 0);
        BlockPos restoredPos = new BlockPos(6, 1, 0);
        BlockPos otherMirrorPos = new BlockPos(8, 1, 0);
        BlockPos invalidPos = new BlockPos(10, 1, 0);
        BlockPos nativeProviderPos = new BlockPos(12, 1, 0);
        placePort(helper, ordinaryPos, ORDINARY);
        placePort(helper, extendedPos, EXTENDED);
        placePort(helper, mirrorPos, MIRROR);
        placePort(helper, otherMirrorPos, MIRROR);
        helper.setBlock(invalidPos, Blocks.CHEST);
        helper.setBlock(nativeProviderPos, AEBlocks.PATTERN_PROVIDER.block());

        ItemStack ordinaryPattern = pattern(Items.GOLD_INGOT);
        ItemStack extendedPattern = pattern(Items.DIAMOND);
        ItemStack updatedPattern = pattern(Items.EMERALD);
        GenericStack returnedMaterial = new GenericStack(AEItemKey.of(Items.COPPER_INGOT), 3L);
        CompoundTag ordinarySaved = new CompoundTag();
        helper.startSequence().thenWaitUntil(() -> {
            assertNodesReady(helper, ordinaryPos, extendedPos, mirrorPos, otherMirrorPos);
        }).thenExecute(() -> {
            PatternInterfaceBlockEntity ordinary = port(helper, ordinaryPos);
            PatternInterfaceBlockEntity extended = port(helper, extendedPos);
            MirrorPatternInterfaceLogic mirror = mirror(helper, mirrorPos);
            ordinary.getLogic().getPatternInv().setItemDirect(0, ordinaryPattern);
            ordinary.saveAdditional(ordinarySaved, helper.getLevel().registryAccess());
            extended.getLogic().getPatternInv().setItemDirect(0, ordinaryPattern.copy());
            extended.getLogic().getPatternInv().setItemDirect(35, extendedPattern);
            extended.getLogic().setPriority(19);
            extended.getLogic().getConfigManager().putSetting(Settings.LOCK_CRAFTING_MODE,
                    LockCraftingMode.LOCK_UNTIL_RESULT);
            mirror.getReturnInv().setStack(0, returnedMaterial);

            helper.assertTrue(mirror.bindToMaster(master(helper, extendedPos)), "Extended MMCR source binds");
            assertPatterns(helper, mirror, ordinaryPattern, extendedPattern);
            helper.assertTrue(mirror.getPriority() == extended.getLogic().getPriority()
                            && mirror.getConfigManager().getSetting(Settings.LOCK_CRAFTING_MODE)
                            == LockCraftingMode.LOCK_UNTIL_RESULT,
                    "Mirror follows source priority and shared native settings");
            extended.getLogic().getPatternInv().setItemDirect(0, ItemStack.EMPTY);
            extended.getLogic().getPatternInv().setItemDirect(35, updatedPattern);
        }).thenWaitUntil(() -> {
            MirrorPatternInterfaceLogic mirror = mirror(helper, mirrorPos);
            mirror.serverTick(helper.getLevel());
            assertPatterns(helper, mirror, updatedPattern);
        }).thenExecute(() -> {
            MirrorPatternInterfaceLogic mirror = mirror(helper, mirrorPos);
            helper.assertTrue(mirror.bindToMaster(master(helper, ordinaryPos)), "Ordinary MMCR source rebinds");
            assertPatterns(helper, mirror, ordinaryPattern);
            port(helper, ordinaryPos).getLogic().getPatternInv().setItemDirect(0, updatedPattern.copy());
        }).thenWaitUntil(() -> {
            MirrorPatternInterfaceLogic mirror = mirror(helper, mirrorPos);
            mirror.serverTick(helper.getLevel());
            assertPatterns(helper, mirror, updatedPattern);
        }).thenExecute(() -> {
            PatternInterfaceBlockEntity ordinary = port(helper, ordinaryPos);
            ordinary.loadWithComponents(ordinarySaved, helper.getLevel().registryAccess());
            helper.assertTrue(port(helper, ordinaryPos) == ordinary, "NBT reload keeps the same source instance");
            helper.assertTrue(ItemStack.matches(ordinary.getLogic().getPatternInv().getStackInSlot(0), ordinaryPattern),
                    "NBT reload replaces the source template without an inventory edit");
        }).thenWaitUntil(() -> {
            MirrorPatternInterfaceLogic mirror = mirror(helper, mirrorPos);
            mirror.serverTick(helper.getLevel());
            assertPatterns(helper, mirror, ordinaryPattern);
        }).thenExecute(() -> {
            PatternProviderLogic ordinary = port(helper, ordinaryPos).getLogic();
            ordinary.setPriority(23);
            ordinary.getConfigManager().putSetting(Settings.LOCK_CRAFTING_MODE, LockCraftingMode.LOCK_UNTIL_RESULT);
        }).thenWaitUntil(() -> {
            MirrorPatternInterfaceLogic mirror = mirror(helper, mirrorPos);
            mirror.serverTick(helper.getLevel());
            helper.assertTrue(mirror.getPriority() == 23 && mirror.getConfigManager().getSetting(Settings.LOCK_CRAFTING_MODE)
                            == LockCraftingMode.LOCK_UNTIL_RESULT,
                    "Shared settings alone synchronize through the unchanged-pattern cache");
            assertPatterns(helper, mirror, ordinaryPattern);
        }).thenExecute(() -> {
            PatternInterfaceBlockEntity previous = port(helper, ordinaryPos);
            helper.setBlock(ordinaryPos, Blocks.AIR);
            placePort(helper, ordinaryPos, ORDINARY);
            PatternInterfaceBlockEntity replacement = port(helper, ordinaryPos);
            helper.assertTrue(replacement != previous, "Replacement at the same coordinates creates a new source instance");
            replacement.getLogic().getPatternInv().setItemDirect(0, extendedPattern.copy());
            replacement.getLogic().getReturnInv().setStack(0, new GenericStack(AEItemKey.of(Items.IRON_INGOT), 5L));
        }).thenWaitUntil(() -> {
            assertNodesReady(helper, ordinaryPos);
            MirrorPatternInterfaceLogic mirror = mirror(helper, mirrorPos);
            mirror.serverTick(helper.getLevel());
            assertPatterns(helper, mirror, extendedPattern);
        }).thenExecute(() -> {
            MirrorPatternInterfaceLogic mirror = mirror(helper, mirrorPos);
            for (BlockPos rejected : List.of(mirrorPos, otherMirrorPos, invalidPos, nativeProviderPos)) {
                helper.assertTrue(!mirror.bindToMaster(master(helper, rejected)), "Loaded illegal master is rejected");
            }
            helper.assertTrue(!mirror.bindToMaster(new MasterLocation(helper.getLevel().dimension(),
                            helper.absolutePos(ordinaryPos), Direction.UP)), "Block sources reject part-side bindings");
            helper.assertTrue(mirror.hasMasterBinding(), "Rejected bindings preserve the existing master");
            assertPatterns(helper, mirror, extendedPattern);
            ItemStack remainder = mirror.getPatternInv().addItems(ordinaryPattern.copy());
            helper.assertTrue(ItemStack.matches(remainder, ordinaryPattern), "Public pattern inventory rejects insertion");
            mirror.getPatternInv().clear();
            assertPatterns(helper, mirror, extendedPattern);
            helper.assertTrue(returnedMaterial.equals(mirror.getReturnInv().getStack(0)),
                    "Rebind and rejected edits preserve real returned material");
            assertReadOnlySettings(helper, mirrorPos, extendedPos, extendedPattern);
            assertPortApis(helper, mirrorPos, returnedMaterial);

            PatternInterfaceBlockEntity host = port(helper, mirrorPos);
            CompoundTag saved = host.saveWithoutMetadata(helper.getLevel().registryAccess());
            CompoundTag unavailable = saved.copy();
            unavailable.getCompound("mmcr_mirror_master").putString("dimension", "mmcr:test_unavailable_dimension");
            helper.assertTrue(mirror.bindToMaster(master(helper, extendedPos)), "Different source changes the live decode cache");
            assertPatterns(helper, mirror, updatedPattern);
            host.loadWithComponents(unavailable, helper.getLevel().registryAccess());
            helper.assertTrue(port(helper, mirrorPos) == host && host.getLogic() == mirror,
                    "Mirror NBT reload retains the initialized host and logic instances");
            helper.assertTrue(mirror.hasMasterBinding(), "Inaccessible-dimension NBT keeps its binding");
            mirror.serverTick(helper.getLevel());
            assertPatterns(helper, mirror, extendedPattern);
            helper.assertTrue(returnedMaterial.equals(mirror.getReturnInv().getStack(0)),
                    "Inaccessible-source restore preserves its own returned material");
            placePort(helper, restoredPos, MIRROR);
            port(helper, restoredPos).loadWithComponents(saved, helper.getLevel().registryAccess());
            helper.assertTrue(mirror(helper, restoredPos).hasMasterBinding(), "NBT restores the source coordinates");
        }).thenWaitUntil(() -> {
            assertNodesReady(helper, restoredPos);
            mirror(helper, restoredPos).serverTick(helper.getLevel());
            assertPatterns(helper, mirror(helper, restoredPos), extendedPattern);
            mirror(helper, mirrorPos).serverTick(helper.getLevel());
            helper.assertTrue(mirror(helper, mirrorPos).hasMasterBinding(), "Unavailable source does not erase the saved binding");
            assertPatterns(helper, mirror(helper, mirrorPos), extendedPattern);
        }).thenExecute(() -> {
            MirrorPatternInterfaceLogic restored = mirror(helper, restoredPos);
            helper.assertTrue(returnedMaterial.equals(restored.getReturnInv().getStack(0)),
                    "NBT restores the mirror's own returned material");
            MirrorPatternInterfaceLogic broken = mirror(helper, otherMirrorPos);
            helper.assertTrue(broken.bindToMaster(master(helper, ordinaryPos)), "Drop fixture copies the source pattern");
            broken.getReturnInv().setStack(0, returnedMaterial);
            helper.assertTrue(helper.getLevel().destroyBlock(helper.absolutePos(otherMirrorPos), true),
                    "Real block destruction invokes mirror contents and block loot");
            List<ItemEntity> drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class,
                    new AABB(helper.absolutePos(otherMirrorPos)).inflate(0.5));
            helper.assertTrue(drops.stream().noneMatch(entity -> entity.getItem().is(AEItems.PROCESSING_PATTERN.asItem())),
                    "Real block destruction never drops a copied processing pattern");
            helper.assertTrue(drops.stream().filter(entity -> entity.getItem().is(Items.COPPER_INGOT))
                            .mapToInt(entity -> entity.getItem().getCount()).sum() == returnedMaterial.amount(),
                    "Real block destruction returns exactly its own copper material");
            helper.assertTrue(ItemStack.matches(port(helper, ordinaryPos).getLogic().getPatternInv().getStackInSlot(0),
                            extendedPattern), "Breaking the mirror preserves the real source pattern");
            assertPatterns(helper, restored, extendedPattern);
            helper.assertTrue(restored.unbindFromMaster(), "Restored binding can be removed");
            helper.assertTrue(!restored.hasMasterBinding(), "Unbind clears the restored master");
            assertPatterns(helper, restored);
            helper.assertTrue(returnedMaterial.equals(restored.getReturnInv().getStack(0)),
                    "Unbind retains real returned material");
            helper.assertTrue(restored.bindToMaster(master(helper, ordinaryPos)), "Restored mirror can bind again");
            helper.assertTrue(mirror(helper, mirrorPos).bindToMaster(master(helper, ordinaryPos)),
                    "Unavailable mirror can reconnect to the live source");
            helper.setBlock(ordinaryPos, Blocks.AIR);
        }).thenWaitUntil(() -> {
            for (BlockPos pos : List.of(mirrorPos, restoredPos)) {
                MirrorPatternInterfaceLogic logic = mirror(helper, pos);
                logic.serverTick(helper.getLevel());
                helper.assertTrue(!logic.hasMasterBinding(), "Removing a loaded source clears its binding");
                assertPatterns(helper, logic);
                helper.assertTrue(returnedMaterial.equals(logic.getReturnInv().getStack(0)),
                        "Source removal only cleans copied state, preserving real material");
            }
        }).thenSucceed();
    }

    public void nativeToolBinding(GameTestHelper helper) {
        BlockPos ordinaryPos = new BlockPos(0, 1, 0);
        BlockPos extendedPos = new BlockPos(0, 1, 2);
        BlockPos firstPos = new BlockPos(2, 1, 0);
        BlockPos nativePos = new BlockPos(3, 1, 0);
        BlockPos lastPos = new BlockPos(4, 1, 0);
        BlockPos outsidePos = new BlockPos(6, 1, 0);
        placePort(helper, ordinaryPos, ORDINARY);
        placePort(helper, extendedPos, EXTENDED);
        for (BlockPos pos : List.of(firstPos, lastPos, outsidePos)) placePort(helper, pos, MIRROR);
        helper.setBlock(nativePos, com.extendedae_plus.init.ModBlocks.MIRROR_PATTERN_PROVIDER_BLOCK.get());

        helper.startSequence().thenWaitUntil(() -> {
            assertNodesReady(helper, ordinaryPos, extendedPos, firstPos, lastPos, outsidePos);
            MirrorPatternProviderBlockEntity nativeMirror = helper.getBlockEntity(nativePos);
            helper.assertTrue(nativeMirror.getMainNode().getNode() != null, "Native mirror node initializes");
        }).thenExecute(() -> {
            ItemStack ordinaryPattern = pattern(Items.GOLD_INGOT);
            ItemStack extendedPattern = pattern(Items.DIAMOND);
            port(helper, ordinaryPos).getLogic().getPatternInv().setItemDirect(0, ordinaryPattern);
            port(helper, extendedPos).getLogic().getPatternInv().setItemDirect(35, extendedPattern);
            Player player = helper.makeMockPlayer(GameType.CREATIVE);
            ItemStack tool = new ItemStack(ModItems.MIRROR_PATTERN_BINDING_TOOL.get());
            player.setItemInHand(InteractionHand.MAIN_HAND, tool);
            useTool(helper, player, ordinaryPos, true);
            useTool(helper, player, firstPos, false);
            helper.assertTrue(mirror(helper, firstPos).hasMasterBinding(), "Native tool binds one MMCR mirror");
            assertPatterns(helper, mirror(helper, firstPos), ordinaryPattern);
            useTool(helper, player, firstPos, false);
            helper.assertTrue(!mirror(helper, firstPos).hasMasterBinding(), "Second normal tool use unbinds");
            assertPatterns(helper, mirror(helper, firstPos));

            useTool(helper, player, extendedPos, true);
            useTool(helper, player, firstPos, true);
            useTool(helper, player, lastPos, true);
            for (BlockPos pos : List.of(firstPos, lastPos)) {
                helper.assertTrue(mirror(helper, pos).hasMasterBinding(), "Range binds every MMCR mirror");
                assertPatterns(helper, mirror(helper, pos), extendedPattern);
            }
            MirrorPatternProviderBlockEntity nativeMirror = helper.getBlockEntity(nativePos);
            helper.assertTrue(nativeMirror.hasMasterBinding(), "Mixed range also binds the native EAEP mirror");
            assertPatterns(helper, nativeMirror.getLogic(), extendedPattern);
            helper.assertTrue(!mirror(helper, outsidePos).hasMasterBinding(), "Range does not bind outside mirrors");
            helper.assertTrue(!nativeMirror.bindToMaster(master(helper, firstPos)),
                    "Native EAEP mirror rejects an MMCR mirror as its master");
            assertPatterns(helper, nativeMirror.getLogic(), extendedPattern);
            helper.assertTrue(!mirror(helper, outsidePos).bindToMaster(master(helper, nativePos)),
                    "MMCR mirror rejects a native mirror as its master");

            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.MIRROR_PATTERN_BINDING_TOOL.get()));
            useTool(helper, player, firstPos, true);
            useTool(helper, player, outsidePos, false);
            helper.assertTrue(!mirror(helper, outsidePos).hasMasterBinding(),
                    "Native tool cannot select an MMCR mirror as a source");
        }).thenSucceed();
    }

    public void dispatchesOwnController(GameTestHelper helper) {
        BlockPos mirrorPos = new BlockPos(1, 2, 0);
        BlockPos controllerPos = new BlockPos(1, 1, 0);
        BlockPos inputPos = new BlockPos(1, 0, 0);
        BlockPos sourcePos = new BlockPos(7, 2, 0);
        BlockPos sourceControllerPos = new BlockPos(7, 1, 0);
        BlockPos sourceInputPos = new BlockPos(7, 0, 0);
        BlockPos storagePos = new BlockPos(4, 0, 0);
        BlockPos energyPos = new BlockPos(4, 0, 2);
        placePort(helper, mirrorPos, MIRROR);
        placePort(helper, sourcePos, ORDINARY);
        placePort(helper, inputPos, "item_input_bus");
        placePort(helper, sourceInputPos, "item_input_bus");
        for (BlockPos pos : List.of(controllerPos, sourceControllerPos)) {
            helper.setBlock(pos, ModBlocks.controllerFor(MMCR.id("test_cube")).get().defaultBlockState()
                    .setValue(MachineControllerBlock.FACING, Direction.SOUTH));
        }
        helper.setBlock(storagePos, AEBlocks.ME_CHEST.block());
        helper.setBlock(energyPos, AEBlocks.CREATIVE_ENERGY_CELL.block());
        MachineControllerBlockEntity controller = helper.getBlockEntity(controllerPos);
        MachineControllerBlockEntity sourceController = helper.getBlockEntity(sourceControllerPos);
        ResourceLocation recipeId = configureMachine(controller, MIRROR, "eaep_mirror_dispatch");
        configureMachine(sourceController, ORDINARY, "eaep_mirror_dispatch_source");
        MEChestBlockEntity storage = helper.getBlockEntity(storagePos);
        storage.setCell(AEItems.ITEM_CELL_1K.stack());

        helper.startSequence().thenWaitUntil(() -> {
            assertNodesReady(helper, mirrorPos, sourcePos);
            CreativeEnergyCellBlockEntity energy = helper.getBlockEntity(energyPos);
            helper.assertTrue(storage.getMainNode().getNode() != null && energy.getMainNode().getNode() != null,
                    "Mirror storage and energy nodes initialize");
        }).thenExecute(() -> {
            PatternInterfaceBlockEntity mirror = port(helper, mirrorPos);
            CreativeEnergyCellBlockEntity energy = helper.getBlockEntity(energyPos);
            GridHelper.createConnection(mirror.getMainNode().getNode(), storage.getMainNode().getNode());
            GridHelper.createConnection(mirror.getMainNode().getNode(), energy.getMainNode().getNode());
            for (BlockPos pos : List.of(inputPos, sourceInputPos)) {
                ItemBusBlockEntity input = helper.getBlockEntity(pos);
                helper.assertTrue(input.nativeItemHandler().insertItem(0, new ItemStack(Items.COAL), false).isEmpty(),
                        "Ordinary input accepts the remaining recipe ingredient");
            }
            controller.requestImmediateStructureCheck();
            sourceController.requestImmediateStructureCheck();
        }).thenWaitUntil(() -> {
            helper.assertTrue(controller.structureSnapshot().formed() && sourceController.structureSnapshot().formed(),
                    "Mirror and source have independent formed MMCR machines");
            helper.assertTrue(port(helper, mirrorPos).getMainNode().isActive(), "Mirror's own ME grid is active");
        }).thenExecute(() -> {
            PatternInterfaceBlockEntity source = port(helper, sourcePos);
            PatternInterfaceBlockEntity mirror = port(helper, mirrorPos);
            helper.assertTrue(source.getMainNode().getNode().getGrid() != mirror.getMainNode().getNode().getGrid()
                            && !source.getMainNode().isActive(), "Source is disconnected from the mirror's powered grid");
            source.getLogic().getPatternInv().setItemDirect(0, pattern(Items.GOLD_INGOT));
            source.getLogic().getConfigManager().putSetting(Settings.LOCK_CRAFTING_MODE,
                    LockCraftingMode.LOCK_UNTIL_RESULT);
            helper.assertTrue(mirror(helper, mirrorPos).bindToMaster(master(helper, sourcePos)),
                    "Disconnected source supplies its processing pattern");
            KeyCounter request = new KeyCounter();
            request.add(AEItemKey.of(Items.IRON_INGOT), 1L);
            helper.assertTrue(mirror.getLogic().pushPattern(mirror.getLogic().getAvailablePatterns().getFirst(),
                            new KeyCounter[]{request}), "Native mirror logic dispatches the real MMCR recipe");
            helper.assertTrue(mirror.getLogic().getCraftingLockedReason() == LockCraftingMode.LOCK_UNTIL_RESULT,
                    "Accepted mirror dispatch takes its own native result lock");
            helper.assertTrue(recipeId.equals(controller.runtimeSnapshot().crafting().recipeId())
                            && sourceController.runtimeSnapshot().crafting().recipeId() == null,
                    "Only the mirror's linked controller starts the pattern recipe");
            helper.assertTrue(request.get(AEItemKey.of(Items.IRON_INGOT)) == 0L,
                    "Accepted dispatch consumes the requested iron");
        }).thenWaitUntil(() -> {
            helper.assertTrue(storage.getInventory().extract(AEItemKey.of(Items.GOLD_INGOT), 1L,
                            Actionable.SIMULATE, IActionSource.empty()) == 1L,
                    "Mirror machine returns its real recipe output into its own ME storage");
            helper.assertTrue(port(helper, mirrorPos).getLogic().getCraftingLockedReason() == LockCraftingMode.NONE,
                    "Mirror output releases its own native result lock");
            helper.assertTrue(sourceController.runtimeSnapshot().crafting().recipeId() == null
                            && port(helper, sourcePos).getLogic().getReturnInv().isEmpty(),
                    "Source controller and returned material remain independent");
            ItemBusBlockEntity sourceInput = helper.getBlockEntity(sourceInputPos);
            helper.assertTrue(sourceInput.nativeItemHandler().getStackInSlot(0).is(Items.COAL),
                    "Source machine never consumes its ordinary recipe ingredient");
        }).thenSucceed();
    }

    private static ResourceLocation configureMachine(MachineControllerBlockEntity controller,
                                                     String portId, String id) {
        ResourceLocation machineId = MMCR.id(id);
        ResourceLocation recipeId = MMCR.id(id + "_recipe");
        DynamicMachine machine = new DynamicMachine(machineId, id, new BlockArray(Map.of(
                new BlockPos(0, 1, 0), KubeJSInterfaceHelpers.port(portId),
                new BlockPos(0, -1, 0), KubeJSInterfaceHelpers.port("item_input_bus"))));
        if (!MachineRegistry.containsStatic(machineId)) MachineRegistry.register(machine);
        if (!RecipeRegistry.containsStatic(recipeId)) {
            RecipeRegistry.registerStatic(MachineRecipe.fromCanonical(recipeId, machineId, 1,
                    List.of(MachineRequirement.fromInput(new MachineIngredient.ItemIngredient(Ingredient.of(Items.IRON_INGOT), 1)),
                            MachineRequirement.fromInput(new MachineIngredient.ItemIngredient(Ingredient.of(Items.COAL), 1)),
                            MachineRequirement.itemOutput(new ItemStack(Items.GOLD_INGOT))),
                    List.of(new MachineOutput.ItemOutput(new ItemStack(Items.GOLD_INGOT), 1F)),
                    List.of(), 0, 1, false, false, false, Set.of()));
        }
        controller.setMachine(machine);
        controller.setStructureCheckIntervalForTesting(1);
        return recipeId;
    }

    private static void assertReadOnlySettings(GameTestHelper helper, BlockPos mirrorPos, BlockPos sourcePos,
                                               ItemStack expectedPattern) {
        PatternInterfaceBlockEntity host = port(helper, mirrorPos);
        PatternProviderLogic logic = host.getLogic();
        Player player = helper.makeMockPlayer(GameType.CREATIVE);
        player.getAbilities().instabuild = true;
        ItemStack card = AEItems.MEMORY_CARD.stack();
        DataComponentMap.Builder input = DataComponentMap.builder();
        port(helper, sourcePos).exportMemoryCardSettings(input, player);
        input.set(AEComponents.EXPORTED_SETTINGS_SOURCE, host.memoryCardSettingsSource());
        CompoundTag payload = new CompoundTag();
        payload.putString("unrelated_payload", "keep_me");
        input.set(DataComponents.CUSTOM_DATA, CustomData.of(payload));
        card.applyComponents(input.build());
        helper.assertTrue(card.has(AEComponents.EXPORTED_PATTERNS), "Prefilled card contains a real competing source template");
        ItemStack originalCard = card.copy();
        CompoundTag originalHost = host.saveWithoutMetadata(helper.getLevel().registryAccess());
        player.setItemInHand(InteractionHand.MAIN_HAND, card);
        for (boolean save : List.of(true, false)) {
            player.setShiftKeyDown(save);
            helper.assertTrue(AE2Bridge.get().useMemoryCard(card, helper.getLevel(), host.getBlockPos(), player),
                    "Mirror handles both memory-card save and load as read-only");
            helper.assertTrue(ItemStack.matches(card, originalCard), "Memory-card use preserves every original component");
            helper.assertTrue(originalHost.equals(host.saveWithoutMetadata(helper.getLevel().registryAccess())),
                    "Memory-card use preserves templates, shared settings, binding and returned material");
            assertPatterns(helper, logic, expectedPattern);
        }
        for (Runnable attempt : List.<Runnable>of(
                () -> host.importMemoryCardSettings(card.getComponents(), player),
                () -> logic.importSettings(card.getComponents(), player))) {
            attempt.run();
            helper.assertTrue(ItemStack.matches(card, originalCard), "Direct import preserves the original card components");
            helper.assertTrue(originalHost.equals(host.saveWithoutMetadata(helper.getLevel().registryAccess())),
                    "Host and logic imports cannot overwrite mirrored templates or shared settings");
            assertPatterns(helper, logic, expectedPattern);
        }
        DataComponentMap.Builder output = DataComponentMap.builder();
        output.set(DataComponents.CUSTOM_DATA, CustomData.of(payload));
        DataComponentMap originalOutput = output.build();
        host.exportMemoryCardSettings(output, player);
        helper.assertTrue(originalOutput.equals(output.build()) && output.build().get(AEComponents.EXPORTED_PATTERNS) == null,
                "Host export preserves unrelated payload and exports no templates");
        logic.exportSettings(output);
        helper.assertTrue(originalOutput.equals(output.build()) && output.build().get(AEComponents.EXPORTED_PATTERNS) == null,
                "Logic export preserves unrelated payload and exports no templates");
        helper.assertTrue(ItemStack.matches(card, originalCard), "Direct exports leave the prefilled card untouched");
        helper.assertTrue(originalHost.equals(host.saveWithoutMetadata(helper.getLevel().registryAccess())),
                "Direct exports preserve templates, shared settings, binding and returned material");
        assertPatterns(helper, logic, expectedPattern);
        helper.assertTrue(!host.isVisibleInTerminal(), "Read-only mirror is hidden from the pattern terminal");
    }

    private static void assertPortApis(GameTestHelper helper, BlockPos pos, GenericStack returnedMaterial) {
        PatternInterfaceBlockEntity host = port(helper, pos);
        BlockArray declaration = MachineDefinitionConverter.toBlockArray(PatternBuilder.pattern().layer("P", "C")
                .where('P', InterfacePredicates.anyOfPort(MMCR.id(MIRROR))).controller('C').build());
        BlockArray publicApi = MachineDefinitionConverter.toBlockArray(StructureAdapters.unwrap(Structures.pattern()
                .layer("P", "C").where('P', BlockConditions.port(MIRROR)).controller('C').build()));
        BlockPos offset = new BlockPos(0, -1, 0);
        for (BlockPredicate predicate : List.of(declaration.pattern().get(offset), publicApi.pattern().get(offset),
                KubeJSInterfaceHelpers.port(MMCR.id(MIRROR)))) {
            helper.assertTrue(predicate.matches(host.getBlockState()) && !predicate.matches(Blocks.CHEST.defaultBlockState()),
                    "Declaration, public API and KubeJS port predicates match the real mirror host");
        }
        GenericInternalInventory generic = helper.getLevel().getCapability(AECapabilities.GENERIC_INTERNAL_INV,
                host.getBlockPos(), null);
        helper.assertTrue(generic != null && returnedMaterial.equals(generic.getStack(0)),
                "Generic capability exposes the mirror's own returned material");
        for (int slot = 0; slot < generic.size(); slot++) {
            helper.assertTrue(!(generic.getKey(slot) instanceof AEItemKey item)
                            || item.getItem() != AEItems.PROCESSING_PATTERN.asItem(),
                    "Generic capability never exposes copied pattern templates");
        }
        MachineIoView view = new MachineIoView(host.capabilitySnapshot());
        helper.assertTrue(view.itemAmount(Ingredient.of(Items.COPPER_INGOT)) == 0L
                        && view.itemAmount(Ingredient.of(Items.IRON_INGOT)) == 0L
                        && view.itemAmount(Ingredient.of(AEItems.PROCESSING_PATTERN.asItem())) == 0L,
                "Input queries exclude output-only returned material, source returns and copied templates");
        helper.assertTrue(view.itemOutputCapacity(new ItemStack(Items.COPPER_INGOT)) > 0L,
                "Output queries see capacity in the mirror's own return inventory");
    }

    private static void useTool(GameTestHelper helper, Player player, BlockPos pos, boolean shift) {
        player.setShiftKeyDown(shift);
        BlockPos absolute = helper.absolutePos(pos);
        UseOnContext context = new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(absolute), Direction.UP, absolute, false));
        ItemStack tool = player.getMainHandItem();
        helper.assertTrue(tool.getItem().onItemUseFirst(tool, context).consumesAction(),
                "Native EAEP binding tool handles the selected port");
    }

    private static ItemStack pattern(Item output) {
        return PatternDetailsHelper.encodeProcessingPattern(
                List.of(new GenericStack(AEItemKey.of(Items.IRON_INGOT), 1L)),
                List.of(new GenericStack(AEItemKey.of(output), 1L)));
    }

    private static void assertPatterns(GameTestHelper helper, PatternProviderLogic logic, ItemStack... expected) {
        helper.assertTrue(logic.getAvailablePatterns().size() == expected.length,
                "Advertised patterns contain exactly the current source patterns, with no stale tail slots");
        for (ItemStack stack : expected) {
            helper.assertTrue(logic.getAvailablePatterns().stream()
                            .anyMatch(details -> details.getDefinition().equals(AEItemKey.of(stack))),
                    "Advertised pattern retains the real encoded processing definition");
        }
    }

    private static void assertNodesReady(GameTestHelper helper, BlockPos... positions) {
        for (BlockPos pos : positions) {
            helper.assertTrue(port(helper, pos).getMainNode().getNode() != null, "Pattern interface node initializes");
        }
    }

    private static MasterLocation master(GameTestHelper helper, BlockPos pos) {
        return new MasterLocation(helper.getLevel().dimension(), helper.absolutePos(pos), null);
    }

    private static void placePort(GameTestHelper helper, BlockPos pos, String id) {
        helper.setBlock(pos, ModBlocks.BLOCKS.get(id).get().defaultBlockState());
    }

    private static PatternInterfaceBlockEntity port(GameTestHelper helper, BlockPos pos) {
        return helper.getBlockEntity(pos);
    }

    private static MirrorPatternInterfaceLogic mirror(GameTestHelper helper, BlockPos pos) {
        PatternProviderLogic logic = port(helper, pos).getLogic();
        helper.assertTrue(logic instanceof MirrorPatternInterfaceLogic, "Registered mirror reuses the MMCR pattern host");
        return (MirrorPatternInterfaceLogic) logic;
    }
}
