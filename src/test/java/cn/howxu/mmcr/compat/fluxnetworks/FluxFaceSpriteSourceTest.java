package cn.howxu.mmcr.compat.fluxnetworks;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.test.TestBootstrap;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.SpriteLoader;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.metadata.animation.AnimationMetadataSection;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.neoforged.neoforge.client.ClientHooks;
import net.neoforged.neoforge.client.event.RegisterSpriteSourceTypesEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Stitches the actual block atlas definition with absent, animated and overridden Flux resources.
 * @author HowXu {@literal <dev@howxu.cn>}
 */
class FluxFaceSpriteSourceTest {
    private static final List<String> TEXTURES = List.of("flux_point_on", "flux_point_colour", "flux_plug_on", "flux_plug_colour");

    @Test
    void padded_faces_follow_the_current_pack_and_keep_each_animation_frame(@TempDir Path temp) throws Exception {
        TestBootstrap.bootstrap();
        var atlasFile = temp.resolve("base/assets/minecraft/atlases/blocks.json");
        Files.createDirectories(atlasFile.getParent());
        try (var definition = getClass().getResourceAsStream("/assets/minecraft/atlases/blocks.json")) {
            Files.copy(definition, atlasFile);
        }
        var types = ClientHooks.makeSpriteSourceTypesMap();
        FluxFaceSpriteSource.register(new RegisterSpriteSourceTypesEvent(types));
        try {
            for (int revision = 0; revision <= 2; revision++) {
                if (revision != 0) writeSprites(temp.resolve(revision == 1 ? "base" : "override"), revision);
                try (var resources = new MultiPackResourceManager(PackType.CLIENT_RESOURCES,
                        List.of(pack(temp, "base"), pack(temp, "override")))) {
                    var atlas = new SpriteLoader(TextureAtlas.LOCATION_BLOCKS, 1024, 0, 0).loadAndStitch(
                            resources, ResourceLocation.withDefaultNamespace("blocks"), 0, Runnable::run).join();
                    try {
                        if (revision == 0) {
                            assertThat(atlas.regions()).containsOnlyKeys(MissingTextureAtlasSprite.getLocation());
                        } else {
                            for (String name : TEXTURES) {
                                var id = MMCR.id("block/fluxnetworks/" + name);
                                assertThat(atlas.regions()).containsKey(id);
                                assertFrames(atlas.regions().get(id).contents(), name, revision);
                            }
                        }
                    } finally {
                        atlas.regions().values().forEach(sprite -> sprite.contents().close());
                    }
                }
            }
        } finally {
            types.inverse().remove(FluxFaceSpriteSource.INSTANCE.type());
        }
    }

    private static PathPackResources pack(Path root, String name) {
        return new PathPackResources(new PackLocationInfo(name, Component.empty(), PackSource.BUILT_IN, Optional.empty()), root.resolve(name));
    }

    private static void writeSprites(Path pack, int revision) throws IOException {
        var dir = Files.createDirectories(pack.resolve("assets/fluxnetworks/textures/block"));
        int size = revision == 1 ? 32 : 16;
        try (var image = new NativeImage(2 * size, 2 * size, false)) {
            for (String name : TEXTURES) {
                for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++)
                    image.setPixelRGBA(x, y, pixel(name, revision, y / size * 2 + x / size, x % size, y % size));
                image.writeToFile(dir.resolve(name + ".png"));
                Files.writeString(dir.resolve(name + ".png.mcmeta"), """
                        {"animation":{"width":%d,"height":%d,"interpolate":true,"frametime":3,"frames":[3,1,{"index":2,"time":7},0]}}
                        """.formatted(size, size));
            }
        }
    }

    private static int pixel(String name, int revision, int frame, int x, int y) {
        int material = TEXTURES.indexOf(name);
        int alpha = switch ((x + material) % 3) { case 0 -> 0; case 1 -> 128; default -> 255; };
        return alpha << 24 | material << 20 | revision << 18 | frame << 16 | y << 8 | x;
    }

    private static void assertFrames(SpriteContents sprite, String name, int revision) {
        assertThat(sprite.getUniqueFrames().toArray()).containsExactly(3, 1, 2, 0);
        var metadata = sprite.metadata().getSection(AnimationMetadataSection.SERIALIZER).orElseThrow();
        var times = new ArrayList<Integer>();
        metadata.forEachFrame((index, time) -> times.add(time));
        assertThat(times).containsExactly(3, 3, 7, 3);
        assertThat(metadata.isInterpolatedFrames()).isTrue();

        var image = sprite.getOriginalImage();
        int width = sprite.width(), height = sprite.height();
        boolean point = name.startsWith("flux_point");
        int scale = revision == 1 ? 2 : 1, size = point ? 5 : 8;
        int u = (point ? 12 : 8) * scale, v = (point ? 6 : 2) * scale, last = (point ? 4 : 8) * scale - 1;
        int left = width * (16 - size) / 32, top = height * (16 - size) / 32;
        int right = width - left - 1, bottom = height - top - 1;
        for (int frame = 0; frame < 4; frame++) {
            int dx = (frame % 2) * width, dy = (frame / 2) * height;
            for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
                if (x < left || x > right || y < top || y > bottom)
                    assertThat(image.getPixelRGBA(dx + x, dy + y) >>> 24).isZero();
            }
            assertThat(image.getPixelRGBA(dx + left, dy + top)).isEqualTo(pixel(name, revision, frame, u, v));
            assertThat(image.getPixelRGBA(dx + right, dy + top)).isEqualTo(pixel(name, revision, frame, u + last, v));
            assertThat(image.getPixelRGBA(dx + left, dy + bottom)).isEqualTo(pixel(name, revision, frame, u, v + last));
            assertThat(image.getPixelRGBA(dx + right, dy + bottom)).isEqualTo(pixel(name, revision, frame, u + last, v + last));
        }
    }
}
