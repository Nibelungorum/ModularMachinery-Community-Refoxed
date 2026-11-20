package cn.howxu.mmcr.compat.athena;

import cn.howxu.mmcr.test.TestBootstrap;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/** @author howxu <dev@howxu.cn> */
class AthenaVariantsDefinitionTest {
    @BeforeAll
    static void bootstrap() throws Exception {
        TestBootstrap.bootstrap();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void both_lit_states_use_only_the_installed_branch(boolean withAthena) {
        var json = JsonParser.parseString("""
                {
                    "variants": {
                        "lit=false": {"model":"test:block/plain"},
                        "lit=true": {"model":"test:block/plain_active"}
                    },
                    "athena_variants": {
                        "lit=false": {"model":"test:block/connected"},
                        "lit=true": {"model":"test:block/connected_active"}
                    }
                }
                """).getAsJsonObject();
        json.getAsJsonObject(withAthena ? "variants" : "athena_variants").getAsJsonObject("lit=true")
                .addProperty("type", "absent:unregistered");
        var definition = AthenaVariantsDefinition.codecFor(withAthena).codec().parse(JsonOps.INSTANCE, json).getOrThrow();
        var lamp = Blocks.REDSTONE_LAMP;
        var roots = definition.instantiate(lamp.getStateDefinition(), () -> "dual variants");
        var dependencies = new ArrayList<Identifier>();
        roots.get(lamp.defaultBlockState().setValue(BlockStateProperties.LIT, false)).resolveDependencies(dependencies::add);
        roots.get(lamp.defaultBlockState().setValue(BlockStateProperties.LIT, true)).resolveDependencies(dependencies::add);
        String expected = withAthena ? "test:block/connected" : "test:block/plain";
        assertThat(dependencies).containsExactly(Identifier.parse(expected), Identifier.parse(expected + "_active"));
    }
}
