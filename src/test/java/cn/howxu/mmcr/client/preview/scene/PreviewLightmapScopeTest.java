package cn.howxu.mmcr.client.preview.scene;

import com.mojang.blaze3d.textures.GpuTextureView;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PreviewLightmapScopeTest {

    @Test
    void override_is_visible_only_inside_scope() {
        GpuTextureView view = new GpuTextureView(null, 0, 1) {
            @Override public void close() { }
            @Override public boolean isClosed() { return false; }
        };

        assertThat(PreviewLightmapScope.current()).isNull();
        PreviewLightmapScope.with(view, () -> assertThat(PreviewLightmapScope.current()).isSameAs(view));
        assertThat(PreviewLightmapScope.current()).isNull();
    }

    @Test
    void override_is_cleared_when_render_fails() {
        assertThatThrownBy(() -> PreviewLightmapScope.with(null, () -> {
            assertThat(PreviewLightmapScope.current()).isNull();
            throw new IllegalStateException("render");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(PreviewLightmapScope.current()).isNull();
    }
}
