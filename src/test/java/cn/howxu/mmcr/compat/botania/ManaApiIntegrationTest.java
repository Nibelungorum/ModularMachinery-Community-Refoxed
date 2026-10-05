package cn.howxu.mmcr.compat.botania;

import cn.howxu.mmcr.api.capability.CapabilityDirections;
import cn.howxu.mmcr.api.capability.CapabilityRequest;
import cn.howxu.mmcr.api.capability.CapabilitySnapshot;
import cn.howxu.mmcr.api.capability.CapabilityType;
import cn.howxu.mmcr.api.capability.CapabilityView;
import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.facet.CapabilityFacet;
import cn.howxu.mmcr.api.capability.plan.CapabilityOperation;
import cn.howxu.mmcr.api.compat.botania.ManaViewFacet;
import cn.howxu.mmcr.api.machine.MachineStructureDefinition;
import cn.howxu.mmcr.api.machine.PortTierRequirementSpec;
import cn.howxu.mmcr.api.machine.definition.BlockPredicate;
import cn.howxu.mmcr.api.machine.definition.InterfacePredicates;
import cn.howxu.mmcr.api.machine.definition.MachineIoView;
import cn.howxu.mmcr.api.machine.definition.PatternDefinition;
import cn.howxu.mmcr.api.machine.definition.PortTiers;
import cn.howxu.mmcr.api.machine.definition.StructureStage;
import cn.howxu.mmcr.api.port.PortDefinition;
import cn.howxu.mmcr.api.recipe.CustomRecipeIo;
import cn.howxu.mmcr.api.recipe.MachineRecipeBuilder;
import cn.howxu.mmcr.api.recipe.OutputRegistry;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.recipe.requirement.RequirementHandlerRegistry;
import cn.howxu.mmcr.compat.kubejs.KubeJSApi;
import cn.howxu.mmcr.compat.kubejs.KubeJSInterfaceHelpers;
import cn.howxu.mmcr.compat.kubejs.MachineRecipeBuilderJS;
import cn.howxu.mmcr.compat.kubejs.MachineStructureBuilderJS;
import cn.howxu.mmcr.internal.api.facade.runtime.IoAdapters;
import cn.howxu.mmcr.internal.api.facade.structure.TierAdapters;
import cn.howxu.mmcr.internal.port.IOPortKind;
import cn.howxu.mmcr.internal.port.PortFamilyDescriptor;
import cn.howxu.mmcr.internal.registration.MachineDefinitionConverter;
import cn.howxu.mmcr.internal.registration.MachineRecipeConverter;
import cn.howxu.mmcr.internal.sync.MachineStructureSyncCodec;
import cn.howxu.mmcr.publicapi.recipe.BotaniaIo;
import cn.howxu.mmcr.publicapi.recipe.CustomIoSpec;
import cn.howxu.mmcr.publicapi.recipe.IoDirection;
import cn.howxu.mmcr.publicapi.recipe.IoValues;
import cn.howxu.mmcr.publicapi.structure.BlockCondition;
import cn.howxu.mmcr.publicapi.structure.BlockConditions;
import cn.howxu.mmcr.publicapi.structure.PortTierLimits;
import cn.howxu.mmcr.registry.PortKinds;
import cn.howxu.mmcr.test.TestBootstrap;
import cn.howxu.mmcr.util.IOType;
import dev.latvian.mods.rhino.ContextFactory;
import dev.latvian.mods.rhino.ScriptableObject;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Total-mana API, script, query and structure contracts without native Botania types.
 * @author howxu <dev@howxu.cn>
 */
class ManaApiIntegrationTest {
    private static final long LARGE_AMOUNT = 3_000_000_000L;
    private static final ResourceLocation ID = ResourceLocation.parse("test:mana_api");

    @BeforeAll
    static void bootstrap() throws Exception { TestBootstrap.bootstrap(); }

    @Test
    void publicFactoriesPreserveLongPayloadsAndPublicBoundary() throws Exception {
        try (var requirements = RequirementHandlerRegistry.openTestScope();
             var outputs = OutputRegistry.openTestScope()) {
            BotaniaRecipeTypes.register();
            assertThat(BotaniaIo.class.getMethod("manaInput", long.class).getReturnType()).isEqualTo(CustomIoSpec.class);
            assertThat(BotaniaIo.class.getMethod("manaOutput", long.class).getReturnType()).isEqualTo(CustomIoSpec.class);
            var input = BotaniaIo.manaInput(LARGE_AMOUNT);
            var output = BotaniaIo.manaOutput(LARGE_AMOUNT);
            assertThat(input.typeId()).isEqualTo(BotaniaManaIds.MANA);
            assertThat(input.io()).isEqualTo(IoDirection.INPUT);
            assertThat(output.io()).isEqualTo(IoDirection.OUTPUT);
            assertThat(input.payload()).isEqualTo(IoValues.customIo(BotaniaManaIds.MANA, IoDirection.INPUT,
                    ManaRecipeDeclarations.inputPayload(LARGE_AMOUNT)).payload());
            assertThat(output.payload()).isEqualTo(IoValues.customIo(BotaniaManaIds.MANA, IoDirection.OUTPUT,
                    ManaRecipeDeclarations.outputPayload(LARGE_AMOUNT)).payload());
            input.payload().getAsJsonObject().addProperty("amount", 1L);
            assertThat(input.payload().getAsJsonObject().get("amount").getAsLong()).isEqualTo(LARGE_AMOUNT);
            for (long invalid : List.of(0L, -1L, Long.MIN_VALUE)) {
                assertThatIllegalArgumentException().isThrownBy(() -> BotaniaIo.manaInput(invalid));
                assertThatIllegalArgumentException().isThrownBy(() -> BotaniaIo.manaOutput(invalid));
            }
        }
    }

    @Test
    void javaAndPublicGenericDeclarationsProduceIdenticalCanonicalTotals() {
        try (var requirements = RequirementHandlerRegistry.openTestScope();
             var outputs = OutputRegistry.openTestScope()) {
            BotaniaRecipeTypes.register();
            for (long amount : List.of(LARGE_AMOUNT, Long.MAX_VALUE)) {
                var direct = MachineRecipeBuilder.recipe(ID).recipePool(ID).inputMana(amount).outputMana(amount).build();
                var input = BotaniaIo.manaInput(amount);
                var output = BotaniaIo.manaOutput(amount);
                var generic = MachineRecipeBuilder.recipe(ID).recipePool(ID)
                        .custom(new CustomRecipeIo(input.typeId(), RecipeModifier.IOType.INPUT, input.payload()))
                        .custom(new CustomRecipeIo(output.typeId(), RecipeModifier.IOType.OUTPUT, output.payload())).build();
                assertThat(direct.requirements()).containsExactly(ManaRequirement.input(amount));
                assertThat(generic.requirements()).isEqualTo(direct.requirements());
                assertThat(direct.customOutputs()).hasSize(1);
                assertThat(generic.customOutputs().getFirst().payload()).isEqualTo(direct.customOutputs().getFirst().payload());
                assertThat(MachineRecipeConverter.toOutput(direct.customOutputs().getFirst())).isEqualTo(new ManaOutput(amount));
            }
            assertThatIllegalArgumentException().isThrownBy(() -> MachineRecipeBuilder.recipe(ID).inputMana(0L));
            assertThatIllegalArgumentException().isThrownBy(() -> MachineRecipeBuilder.recipe(ID).outputMana(-1L));
        }
    }

    @Test
    void realRhinoApiDirectAndGenericBuildersPreserveLongCanonicalRecipes() {
        try (var requirements = RequirementHandlerRegistry.openTestScope();
             var outputs = OutputRegistry.openTestScope()) {
            BotaniaRecipeTypes.register();
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
            ScriptableObject.putProperty(scope, "inputPayload", ManaRecipeDeclarations.inputPayload(LARGE_AMOUNT), context);
            ScriptableObject.putProperty(scope, "outputPayload", ManaRecipeDeclarations.outputPayload(LARGE_AMOUNT), context);
            context.evaluateString(scope, """
                    apiBuilder.addRequirement(api.manaInput(3000000000));
                    apiBuilder.addRequirement(api.manaOutput(3000000000));
                    directBuilder.inputMana(3000000000).outputMana(3000000000);
                    genericBuilder.custom('botania:mana', input, inputPayload).custom('botania:mana', output, outputPayload);
                    """, "mana-io-routing", 1, null);
            var expected = apiBuilder.createObject();
            assertThat(expected.requirements()).containsExactly(ManaRequirement.input(LARGE_AMOUNT), ManaRequirement.output(LARGE_AMOUNT));
            assertThat(expected.machineOutputs()).containsExactly(new ManaOutput(LARGE_AMOUNT));
            for (var candidate : List.of(directBuilder, genericBuilder)) {
                var recipe = candidate.createObject();
                assertThat(recipe.requirements()).isEqualTo(expected.requirements());
                assertThat(recipe.machineOutputs()).isEqualTo(expected.machineOutputs());
            }
            for (String invalid : List.of("directBuilder.inputMana(0)", "directBuilder.outputMana(-1)",
                    "api.manaInput(-1)", "api.manaOutput(0)")) {
                assertThatThrownBy(() -> context.evaluateString(scope, invalid, "invalid-mana-io", 1, null))
                        .hasMessageContaining("amount must be positive");
            }
            var invalidInput = ManaRecipeDeclarations.inputPayload(LARGE_AMOUNT);
            invalidInput.addProperty("amount", 0L);
            var invalidOutput = ManaRecipeDeclarations.outputPayload(LARGE_AMOUNT);
            invalidOutput.addProperty("amount", -1L);
            ScriptableObject.putProperty(scope, "invalidInput", invalidInput, context);
            ScriptableObject.putProperty(scope, "invalidOutput", invalidOutput, context);
            for (String invalid : List.of("genericBuilder.custom('botania:mana', input, invalidInput)",
                    "genericBuilder.custom('botania:mana', output, invalidOutput)")) {
                assertThatThrownBy(() -> context.evaluateString(scope, invalid, "invalid-generic-mana-io", 1, null))
                        .hasMessageContaining("amount must be positive");
            }
        }
    }

    @Test
    void manaQueriesDeduplicateAliasesFilterDirectionsAndTagsAndReadLiveStorage() {
        long[] shared = {30L, 100L};
        var core = new MachineIoView(new CapabilitySnapshot(List.of(
                new ManaView(shared, shared, IOType.INPUT, List.of("mana")),
                new ManaView(shared, shared, IOType.INPUT, List.of("mana", "alias")),
                new ManaView(shared, shared, IOType.OUTPUT, List.of("mana")),
                new ManaView(shared, shared, IOType.OUTPUT, List.of("mana", "alias")),
                new ManaView(new Object(), new long[]{7L, 10L}, IOType.INPUT, List.of("other")),
                new ManaView(new Object(), new long[]{20L, 10L}, IOType.OUTPUT, List.of("other")))));
        var snapshot = IoAdapters.wrap(core);
        assertThat(core.manaInput()).isEqualTo(37L);
        assertThat(snapshot.manaInput()).isEqualTo(core.manaInput());
        assertThat(snapshot.manaOutputCapacity()).isEqualTo(70L);
        assertThat(snapshot.forTags(Set.of("mana", "alias")).manaInput()).isEqualTo(30L);
        assertThat(snapshot.forTags(Set.of("mana")).manaOutputCapacity()).isEqualTo(70L);
        assertThat(snapshot.forTags(Set.of("missing")).manaInput()).isZero();
        assertThat(snapshot.forTags(Set.of("missing")).manaOutputCapacity()).isZero();
        assertThat(snapshot.energyInput()).isZero();
        assertThat(snapshot.sourceInput()).isZero();
        shared[0] = 50L;
        assertThat(snapshot.manaInput()).isEqualTo(57L);
        assertThat(snapshot.manaOutputCapacity()).isEqualTo(50L);
    }

    @Test
    void manaQueryIdentityIsPhysicalAndAggregatesSaturate() {
        Object first = new String("same");
        Object second = new String("same");
        var core = new MachineIoView(new CapabilitySnapshot(List.of(
                new ManaView(first, new long[]{Long.MAX_VALUE - 5L, Long.MAX_VALUE}, IOType.INPUT, List.of()),
                new ManaView(second, new long[]{10L, Long.MAX_VALUE}, IOType.INPUT, List.of()),
                new ManaView(first, new long[]{0L, Long.MAX_VALUE - 5L}, IOType.OUTPUT, List.of()),
                new ManaView(second, new long[]{0L, 10L}, IOType.OUTPUT, List.of()))));
        assertThat(core.manaInput()).isEqualTo(Long.MAX_VALUE);
        assertThat(IoAdapters.wrap(core).manaOutputCapacity()).isEqualTo(Long.MAX_VALUE);
    }

    @Test
    void javaPublicAndRhinoTiersAcceptOnlyExactNormalPoolFormsAndSyncUnchanged() {
        var api = new KubeJSApi();
        var limits = PortTierLimits.builder().anyManaInput().anyManaOutput().build();
        var core = PortTiers.builder().anyManaInput().anyManaOutput().build();
        var expected = PortTierRequirementSpec.from(core);
        assertThat(TierAdapters.unwrap(limits)).isEqualTo(core);
        assertThat(PortTierRequirementSpec.builder().anyManaInput().anyManaOutput().build()).isEqualTo(expected);
        assertThat(limits.requirements()).extracting(PortTierLimits.RequirementView::category)
                .containsOnly(PortTierLimits.PortCategory.MANA);
        assertThat(limits.requirements()).extracting(PortTierLimits.RequirementView::ioType)
                .containsExactly(IoDirection.INPUT, IoDirection.OUTPUT);
        assertThat(api.portTierRequirements(List.of("mana_input_pool>=normal", "mana_output_pool>=normal"))).isEqualTo(expected);
        assertThat(expected.requirements()).extracting(PortTierRequirementSpec.Requirement::id)
                .containsExactly("mana_input_pool>=normal", "mana_output_pool>=normal");
        var context = new ContextFactory().enter();
        var scope = context.initStandardObjects();
        ScriptableObject.putProperty(scope, "api", api, context);
        ScriptableObject.putProperty(scope, "expected", expected, context);
        assertThat(context.evaluateString(scope, """
                api.portTierRequirements(['mana_input_pool>=normal', 'mana_output_pool>=normal']).equals(expected)
                """, "mana-tier-equivalence", 1, null)).isEqualTo(true);
        for (String invalid : List.of("mana_input_pool>=tiny", "mana_output_pool>=ultimate", "mana_input_hatch>=normal",
                "mana_input_pool>=NORMAL", "mana_side_pool>=normal", "mana_input_pool >=normal", "mana_input_pool>=normal ")) {
            assertThatIllegalArgumentException().isThrownBy(() -> api.portTierRequirements(List.of(invalid)));
            ScriptableObject.putProperty(scope, "invalid", invalid, context);
            assertThatThrownBy(() -> context.evaluateString(scope, "api.portTierRequirements([invalid])", "invalid-mana-tier", 1, null));
        }
        assertThatIllegalArgumentException().isThrownBy(() -> new PortTiers.Requirement(PortTiers.PortCategory.MANA, IOType.INPUT, 1, "normal"));
        assertThatIllegalArgumentException().isThrownBy(() -> new PortTierRequirementSpec.Requirement(PortTierRequirementSpec.PortCategory.MANA, IOType.INPUT, 0, "tiny"));
        var pattern = new PatternDefinition(List.of(List.of("C")), Map.of('C', BlockPredicate.block(Blocks.STONE)), 'C', 1, 1, 1);
        var declaration = MachineDefinitionConverter.toDeclaration(new StructureStage(StructureStage.Kind.FULL, pattern, null, core, null));
        assertThat(declaration.portTierRequirements()).isEqualTo(expected);
        var original = new MachineStructureDefinition(ID, List.of(declaration));
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            MachineStructureSyncCodec.encode(buffer, original);
            assertThat(MachineStructureSyncCodec.decode(buffer).declarations()).isEqualTo(original.declarations());
            assertThat(buffer.isReadable()).isFalse();
        } finally {
            buffer.release();
        }
    }

    @Test
    void registeredManaFamilyAndDirectionsMatchThroughAllStructureWrappers() {
        PortKinds.clearForTesting();
        try {
            assertThat(InterfacePredicates.anyManaPorts().alternatives()).isEmpty();
            var input = new ManaKind("minecraft:gold_block", IOType.INPUT);
            var output = new ManaKind("minecraft:diamond_block", IOType.OUTPUT);
            PortKinds.register(input);
            PortKinds.register(output);
            assertThat(blocks(BlockConditions.manaInput())).containsExactly(Blocks.GOLD_BLOCK);
            assertThat(blocks(BlockConditions.manaOutput())).containsExactly(Blocks.DIAMOND_BLOCK);
            assertThat(blocks(BlockConditions.manaPorts())).containsExactly(Blocks.GOLD_BLOCK, Blocks.DIAMOND_BLOCK);
            assertThat(blocks(BlockConditions.ports())).contains(Blocks.GOLD_BLOCK, Blocks.DIAMOND_BLOCK);
            assertThat(KubeJSInterfaceHelpers.anyOfManaInput().matches(Blocks.GOLD_BLOCK.defaultBlockState())).isTrue();
            assertThat(KubeJSInterfaceHelpers.anyOfManaInput().matches(Blocks.DIAMOND_BLOCK.defaultBlockState())).isFalse();
            assertThat(KubeJSInterfaceHelpers.anyOfManaOutput().matches(Blocks.STONE.defaultBlockState())).isFalse();
            var tiers = PortTierRequirementSpec.builder().anyManaInput().anyManaOutput().build();
            assertThat(tiers.validate(List.of(input, output))).isEmpty();
            assertThat(tiers.validate(List.of(input))).isPresent();
            assertThat(tiers.validate(List.of(PortKinds.ITEM_INPUT, PortKinds.ENERGY_OUTPUT))).isPresent();
            var context = new ContextFactory().enter();
            var scope = context.initStandardObjects();
            ScriptableObject.putProperty(scope, "api", new KubeJSApi(), context);
            ScriptableObject.putProperty(scope, "structure", new MachineStructureBuilderJS(ID), context);
            ScriptableObject.putProperty(scope, "inputState", Blocks.GOLD_BLOCK.defaultBlockState(), context);
            ScriptableObject.putProperty(scope, "outputState", Blocks.DIAMOND_BLOCK.defaultBlockState(), context);
            assertThat(context.evaluateString(scope, """
                    api.anyOfManaInput().matches(inputState) && !api.anyOfManaInput().matches(outputState)
                    && api.anyManaOutput().matches(outputState) && !api.anyManaOutput().matches(inputState)
                    && api.anyOfManaPorts().matches(inputState) && api.anyManaPorts().matches(outputState)
                    && structure.anyOfManaInput().matches(inputState) && structure.anyOfManaOutput().matches(outputState)
                    && structure.anyManaPorts().matches(inputState) && structure.anyOfManaPorts().matches(outputState)
                    """, "mana-family-predicates", 1, null)).isEqualTo(true);
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
        return Stream.concat(Stream.of(condition), condition.alternatives().stream().flatMap(ManaApiIntegrationTest::flatten));
    }

    /** Sentinel blocks test registered family matching without a native pool entity.
     * @author howxu <dev@howxu.cn>
     */
    private record ManaKind(String id, IOType ioType) implements IOPortKind {
        public BlockEntityType.BlockEntitySupplier<? extends BlockEntity> entityFactory() { return PortKinds.ITEM_INPUT.entityFactory(); }
        public List<PortFamilyDescriptor> families() { return List.of(new PortFamilyDescriptor(BotaniaManaIds.MANA, ioType, 0, List.of(id))); }
        public PortDefinition definition() {
            return PortDefinition.of(ResourceLocation.parse(id), List.of(IOPortKind.binding(BotaniaManaIds.TYPE, ioType, families())));
        }
    }

    /** Read-only physical aliases exercise query semantics independently of FE.
     * @author howxu <dev@howxu.cn>
     */
    private record ManaView(Object queryIdentity, long[] storage, IOType direction, List<String> tags)
            implements MachineCapability, CapabilityView, ManaViewFacet {
        public CapabilityType type() { return BotaniaManaIds.TYPE; }
        public CapabilityDirections directions() { return CapabilityDirections.of(direction); }
        public IOType ioType() { return direction; }
        public CapabilityView view() { return this; }
        public Set<Class<? extends CapabilityFacet>> facets() { return Set.of(ManaViewFacet.class); }
        public long amount() { return storage[0]; }
        public long capacity() { return storage[1]; }
        public CapabilityOperation prepare(CapabilityRequest request) { throw new UnsupportedOperationException(); }
    }
}
