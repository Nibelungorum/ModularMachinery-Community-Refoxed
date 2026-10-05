package cn.howxu.mmcr.compat.botania.client;

import cn.howxu.mmcr.compat.botania.BotaniaBridge;
import cn.howxu.mmcr.compat.botania.loaded.ManaPortBlockEntity;
import cn.howxu.mmcr.registry.ModBlockEntities;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import vazkii.botania.api.block.WandHUD;
import vazkii.botania.api.neoforge.BotaniaNeoForgeCapabilities;

/** Client-only native registrations, reached through the guarded Client reflection hook.
 * @author howxu <dev@howxu.cn>
 */
public final class BotaniaClientRegistration {
    private BotaniaClientRegistration() {}

    @SuppressWarnings("unchecked")
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        for (var kind : BotaniaBridge.get().portKinds()) {
            event.registerBlockEntityRenderer((BlockEntityType<ManaPortBlockEntity>)
                    (BlockEntityType<?>) ModBlockEntities.BES.get(kind.id()).get(), ManaPortRenderer::new);
        }
    }

    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        for (var kind : BotaniaBridge.get().portKinds()) {
            event.registerBlockEntity(BotaniaNeoForgeCapabilities.getBlockApiLookupById(WandHUD.BLOCK_LOOKUP),
                    ModBlockEntities.BES.get(kind.id()).get(),
                    (entity, context) -> new ManaWandHud((ManaPortBlockEntity) entity));
        }
    }
}
