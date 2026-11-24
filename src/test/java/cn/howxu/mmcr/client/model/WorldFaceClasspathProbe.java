package cn.howxu.mmcr.client.model;

import cn.howxu.mmcr.compat.athena.AthenaModelBridgeBootstrap;
import cn.howxu.mmcr.test.TestBootstrap;

import static org.assertj.core.api.Assertions.assertThat;

/** @author HowXu {@literal <dev@howxu.cn>} */
public final class WorldFaceClasspathProbe {
    public static void main(String[] args) throws Exception {
        boolean installed = Boolean.parseBoolean(args[0]);
        assertThat(WorldFaceClasspathProbe.class.getClassLoader()
                .getResource("earth/terrarium/athena/api/client/neoforge/AthenaBakedModel.class") != null).isEqualTo(installed);
        TestBootstrap.bootstrap();
        assertThat(AthenaModelBridgeBootstrap.selectForTesting(installed).available()).isEqualTo(installed);
        var ordinary = new DynamicOverlayModelLoaderTest();
        try {
            ordinary.port_layers_keep_native_animations_and_pass_order_across_atlas_replacement();
            ordinary.only_controller_front_changes_when_active_and_vertical_roll_is_retained();
            ordinary.ctm_proxy_keeps_weighted_base_selection_and_filters_other_layers();
        } finally {
            ordinary.closeSprites();
        }
        if (installed) new AthenaWorldFaceTest().neighbor_selected_tiles_sample_one_panel(false);
    }
}
