package cn.howxu.mmcr.compat.athena;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.compat.athena.loaded.AthenaGeometryModel;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.test.TestBootstrap;
import com.google.common.collect.ImmutableMap;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import earth.terrarium.athena.impl.client.DefaultModels;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelDispatcher;
import net.minecraft.client.resources.model.cuboid.CuboidModel;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.UnbakedModelLoader;
import net.neoforged.neoforge.client.model.UnbakedModelParser;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** @author howxu <dev@howxu.cn> */
class AthenaGeometryModelTest {
    @BeforeAll
    static void bootstrap() throws Exception {
        TestBootstrap.bootstrap();
        DefaultModels.init();
    }

    @ParameterizedTest
    @ValueSource(strings = {"gearbox", "air_intake", "combustion_chamber", "vent"})
    void multipart_selects_the_shared_casing_and_only_the_matching_decoration(String name) throws IOException {
        var states = ModBlocks.BLOCKS.get(name).get().getStateDefinition();
        try (var reader = new InputStreamReader(Objects.requireNonNull(getClass().getResourceAsStream(
                "/assets/mmcr/blockstates/" + name + ".json")), StandardCharsets.UTF_8)) {
            var definition = BlockStateModelDispatcher.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseReader(reader)).getOrThrow();
            var selectors = definition.multiPart().orElseThrow().selectors();
            for (var state : states.getPossibleStates()) {
                var models = new ArrayList<Identifier>();
                selectors.stream().filter(s -> s.instantiate(states).test(state))
                        .forEach(s -> s.variant().resolveDependencies(models::add));
                boolean lit = state.hasProperty(BlockStateProperties.LIT) && state.getValue(BlockStateProperties.LIT);
                assertThat(models).containsExactly(MMCR.id("block/basic_casing_ctm"),
                        MMCR.id("block/decoration/" + name + (lit ? "_active" : "")));
            }
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void shared_casing_retains_its_parent_and_only_reads_ctm_when_available(boolean installed) throws Exception {
        Map<Identifier, UnbakedModelLoader<?>> loaders = new HashMap<>();
        if (installed) AthenaGeometryModel.register(new ModelEvent.RegisterLoaders(loaders));
        var field = UnbakedModelParser.class.getDeclaredField("LOADERS");
        field.setAccessible(true);
        var previous = field.get(null);
        try (var reader = new InputStreamReader(Objects.requireNonNull(getClass().getResourceAsStream(
                "/assets/mmcr/models/block/basic_casing_ctm.json")), StandardCharsets.UTF_8)) {
            field.set(null, ImmutableMap.copyOf(loaders));
            var json = JsonParser.parseReader(reader).getAsJsonObject();
            var model = UnbakedModelParser.parse(new StringReader(json.toString()));
            assertThat(model).isExactlyInstanceOf(installed ? AthenaGeometryModel.class : CuboidModel.class);
            assertThat(model.parent()).isEqualTo(MMCR.id("block/basic_casing"));

            json.addProperty("athena:loader", "test:unregistered");
            if (installed) {
                assertThatThrownBy(() -> UnbakedModelParser.parse(new StringReader(json.toString())))
                        .isInstanceOf(JsonParseException.class).hasMessageContaining("test:unregistered");
            } else {
                assertThat(UnbakedModelParser.parse(new StringReader(json.toString())).parent()).isEqualTo(model.parent());
            }
        } finally {
            field.set(null, previous);
        }
    }
}
