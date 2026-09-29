package cn.howxu.mmcr.internal.network;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.Machine;
import cn.howxu.mmcr.api.machine.MachineRegistry;
import cn.howxu.mmcr.registry.ModDataComponents;
import cn.howxu.mmcr.registry.ModItems;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client request to persist the selected blueprint structure stage.
 * @author howxu <dev@howxu.cn>
 */
public record PktBlueprintStageUpdatePayload(int stageNumber) implements CustomPacketPayload {
    public static final Type<PktBlueprintStageUpdatePayload> TYPE = new Type<>(MMCR.id("blueprint_stage_update"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PktBlueprintStageUpdatePayload> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.VAR_INT, PktBlueprintStageUpdatePayload::stageNumber,
                    PktBlueprintStageUpdatePayload::new);

    public PktBlueprintStageUpdatePayload {
        if (stageNumber < 1) throw new IllegalArgumentException("Blueprint stage number must be positive");
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            ItemStack stack = player.getMainHandItem();
            if (!canUpdate(stack)) return;
            ResourceLocation machineId = stack.get(ModDataComponents.BLUEPRINT_MACHINE.get());
            Machine machine = machineId == null ? null : MachineRegistry.getMachine(machineId);
            if (machine == null || machine.structureStages().stream().noneMatch(stage -> stage.number() == stageNumber)) return;
            stack.set(ModDataComponents.BLUEPRINT_STAGE.get(), stageNumber);
        });
    }

    private static boolean canUpdate(ItemStack stack) {
        return stack.is(ModItems.BLUEPRINT.get())
                && stack.get(ModDataComponents.BLUEPRINT_MACHINE.get()) != null;
    }
}
