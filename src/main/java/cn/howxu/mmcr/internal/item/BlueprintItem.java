package cn.howxu.mmcr.internal.item;

import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.api.machine.Machine;
import cn.howxu.mmcr.api.machine.MachineRegistry;
import cn.howxu.mmcr.api.machine.MachineStructureStage;
import cn.howxu.mmcr.api.machine.level.MachineLevelRegistry;
import cn.howxu.mmcr.internal.assembly.MultiblockAssemblyService;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.registry.ModDataComponents;
import cn.howxu.mmcr.util.ItemSpecialOperationUtil;
import cn.howxu.mmcr.util.ReadableNumber;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Stores the material requirements for a selected machine structure.
 *
 * @author howxu <dev@howxu.cn>
 */
public class BlueprintItem extends Item {
    private static final Map<ResourceLocation, CachedRequirements> REQUIREMENT_TEXT_CACHE = new ConcurrentHashMap<>();

    public BlueprintItem(ResourceLocation id) {
        super(new Item.Properties().stacksTo(1).setId(ResourceKey.create(Registries.ITEM, id)));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        if (player == null || !ItemSpecialOperationUtil.isSpecialOperated(player)
                || !(level.getBlockEntity(context.getClickedPos()) instanceof MachineControllerBlockEntity controller)) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide()) {
            controller.boundMachine().map(Machine::registryName)
                    .ifPresent(machineId -> {
                        ItemStack stack = context.getItemInHand();
                        stack.set(ModDataComponents.BLUEPRINT_MACHINE.get(), machineId);
                        stack.remove(ModDataComponents.BLUEPRINT_STAGE.get());
                        stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
                    });
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public Component getName(ItemStack stack) {
        ResourceLocation machineId = stack.get(ModDataComponents.BLUEPRINT_MACHINE.get());
        Machine machine = machineId == null ? null : MachineRegistry.getMachine(machineId);
        return machine == null ? super.getName(stack) : machine.displayName();
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return stack.getOrDefault(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, false) || super.isFoil(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display,
            Consumer<Component> tooltip, TooltipFlag flag) {
        ResourceLocation machineId = stack.get(ModDataComponents.BLUEPRINT_MACHINE.get());
        if (machineId == null) return;
        Machine machine = MachineRegistry.getMachine(machineId);
        if (machine == null) return;
        boolean showRequirements = flag.hasShiftDown() || flag.shouldDisplayAllInformation();
        tooltip.accept(Component.translatable(showRequirements
                ? "tooltip.mmcr.blueprint.recipe_list"
                : "tooltip.mmcr.blueprint.recipe_list_collapsed").withStyle(ChatFormatting.AQUA));
        if (showRequirements) requirementText(machine).forEach(tooltip);
    }

    private static List<Component> requirementText(Machine machine) {
        ResourceLocation machineId = machine.registryName();
        CachedRequirements cached = REQUIREMENT_TEXT_CACHE.get(machineId);
        if (cached != null && cached.machine() == machine) return cached.lines();
        List<Component> lines = requirements(machine).stream()
                .map(requirement -> (Component) Component.literal(ReadableNumber.formatExact(requirement.getCount()) + " ")
                        .append(requirement.getHoverName()))
                .toList();
        REQUIREMENT_TEXT_CACHE.put(machineId, new CachedRequirements(machine, lines));
        return lines;
    }

    private static List<ItemStack> requirements(Machine machine) {
        return machine.structureStages().stream()
                .min(Comparator.comparingInt(stage -> stage.number()))
                .map(stage -> MultiblockAssemblyService.aggregateRequirements(
                        MultiblockAssemblyService.createTemplatePlacements(BlockPos.ZERO, defaultLevelPattern(stage))))
                .orElseGet(List::of);
    }

    private static BlockArray defaultLevelPattern(MachineStructureStage stage) {
        Map<BlockPos, BlockPredicate> pattern = new LinkedHashMap<>(stage.pattern().pattern());
        stage.levelSlots().forEach((position, typeId) -> MachineLevelRegistry.levelsForType(typeId).stream()
                .min(Comparator.comparingInt(level -> level.priority()))
                .ifPresent(level -> pattern.put(position, level.statePredicate())));
        return new BlockArray(pattern, stage.pattern().tagsByPosition(), stage.pattern().symbolsByPosition());
    }

    private record CachedRequirements(Machine machine, List<Component> lines) {
    }
}
