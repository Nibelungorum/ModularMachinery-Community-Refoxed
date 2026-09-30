package cn.howxu.mmcr.client;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.Machine;
import cn.howxu.mmcr.api.machine.MachineRegistry;
import cn.howxu.mmcr.client.gui.BlueprintScreen;
import cn.howxu.mmcr.registry.ModDataComponents;
import cn.howxu.mmcr.registry.ModItems;
import cn.howxu.mmcr.util.ItemSpecialOperationUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;

/**
 * Client input bridge for opening a bound blueprint preview.
 *
 * @author howxu <dev@howxu.cn>
 */
@EventBusSubscriber(modid = MMCR.MODID, value = Dist.CLIENT)
public final class BlueprintClientHandler {

    private BlueprintClientHandler() {
    }

    @SubscribeEvent
    public static void onInteractionKey(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return;

        boolean miss = minecraft.hitResult != null
                && minecraft.hitResult.getType() == HitResult.Type.MISS;
        if (!shouldHandle(event.isUseItem(), event.getHand(), ItemSpecialOperationUtil.isSpecialOperated(minecraft.player), minecraft.screen != null,
                miss, minecraft.player.getMainHandItem().is(ModItems.BLUEPRINT.get()))) return;

        ItemStack stack = minecraft.player.getMainHandItem();
        ResourceLocation machineId = stack.get(ModDataComponents.BLUEPRINT_MACHINE.get());
        Machine machine = machineId == null ? null : MachineRegistry.getMachine(machineId);
        if (machine == null) {
            minecraft.gui.getChat().addMessage(
                    Component.translatable("message.mmcr.blueprint.unbound"));
        } else {
            minecraft.setScreen(new BlueprintScreen(machine, stack));
        }
        event.setSwingHand(false);
        event.setCanceled(true);
    }

    static boolean shouldHandle(boolean useItem, InteractionHand hand, boolean crouching,
                                boolean hasScreen, boolean miss, boolean mainHandBlueprint) {
        return useItem && hand == InteractionHand.MAIN_HAND && !crouching && !hasScreen && miss && mainHandBlueprint;
    }
}
