package cn.howxu.mmcr.client.model;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.test.TestBootstrap;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.Transparency;
import com.mojang.serialization.JsonOps;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
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
import net.minecraft.util.TriState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3f;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

/** @author howxu <dev@howxu.cn> */
class EmissiveOverlayModelTest {
    @BeforeAll
    static void bootstrap() throws Exception {
        TestBootstrap.bootstrap();
    }

    @Test
    void nested_vanilla_model_resolves_its_own_dependencies_and_fields_are_required() {
        var codec = EmissiveOverlayModel.Unbaked.CODEC.codec();
        var json = JsonParser.parseString("""
                {"light_emission":15,"shade":false,"model":{"model":"minecraft:block/stone"}}
                """).getAsJsonObject();
        var model = codec.parse(JsonOps.INSTANCE, json).getOrThrow();
        var dependencies = new ArrayList<Identifier>();
        model.resolveDependencies(dependencies::add);
        assertThat(dependencies).containsExactly(Identifier.parse("minecraft:block/stone"));
        for (String field : List.of("light_emission", "shade", "model")) {
            var invalid = json.deepCopy();
            invalid.remove(field);
            assertThat(codec.parse(JsonOps.INSTANCE, invalid).isError()).isTrue();
        }
        for (int invalid : List.of(-1, 16)) {
            json.addProperty("light_emission", invalid);
            assertThat(codec.parse(JsonOps.INSTANCE, json).isError()).isTrue();
        }
    }

    @Test
    void only_new_parts_are_lit_and_dynamic_geometry_keeps_neighbor_uvs() {
        var id = MMCR.id("block/test_overlay");
        var contents = new SpriteContents(id, new FrameSize(1, 1), new NativeImage(1, 1, true));
        try (var sprite = new TextureAtlasSprite(TextureAtlas.LOCATION_BLOCKS, contents, 1, 1, 0, 0, 0) {}) {
            var particle = new Material.Baked(sprite, false);
            var material = BakedQuad.MaterialInfo.of(particle, Transparency.TRANSLUCENT, 3, true, 0, false);
            var isolated = part(particle, material, 1);
            var joined = part(particle, material, 11);
            var pos = new BlockPos(5, 0, 7);
            var state = Blocks.STONE.defaultBlockState();
            var random = RandomSource.create(42);
            var connected = new AtomicBoolean(false);
            BlockAndTintGetter world = (BlockAndTintGetter) Proxy.newProxyInstance(getClass().getClassLoader(),
                    new Class<?>[]{BlockAndTintGetter.class}, (proxy, method, args) -> {
                        assertThat(method.getName()).isEqualTo("getBlockState");
                        assertThat(args[0]).isEqualTo(pos.east());
                        return connected.get() ? state : Blocks.AIR.defaultBlockState();
                    });
            BlockStateModel child = new BlockStateModel() {
                public void collectParts(RandomSource rng, List<BlockStateModelPart> parts) {
                    assertThat(rng).isSameAs(random);
                    parts.add(isolated);
                }

                public void collectParts(BlockAndTintGetter level, BlockPos at, BlockState s, RandomSource rng, List<BlockStateModelPart> parts) {
                    assertThat(rng).isSameAs(random);
                    assertThat(s).isSameAs(state);
                    parts.add(level.getBlockState(at.east()) == s ? joined : isolated);
                }

                public Object createGeometryKey(BlockAndTintGetter level, BlockPos at, BlockState s, RandomSource rng) {
                    return level.getBlockState(at.east()) == s ? "joined" : null;
                }

                public Material.Baked particleMaterial() { return particle; }
                public int materialFlags() { return material.flags(); }
            };
            var model = new EmissiveOverlayModel(child, 15, false);
            for (boolean adjacent : List.of(false, true)) {
                connected.set(adjacent);
                var parts = new ArrayList<BlockStateModelPart>(List.of(isolated));
                model.collectParts(world, pos, state, random, parts);
                assertThat(parts).hasSize(2);
                assertThat(parts.getFirst()).isSameAs(isolated);
                var overlay = parts.getLast();
                var source = adjacent ? joined : isolated;
                assertThat(overlay.getQuads(Direction.WEST)).isEmpty();
                for (Direction face : new Direction[]{null, Direction.UP}) {
                    BakedQuad q = overlay.getQuads(face).getFirst();
                    BakedQuad original = source.getQuads(face).getFirst();
                    assertThat(q.direction()).isEqualTo(original.direction());
                    for (int v = 0; v < 4; v++) {
                        assertThat(q.position(v)).isSameAs(original.position(v));
                        assertThat(q.packedUV(v)).isEqualTo(original.packedUV(v));
                    }
                    assertThat(q.bakedColors()).isSameAs(original.bakedColors());
                    assertThat(q.bakedNormals()).isSameAs(original.bakedNormals());
                    assertThat(q.materialInfo()).isEqualTo(new BakedQuad.MaterialInfo(sprite, material.layer(),
                            material.itemRenderType(), material.tintIndex(), false, 15, material.ambientOcclusion()));
                    assertThat(original.materialInfo()).isSameAs(material);
                }
                assertThat(overlay.particleMaterial()).isSameAs(particle);
                assertThat(overlay.materialFlags()).isEqualTo(source.materialFlags());
                assertThat(overlay.ambientOcclusion()).isEqualTo(TriState.TRUE);
                var key = model.createGeometryKey(world, pos, state, random);
                if (adjacent) {
                    assertThat(key).isNotNull().isEqualTo(model.createGeometryKey(world, pos, state, random))
                            .isNotEqualTo(new EmissiveOverlayModel(child, 8, true).createGeometryKey(world, pos, state, random));
                } else {
                    assertThat(key).isNull();
                }
            }
            var standalone = new ArrayList<BlockStateModelPart>();
            model.collectParts(random, standalone);
            var quad = standalone.getFirst().getQuads(null).getFirst();
            assertThat(quad.packedUV0()).isEqualTo(isolated.quad.packedUV0());
            assertThat(quad.materialInfo().lightEmission()).isEqualTo(15);
        }
    }

    private static TestPart part(Material.Baked particle, BakedQuad.MaterialInfo material, int tile) {
        return new TestPart(particle, new BakedQuad(new Vector3f(), new Vector3f(0, 0, 1),
                new Vector3f(1, 0, 1), new Vector3f(1, 0, 0), tile, tile + 1, tile + 2, tile + 3,
                Direction.UP, material));
    }

    private record TestPart(Material.Baked particleMaterial, BakedQuad quad) implements BlockStateModelPart {
        public List<BakedQuad> getQuads(Direction face) { return face == null || face == Direction.UP ? List.of(quad) : List.of(); }
        public int materialFlags() { return quad.materialInfo().flags(); }
        public boolean useAmbientOcclusion() { return true; }
        public TriState ambientOcclusion() { return TriState.TRUE; }
    }
}
