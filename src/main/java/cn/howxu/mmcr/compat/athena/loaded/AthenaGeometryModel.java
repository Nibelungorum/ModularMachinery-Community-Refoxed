package cn.howxu.mmcr.compat.athena.loaded;

import cn.howxu.mmcr.MMCR;
import com.google.gson.JsonParseException;
import com.mojang.serialization.JsonOps;
import earth.terrarium.athena.neoforge.client.AthenaCustomUnbakedBlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.cuboid.CuboidModel;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.DelegateUnbakedModel;

/**
 * A named model with ordinary item geometry and an Athena model for world rendering.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class AthenaGeometryModel extends DelegateUnbakedModel {
    private final AthenaCustomUnbakedBlockStateModel connected;

    private AthenaGeometryModel(CuboidModel fallback, AthenaCustomUnbakedBlockStateModel connected) {
        super(fallback);
        this.connected = connected;
    }

    public static void register(ModelEvent.RegisterLoaders event) {
        event.register(MMCR.id("athena"), (json, context) -> new AthenaGeometryModel(
                context.deserialize(json, CuboidModel.class),
                AthenaCustomUnbakedBlockStateModel.CODEC.codec().parse(JsonOps.INSTANCE, json).getOrThrow(JsonParseException::new)));
    }

    public BlockStateModel bakeBlock(ModelBaker baker) {
        return connected.bake(baker);
    }
}
