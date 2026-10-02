package cn.howxu.mmcr.compat.create.loaded;

import cn.howxu.mmcr.registry.ModBlockEntities;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.base.ShaftRenderer;
import com.simibubi.create.content.kinetics.base.SingleAxisRotatingVisual;
import dev.engine_room.flywheel.lib.visualization.SimpleBlockEntityVisualizer;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Client-only registration for native shaft rendering with and without Flywheel.
 * @author howxu <dev@howxu.cn>
 */
@OnlyIn(Dist.CLIENT)
public final class CreateClientRegistration {
    private CreateClientRegistration() {}

    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        for (StressInterfaceKind kind : StressInterfaceKind.values()) {
            event.registerBlockEntityRenderer(type(kind), ShaftRenderer::new);
        }
    }

    public static void registerVisuals(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            for (StressInterfaceKind kind : StressInterfaceKind.values()) {
                SimpleBlockEntityVisualizer.builder(type(kind))
                        .factory(SingleAxisRotatingVisual::shaft)
                        .skipVanillaRender(entity -> true)
                        .apply();
            }
        });
    }

    @SuppressWarnings("unchecked")
    private static BlockEntityType<KineticBlockEntity> type(StressInterfaceKind kind) {
        return (BlockEntityType<KineticBlockEntity>) ModBlockEntities.BES.get(kind.id()).get();
    }
}
