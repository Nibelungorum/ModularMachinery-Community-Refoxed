package cn.howxu.mmcr.internal.sync;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.api.machine.MachineRegistry;
import cn.howxu.mmcr.api.machine.MachineAppearanceSpec;
import cn.howxu.mmcr.api.machine.MachineControllerSpec;
import cn.howxu.mmcr.api.machine.MachineDefinitions;
import cn.howxu.mmcr.api.machine.MachineRegistration;
import cn.howxu.mmcr.api.machine.MachineStructureDefinition;
import cn.howxu.mmcr.api.machine.MachineStructureRequirements;
import cn.howxu.mmcr.api.machine.MachineStructureRegistry;
import cn.howxu.mmcr.api.machine.PortRequirementSpec;
import cn.howxu.mmcr.api.recipe.LevelRequirement;
import cn.howxu.mmcr.api.recipe.MachineIngredient;
import cn.howxu.mmcr.api.recipe.MachineOutput;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.RecipeRegistry;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.recipe.modifier.SingleBlockModifierReplacement;
import cn.howxu.mmcr.api.recipe.requirement.ItemRequirement;
import cn.howxu.mmcr.api.recipe.requirement.FluidRequirement;
import cn.howxu.mmcr.api.recipe.requirement.EnergyRequirement;
import cn.howxu.mmcr.internal.network.PktRuntimeContentPayload;
import cn.howxu.mmcr.test.RecipeTestSupport;
import cn.howxu.mmcr.test.TestBootstrap;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import com.mojang.serialization.Lifecycle;

import java.util.Collections;

import cn.howxu.mmcr.api.machine.PortTierRequirementSpec;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.JsonOps;
import java.nio.charset.StandardCharsets;
import java.util.stream.IntStream;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RuntimeContentSnapshotTest {

    private static RegistryAccess registries;

    @BeforeAll
    static void bootstrap() throws Exception {
        TestBootstrap.bootstrap();
        MappedRegistry<Enchantment> enchantments = new MappedRegistry<>(Registries.ENCHANTMENT, Lifecycle.stable());
        VanillaRegistries.createLookup().lookupOrThrow(Registries.ENCHANTMENT).listElements()
                .forEach(holder -> Registry.register(enchantments,
                        holder.key().identifier(), holder.value()));
        enchantments.freeze();
        List<Registry<?>> activeRegistries = new java.util.ArrayList<>();
        RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY).registries()
                .forEach(entry -> activeRegistries.add(entry.value()));
        activeRegistries.add(enchantments);
        registries = new RegistryAccess.ImmutableRegistryAccess(activeRegistries);
    }

    @BeforeEach
    void restoreDefaultRuntimeState() {
        ClientRuntimeSnapshotBridge.resetForTesting();
        TestBootstrap.registerRuntimeBuiltins();
    }

    @Test
    void snapshotDefensivelyCopiesAllMaps() {
        Identifier machineId = MMCR.id("runtime_test_machine");
        Map<Identifier, MachineStructureDefinition> structures = mapWithOpaqueValue(machineId);
        Map<Identifier, MachineRecipe> recipes = mapWithOpaqueValue(machineId);
        Map<Identifier, MachineControllerSpec> controllerSpecs = mapWithOpaqueValue(machineId);
        Map<Identifier, MachineAppearanceSpec> appearances = mapWithOpaqueValue(machineId);
        RuntimeContentSnapshot snapshot = new RuntimeContentSnapshot(
                structures, recipes, controllerSpecs, appearances, 7L);

        structures.clear();
        recipes.clear();
        controllerSpecs.clear();
        appearances.clear();

        assertThat(snapshot.structures()).containsKey(machineId);
        assertThat(snapshot.recipes()).containsKey(machineId);
        assertThat(snapshot.controllerSpecs()).containsKey(machineId);
        assertThat(snapshot.appearances()).containsKey(machineId);
        assertThatThrownBy(() -> snapshot.structures().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> snapshot.recipes().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> snapshot.controllerSpecs().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> snapshot.appearances().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void structureSyncCodecRoundTripsPatternAndMetadata() {
        MachineStructureDefinition original = structure(MMCR.id("runtime_test_machine"));
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), registries);

        MachineStructureSyncCodec.encode(buf, original);
        MachineStructureDefinition decoded = MachineStructureSyncCodec.decode(buf);

        assertThat(decoded.machineId()).isEqualTo(original.machineId());
        assertThat(decoded.declarations()).isEqualTo(original.declarations());
    }

    @Test
    void structureSyncCodecRoundTripsModifierReplacementIdAndPredicate() {
        BlockArray blockArray = new BlockArray(Map.of(BlockPos.ZERO, new BlockPredicate.OfBlock(Blocks.IRON_BLOCK)),
                Map.of(), Map.of(BlockPos.ZERO, 'M'));
        MachineStructureRequirements requirements = MachineStructureRequirements.builder()
                .modifier('M', new SingleBlockModifierReplacement(MMCR.id("speed"),
                        new BlockPredicate.OfBlock(Blocks.DIAMOND_BLOCK)))
                .build(blockArray);
        MachineStructureDefinition original = new MachineStructureDefinition(MMCR.id("modifier_sync"), blockArray,
                PortRequirementSpec.none(), PortTierRequirementSpec.none(), List.of(), requirements);
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), registries);

        MachineStructureSyncCodec.encode(buf, original);
        MachineStructureDefinition decoded = MachineStructureSyncCodec.decode(buf);

        SingleBlockModifierReplacement decodedReplacement = decoded.declarations().getFirst()
                .requirements().modifierReplacements().get('M').getFirst();
        assertThat(decodedReplacement.getModifierId()).isEqualTo(MMCR.id("speed"));
        assertThat(decodedReplacement.getModifiers()).isEmpty();
        assertThat(decodedReplacement.getDescriptiveStack().isEmpty()).isTrue();
        assertThat(decodedReplacement.getReplacement()).isEqualTo(new BlockPredicate.OfBlock(Blocks.DIAMOND_BLOCK));
    }

    @Test
    void recipeSyncCodecRoundTripsRuntimeRecipe() {
        MachineRecipe original = RecipeTestSupport.create(
                MMCR.id("sync_recipe"), MMCR.id("runtime_test_machine"), 40,
                List.of(new MachineIngredient.ItemIngredient(Ingredient.of(Items.IRON_INGOT), 2)),
                List.of(new ItemStack(Items.GOLD_INGOT, 4)),
                List.of(new RecipeModifier("input_bus", RecipeModifier.IOType.INPUT, 2F, RecipeModifier.Operation.MULTIPLY, false)),
                5, 3, true, List.of(), List.of(), true,
                 List.of(),
                true, Set.of(MMCR.id("runtime_host")));
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), registries);

        MachineRecipeSyncCodec.encode(buf, original);
        MachineRecipe decoded = MachineRecipeSyncCodec.decode(buf);

        assertThat(decoded.id()).isEqualTo(original.id());
        assertThat(decoded.machineId()).isEqualTo(original.machineId());
        assertThat(decoded.tickTime()).isEqualTo(40);
        assertThat(decoded.priority()).isEqualTo(5);
        assertThat(decoded.maxThreads()).isEqualTo(3);
        assertThat(decoded.doesCancelRecipeOnPerTickFailure()).isTrue();
        assertThat(decoded.isParallelized()).isTrue();
        assertThat(decoded.allowPartialOutputs()).isTrue();
        assertThat(decoded.requiredHostIds()).containsExactly(MMCR.id("runtime_host"));
        assertThat(decoded.levelRequirements()).containsExactlyElementsOf(original.levelRequirements());
        assertThat(decoded.modifiers()).containsExactlyElementsOf(original.modifiers());
        assertThat(decoded.machineOutputs()).singleElement().isInstanceOfSatisfying(MachineOutput.ItemOutput.class, output -> {
            assertThat(output.stack().getItem()).isEqualTo(Items.GOLD_INGOT);
            assertThat(output.stack().getCount()).isEqualTo(4);
        });
        assertThat(decoded.requirements()).hasSameSizeAs(original.requirements());
        assertThat(decoded.requirements().getFirst()).satisfies(requirement -> {
            assertThat(requirement).isInstanceOf(ItemRequirement.class);
            ItemRequirement item = (ItemRequirement) requirement;
            assertThat(item.io()).isEqualTo(RecipeModifier.IOType.INPUT);
            assertThat(item.count()).isEqualTo(2);
        });
        assertThat(decoded.requirements().get(1)).satisfies(requirement -> {
            assertThat(requirement).isInstanceOf(ItemRequirement.class);
            ItemRequirement item = (ItemRequirement) requirement;
            assertThat(item.io()).isEqualTo(RecipeModifier.IOType.OUTPUT);
            assertThat(item.stack().getItem()).isEqualTo(Items.GOLD_INGOT);
            assertThat(item.stack().getCount()).isEqualTo(4);
        });
    }

    @Test
    void recipeSyncCodecNormalizesLegacyTagIngredientString() throws Exception {
        Method normalize = MachineRecipeSyncCodec.class.getDeclaredMethod("normalizeIngredient", JsonElement.class);
        normalize.setAccessible(true);

        var normalized = normalize.invoke(null, JsonParser.parseString("\"#minecraft:planks\""));

        assertThat(normalized).isInstanceOfSatisfying(JsonPrimitive.class, primitive ->
                assertThat(primitive.getAsString()).isEqualTo("#minecraft:planks"));
        var ops = RegistryOps.create(JsonOps.INSTANCE, registries);
        var encodedTag = Ingredient.CODEC.encodeStart(ops,
                Ingredient.of(HolderSet.emptyNamed(BuiltInRegistries.ITEM, ItemTags.LOGS))).getOrThrow();
        assertThat(encodedTag.getAsString()).isEqualTo("#minecraft:logs");
    }

    @Test
    void runtimeContentPayloadRoundTripsCompleteSnapshot() {
        Identifier machineId = MMCR.id("runtime_test_machine");
        RuntimeContentSnapshot snapshot = new RuntimeContentSnapshot(
                Map.of(machineId, structureWithLevelAndModifier(machineId)),
                Map.of(MMCR.id("sync_recipe"), recipe(MMCR.id("sync_recipe"), machineId)),
                Map.of(machineId, MachineControllerSpec.defaultsFor(machineId)),
                Map.of(machineId, MachineAppearanceSpec.defaults()),
                11L);
        PktRuntimeContentPayload payload = new PktRuntimeContentPayload(snapshot);
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), registries);

        PktRuntimeContentPayload.STREAM_CODEC.encode(buf, payload);
        PktRuntimeContentPayload decoded = PktRuntimeContentPayload.STREAM_CODEC.decode(buf);

        assertThat(decoded.snapshot().structures()).containsOnlyKeys(machineId);
        assertThat(decoded.snapshot().recipes()).containsOnlyKeys(MMCR.id("sync_recipe"));
        assertThat(decoded.snapshot().contentVersion()).isEqualTo(11L);
        MachineStructureDefinition decodedStructure = decoded.snapshot().structures().get(machineId);
        assertThat(decodedStructure.requirements().levelSlots()).containsEntry('L', MMCR.id("coil"));
        assertThat(decodedStructure.requirements().modifierReplacements()).containsKey('M');
    }

    @Test
    void runtimeContentPayloadRejectsDuplicateStructureKeys() {
        Identifier machineId = MMCR.id("runtime_test_machine");
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), registries);
        buf.writeVarInt(2);
        Identifier.STREAM_CODEC.encode(buf, machineId);
        MachineStructureSyncCodec.encode(buf, structure(machineId));
        Identifier.STREAM_CODEC.encode(buf, machineId);
        MachineStructureSyncCodec.encode(buf, structure(machineId));

        assertThatThrownBy(() -> PktRuntimeContentPayload.STREAM_CODEC.decode(buf))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicate runtime content key");
    }

    @Test
    void runtimeContentPayloadRejectsStructureKeyIdMismatch() {
        Identifier key = MMCR.id("runtime_test_machine");
        Identifier structureId = MMCR.id("runtime_test_machine_new");
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), registries);
        buf.writeVarInt(1);
        Identifier.STREAM_CODEC.encode(buf, key);
        MachineStructureSyncCodec.encode(buf, structure(structureId));
        buf.writeVarInt(0);
        buf.writeVarInt(0);
        buf.writeVarInt(0);
        buf.writeVarLong(1L);

        assertThatThrownBy(() -> PktRuntimeContentPayload.STREAM_CODEC.decode(buf))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Structure key does not match machine id");
    }

    @Test
    void applyClientReplacesOldDynamicStructuresAndRecipes() {
        Identifier oldMachine = MMCR.id("runtime_test_machine");
        Identifier newMachine = MMCR.id("runtime_test_machine_new");
        Identifier oldRecipe = MMCR.id("old_synced_recipe");
        Identifier newRecipe = MMCR.id("new_synced_recipe");
        registerMachineIfMissing(oldMachine);
        registerMachineIfMissing(newMachine);
        MachineStructureRegistry.replaceDynamic(Map.of(oldMachine, structure(oldMachine)));
        RecipeRegistry.replaceDynamic(Map.of(oldRecipe, recipe(oldRecipe, oldMachine)));

        new RuntimeContentSnapshot(
                Map.of(newMachine, structure(newMachine)),
                Map.of(newRecipe, recipe(newRecipe, newMachine)),
                Map.of(newMachine, MachineControllerSpec.defaultsFor(newMachine)),
                Map.of(), 12L).applyClient();

        assertThat(MachineStructureRegistry.effectiveSnapshot()).containsOnlyKeys(newMachine);
        assertThat(RecipeRegistry.effectiveSnapshot()).containsOnlyKeys(newRecipe);
        assertThat(MachineRegistry.getMachine(oldMachine)).isNull();
        assertThat(RecipeRegistry.getRecipe(oldRecipe)).isNull();
    }

    @Test
    void applyClientRemovesDynamicContentOmittedFromSnapshot() {
        Identifier removedMachine = MMCR.id("runtime_test_machine");
        Identifier removedRecipe = MMCR.id("removed_synced_recipe");
        registerMachineIfMissing(removedMachine);
        MachineStructureRegistry.replaceDynamic(Map.of(removedMachine, structure(removedMachine)));
        RecipeRegistry.replaceDynamic(Map.of(removedRecipe, recipe(removedRecipe, removedMachine)));

        RuntimeContentSnapshot.empty().applyClient();

        assertThat(MachineStructureRegistry.effectiveSnapshot()).doesNotContainKey(removedMachine);
        assertThat(RecipeRegistry.effectiveSnapshot()).doesNotContainKey(removedRecipe);
        assertThat(MachineRegistry.getMachine(removedMachine)).isNull();
        assertThat(RecipeRegistry.getRecipe(removedRecipe)).isNull();
    }

    @Test
    void applyClientRejectsOlderSnapshotBeforeReplacingContent() {
        Identifier machineId = MMCR.id("runtime_test_machine");
        registerMachineIfMissing(machineId);
        RuntimeContentSnapshot current = new RuntimeContentSnapshot(
                Map.of(machineId, structure(machineId)), Map.of(), Map.of(), Map.of(), 20L);

        current.applyClient();
        new RuntimeContentSnapshot(Map.of(), Map.of(), Map.of(), Map.of(), 19L).applyClient();

        assertThat(MachineStructureRegistry.effectiveSnapshot()).containsOnlyKeys(machineId);
    }

    @Test
    void invalidSnapshotDoesNotPartiallyReplaceClientContent() {
        Identifier oldMachine = MMCR.id("runtime_test_machine");
        Identifier newMachine = MMCR.id("runtime_test_machine_new");
        registerMachineIfMissing(oldMachine);
        registerMachineIfMissing(newMachine);
        MachineStructureRegistry.replaceDynamic(Map.of(oldMachine, structure(oldMachine)));
        Map<Identifier, MachineStructureDefinition> before = MachineStructureRegistry.effectiveSnapshot();

        MachineRecipe invalidRecipe = recipe(MMCR.id("actual_recipe"), newMachine);
        RuntimeContentSnapshot invalid = new RuntimeContentSnapshot(
                Map.of(newMachine, structure(newMachine)),
                Map.of(MMCR.id("wrong_recipe_key"), invalidRecipe),
                Map.of(), Map.of(), 30L);

        assertThatThrownBy(invalid::applyClient).isInstanceOf(IllegalArgumentException.class);
        assertThat(MachineStructureRegistry.effectiveSnapshot()).isEqualTo(before);
    }

    @Test
    void effectiveReadsWaitForCoordinatorCommitBoundary() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        Thread reader;
        synchronized (RuntimeContentVersion.lock()) {
            reader = new Thread(() -> {
                started.countDown();
                MachineStructureRegistry.effectiveSnapshot();
                RecipeRegistry.effectiveSnapshot();
                MachineRegistry.effectiveSnapshot();
            });
            reader.start();
            assertThat(started.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(reader.isAlive()).isTrue();
        }
        reader.join(1_000);
        assertThat(reader.isAlive()).isFalse();
    }

    @Test
    void applyingClientSnapshotDoesNotAdvanceServerContentVersion() {
        long before = RuntimeContentVersion.current();
        Identifier machineId = MMCR.id("runtime_test_machine");
        registerMachineIfMissing(machineId);

        new RuntimeContentSnapshot(Map.of(machineId, structure(machineId)), Map.of(), Map.of(), Map.of(), 31L)
                .applyClient();

        assertThat(RuntimeContentVersion.current()).isEqualTo(before);
    }

    @Test
    void newClientConnectionAcceptsLowerVersionFromAnotherServer() {
        Identifier machineId = MMCR.id("runtime_test_machine");
        registerMachineIfMissing(machineId);
        new RuntimeContentSnapshot(Map.of(machineId, structure(machineId)), Map.of(), Map.of(), Map.of(), 90L)
                .applyClient();

        ClientRuntimeSnapshotBridge.resetForConnection();

        assertThat(new RuntimeContentSnapshot(Map.of(machineId, structure(machineId)), Map.of(), Map.of(), Map.of(), 1L)
                .applyClient()).isTrue();
    }

    @Test
    void newClientConnectionRetainsRuntimeContentUntilSnapshotArrives() {
        Identifier machineId = MMCR.id("runtime_test_machine");
        registerMachineIfMissing(machineId);
        new RuntimeContentSnapshot(Map.of(machineId, structure(machineId)), Map.of(),
                Map.of(machineId, MachineControllerSpec.defaultsFor(machineId)),
                Map.of(machineId, MachineAppearanceSpec.defaults()), 90L).applyClient();

        ClientRuntimeSnapshotBridge.resetForConnection();

        assertThat(MachineStructureRegistry.effectiveSnapshot()).containsKey(machineId);
        assertThat(RecipeRegistry.effectiveSnapshot()).isEmpty();
        assertThat(cn.howxu.mmcr.client.controller.ControllerSpecCache.snapshot()).isEmpty();
        assertThat(cn.howxu.mmcr.client.model.MachineAppearanceCache.snapshot()).isEmpty();
    }

    @Test
    void clientRejectsControllerSpecForAnotherMachine() {
        Identifier machineId = MMCR.id("runtime_test_machine");
        registerMachineIfMissing(machineId);
        RuntimeContentSnapshot invalid = new RuntimeContentSnapshot(
                Map.of(machineId, structure(machineId)), Map.of(),
                Map.of(machineId, MachineControllerSpec.defaultsFor(MMCR.id("runtime_test_machine_new"))), Map.of(), 92L);

        assertThatThrownBy(invalid::applyClient)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Controller spec key does not match spec id");
    }

    @Test
    void recipeCodecRejectsOversizedFluidOutput() {
        MachineRecipe recipe = RecipeTestSupport.create(
                MMCR.id("oversized_fluid_recipe"), MMCR.id("runtime_test_machine"), 20,
                List.of(), List.of(), List.of(), 0, 1, false, List.of(),
                List.of(new FluidRequirement(RecipeModifier.IOType.OUTPUT, null, 0,
                        new net.neoforged.neoforge.fluids.FluidStack(Fluids.WATER, 10_000_001))));
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), registries);

        assertThatThrownBy(() -> MachineRecipeSyncCodec.encode(buf, recipe))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid fluid amount");
    }

    @Test
    void recipeCodecRejectsOversizedRequirementCountOnEncode() {
        MachineRecipe recipe = RecipeTestSupport.create(
                MMCR.id("oversized_requirements_recipe"), MMCR.id("runtime_test_machine"), 20,
                List.of(), List.of(), List.of(), 0, 1, false, List.of(),
                Collections.nCopies(4097, new EnergyRequirement(1)));
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), registries);

        assertThatThrownBy(() -> MachineRecipeSyncCodec.encode(buf, recipe))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid requirement count");
    }

    @Test
    void recipeCodecRejectsOversizedModifierCountOnEncode() {
        MachineRecipe recipe = RecipeTestSupport.create(
                MMCR.id("oversized_modifiers_recipe"), MMCR.id("runtime_test_machine"), 20,
                List.of(), List.of(), Collections.nCopies(1025,
                        new RecipeModifier("target", RecipeModifier.IOType.INPUT, 1F,
                                RecipeModifier.Operation.ADD, false)), 0, 1);
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), registries);

        assertThatThrownBy(() -> MachineRecipeSyncCodec.encode(buf, recipe))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid modifier count");
    }

    @Test
    void recipeCodecRejectsOversizedLevelRequirementCountOnEncode() throws Exception {
        Method writeLevels = MachineRecipeSyncCodec.class.getDeclaredMethod(
                "writeLevelRequirements", RegistryFriendlyByteBuf.class, List.class);
        writeLevels.setAccessible(true);
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), registries);

        assertThatThrownBy(() -> writeLevels.invoke(null, buf,
                Collections.nCopies(1025, new LevelRequirement(MMCR.id("level_type"), MMCR.id("level")))))
                .isInstanceOf(java.lang.reflect.InvocationTargetException.class)
                .hasCauseInstanceOf(IllegalArgumentException.class)
                .hasRootCauseMessage("Invalid level requirement count: 1025");
    }

    @Test
    void recipeCodecRejectsOversizedRequiredHostCountOnEncode() {
        MachineRecipe recipe = RecipeTestSupport.create(
                MMCR.id("oversized_hosts_recipe"), MMCR.id("runtime_test_machine"), 20,
                List.of(), List.of(), List.of(), 0, 1, false, List.of(), List.of(), false,
                List.of(), false, hostIds(1025));
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), registries);

        assertThatThrownBy(() -> MachineRecipeSyncCodec.encode(buf, recipe))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid required host count");
    }

    @Test
    void recipeCodecRejectsOversizedRequirementTagCountOnEncode() {
        MachineRecipe recipe = RecipeTestSupport.create(
                MMCR.id("oversized_tags_recipe"), MMCR.id("runtime_test_machine"), 20,
                List.of(), List.of(), List.of(), 0, 1, false, List.of(),
                List.of(new EnergyRequirement(1, Collections.nCopies(1025, "tag"))));
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), registries);

        assertThatThrownBy(() -> MachineRecipeSyncCodec.encode(buf, recipe))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid tag count");
    }

    @Test
    void structureSyncCodecRejectsOversizedDeclarationCountOnEncode() {
        List<MachineStructureDefinition.Declaration> declarations = Collections.nCopies(1025,
                structure(MMCR.id("runtime_test_machine")).declarations().getFirst());
        MachineStructureDefinition original = new MachineStructureDefinition(MMCR.id("oversized"), declarations);
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), registries);

        assertThatThrownBy(() -> MachineStructureSyncCodec.encode(buf, original))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid declaration count");
    }

    @SuppressWarnings("unchecked")
    private static <T> Map<Identifier, T> mapWithOpaqueValue(Identifier id) {
        Map<Identifier, T> map = new LinkedHashMap<>();
        map.put(id, (T) new Object());
        return map;
    }

    private static void registerMachineIfMissing(Identifier id) {
        if (MachineDefinitions.getRegistration(id) == null) {
            MachineDefinitions.register(MachineRegistration.builder(id).build());
        }
    }

    private static MachineStructureDefinition structure(Identifier id) {
        Map<BlockPos, BlockPredicate> pattern = new LinkedHashMap<>();
        pattern.put(BlockPos.ZERO, new BlockPredicate.OfBlock(Blocks.BLAST_FURNACE));
        pattern.put(BlockPos.ZERO.east(), new BlockPredicate.OfBlock(Blocks.IRON_BLOCK));
        BlockArray blockArray = new BlockArray(pattern, Map.of(), Map.of(BlockPos.ZERO.east(), 'L'))
                .tagged(BlockPos.ZERO.east(), "input_bus");
        PortRequirementSpec portRequirements = PortRequirementSpec.builder().min("item_input_bus", 1).build();
        return new MachineStructureDefinition(id, blockArray, portRequirements,
                PortTierRequirementSpec.none(), List.of(),
                MachineStructureRequirements.builder().levelSlot('L', MMCR.id("coil")).build(blockArray));
    }

    private static MachineStructureDefinition structureWithLevelAndModifier(Identifier id) {
        Map<BlockPos, BlockPredicate> pattern = new LinkedHashMap<>();
        pattern.put(BlockPos.ZERO, new BlockPredicate.OfBlock(Blocks.BLAST_FURNACE));
        pattern.put(BlockPos.ZERO.east(), new BlockPredicate.OfBlock(Blocks.IRON_BLOCK));
        pattern.put(BlockPos.ZERO.north(), new BlockPredicate.OfBlock(Blocks.GOLD_BLOCK));
        BlockArray blockArray = new BlockArray(pattern, Map.of(), Map.of(
                BlockPos.ZERO.east(), 'L', BlockPos.ZERO.north(), 'M'));
        MachineStructureRequirements requirements = MachineStructureRequirements.builder()
                .levelSlot('L', MMCR.id("coil"))
                .modifier('M', new SingleBlockModifierReplacement(MMCR.id("speed"),
                        new BlockPredicate.OfBlock(Blocks.DIAMOND_BLOCK)))
                .build(blockArray);
        return new MachineStructureDefinition(id, blockArray, PortRequirementSpec.none(),
                PortTierRequirementSpec.none(), List.of(), requirements);
    }

    private static MachineRecipe recipe(Identifier id, Identifier machineId) {
        return RecipeTestSupport.create(
                id, machineId, 40,
                List.of(new MachineIngredient.ItemIngredient(Ingredient.of(Items.IRON_INGOT), 2)),
                List.of(new ItemStack(Items.GOLD_INGOT, 4)),
                List.of(), 0, 1, true, List.of(), List.of(), false,
                List.of(), true, Set.of());
    }

    private static Set<Identifier> hostIds(int count) {
        return IntStream.range(0, count)
                .mapToObj(index -> MMCR.id("host_" + index))
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    private static String classBytes(Class<?> type) throws IOException {
        String resource = type.getSimpleName() + ".class";
        try (InputStream stream = type.getResourceAsStream(resource)) {
            if (stream == null) throw new IOException("Missing class resource " + resource);
            return new String(stream.readAllBytes(), StandardCharsets.ISO_8859_1);
        }
    }
}
