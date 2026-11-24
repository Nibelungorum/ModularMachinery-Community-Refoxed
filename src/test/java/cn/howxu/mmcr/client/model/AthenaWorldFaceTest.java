package cn.howxu.mmcr.client.model;

import cn.howxu.mmcr.compat.athena.loaded.LoadedAthenaModelBridge;
import cn.howxu.mmcr.test.TestBootstrap;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import earth.terrarium.athena.api.client.neoforge.AthenaBakedModel;
import earth.terrarium.athena.impl.client.models.ConnectedBlockModel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceMetadata;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.List;

import static cn.howxu.mmcr.client.model.DynamicOverlayModelLoaderTest.assertUv;
import static cn.howxu.mmcr.client.model.DynamicOverlayModelLoaderTest.position;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises Athena 4.0's five-texture CTM baker, including its single-tile and quarter-tile faces.
 *
 * @author HowXu {@literal <dev@howxu.cn>}
 */
class AthenaWorldFaceTest {
    @BeforeAll
    static void bootstrap() throws Exception { TestBootstrap.bootstrap(); }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void neighbor_selected_tiles_sample_one_panel(boolean plane) {
        var json = JsonParser.parseString("""
                {"ctm_textures":{"particle":"mmcr:block/p","center":"mmcr:block/c","vertical":"mmcr:block/v",
                                 "horizontal":"mmcr:block/h","empty":"mmcr:block/e"}}
                """).getAsJsonObject();
        json.addProperty("render_type", plane ? "translucent" : "solid");
        var sprites = new HashMap<ResourceLocation, TextureAtlasSprite>();
        try {
            var source = new AthenaBakedModel(ConnectedBlockModel.FACTORY.create(json).get(),
                    m -> sprites.computeIfAbsent(m.texture(), AthenaWorldFaceTest::sprite));
            var panel = sprites.computeIfAbsent(DynamicOverlayBakedModel.defaultPortOverlayTexture(), AthenaWorldFaceTest::sprite);
            var model = new DynamicOverlayModelLoader.DynamicModel(DynamicOverlayBakedModel.Kind.PORT, null,
                    panel, id -> panel, ItemTransforms.NO_TRANSFORMS, ItemOverrides.EMPTY, null);
            var state = Blocks.IRON_BLOCK.defaultBlockState();
            var pos = BlockPos.ZERO;
            var level = (BlockAndTintGetter) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{BlockAndTintGetter.class},
                    (p, method, args) -> {
                        if (!method.getName().equals("getBlockState")) throw new AssertionError(method);
                        var other = (BlockPos) args[0];
                        return other.equals(pos) || other.equals(pos.east()) || plane && other.getY() == 0 ? state : Blocks.AIR.defaultBlockState();
                    });
            var sourceData = new LoadedAthenaModelBridge().modelData(source, level, pos, state, ModelData.EMPTY);
            var data = DynamicOverlayModelLoader.DynamicModel.withCtmSource(source, level, pos, state, ModelData.EMPTY, 11);
            var random = RandomSource.create(11);
            assertThat(model.getQuads(state, null, random, data, null)).isEmpty();
            assertThat(model.getQuads(state, Direction.EAST, random, data, null)).isEmpty();
            var sourcePass = plane ? RenderType.translucent() : RenderType.solid();
            assertThat(model.getParticleIcon(data)).isSameAs(source.getParticleIcon(sourceData));
            assertThat(model.useAmbientOcclusion(state, data, sourcePass)).isEqualTo(source.useAmbientOcclusion(state, sourceData, sourcePass));
            var panelPass = plane ? RenderType.translucent() : RenderType.cutout();
            for (Direction face : List.of(Direction.UP, Direction.DOWN)) {
                var tiles = source.getQuads(state, face, random, sourceData, sourcePass);
                var all = model.getQuads(state, face, random, data, null);
                assertThat(tiles).hasSize(plane ? 1 : 4);
                assertThat(all).hasSize(2 * tiles.size());
                assertThat(model.getQuads(state, face, random, data, panelPass))
                        .filteredOn(q -> q.getSprite() == panel).hasSize(tiles.size());
                assertThat(model.getQuads(state, face, random, data, plane ? RenderType.cutout() : RenderType.translucent())).isEmpty();
                for (int i = 0; i < tiles.size(); i++) {
                    var tile = tiles.get(i);
                    var base = all.get(2 * i);
                    var overlay = all.get(2 * i + 1);
                    assertThat(base.getDirection()).isEqualTo(face);
                    assertThat(base.getSprite()).isSameAs(tile.getSprite());
                    assertThat(base.getVertices()).containsExactly(tile.getVertices());
                    assertThat(overlay.getSprite()).isSameAs(panel);
                    assertThat(overlay.getDirection()).isEqualTo(base.getDirection());
                    assertThat(overlay.isTinted()).isFalse();
                    for (int j = 0; j < 4; j++) {
                        var point = position(base, j);
                        assertThat(position(overlay, j)).isEqualTo(point);
                        assertUv(overlay, j, face == Direction.UP ? point.x : 1 - point.x, point.z);
                    }
                }
            }
        } finally {
            sprites.values().forEach(sprite -> sprite.contents().close());
        }
    }

    private static TextureAtlasSprite sprite(ResourceLocation id) {
        var image = new NativeImage(2, 2, true);
        image.setPixelRGBA(0, 0, -1);
        var contents = new SpriteContents(id, new FrameSize(2, 2), image, ResourceMetadata.EMPTY);
        return new TextureAtlasSprite(TextureAtlas.LOCATION_BLOCKS, contents, 32, 32, 4, 8) {};
    }
}
