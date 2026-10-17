package cn.howxu.mmcr.compat.jei;

import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.OutputRegistry;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.api.recipe.requirement.RequirementHandlerRegistry;
import cn.howxu.mmcr.compat.ars_nouveau.ArsNouveauRecipeTypes;
import cn.howxu.mmcr.compat.ars_nouveau.SourceRequirement;
import cn.howxu.mmcr.compat.botania.BotaniaRecipeTypes;
import cn.howxu.mmcr.compat.botania.ManaRequirement;
import cn.howxu.mmcr.compat.pneumaticcraft.AirRequirement;
import cn.howxu.mmcr.test.TestBootstrap;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.util.List;
import java.util.Set;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Probability reaches JEI through canonical recipe data, not a separate display-only value.
 * @author howxu <dev@howxu.cn>
 */
class ResourceChanceDisplayTest {
    private RequirementHandlerRegistry.TestScope requirementScope;
    private OutputRegistry.TestScope outputScope;

    @BeforeAll
    static void bootstrap() throws Exception {
        TestBootstrap.bootstrap();
    }

    @BeforeEach
    void registerDeclarations() {
        requirementScope = RequirementHandlerRegistry.openTestScope();
        outputScope = OutputRegistry.openTestScope();
        ArsNouveauRecipeTypes.register();
        BotaniaRecipeTypes.register();
        if (RequirementHandlerRegistry.typeFor(AirRequirement.TYPE.id()) == null) {
            RequirementHandlerRegistry.register(AirRequirement.TYPE);
        }
    }

    @AfterEach
    void restoreDeclarations() {
        outputScope.close();
        requirementScope.close();
    }

    @Test
    void source_and_mana_entries_retain_consumption_and_production_chance() {
        var source = SourceRequirement.CODEC.codec().parse(JsonOps.INSTANCE, JsonParser.parseString("""
                {"type":"ars_nouveau:source","io":"input","amount":200,"consume_chance":0}
                """)).getOrThrow();
        var mana = ManaRequirement.CODEC.codec().parse(JsonOps.INSTANCE, JsonParser.parseString("""
                {"type":"botania:mana","io":"output","amount":300,"chance":0.25}
                """)).getOrThrow();
        var entries = display(List.of(source, mana)).entries();

        assertThat(entries).extracting(JeiDisplayEntry::chance).containsExactly(0F, 0.25F);
        assertThat(entries).extracting(JeiDisplayEntry::transferable).containsOnly(false);
        assertThat(MachineRecipeLayout.forDisplay(display(List.of(source, mana)), 4).manaRows())
                .singleElement().satisfies(row -> assertThat(row.entry().chance()).isEqualTo(0.25F));
    }

    @Test
    void air_metadata_shows_keep_and_per_tick_semantics_without_losing_pressure() {
        var air = AirRequirement.CODEC.codec().parse(JsonOps.INSTANCE, JsonParser.parseString("""
                {"type":"pneumaticcraft:air","io":"input","air_per_tick":100,
                 "min_pressure":2,"consume_chance":0,"tags":["supply"]}
                """)).getOrThrow();
        var metadata = display(List.of(air)).airInputs().getFirst();

        assertThat(metadata.minPressure()).isEqualTo(2F);
        assertThat(metadata.tags()).containsExactly("supply");
        assertThat(key(metadata.label(true))).isEqualTo("jei.mmcr.machine_recipe.chanced_resource");
        assertThat(metadata.tooltip(true).stream().map(ResourceChanceDisplayTest::key))
                .containsExactly("jei.mmcr.machine_recipe.air_in.tooltip", "jei.mmcr.machine_recipe.keep",
                        "jei.mmcr.machine_recipe.air_chance_per_tick");
    }

    @Test
    void air_output_label_and_tooltip_expose_output_chance() {
        var air = AirRequirement.CODEC.codec().parse(JsonOps.INSTANCE, JsonParser.parseString("""
                {"type":"pneumaticcraft:air","io":"output","air_per_tick":100,"chance":0.25}
                """)).getOrThrow();
        var metadata = display(List.of(air)).airOutputs().getFirst();
        var chance = (TranslatableContents) metadata.tooltip(false).get(1).getContents();

        assertThat(key(metadata.label(false))).isEqualTo("jei.mmcr.machine_recipe.chanced_resource");
        assertThat(chance.getKey()).isEqualTo("jei.mmcr.machine_recipe.output_chance");
        assertThat(chance.getArgs()).containsExactly("25%");
    }

    private static String key(Component component) {
        return ((TranslatableContents) component.getContents()).getKey();
    }

    private static MachineRecipeDisplay display(List<MachineRequirement> requirements) {
        var id = ResourceLocation.parse("test:resource_chance_display");
        return MachineRecipeDisplay.from(MachineRecipe.fromCanonical(id, id, 20, requirements, List.of(),
                List.of(), 0, 1, false, false, false, Set.of()));
    }
}
