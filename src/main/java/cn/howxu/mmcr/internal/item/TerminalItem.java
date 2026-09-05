package cn.howxu.mmcr.internal.item;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.Machine;
import cn.howxu.mmcr.api.machine.level.LevelType;
import cn.howxu.mmcr.api.machine.level.MachineLevel;
import cn.howxu.mmcr.api.machine.level.MachineLevelRegistry;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Map;
import java.util.function.Consumer;


/**
 * Multiblock build and demolish terminal.
 *
 * @author howxu <dev@howxu.cn>
 */
public class TerminalItem extends Item {

    public TerminalItem() {
        super(new Item.Properties()
                .stacksTo(1)
                .setId(ResourceKey.create(Registries.ITEM, MMCR.id("terminal"))));
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!player.isShiftKeyDown()) return InteractionResult.PASS;
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (player instanceof ServerPlayer serverPlayer) {
            TerminalService.Result result = TerminalService.clear(serverPlayer, stack);
            if (result.accepted()) serverPlayer.sendSystemMessage(Component.translatable(result.messageKey()));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null || !player.isShiftKeyDown()) return InteractionResult.PASS;
        if (context.getLevel().isClientSide()) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer serverPlayer)) return InteractionResult.SUCCESS;
        BlockEntity blockEntity = context.getLevel().getBlockEntity(context.getClickedPos());
        GlobalPos target = GlobalPos.of(context.getLevel().dimension(), context.getClickedPos());
        if (blockEntity instanceof MachineControllerBlockEntity) {
            TerminalService.Result result = TerminalService.bindController(serverPlayer, context.getItemInHand(), target);
            if (result.accepted()) serverPlayer.sendSystemMessage(Component.translatable(result.messageKey()));
            return result.accepted()
                    ? InteractionResult.SUCCESS : InteractionResult.FAIL;
        }
        if (TerminalData.from(context.getItemInHand()).inventoryMode() == TerminalInventoryMode.CONTAINER) {
            return TerminalService.bindContainer(serverPlayer, context.getItemInHand(), target).accepted()
                    ? InteractionResult.SUCCESS : InteractionResult.PASS;
        }
        return InteractionResult.PASS;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        TerminalData data = TerminalData.from(stack);

        Component modeLabel = Component.translatable(data.inventoryMode() == TerminalInventoryMode.INVENTORY
                ? "tooltip.mmcr.terminal.inventory_mode.inventory"
                : "tooltip.mmcr.terminal.inventory_mode.container");
        tooltip.accept(Component.translatable("tooltip.mmcr.terminal.mode", modeLabel)
                .withStyle(ChatFormatting.GRAY));

        appendController(data.controller(), context.level(), tooltip);
        appendContainer(data, context.level(), tooltip);
        appendLevels(data.selectedLevels(), tooltip);

        tooltip.accept(Component.translatable("tooltip.mmcr.terminal.stage", Integer.toString(data.stage()))
                .withStyle(ChatFormatting.GRAY));

        Component previewState = Component.translatable(data.previewEnabled()
                ? "gui.mmcr.terminal.preview.on" : "gui.mmcr.terminal.preview.off");
        Component layerLabel = data.previewLayer() == Integer.MAX_VALUE
                ? Component.translatable("gui.mmcr.terminal.all")
                : Component.literal(Integer.toString(data.previewLayer()));
        tooltip.accept(Component.translatable("tooltip.mmcr.terminal.preview", previewState.copy().append(" / ").append(layerLabel))
                .withStyle(ChatFormatting.GRAY));
    }

    private static void appendController(GlobalPos controller, Level level, Consumer<Component> tooltip) {
        if (controller == null) {
            tooltip.accept(Component.translatable("tooltip.mmcr.terminal.unbound")
                    .withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        Component value = resolveControllerValue(controller, level);
        tooltip.accept(Component.translatable("tooltip.mmcr.terminal.controller", value,
                        controller.dimension().identifier().toString())
                .withStyle(ChatFormatting.GRAY));
    }

    private static Component resolveControllerValue(GlobalPos controller, Level level) {
        Component blockEntityName = lookupControllerMachineName(controller, level);
        if (blockEntityName != null) return blockEntityName;
        return Component.literal(controller.pos().toShortString());
    }

    private static Component lookupControllerMachineName(GlobalPos controller, Level level) {
        if (level == null || !level.dimension().equals(controller.dimension()) || !level.hasChunkAt(controller.pos())) {
            return null;
        }
        BlockEntity be = level.getBlockEntity(controller.pos());
        if (!(be instanceof MachineControllerBlockEntity controllerBE)) return null;
        Machine machine = controllerBE.boundMachine().orElse(null);
        return machine == null ? null : machine.displayName();
    }

    private static void appendContainer(TerminalData data, Level level, Consumer<Component> tooltip) {
        if (data.inventoryMode() != TerminalInventoryMode.CONTAINER || data.container() == null) return;
        GlobalPos container = data.container();
        Component value = resolveContainerValue(container, level);
        tooltip.accept(Component.translatable("tooltip.mmcr.terminal.container", value,
                        container.dimension().identifier().toString())
                .withStyle(ChatFormatting.GRAY));
    }

    private static Component resolveContainerValue(GlobalPos container, Level level) {
        BlockPos pos = container.pos();
        if (level == null || !level.dimension().equals(container.dimension()) || !level.hasChunkAt(pos)) {
            return Component.literal(pos.toShortString());
        }
        BlockState state = level.getBlockState(pos);
        Component name = state.getBlock().getName();
        return Component.literal(name.getString() + " @ " + pos.toShortString());
    }

    private static void appendLevels(Map<Identifier, Identifier> selectedLevels, Consumer<Component> tooltip) {
        for (Map.Entry<Identifier, Identifier> entry : selectedLevels.entrySet()) {
            Identifier typeId = entry.getKey();
            Identifier levelId = entry.getValue();
            MachineLevel levelEntry = MachineLevelRegistry.getLevel(levelId);
            if (levelEntry == null || !levelEntry.typeId().equals(typeId)) continue;
            LevelType type = MachineLevelRegistry.getType(typeId);
            Component typeName = type == null ? Component.literal(typeId.toString()) : type.displayName();
            Component levelName = levelEntry.statePredicate().preferredState()
                    .map(state -> (Component) state.getBlock().getName())
                    .orElseGet(() -> levelEntry.representative().getHoverName());
            tooltip.accept(Component.translatable("tooltip.mmcr.terminal.level", typeName, levelName)
                    .withStyle(ChatFormatting.GRAY));
        }
    }
}
