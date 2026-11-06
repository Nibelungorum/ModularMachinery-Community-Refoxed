package cn.howxu.mmcr;

import appeng.api.config.Actionable;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.api.machine.DynamicMachine;
import cn.howxu.mmcr.api.machine.MachineRegistry;
import cn.howxu.mmcr.api.recipe.MachineIngredient;
import cn.howxu.mmcr.api.recipe.MachineOutput;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.RecipeRegistry;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.tile.PatternInterfaceBlockEntity;
import cn.howxu.mmcr.internal.block.MachineControllerBlock;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.TypedEntityData;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Real removal, block-item placement and reload keep one compressed material owner.
 * @author howxu <dev@howxu.cn>
 */
public final class PendingPatternLifecycleGameTest {
    public static void ordinaryCarrierRoundTrip(GameTestHelper helper) {
        hugeReturnAndLegacyQueueHaveOneDropOwner(helper);
    }

    public static void extendedCarrierRoundTrip(GameTestHelper helper) {
        carrierRoundTrip(helper, "eae_me_extended_pattern_interface");
    }

    public static void mirrorCarrierRoundTrip(GameTestHelper helper) {
        carrierRoundTrip(helper, "eaep_me_mirror_pattern_interface");
    }

    public static void hugeReturnAndLegacyQueueHaveOneDropOwner(GameTestHelper helper) {
        carrierRoundTrip(helper, "ae2_me_pattern_interface");
    }

    private static void carrierRoundTrip(GameTestHelper helper, String kind) {
        BlockPos first = new BlockPos(0, 1, 0);
        var host = place(helper, first, kind);
        long quantity = 5L * 2_147_483_647L;
        var iron = carrierIron();
        var water = AEFluidKey.of(Fluids.WATER);
        var legacyInputs = new KeyCounter();
        legacyInputs.add(iron, 5L);
        PendingPatternQueueGameTest.read(helper, host.pendingPatterns(), PendingPatternQueueGameTest.legacyData(helper,
                new KeyCounter[]{legacyInputs}, List.of(new GenericStack(AEItemKey.of(Items.GOLD_INGOT), 1L)), 2_147_483_647L));
        host.getLogic().getReturnInv().insert(iron, quantity, Actionable.MODULATE, IActionSource.empty());
        host.getLogic().getReturnInv().insert(water, quantity, Actionable.MODULATE, IActionSource.empty());
        boolean writablePatterns = host.getLogic().getPatternInv().size() > 0;
        if (writablePatterns) host.getLogic().getPatternInv().setItemDirect(0, Items.PAPER.getDefaultInstance());
        host.linkControllerAppearance(helper.absolutePos(new BlockPos(0, 1, 3)), null);

        // Player removal can clear native contents before loot is collected; explosion/removal
        // can reach this entry twice. Neither entry may expand or duplicate the carrier balance.
        host.dropContents();
        host.dropContents();
        ItemStack carrier = destroyAndCollect(helper, first, kind, writablePatterns ? 1L : 0L);
        var data = carrier.get(DataComponents.BLOCK_ENTITY_DATA);
        helper.assertTrue(data != null && data.getUnsafe().keySet().equals(Set.of("pending_patterns", "returnInv")),
                "Carrier includes only balances, never node identity, links or physical patterns");

        BlockPos second = new BlockPos(2, 1, 0);
        var restored = placeCarrier(helper, second, carrier);
        assertBalances(helper, restored, quantity);
        helper.assertTrue(restored.getLogic().getPatternInv().isEmpty() && restored.linkedControllerPositions().isEmpty(),
                "Real block-item placement does not clone native patterns or links");
        ItemStack secondCarrier = destroyAndCollect(helper, second, kind, 0L);

        BlockPos third = new BlockPos(4, 1, 0);
        var reloaded = placeCarrier(helper, third, secondCarrier);
        var saved = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, helper.getLevel().registryAccess());
        reloaded.saveAdditional(saved);
        reloaded.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING,
                helper.getLevel().registryAccess(), saved.buildResult()));
        assertBalances(helper, reloaded, quantity);
        PendingPatternQueueGameTest.refund(helper, reloaded);
        helper.assertTrue(reloaded.pendingPatterns().isEmpty()
                        && reloaded.getLogic().getReturnInv().getAvailableStacks().get(iron) == quantity * 2L,
                "After two real removals and reload, the legacy group transfers once, without losing any huge balance");
        helper.succeed();
    }

    public static void pendingSaveAndOldSave(GameTestHelper helper) {
        var host = place(helper, BlockPos.ZERO, "ae2_me_pattern_interface");
        loadLegacy(helper, host, 1L);
        helper.assertTrue(host.itemInputStorage().size() == 0 && host.fluidInputStorage().size() == 0
                        && host.getLogic().getReturnInv().isEmpty(), "Legacy inputs are not ordinary machine inputs");
        helper.assertFalse(host.getUpdateTag(helper.getLevel().registryAccess()).contains("pending_patterns"),
                "Client block updates do not contain private material escrow");
        var saved = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, helper.getLevel().registryAccess());
        host.saveAdditional(saved);
        var restored = place(helper, new BlockPos(2, 0, 0), "ae2_me_pattern_interface");
        restored.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING,
                helper.getLevel().registryAccess(), saved.buildResult()));
        helper.assertTrue(restored.pendingPatterns().totalCopies() == 1L, "Block save retains legacy material ownership");
        PendingPatternQueueGameTest.refund(helper, restored);
        var refunded = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, helper.getLevel().registryAccess());
        restored.saveAdditional(refunded);
        restored.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING,
                helper.getLevel().registryAccess(), refunded.buildResult()));
        helper.assertTrue(restored.pendingPatterns().isEmpty()
                        && restored.getLogic().getReturnInv().getAvailableStacks().get(AEItemKey.of(Items.IRON_INGOT)) == 5L,
                "Saving and reloading a refunded group does not restore a second escrow owner");
        helper.succeed();
    }

    public static void oldCarrierOnlyRefunds(GameTestHelper helper) {
        var old = place(helper, BlockPos.ZERO, "ae2_me_pattern_interface");
        loadLegacy(helper, old, 1L);
        var output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, helper.getLevel().registryAccess());
        old.pendingPatterns().write(output, helper.getLevel().getGameTime());
        ItemStack oldCarrier = new ItemStack(ModBlocks.BLOCKS.get("ae2_me_pattern_interface").get());
        oldCarrier.set(DataComponents.BLOCK_ENTITY_DATA, TypedEntityData.of(old.getType(), output.buildResult()));
        var restored = placeCarrier(helper, new BlockPos(2, 1, 0), oldCarrier);
        restored.refundLegacyInputs();
        helper.assertTrue(restored.pendingPatterns().isEmpty()
                        && restored.getLogic().getReturnInv().getAvailableStacks().get(AEItemKey.of(Items.IRON_INGOT)) == 5L,
                "An old pending-only carrier is accepted by real placement and immediately refunds, not executes");
        helper.succeed();
    }

    public static void legacyAcceptedInputsAreRefundedNotExecuted(GameTestHelper helper) {
        var host = place(helper, new BlockPos(0, 1, 0), "ae2_me_pattern_interface");
        var machineId = MMCR.id("legacy_pattern_refund_test");
        var recipeId = MMCR.id("legacy_pattern_refund_recipe");
        var machine = new DynamicMachine(machineId, machineId.toString(), new BlockArray(Map.of(
                new BlockPos(0, 1, 0), new BlockPredicate.OfBlock(host.getBlockState().getBlock()))));
        if (!MachineRegistry.containsStatic(machineId)) MachineRegistry.register(machine);
        if (!RecipeRegistry.containsStatic(recipeId)) {
            RecipeRegistry.registerStatic(MachineRecipe.fromCanonical(recipeId, machineId, 1,
                    List.of(MachineRequirement.fromInput(new MachineIngredient.ItemIngredient(Ingredient.of(Items.IRON_INGOT), 5)),
                            MachineRequirement.itemOutput(new ItemStack(Items.GOLD_INGOT))),
                    List.of(new MachineOutput.ItemOutput(new ItemStack(Items.GOLD_INGOT), 1F)),
                    List.of(), 0, 1, false, false, false, Set.of()));
        }
        helper.setBlock(BlockPos.ZERO, ModBlocks.controllerFor(MMCR.id("test_cube")).get().defaultBlockState()
                .setValue(MachineControllerBlock.FACING, Direction.SOUTH));
        var controller = helper.getBlockEntity(BlockPos.ZERO, MachineControllerBlockEntity.class);
        controller.setMachine(machine);
        controller.requestImmediateStructureCheck();
        controller.serverTick();
        host.linkControllerAppearance(controller.getBlockPos(), null);
        ItemStack pattern = PatternDetailsHelper.encodeProcessingPattern(
                List.of(new GenericStack(AEItemKey.of(Items.IRON_INGOT), 5L)),
                List.of(new GenericStack(AEItemKey.of(Items.GOLD_INGOT), 1L)));
        host.getLogic().getPatternInv().setItemDirect(0, pattern);
        var inputs = new KeyCounter();
        inputs.add(AEItemKey.of(Items.IRON_INGOT), 5L);
        PendingPatternQueueGameTest.read(helper, host.pendingPatterns(), PendingPatternQueueGameTest.legacyData(helper,
                new KeyCounter[]{inputs}, List.of(new GenericStack(AEItemKey.of(Items.GOLD_INGOT), 1L)), 1L,
                AEItemKey.of(pattern)));
        host.refundLegacyInputs();
        var balances = host.getLogic().getReturnInv().getAvailableStacks();
        helper.assertTrue(host.pendingPatterns().isEmpty() && balances.get(AEItemKey.of(Items.IRON_INGOT)) == 5L
                        && balances.get(AEItemKey.of(Items.GOLD_INGOT)) == 0L
                        && controller.runtimeSnapshot().crafting().recipeId() == null,
                "An extant matching recipe and physical pattern cannot turn legacy accepted inputs into a new execution");
        helper.assertTrue(ItemStack.matches(host.getLogic().getPatternInv().getStackInSlot(0), pattern),
                "Refund does not remove the physical pattern");
        helper.succeed();
    }

    public static void legacyTickAttemptsOnlyOneGroup(GameTestHelper helper) {
        var host = place(helper, BlockPos.ZERO, "ae2_me_pattern_interface");
        var inputs = new KeyCounter();
        inputs.add(AEItemKey.of(Items.IRON_INGOT), 5L);
        var saved = PendingPatternQueueGameTest.legacyData(helper, new KeyCounter[]{inputs},
                List.of(new GenericStack(AEItemKey.of(Items.GOLD_INGOT), 1L)), 1L);
        var groups = saved.getCompoundOrEmpty("pending_patterns").getListOrEmpty("groups");
        var second = groups.getCompoundOrEmpty(0).copy();
        second.putLong("id", 2L);
        second.putLong("copies", 2L);
        groups.add(second);
        PendingPatternQueueGameTest.read(helper, host.pendingPatterns(), saved);
        host.refundLegacyInputs();
        host.refundLegacyInputs();
        helper.assertTrue(host.pendingPatterns().totalCopies() == 2L
                        && host.getLogic().getReturnInv().getAvailableStacks().get(AEItemKey.of(Items.IRON_INGOT)) == 5L,
                "Repeated entry within one server tick refunds at most one whole group");
        helper.succeed();
    }

    public static void unknownCarrierPreservesBlockedOwnership(GameTestHelper helper) {
        var source = place(helper, BlockPos.ZERO, "ae2_me_pattern_interface");
        loadLegacy(helper, source, 1L);
        var saved = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, helper.getLevel().registryAccess());
        source.pendingPatterns().write(saved, helper.getLevel().getGameTime());
        var payload = saved.buildResult();
        payload.getCompoundOrEmpty("pending_patterns").putInt("version", 99);
        var carrier = new ItemStack(ModBlocks.BLOCKS.get("ae2_me_pattern_interface").get());
        carrier.set(DataComponents.BLOCK_ENTITY_DATA, TypedEntityData.of(source.getType(), payload));
        var restored = placeCarrier(helper, new BlockPos(2, 1, 0), carrier);
        restored.refundLegacyInputs();
        var recollected = restored.collectComponents().get(DataComponents.BLOCK_ENTITY_DATA);
        helper.assertTrue(!restored.pendingPatterns().isEmpty() && restored.pendingPatterns().groups().isEmpty()
                        && restored.getLogic().getReturnInv().isEmpty() && recollected != null
                        && recollected.getUnsafe().get("pending_patterns").equals(payload.get("pending_patterns")),
                "Placing and recollecting a future-version carrier preserves all opaque ownership and stays blocked");
        helper.succeed();
    }

    private static ItemStack destroyAndCollect(GameTestHelper helper, BlockPos position, String kind, long patterns) {
        BlockPos absolute = helper.absolutePos(position);
        helper.assertTrue(helper.getLevel().destroyBlock(absolute, true), "Real world destruction removes the interface");
        List<ItemEntity> entities = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(absolute).inflate(1));
        var carriers = entities.stream().map(ItemEntity::getItem)
                .filter(stack -> stack.is(ModBlocks.BLOCKS.get(kind).get().asItem())).toList();
        helper.assertTrue(carriers.size() == 1 && carriers.getFirst().getCount() == 1,
                "Exactly one interface drop owns all material balances");
        helper.assertTrue(entities.stream().filter(entity -> entity.getItem().is(Items.PAPER))
                        .mapToLong(entity -> entity.getItem().getCount()).sum() == patterns
                        && entities.size() == patterns + 1L,
                "Only physical patterns drop separately: huge items and fluids never expand into loose drops");
        ItemStack carrier = carriers.getFirst().copy();
        entities.forEach(ItemEntity::discard);
        return carrier;
    }

    private static PatternInterfaceBlockEntity placeCarrier(GameTestHelper helper, BlockPos position, ItemStack carrier) {
        BlockPos absolute = helper.absolutePos(position);
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        var context = new BlockPlaceContext(helper.getLevel(), player, InteractionHand.MAIN_HAND, carrier.copy(),
                new BlockHitResult(Vec3.atCenterOf(absolute), Direction.UP, absolute, false));
        helper.assertTrue(((BlockItem) carrier.getItem()).place(context).consumesAction(),
                "The actual block-item placement restores the compressed carrier");
        return helper.getBlockEntity(position, PatternInterfaceBlockEntity.class);
    }

    private static void assertBalances(GameTestHelper helper, PatternInterfaceBlockEntity host, long quantity) {
        var balances = host.getLogic().getReturnInv().getAvailableStacks();
        helper.assertTrue(host.pendingPatterns().totalCopies() == 2_147_483_647L
                        && balances.get(carrierIron()) == quantity
                        && balances.get(AEFluidKey.of(Fluids.WATER)) == quantity,
                "Both compressed escrow and huge item/fluid return balances survive exactly");
    }

    private static PatternInterfaceBlockEntity place(GameTestHelper helper, BlockPos position, String kind) {
        helper.setBlock(position, ModBlocks.BLOCKS.get(kind).get().defaultBlockState());
        return helper.getBlockEntity(position, PatternInterfaceBlockEntity.class);
    }

    private static AEItemKey carrierIron() {
        var stack = new ItemStack(Items.IRON_INGOT);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("carrier-component-iron"));
        return AEItemKey.of(stack);
    }

    private static void loadLegacy(GameTestHelper helper, PatternInterfaceBlockEntity host, long copies) {
        var inputs = new KeyCounter();
        inputs.add(AEItemKey.of(Items.IRON_INGOT), 5L);
        PendingPatternQueueGameTest.read(helper, host.pendingPatterns(), PendingPatternQueueGameTest.legacyData(helper,
                new KeyCounter[]{inputs}, List.of(new GenericStack(AEItemKey.of(Items.GOLD_INGOT), 1L)), copies));
    }
}
