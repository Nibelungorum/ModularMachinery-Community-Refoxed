package cn.howxu.mmcr.compat.botania;

import cn.howxu.mmcr.api.capability.CapabilitySnapshot;
import cn.howxu.mmcr.api.controller.ControllerScreenText;
import cn.howxu.mmcr.api.controller.ControllerScreenTextScope;
import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.api.machine.MachineControllerSpec;
import cn.howxu.mmcr.api.machine.MachineRegistration;
import cn.howxu.mmcr.api.machine.MachineStructureDefinition;
import cn.howxu.mmcr.api.machine.definition.MachineBehaviorContext;
import cn.howxu.mmcr.api.machine.definition.MachineIoView;
import cn.howxu.mmcr.api.machine.definition.RecipeBehavior;
import cn.howxu.mmcr.api.recipe.MachineOutput;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.MachineRecipeJson;
import cn.howxu.mmcr.api.recipe.requirement.ItemRequirement;
import cn.howxu.mmcr.compat.kubejs.KubeJSApi;
import cn.howxu.mmcr.compat.kubejs.MachineBuilderJS;
import cn.howxu.mmcr.compat.kubejs.MachineRecipeBuilderJS;
import cn.howxu.mmcr.compat.kubejs.MachineStructureBuilderJS;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.util.IOType;
import com.mojang.serialization.JsonOps;
import dev.latvian.mods.rhino.ContextFactory;
import dev.latvian.mods.rhino.NativeJavaClass;
import dev.latvian.mods.rhino.ScriptableObject;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.registries.DeferredHolder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Executes complete example files with real builders and live item/requirement codecs.
 * @author howxu <dev@howxu.cn>
 */
public final class BotaniaManaExampleGameTest {
    private static final ResourceLocation MACHINE = ResourceLocation.parse("mmcr_example:mana_machine");

    public void examplesExecute(GameTestHelper helper) {
        var fixture = new ExampleFixture();
        var context = new ContextFactory().enter();
        var scope = context.initStandardObjects();
        ScriptableObject.putProperty(scope, "fixture", fixture, context);
        ScriptableObject.putProperty(scope, "ComponentClass", new NativeJavaClass(context, scope, Component.class), context);
        ScriptableObject.putProperty(scope, "IdClass", new NativeJavaClass(context, scope, ResourceLocation.class), context);
        // Publication is captured; every chained declaration and callback uses the real Java implementation.
        context.evaluateString(scope, """
                var Java = { loadClass: function(name) {
                    if (name === 'net.minecraft.network.chat.Component') return ComponentClass;
                    if (name === 'net.minecraft.resources.ResourceLocation') return IdClass;
                    if (name === 'cn.howxu.mmcr.compat.kubejs.MachineRecipeBuilderJS') {
                        return function(id) { return fixture.createRecipe(id); };
                    }
                    throw new Error('Unexpected example class: ' + name);
                }};
                var MMCREvents = {
                    startup: function(callback) { callback(fixture); },
                    server: function(callback) { callback(fixture); }
                };
                """, "mana-example-bindings", 1, null);
        String controllerKey = MachineControllerSpec.defaultsFor(MACHINE).id().getPath();
        DeferredHolder<Block, Block> previous = ModBlocks.BLOCKS.put(controllerKey, ModBlocks.FACTORY_CONTROLLER);
        try {
            for (String file : List.of("startup_scripts/mana_machine.js", "server_scripts/mana_recipes.js")) {
                Path script = examples().resolve(file);
                context.evaluateString(scope, Files.readString(script), script.toString(), 1, null);
            }
            helper.assertTrue(fixture.machine != null && fixture.machine.id().equals(MACHINE), "Startup machine parsed");
            helper.assertTrue(fixture.structure != null && fixture.structure.machineId().equals(MACHINE), "Server structure parsed");
            var pattern = fixture.structure.pattern();
            helper.assertTrue(pattern.width() == 3 && pattern.height() == 2 && pattern.length() == 1, "Full example pattern compiled");
            for (String port : List.of(BotaniaManaIds.INPUT, BotaniaManaIds.OUTPUT, "item_input_bus", "item_output_bus")) {
                Block block = ModBlocks.BLOCKS.get(port).get();
                helper.assertTrue(pattern.pattern().containsValue(new BlockPredicate.OfBlock(block)), "Structure contains " + port);
            }
            helper.assertTrue(fixture.recipes.size() == 1, "Example built exactly one recipe");
            MachineRecipe recipe = fixture.recipes.getFirst();
            var json = MachineRecipe.CODEC.codec().encodeStart(helper.getLevel().registryAccess()
                    .createSerializationContext(JsonOps.INSTANCE), recipe).getOrThrow().getAsJsonObject();
            json.addProperty("type", "mmcr:machine_recipe");
            MachineRecipe decoded = MachineRecipeJson.parse(recipe.id(), json, helper.getLevel().registryAccess(),
                    pool -> fixture.machine.recipePoolIds().contains(pool));
            helper.assertTrue(decoded.runtimeRequirements().contains(ManaRequirement.input(25_000)), "Mana input survives actual parser");
            helper.assertTrue(decoded.machineOutputs().contains(new ManaOutput(10_000)), "Mana output survives actual parser");
            helper.assertTrue(decoded.runtimeRequirements().stream().anyMatch(requirement -> requirement instanceof ItemRequirement item
                    && item.io().isInput() && item.item().test(Items.IRON_INGOT.getDefaultInstance())), "Normal iron requirement shares planning");
            helper.assertTrue(decoded.machineOutputs().stream().anyMatch(output -> output instanceof MachineOutput.ItemOutput item
                    && item.stack().is(Items.GOLD_INGOT)), "Normal gold output retained");

            ManaStorage input = new ManaStorage(() -> {});
            ManaStorage output = new ManaStorage(() -> {});
            input.setAmount(45_000);
            output.setAmount(12_000);
            var view = new MachineIoView(new CapabilitySnapshot(List.of(
                    new ManaPortCapability(null, input, IOType.INPUT), new ManaPortCapability(null, output, IOType.OUTPUT))));
            List<Component> text = new ArrayList<>();
            var callbackContext = new MachineBehaviorContext(null, helper.getLevel(), BlockPos.ZERO, MACHINE, 0,
                    textCollector(text), null, view);
            ((RecipeBehavior) fixture.machine.behavior()).preServerTick().accept(callbackContext);
            helper.assertTrue(text.equals(List.of(Component.translatable("gui.mmcr.example.mana_available", "45,000", "988,000"))),
                    "Executed script reads real mana snapshot through ioView and localizes the result");
            helper.assertTrue(input.amount() == 45_000 && output.amount() == 12_000, "Example snapshot reads never transfer mana");
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read Botania mana example", exception);
        } finally {
            if (previous == null) ModBlocks.BLOCKS.remove(controllerKey);
            else ModBlocks.BLOCKS.put(controllerKey, previous);
        }
        helper.succeed();
    }

    private static ControllerScreenText textCollector(List<Component> text) {
        return new ControllerScreenText() {
            @Override public void append(ControllerScreenTextScope scope, ResourceLocation id, Component value) { text.add(value); }
            @Override public void appendAfter(ControllerScreenTextScope scope, ResourceLocation id, ResourceLocation after, Component value) { text.add(value); }
            @Override public void replace(ResourceLocation id, Component value) { text.add(value); }
            @Override public void remove(ControllerScreenTextScope scope, ResourceLocation id) {}
            @Override public void clear(ControllerScreenTextScope scope) { text.clear(); }
        };
    }

    private static Path examples() {
        for (Path root = Path.of("").toAbsolutePath(); root != null; root = root.getParent()) {
            Path examples = root.resolve("example/botania_mana_pools");
            if (Files.isDirectory(examples)) return examples;
        }
        throw new IllegalStateException("Cannot locate example/botania_mana_pools");
    }

    /** Event boundary capturing registration without adding the example as a permanent machine.
     * @author howxu <dev@howxu.cn>
     */
    public static final class ExampleFixture {
        private MachineRegistration machine;
        private MachineStructureDefinition structure;
        private final List<MachineRecipe> recipes = new ArrayList<>();

        public KubeJSApi getAPI() { return new KubeJSApi(); }
        public MachineBuilderJS createMachine(String id) { return new CapturedMachine(id, this); }
        public MachineStructureBuilderJS createStructure(String id) { return new CapturedStructure(id, this); }
        public MachineRecipeBuilderJS createRecipe(String id) { return new CapturedRecipe(id, this); }
    }

    /** Real startup builder with only the publication boundary captured.
     * @author howxu <dev@howxu.cn>
     */
    public static final class CapturedMachine extends MachineBuilderJS {
        private final ExampleFixture fixture;
        public CapturedMachine(String id, ExampleFixture fixture) { super(id); this.fixture = fixture; }
        @Override public MachineBuilderJS register() { fixture.machine = createObject(); return this; }
    }

    /** Real pattern builder with only the publication boundary captured.
     * @author howxu <dev@howxu.cn>
     */
    public static final class CapturedStructure extends MachineStructureBuilderJS {
        private final ExampleFixture fixture;
        public CapturedStructure(String id, ExampleFixture fixture) { super(id); this.fixture = fixture; }
        @Override public void build() { fixture.structure = createObject(); }
    }

    /** Real recipe helpers; the fixture's captured startup declaration supplies pool membership.
     * @author howxu <dev@howxu.cn>
     */
    public static final class CapturedRecipe extends MachineRecipeBuilderJS {
        private final ExampleFixture fixture;
        public CapturedRecipe(String id, ExampleFixture fixture) { super(id); this.fixture = fixture; }
        @Override public MachineRecipeBuilderJS recipePool(String id) {
            ResourceLocation pool = ResourceLocation.parse(id);
            if (fixture.machine == null || !fixture.machine.recipePoolIds().contains(pool)) {
                throw new IllegalArgumentException("Example recipe has no startup pool: " + id);
            }
            recipePoolId = pool;
            return this;
        }
        @Override public void build() { fixture.recipes.add(createObject()); }
    }
}
