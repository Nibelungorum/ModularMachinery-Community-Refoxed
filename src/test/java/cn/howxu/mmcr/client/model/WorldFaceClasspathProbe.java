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
        ordinary.runtime_block_quads_follow_the_reloaded_atlas();
        ordinary.only_controller_front_changes_material_when_active_and_vertical_roll_is_retained();
        if (installed) {
            var ctm = new AthenaWorldFaceTest();
            ctm.neighbor_selected_quads_keep_geometry_and_sample_one_whole_panel(false);
            ctm.neighbor_selected_quads_keep_geometry_and_sample_one_whole_panel(true);
        }
    }
}
