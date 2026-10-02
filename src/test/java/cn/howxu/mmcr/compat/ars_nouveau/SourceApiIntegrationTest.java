package cn.howxu.mmcr.compat.ars_nouveau;

import cn.howxu.mmcr.api.capability.CapabilityType;
import cn.howxu.mmcr.api.machine.definition.InterfacePredicates;
import cn.howxu.mmcr.api.port.PortDefinition;
import cn.howxu.mmcr.api.recipe.CustomRecipeIo;
import cn.howxu.mmcr.api.recipe.MachineRecipeBuilder;
import cn.howxu.mmcr.api.recipe.OutputRegistry;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.recipe.requirement.RequirementHandlerRegistry;
import cn.howxu.mmcr.compat.kubejs.KubeJSApi;
import cn.howxu.mmcr.compat.kubejs.KubeJSInterfaceHelpers;
import cn.howxu.mmcr.compat.kubejs.MachineRecipeBuilderJS;
import cn.howxu.mmcr.internal.port.IOPortKind;
import cn.howxu.mmcr.internal.port.PortFamilyDescriptor;
import cn.howxu.mmcr.internal.registration.MachineRecipeConverter;
import cn.howxu.mmcr.internal.tile.IOPortBlockEntity;
import cn.howxu.mmcr.publicapi.recipe.ArsNouveauIo;
import cn.howxu.mmcr.publicapi.recipe.CustomIoSpec;
import cn.howxu.mmcr.publicapi.recipe.IoDirection;
import cn.howxu.mmcr.publicapi.recipe.IoValues;
import cn.howxu.mmcr.publicapi.structure.BlockCondition;
import cn.howxu.mmcr.publicapi.structure.BlockConditions;
import cn.howxu.mmcr.registry.PortKinds;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.test.TestBootstrap;
import cn.howxu.mmcr.util.IOType;
import dev.latvian.mods.rhino.ContextFactory;
import dev.latvian.mods.rhino.ScriptableObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Source declaration and script integration without a loaded Ars capability implementation.
 * @author howxu <dev@howxu.cn>
 */
class SourceApiIntegrationTest {
    private static final long LARGE_AMOUNT = 3_000_000_000L;
    private static final ResourceLocation ID = ResourceLocation.parse("test:source_api");

    @BeforeAll
    static void bootstrap() throws Exception { TestBootstrap.bootstrap(); }

    @Test
    void public_factories_preserve_long_payloads_and_only_expose_public_io() throws Exception {
        try (var requirements = RequirementHandlerRegistry.openTestScope();
             var outputs = OutputRegistry.openTestScope()) {
            ArsNouveauRecipeTypes.register();
            assertThat(ArsNouveauIo.class.getMethod("sourceInput", long.class).getReturnType()).isEqualTo(CustomIoSpec.class);
            assertThat(ArsNouveauIo.class.getMethod("sourceOutput", long.class).getReturnType()).isEqualTo(CustomIoSpec.class);
            var input = ArsNouveauIo.sourceInput(LARGE_AMOUNT);
            var output = ArsNouveauIo.sourceOutput(LARGE_AMOUNT);
            assertThat(input.typeId()).isEqualTo(ArsSourceIds.SOURCE);
            assertThat(input.io()).isEqualTo(IoDirection.INPUT);
            assertThat(output.io()).isEqualTo(IoDirection.OUTPUT);
            assertThat(input.payload()).isEqualTo(IoValues.customIo(ArsSourceIds.SOURCE, IoDirection.INPUT,
                    SourceRecipeDeclarations.inputPayload(LARGE_AMOUNT)).payload());
            assertThat(output.payload()).isEqualTo(IoValues.customIo(ArsSourceIds.SOURCE, IoDirection.OUTPUT,
                    SourceRecipeDeclarations.outputPayload(LARGE_AMOUNT)).payload());
            var copy = input.payload().getAsJsonObject();
            copy.addProperty("amount", 1L);
            assertThat(input.payload().getAsJsonObject().get("amount").getAsLong()).isEqualTo(LARGE_AMOUNT);
            assertThatIllegalArgumentException().isThrownBy(() -> ArsNouveauIo.sourceInput(0L));
            assertThatIllegalArgumentException().isThrownBy(() -> ArsNouveauIo.sourceOutput(-1L));
        }
    }

    @Test
    void typed_builder_and_public_generic_declarations_produce_the_same_canonical_io() {
        try (var requirements = RequirementHandlerRegistry.openTestScope();
             var outputs = OutputRegistry.openTestScope()) {
            ArsNouveauRecipeTypes.register();
            var direct = MachineRecipeBuilder.recipe(ID).recipePool(ID)
                    .inputSource(LARGE_AMOUNT).outputSource(LARGE_AMOUNT).build();
            CustomIoSpec input = ArsNouveauIo.sourceInput(LARGE_AMOUNT);
            CustomIoSpec output = ArsNouveauIo.sourceOutput(LARGE_AMOUNT);
            var generic = MachineRecipeBuilder.recipe(ID).recipePool(ID)
                    .custom(new CustomRecipeIo(input.typeId(), RecipeModifier.IOType.INPUT, input.payload()))
                    .custom(new CustomRecipeIo(output.typeId(), RecipeModifier.IOType.OUTPUT, output.payload())).build();
            assertThat(direct.requirements()).containsExactly(SourceRequirement.input(LARGE_AMOUNT));
            assertThat(generic.requirements()).isEqualTo(direct.requirements());
            assertThat(direct.customOutputs()).hasSize(1);
            assertThat(generic.customOutputs().getFirst().payload()).isEqualTo(direct.customOutputs().getFirst().payload());
            assertThat(MachineRecipeConverter.toOutput(direct.customOutputs().getFirst()))
                    .isEqualTo(new SourceOutput(LARGE_AMOUNT));
        }
    }

    @Test
    void real_rhino_api_direct_builder_and_generic_builder_preserve_long_canonical_io() {
        try (var requirements = RequirementHandlerRegistry.openTestScope();
             var outputs = OutputRegistry.openTestScope()) {
            ArsNouveauRecipeTypes.register();
            var apiBuilder = builder();
            var directBuilder = builder();
            var genericBuilder = builder();
            var context = new ContextFactory().enter();
            var scope = context.initStandardObjects();
            ScriptableObject.putProperty(scope, "api", new KubeJSApi(), context);
            ScriptableObject.putProperty(scope, "apiBuilder", apiBuilder, context);
            ScriptableObject.putProperty(scope, "directBuilder", directBuilder, context);
            ScriptableObject.putProperty(scope, "genericBuilder", genericBuilder, context);
            ScriptableObject.putProperty(scope, "input", RecipeModifier.IOType.INPUT, context);
            ScriptableObject.putProperty(scope, "output", RecipeModifier.IOType.OUTPUT, context);
            ScriptableObject.putProperty(scope, "type", ArsSourceIds.SOURCE.toString(), context);
            ScriptableObject.putProperty(scope, "inputPayload", SourceRecipeDeclarations.inputPayload(LARGE_AMOUNT), context);
            ScriptableObject.putProperty(scope, "outputPayload", SourceRecipeDeclarations.outputPayload(LARGE_AMOUNT), context);
            context.evaluateString(scope, """
                    apiBuilder.addRequirement(api.sourceInput(3000000000));
                    apiBuilder.addRequirement(api.sourceOutput(3000000000));
                    directBuilder.inputSource(3000000000).outputSource(3000000000);
                    genericBuilder.custom(type, input, inputPayload).custom(type, output, outputPayload);
                    """, "source-io-routing", 1, null);
            var expected = apiBuilder.createObject();
            assertThat(expected.requirements()).containsExactly(
                    SourceRequirement.input(LARGE_AMOUNT), SourceRequirement.output(LARGE_AMOUNT));
            assertThat(expected.machineOutputs()).containsExactly(new SourceOutput(LARGE_AMOUNT));
            for (var candidate : List.of(directBuilder, genericBuilder)) {
                var recipe = candidate.createObject();
                assertThat(recipe.requirements()).isEqualTo(expected.requirements());
                assertThat(recipe.machineOutputs()).isEqualTo(expected.machineOutputs());
            }
            assertThatThrownBy(() -> context.evaluateString(scope, "directBuilder.inputSource(0)",
                    "invalid-source-input", 1, null)).hasMessageContaining("amount must be positive");
            assertThatThrownBy(() -> context.evaluateString(scope, "api.sourceOutput(-1)",
                    "invalid-source-output", 1, null)).hasMessageContaining("amount must be positive");
        }
    }

    @Test
    void script_tier_parser_accepts_only_exact_normal_source_interface_forms() {
        var api = new KubeJSApi();
        var spec = api.portTierRequirements(List.of("source_input_interface>=normal", "source_output_interface>=normal"));
        assertThat(spec.requirements()).extracting(requirement -> requirement.id()).containsExactly(
                "source_input_interface>=normal", "source_output_interface>=normal");
        assertThat(spec.requirements()).extracting(requirement -> requirement.minTier()).containsOnly(0);
        for (String invalid : List.of("source_input_interface>=tiny", "source_output_interface>=ultimate",
                "source_input_hatch>=normal", "source_input_interface>=NORMAL", "source_side_interface>=normal")) {
            assertThatIllegalArgumentException().isThrownBy(() -> api.portTierRequirements(List.of(invalid)));
        }
        assertThat(api.portTierRequirements(List.of("item_input_bus>=normal", "fluid_output_hatch>=vacuum",
                "energy_input_hatch>=ultimate")).requirements()).extracting(requirement -> requirement.minTierId())
                .containsExactly("normal", "vacuum", "ultimate");
    }

    @Test
    void predicates_delegate_registered_family_and_direction_through_public_and_script_wrappers() {
        PortKinds.clearForTesting();
        try {
            assertThat(InterfacePredicates.anySourcePorts().alternatives()).isEmpty();
            PortKinds.register(new SourceKind("minecraft:gold_block", IOType.INPUT));
            PortKinds.register(new SourceKind("minecraft:diamond_block", IOType.OUTPUT));
            assertThat(blocks(BlockConditions.sourceInput())).containsExactly(Blocks.GOLD_BLOCK);
            assertThat(blocks(BlockConditions.sourceOutput())).containsExactly(Blocks.DIAMOND_BLOCK);
            assertThat(blocks(BlockConditions.sourcePorts())).containsExactly(Blocks.GOLD_BLOCK, Blocks.DIAMOND_BLOCK);
            assertThat(KubeJSInterfaceHelpers.anyOfSourceInput().matches(Blocks.GOLD_BLOCK.defaultBlockState())).isTrue();
            assertThat(KubeJSInterfaceHelpers.anyOfSourceInput().matches(Blocks.DIAMOND_BLOCK.defaultBlockState())).isFalse();
            assertThat(KubeJSInterfaceHelpers.anyOfSourceInput().matches(Blocks.STONE.defaultBlockState())).isFalse();
            var feState = ModBlocks.BLOCKS.get("energy_input_hatch").get().defaultBlockState();
            assertThat(KubeJSInterfaceHelpers.anyOfEnergyInput().matches(feState)).isTrue();
            assertThat(KubeJSInterfaceHelpers.anyOfSourceInput().matches(feState)).isFalse();
            var context = new ContextFactory().enter();
            var scope = context.initStandardObjects();
            ScriptableObject.putProperty(scope, "api", new KubeJSApi(), context);
            ScriptableObject.putProperty(scope, "inputState", Blocks.GOLD_BLOCK.defaultBlockState(), context);
            ScriptableObject.putProperty(scope, "outputState", Blocks.DIAMOND_BLOCK.defaultBlockState(), context);
            assertThat(context.evaluateString(scope, """
                    api.anyOfSourceInput().matches(inputState) && !api.anyOfSourceInput().matches(outputState)
                    && api.anyOfSourceOutput().matches(outputState) && !api.anyOfSourceOutput().matches(inputState)
                    && api.anyOfSourcePorts().matches(inputState) && api.anyOfSourcePorts().matches(outputState)
                    """, "source-family-predicates", 1, null)).isEqualTo(true);
            assertThat(InterfacePredicates.anySourceInput().alternatives()).hasSize(1);
            assertThat(InterfacePredicates.anySourceOutput().alternatives()).hasSize(1);
            assertThat(InterfacePredicates.anySourcePorts().alternatives()).hasSize(2);
        } finally {
            PortKinds.clearForTesting();
        }
    }

    private static MachineRecipeBuilderJS builder() {
        var builder = new MachineRecipeBuilderJS(ID);
        builder.recipePoolId = ID;
        return builder;
    }

    private static List<Block> blocks(BlockCondition condition) {
        return flatten(condition).flatMap(value -> value.blockSupplier().stream()).map(supplier -> (Block) supplier.get()).toList();
    }

    private static Stream<BlockCondition> flatten(BlockCondition condition) {
        return Stream.concat(Stream.of(condition), condition.alternatives().stream().flatMap(SourceApiIntegrationTest::flatten));
    }

    /** Sentinel vanilla blocks exercise family predicates without registering an Ars block entity.
     * @author howxu <dev@howxu.cn>
     */
    private record SourceKind(String id, IOType ioType) implements IOPortKind {
        public BlockEntityType.BlockEntitySupplier<? extends IOPortBlockEntity> entityFactory() {
            return PortKinds.ITEM_INPUT.entityFactory();
        }
        public List<PortFamilyDescriptor> families() {
            return List.of(new PortFamilyDescriptor(ArsSourceIds.SOURCE, ioType, 0, List.of(id)));
        }
        public PortDefinition definition() {
            return PortDefinition.of(ResourceLocation.parse(id), List.of(IOPortKind.binding(
                    new CapabilityType(ArsSourceIds.SOURCE), ioType, families())));
        }
    }
}
