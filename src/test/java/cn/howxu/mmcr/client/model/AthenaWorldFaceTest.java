package cn.howxu.mmcr.client.model;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.compat.athena.loaded.LoadedAthenaModelBridge;
import cn.howxu.mmcr.test.TestBootstrap;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import earth.terrarium.athena.api.client.neoforge.AthenaBakedModel;
import earth.terrarium.athena.impl.client.models.ConnectedBlockModel;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.model.data.ModelData;
import org.joml.Vector3f;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Uses Athena's real neighbor selection and baker for both CTM layouts.
 *
 * @author HowXu {@literal <dev@howxu.cn>}
 */
class AthenaWorldFaceTest {
    private static final int[] MARKS = {0xffff0000, 0, 0xff00ff00, 0xff0000ff};

    @BeforeAll
    static void bootstrap() throws Exception { TestBootstrap.bootstrap(); }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void neighbor_selected_quads_keep_geometry_and_sample_one_whole_panel(boolean fortySeven) {
        var textures = fortySeven ? "\"mmcr:block/tile_[$index]\"" : """
                {"particle":"mmcr:block/p", "center":"mmcr:block/c", "vertical":"mmcr:block/v",
                 "horizontal":"mmcr:block/h", "empty":"mmcr:block/e"}
                """;
        var json = JsonParser.parseString("{\"tint\":[128,255,255],\"ctm_textures\":" + textures + "}").getAsJsonObject();
        var sources = new HashMap<Identifier, TextureAtlasSprite>();
        try (var panel = sprite(MMCR.id("block/panel"), MARKS)) {
            var connected = ConnectedBlockModel.FACTORY.create(json).get();
            var model = new AthenaBakedModel(connected, m -> {
                var tile = sources.computeIfAbsent(m.sprite(), id -> sprite(id, -1, -1, -1, -1));
                return new Material.Baked(tile, m.sprite().getPath().matches("block/(p|tile_0)"));
            });
            var state = Blocks.IRON_BLOCK.defaultBlockState();
            var level = (BlockAndTintGetter) Proxy.newProxyInstance(getClass().getClassLoader(),
                    new Class<?>[]{BlockAndTintGetter.class}, (proxy, method, args) -> switch (method.getName()) {
                        case "getBlockState" -> BlockPos.ZERO.east().equals(args[0]) ? state : Blocks.AIR.defaultBlockState();
                        case "getModelData" -> ModelData.EMPTY;
                        default -> throw new UnsupportedOperationException(method.getName());
                    });
            var parts = new ArrayList<BlockStateModelPart>();
            assertThat(new LoadedAthenaModelBridge().collectParts(model, level, BlockPos.ZERO, state,
                    RandomSource.create(11), parts)).isTrue();
            var original = parts.getFirst();
            var decorated = new LayeredFacePart(original, null, Direction.EAST, List.of(new Material.Baked(panel, false)));
            assertThat(decorated.particleMaterial()).isSameAs(original.particleMaterial());
            assertThat(decorated.ambientOcclusion()).isEqualTo(original.ambientOcclusion());
            assertThat(decorated.getQuads(null)).isEmpty();
            assertThat(decorated.getQuads(Direction.EAST)).isEmpty(); // The adjacent casing hides this face.
            var passes = new HashSet<ChunkSectionLayer>();
            for (var face : Direction.values()) {
                if (face == Direction.EAST) continue;
                var tiles = original.getQuads(face);
                assertThat(tiles).hasSize(fortySeven ? 1 : 4);
                var result = decorated.getQuads(face);
                assertThat(result).hasSize(2 * tiles.size());
                for (int i = 0; i < tiles.size(); i++) {
                    var base = result.get(2 * i);
                    var overlay = result.get(2 * i + 1);
                    assertThat(base).isEqualTo(tiles.get(i)); // Includes CTM sprite, UVs, tint and material.
                    assertThat(base.bakedColors().color(0)).isEqualTo(0xff80ffff);
                    assertThat(overlay.bakedColors().color(0)).isEqualTo(-1);
                    assertThat(overlay.materialInfo().isTinted()).isFalse();
                    assertThat(overlay.materialInfo().sprite()).isSameAs(panel);
                    var pass = base.materialInfo().layer().translucent() ? ChunkSectionLayer.TRANSLUCENT : ChunkSectionLayer.CUTOUT;
                    assertThat(overlay.materialInfo().layer()).isEqualTo(pass);
                    passes.add(pass);
                    assertThat(overlay.direction()).isEqualTo(base.direction());
                    assertThat(overlay.bakedNormals()).isEqualTo(base.bakedNormals());
                    for (int j = 0; j < 4; j++) assertThat(overlay.position(j)).isEqualTo(base.position(j));
                    assertPanelPixels(overlay, face);
                }
            }
            assertThat(passes).containsExactlyInAnyOrder(ChunkSectionLayer.CUTOUT, ChunkSectionLayer.TRANSLUCENT);
        } finally {
            sources.values().forEach(TextureAtlasSprite::close);
        }
    }

    private static void assertPanelPixels(BakedQuad q, Direction face) {
        var sprite = q.materialInfo().sprite();
        for (float s : new float[]{.25f, .75f}) for (float t : new float[]{.25f, .75f}) {
            var point = new Vector3f(q.position0()).lerp(q.position1(), s)
                    .add(new Vector3f(q.position3()).sub(q.position0()).mul(t));
            float[] panel = DynamicOverlayModelLoader.uv(face, Direction.EAST, point);
            int expected = MARKS[(int) (2 * panel[0]) + 2 * (int) (2 * panel[1])];
            float u0 = UVPair.unpackU(q.packedUV0()), v0 = UVPair.unpackV(q.packedUV0());
            float u = u0 + s * (UVPair.unpackU(q.packedUV1()) - u0) + t * (UVPair.unpackU(q.packedUV3()) - u0);
            float v = v0 + s * (UVPair.unpackV(q.packedUV1()) - v0) + t * (UVPair.unpackV(q.packedUV3()) - v0);
            int x = (int) (2 * (u - sprite.getU0()) / (sprite.getU1() - sprite.getU0()));
            int y = (int) (2 * (v - sprite.getV0()) / (sprite.getV1() - sprite.getV0()));
            assertThat(sprite.getPixelRGBA(0, x, y)).isEqualTo(expected);
        }
    }

    private static TextureAtlasSprite sprite(Identifier id, int... colors) {
        var image = new NativeImage(2, 2, false);
        for (int i = 0; i < colors.length; i++) image.setPixel(i % 2, i / 2, colors[i]);
        var contents = new SpriteContents(id, new FrameSize(2, 2), image);
        return new TextureAtlasSprite(TextureAtlas.LOCATION_BLOCKS, contents, 32, 32, 4, 8, 0) {};
    }
}
