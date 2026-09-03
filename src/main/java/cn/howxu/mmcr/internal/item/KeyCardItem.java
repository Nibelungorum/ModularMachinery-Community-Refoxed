package cn.howxu.mmcr.internal.item;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.network.KeyCardBinding;
import cn.howxu.mmcr.api.network.MachineReference;
import cn.howxu.mmcr.internal.multiblock.NetworkInterfaceBindingCoordinator;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.internal.tile.NetworkInterfaceBlockEntity;
import cn.howxu.mmcr.registry.ModDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
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

import java.util.function.Consumer;
import java.util.Locale;

/** Binds a formed network interface as the source for later connections.
 * @author howxu <dev@howxu.cn>
 */
public class KeyCardItem extends Item {

    public KeyCardItem() {
        super(new Item.Properties()
                .stacksTo(1)
                .setId(ResourceKey.create(Registries.ITEM, MMCR.id("key_card"))));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        if (player == null) return InteractionResult.PASS;

        BlockEntity blockEntity = level.getBlockEntity(context.getClickedPos());
        if (!(blockEntity instanceof NetworkInterfaceBlockEntity target)) return InteractionResult.PASS;
        if (level.isClientSide()) return InteractionResult.SUCCESS;

        ItemStack stack = context.getItemInHand();
        if (player.isShiftKeyDown()) {
            select(level, player, stack, target, context.getClickedPos());
            return InteractionResult.SUCCESS;
        }

        connect(level, player, stack, target, context.getClickedPos());
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!player.isShiftKeyDown()) return InteractionResult.PASS;
        if (!level.isClientSide()) {
            player.getItemInHand(hand).remove(ModDataComponents.KEY_CARD_BINDING.get());
            player.sendSystemMessage(Component.translatable("message.mmcr.key_card.cleared"));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        KeyCardBinding binding = stack.get(ModDataComponents.KEY_CARD_BINDING.get());
        if (binding == null) {
            tooltip.accept(Component.translatable("tooltip.mmcr.key_card.not_selected").withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        tooltip.accept(Component.translatable("tooltip.mmcr.key_card.selected",
                binding.interfacePos().pos().toShortString(), binding.machine().type())
                .withStyle(ChatFormatting.GRAY));
    }

    private static void select(Level level, Player player, ItemStack stack,
                               NetworkInterfaceBlockEntity network, BlockPos interfacePos) {
        GlobalPos owner = network.owner().orElse(null);
        if (owner == null || !owner.dimension().equals(level.dimension())
                || !(level.getBlockEntity(owner.pos()) instanceof MachineControllerBlockEntity controller)
                || !controller.currentStructureSnapshot().formed()) {
            player.sendSystemMessage(Component.translatable("message.mmcr.key_card.selection_invalid"));
            return;
        }
        MachineReference machine = controller.machineReference();
        if (machine == null) {
            player.sendSystemMessage(Component.translatable("message.mmcr.key_card.selection_invalid"));
            return;
        }
        GlobalPos endpoint = GlobalPos.of(level.dimension(), interfacePos);
        stack.set(ModDataComponents.KEY_CARD_BINDING.get(), new KeyCardBinding(endpoint, machine));
        player.sendSystemMessage(Component.translatable("message.mmcr.key_card.selection_updated",
                interfacePos.toShortString()));
    }

    private static void connect(Level level, Player player, ItemStack stack,
                                NetworkInterfaceBlockEntity target, BlockPos targetPos) {
        KeyCardBinding binding = stack.get(ModDataComponents.KEY_CARD_BINDING.get());
        if (binding == null) {
            player.sendSystemMessage(Component.translatable("message.mmcr.key_card.not_selected"));
            return;
        }

        if (!(level instanceof ServerLevel serverLevel)) return;
        MachineReference targetMachine = targetMachine(level, target);
        NetworkInterfaceBindingCoordinator.ConnectionResult result = NetworkInterfaceBindingCoordinator.connect(
                serverLevel.getServer(), binding.interfacePos(), binding.machine(),
                GlobalPos.of(level.dimension(), targetPos), targetMachine);
        player.sendSystemMessage(Component.translatable("message.mmcr.key_card.result." + result.name().toLowerCase(Locale.ROOT)));
    }

    private static MachineReference targetMachine(Level level, NetworkInterfaceBlockEntity network) {
        GlobalPos owner = network.owner().orElse(null);
        if (owner == null || !owner.dimension().equals(level.dimension())
                || !(level.getBlockEntity(owner.pos()) instanceof MachineControllerBlockEntity controller)) return null;
        return controller.machineReference();
    }
}
