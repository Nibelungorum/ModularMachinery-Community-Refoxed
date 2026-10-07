package cn.howxu.mmcr.client.model;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.MachineAppearanceSpec;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.test.TestBootstrap;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.gui.render.DynamicAtlasAllocator;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.metadata.animation.AnimationMetadataSection;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ResolvedModel;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.client.resources.model.cuboid.ItemTransforms;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.client.resources.model.sprite.MaterialBaker;
import net.minecraft.client.resources.model.sprite.TextureSlots;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.joml.Matrix4f;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class DynamicOverlayItemModelTest {
    @AfterEach
    void clearAppearanceCache() {
        MachineAppearanceCache.replaceSnapshot(Map.of());
    }

    @Test
    void controller_items_display_the_idle_state_overlay() {
        var machineId = MMCR.id("stateful_controller");
        MachineAppearanceCache.replaceSnapshot(Map.of(machineId, new MachineAppearanceSpec(
                MMCR.id("basic_casing"), null, null,
                MMCR.id("block/custom_idle"), MMCR.id("block/custom_active"))));

        var description = DynamicOverlayItemModel.Description.controller(machineId);

        assertThat(description.stateOverlayTexture()).isEqualTo(MMCR.id("block/custom_idle"));
    }

    @ParameterizedTest
    @CsvSource({"false, false, false", "true, false, false", "false, true, false", "false, false, true"})
    void gui_cache_refreshes_when_any_item_layer_is_animated(boolean animatedBase, boolean animatedOverlay,
                                                           boolean animatedStateOverlay) throws Exception {
        TestBootstrap.bootstrap();
        var machineId = MMCR.id("test_cube");
        var baseTexture = MMCR.id("block/test_item_base");
        var defaults = MachineAppearanceSpec.defaults();
        MachineAppearanceCache.replaceSnapshot(Map.of(machineId, new MachineAppearanceSpec(
                defaults.machineBasicBlock(), baseTexture, null)));
        var description = DynamicOverlayItemModel.Description.controller(machineId);

        try (var staticSprite = sprite(false); var animatedSprite = sprite(true)) {
            MaterialBaker materials = (MaterialBaker) Proxy.newProxyInstance(getClass().getClassLoader(),
                    new Class<?>[]{MaterialBaker.class}, (proxy, method, args) -> {
                        Material material = (Material) args[0];
                        boolean animated = material.sprite().equals(baseTexture) ? animatedBase
                                : material.sprite().equals(description.stateOverlayTexture()) ? animatedStateOverlay
                                : animatedOverlay;
                        return new Material.Baked(animated ? animatedSprite : staticSprite, material.forceTranslucent());
                    });
            ResolvedModel resolved = (ResolvedModel) Proxy.newProxyInstance(getClass().getClassLoader(),
                    new Class<?>[]{ResolvedModel.class}, (proxy, method, args) -> switch (method.getName()) {
                        case "getTopTextureSlots" -> TextureSlots.EMPTY;
                        case "getTopGuiLight" -> UnbakedModel.GuiLight.SIDE;
                        case "getTopTransforms" -> ItemTransforms.NO_TRANSFORMS;
                        case "resolveParticleMaterial" -> new Material.Baked(staticSprite, false);
                        default -> throw new UnsupportedOperationException(method.getName());
                    });
            ModelBaker baker = (ModelBaker) Proxy.newProxyInstance(getClass().getClassLoader(),
                    new Class<?>[]{ModelBaker.class}, (proxy, method, args) -> switch (method.getName()) {
                        case "getModel" -> resolved;
                        case "materials" -> materials;
                        default -> throw new UnsupportedOperationException(method.getName());
                    });
            ItemModel model = new DynamicOverlayItemModel.Unbaked().bake(
                    new ItemModel.BakingContext(baker, null, null, null, null, null), new Matrix4f());
            var renderState = new ItemStackRenderState();
            model.update(renderState, new ItemStack(ModBlocks.controllerFor(machineId).get().asItem()),
                    null, ItemDisplayContext.GUI, null, null, 0);

            boolean animated = animatedBase || animatedOverlay || animatedStateOverlay;
            assertThat(renderState.isAnimated()).isEqualTo(animated);
            var allocator = new DynamicAtlasAllocator<Object>(1, 1);
            allocator.getOrAllocate(description, renderState.isAnimated());
            allocator.endFrame();
            assertThat(allocator.getOrAllocate(description, renderState.isAnimated()).state())
                    .isEqualTo(animated ? DynamicAtlasAllocator.SlotState.STALE : DynamicAtlasAllocator.SlotState.READY);
        }
    }

    private static TextureAtlasSprite sprite(boolean animated) {
        NativeImage image = new NativeImage(1, animated ? 2 : 1, true);
        var animation = new AnimationMetadataSection(Optional.empty(), Optional.empty(), Optional.empty(), 1, false);
        var contents = new SpriteContents(MMCR.id(animated ? "block/test_animated" : "block/test_static"),
                new FrameSize(1, 1), image, animated ? Optional.of(animation) : Optional.empty(), List.of(), Optional.empty());
        return new TextureAtlasSprite(TextureAtlas.LOCATION_BLOCKS, contents, 1, 1, 0, 0, 0) {};
    }
}
