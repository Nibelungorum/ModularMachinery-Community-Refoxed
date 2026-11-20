package cn.howxu.mmcr.client.model;

import cn.howxu.mmcr.test.TestBootstrap;
import com.google.common.collect.ImmutableMap;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import earth.terrarium.athena.api.client.models.neoforge.FactoryManagerImpl;
import earth.terrarium.athena.impl.client.models.ConnectedBlockModel;
import earth.terrarium.athena.neoforge.client.AthenaNeoForgeClient;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.renderer.block.model.BlockModelDefinition;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.MultiPartBakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceMetadata;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.client.NamedRenderTypeManager;
import net.neoforged.neoforge.client.NeoForgeRenderTypes;
import net.neoforged.neoforge.client.RenderTypeGroup;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.ExtendedBlockModelDeserializer;
import net.neoforged.neoforge.client.model.IQuadTransformer;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.geometry.GeometryLoaderManager;
import net.neoforged.neoforge.client.model.geometry.IGeometryLoader;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Exercises the shipped multipart model and both optional-loader paths without a client window.
 * @author howxu <dev@howxu.cn>
 */
class EmissiveOverlayModelTest {
    private static final Path ASSETS = Path.of("example/assets/kubejs");
    private final Map<ResourceLocation, TextureAtlasSprite> sprites = new HashMap<>();

    @BeforeAll
    static void bootstrap() throws Exception {
        TestBootstrap.bootstrap();
    }

    @Test
    void rejects_incomplete_or_out_of_range_overlays_before_baking() {
        JsonDeserializationContext context = ExtendedBlockModelDeserializer.INSTANCE::fromJson;
        var valid = JsonParser.parseString("""
                {"light_emission":15,"shade":false,"model":{"parent":"minecraft:block/cube_all"}}
                """).getAsJsonObject();
        for (String field : List.of("light_emission", "shade", "model")) {
            var invalid = valid.deepCopy();
            invalid.remove(field);
            assertThatThrownBy(() -> EmissiveOverlayModel.LOADER.read(invalid, context)).isInstanceOf(JsonParseException.class);
        }
        for (int light : List.of(-1, 16)) {
            var invalid = valid.deepCopy();
            invalid.addProperty("light_emission", light);
            assertThatThrownBy(() -> EmissiveOverlayModel.LOADER.read(invalid, context)).isInstanceOf(JsonParseException.class);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void only_active_markings_are_full_bright_and_ctm_connects_across_lit_states(boolean athena) throws Exception {
        // Install the loader and render-type tables normally supplied by the client mod bus.
        var loaders = GeometryLoaderManager.class.getDeclaredField("LOADERS");
        var layers = NamedRenderTypeManager.class.getDeclaredField("RENDER_TYPES");
        loaders.setAccessible(true);
        layers.setAccessible(true);
        Object oldLoaders = loaders.get(null), oldLayers = layers.get(null);
        var ctm = ResourceLocation.parse("athena:ctm");
        boolean registerCtm = athena && FactoryManagerImpl.get(ctm) == null;
        try {
            Map<ResourceLocation, IGeometryLoader<?>> registered = new HashMap<>();
            var event = new ModelEvent.RegisterGeometryLoaders(registered);
            RuntimeMachineModelRegistry.registerGeometryLoaders(event);
            if (registerCtm) FactoryManagerImpl.register(ctm, ConnectedBlockModel.FACTORY);
            if (athena) AthenaNeoForgeClient.onRegisterGeometryLoaders(event);
            loaders.set(null, ImmutableMap.copyOf(registered));
            layers.set(null, ImmutableMap.of(ResourceLocation.withDefaultNamespace("translucent"),
                    new RenderTypeGroup(RenderType.translucent(), NeoForgeRenderTypes.ITEM_LAYERED_TRANSLUCENT.get())));

            var model = bakeCoil();
            var idle = Blocks.REDSTONE_LAMP.defaultBlockState();
            var random = RandomSource.create(42);
            for (boolean active : List.of(false, true)) {
                var state = idle.setValue(BlockStateProperties.LIT, active);
                // An exposed top face surrounded horizontally by idle members of the same block.
                var plane = (BlockAndTintGetter) Proxy.newProxyInstance(getClass().getClassLoader(),
                        new Class<?>[]{BlockAndTintGetter.class}, (p, method, args) -> {
                            if (!method.getName().equals("getBlockState")) throw new AssertionError(method);
                            var pos = (BlockPos) args[0];
                            return pos.equals(BlockPos.ZERO) ? state : pos.getY() == 0 ? idle : Blocks.AIR.defaultBlockState();
                        });
                var data = model.getModelData(plane, BlockPos.ZERO, state, ModelData.EMPTY);
                String tile = "block/machine_coil_cupronickel/" + (athena ? "empty" : "particle");
                var base = model.getQuads(state, Direction.UP, random, data, RenderType.solid());
                assertThat(base).singleElement().satisfies(q -> {
                    assertThat(q.getSprite().contents().name().getPath()).isEqualTo(tile);
                    assertThat(q.getVertices()[IQuadTransformer.UV2]).isNotEqualTo(LightTexture.FULL_BRIGHT);
                });
                var bloom = model.getQuads(state, Direction.UP, random, data, RenderType.translucent());
                if (!active) assertThat(bloom).isEmpty();
                else assertThat(bloom).singleElement().satisfies(q -> {
                    assertThat(q.getSprite().contents().name().getPath()).isEqualTo(tile + "_bloom");
                    assertThat(q.isShade()).isFalse();
                    for (int v = 0; v < 4; v++) {
                        assertThat(q.getVertices()[v * IQuadTransformer.STRIDE + IQuadTransformer.UV2]).isEqualTo(LightTexture.FULL_BRIGHT);
                    }
                });
            }
        } finally {
            loaders.set(null, oldLoaders);
            layers.set(null, oldLayers);
            if (registerCtm) FactoryManagerImpl.getTypes().remove(ctm);
            sprites.values().forEach(s -> s.contents().close());
        }
    }

    private BakedModel bakeCoil() throws IOException {
        var context = new BlockModelDefinition.Context();
        context.setDefinition(Blocks.REDSTONE_LAMP.getStateDefinition());
        try (var reader = Files.newBufferedReader(ASSETS.resolve("blockstates/machine_coil_cupronickel.json"))) {
            var parts = BlockModelDefinition.fromStream(context, reader).getMultiPart();
            var builder = new MultiPartBakedModel.Builder();
            for (var selector : parts.getSelectors()) {
                var variant = selector.getVariant().getVariants().getFirst();
                var model = loadModel(variant.getModelLocation());
                model.resolveParents(this::loadModel);
                builder.add(selector.getPredicate(context.getDefinition()), model.bake(null,
                        m -> sprites.computeIfAbsent(m.texture(), this::loadSprite), variant));
            }
            return builder.build();
        }
    }

    private BlockModel loadModel(ResourceLocation id) {
        String path = "models/" + id.getPath() + ".json";
        try (var stream = id.getNamespace().equals("minecraft") ? getClass().getResourceAsStream("/assets/minecraft/" + path)
                : Files.newInputStream(ASSETS.resolve(path))) {
            return BlockModel.fromStream(new InputStreamReader(stream, StandardCharsets.UTF_8));
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private TextureAtlasSprite loadSprite(ResourceLocation id) {
        try (var stream = Files.newInputStream(ASSETS.resolve("textures/" + id.getPath() + ".png"))) {
            var image = NativeImage.read(stream);
            var contents = new SpriteContents(id, new FrameSize(image.getWidth(), image.getHeight()), image, ResourceMetadata.EMPTY);
            return new TextureAtlasSprite(TextureAtlas.LOCATION_BLOCKS, contents, image.getWidth(), image.getHeight(), 0, 0) {};
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }
}
