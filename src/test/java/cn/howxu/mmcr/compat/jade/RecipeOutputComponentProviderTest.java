package cn.howxu.mmcr.compat.jade;

import cn.howxu.mmcr.LevelStub;
import cn.howxu.mmcr.api.recipe.MachineOutput;
import cn.howxu.mmcr.api.recipe.MachineOutputAmount;
import cn.howxu.mmcr.api.recipe.OutputRegistry;
import cn.howxu.mmcr.api.recipe.OutputType;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier.IOType;
import cn.howxu.mmcr.api.recipe.requirement.EnergyRequirement;
import cn.howxu.mmcr.compat.mekanism.MekanismBridgeBootstrap;
import cn.howxu.mmcr.compat.mekanism.loaded.LoadedChemicalOutput;
import cn.howxu.mmcr.compat.mekanism.loaded.jade.ChemicalOutputRenderer;
import cn.howxu.mmcr.compat.mekanism.loaded.LoadedMekanismBridge;
import cn.howxu.mmcr.test.TestBootstrap;
import com.mojang.serialization.Codec;
import com.mojang.serialization.Lifecycle;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import mekanism.api.MekanismAPI;
import mekanism.api.chemical.Chemical;
import mekanism.api.chemical.ChemicalBuilder;
import net.minecraft.client.gui.layouts.LayoutElement;
import net.minecraft.client.resources.language.ClientLanguage;
import net.minecraft.core.Holder;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.locale.Language;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.ITooltip;
import snownee.jade.api.TooltipPosition;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class RecipeOutputComponentProviderTest {

    @BeforeAll
    static void bootstrap() throws Exception {
        MekanismBridgeBootstrap.installForTesting(new LoadedMekanismBridge());
        TestBootstrap.bootstrap();
        OutputRegistry.register(LoadedChemicalOutput.TYPE);
        ChemicalOutputRenderer.register();
        OutputRegistry.register(CountingOutput.TYPE);
        // Unit tests don't load resource packs, so seed the translation we assert against.
        var ctor = ClientLanguage.class.getDeclaredConstructor(Map.class, boolean.class);
        ctor.setAccessible(true);
        Language.inject(ctor.newInstance(Map.of(
                "jade.mmcr.machine_controller.recipe_output", "Recipe Output",
                "jade.mmcr.machine_controller.recipe_output.fluid", "%s %s",
                "jade.mmcr.machine_controller.recipe_output.generic", "%s × %s",
                "chat.square_brackets", "[%s]"), false));
    }

    @AfterAll
    static void resetMek() {
        MekanismBridgeBootstrap.resetForTesting();
    }

    @Test
    void rendersLabelForItemOutputs() {
        CompoundTag data = new CompoundTag();
        RecipeOutputCodec.write(data, List.of(new MachineOutputAmount(
                new MachineOutput.ItemOutput(new ItemStack(Items.STONE, 2), 1F), 2L)));
        List<Object> calls = collect(data);
        assertThat(calls.getFirst()).isInstanceOf(Component.class);
        assertThat(((Component) calls.getFirst()).getString()).contains("Recipe Output");
    }

    @Test
    void rendersEnchantedParallelOutputUsingTheClientRegistryContext() throws Exception {
        var serverRegistries = outputRegistryAccess();
        var clientRegistries = outputRegistryAccess();
        var serverOps = serverRegistries.createSerializationContext(NbtOps.INSTANCE);
        var clientOps = clientRegistries.createSerializationContext(NbtOps.INSTANCE);
        ItemStack diamond = new ItemStack(Items.DIAMOND, 9);
        diamond.set(DataComponents.CUSTOM_NAME, Component.literal("What a magic recipe"));
        diamond.enchant(serverRegistries.lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(Enchantments.SHARPNESS), 4);
        CompoundTag data = new CompoundTag();
        RecipeOutputCodec.write(data, List.of(new MachineOutputAmount(
                new MachineOutput.ItemOutput(diamond, 1F), 36L)), serverOps);

        assertThat(RecipeOutputCodec.read(data, clientOps)).singleElement().satisfies(output -> {
            ItemStack stack = ((MachineOutput.ItemOutput) output.output()).resolvedStack();
            assertThat(stack.getItem()).isSameAs(Items.DIAMOND);
            assertThat(stack.getCount()).isEqualTo(1);
            assertThat(stack.getHoverName()).isEqualTo(diamond.getHoverName());
            assertThat(stack.get(DataComponents.ENCHANTMENTS).getLevel(clientRegistries
                    .lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SHARPNESS))).isEqualTo(4);
            assertThat(output.amount()).isEqualTo(36L);
        });
        assertThat(RecipeOutputCodec.read(data)).isEmpty();
        Level level = LevelStub.create(Map.of());
        var registryAccess = Level.class.getDeclaredField("registryAccess");
        registryAccess.setAccessible(true);
        registryAccess.set(level, clientRegistries);
        assertThat(collectCalls(data, level).stream().map(TooltipCall::value).toList())
                .contains(Component.translatable("jade.mmcr.machine_controller.recipe_output"));
        assertThat(diamond.getCount()).isEqualTo(9);
    }

    @Test
    void tooltipTakesOneOwnedCopyToClassifyEachOutput() {
        CompoundTag data = new CompoundTag();
        RecipeOutputCodec.write(data, List.of(new MachineOutputAmount(new CountingOutput(5L, 1F), 4_294_967_297L)));
        CountingOutput.COPIES.set(0);
        assertThat(RecipeOutputCodec.read(data)).hasSize(1);
        int decodeCopies = CountingOutput.COPIES.get();
        CountingOutput.COPIES.set(0);

        assertThat(collect(data)).filteredOn(Component.class::isInstance)
                .extracting(value -> ((Component) value).getString())
                .contains("4294967297 × mmcr_test:jade_counting_output");
        assertThat(CountingOutput.COPIES).hasValue(decodeCopies + 1);
    }

    @Test
    void emptyOutputsProduceNothing() {
        CompoundTag data = new CompoundTag();
        List<Object> calls = collect(data);
        assertThat(calls).isEmpty();
    }

    @Test
    void priorityIsTooltipTail() {
        assertThat(RecipeOutputComponentProvider.INSTANCE.getDefaultPriority()).isEqualTo(TooltipPosition.TAIL - 9);
    }

    @Test
    void rendersChemicalOutputLineForRegisteredChemical() {
        Holder.Reference<Chemical> chemical = registerChemical("jade_render_test");
        CompoundTag data = new CompoundTag();
        RecipeOutputCodec.write(data, List.of(new MachineOutputAmount(
                new LoadedChemicalOutput(chemical.key().identifier(), 200L, 1F), 200L)));

        List<Object> calls = collect(data);

        assertThat(calls).isNotEmpty();
        assertThat(((Component) calls.getFirst()).getString()).contains("Recipe Output");
        String translationKey = "chemical.mmcr_test.jade_render_test";
        boolean hasChemicalKey = calls.stream().anyMatch(call -> call instanceof Component component
                && component.getString().contains(translationKey));
        assertThat(hasChemicalKey).isTrue();
    }


    @Test
    void chemicalOutputContributesItsConfiguredAmountToControllerDisplay() {
        Holder.Reference<Chemical> chemical = registerChemical("jade_output_amount_test");

        assertThat(MachineOutput.scaledAmount(new LoadedChemicalOutput(chemical.key().identifier(), 200L, 1F)))
                .isEqualTo(200L);
    }

    @Test
    void skipsChemicalOutputWhenChemicalIsNotRegistered() {
        CompoundTag data = new CompoundTag();
        RecipeOutputCodec.write(data, List.of(new MachineOutputAmount(
                new LoadedChemicalOutput(Identifier.fromNamespaceAndPath("mmcr_test", "missing_chemical"),
                        200L, 1F), 200L)));

        List<Object> calls = collect(data);

        assertThat(calls).isEmpty();
    }

    private static List<Object> collect(CompoundTag data) {
        return collectCalls(data).stream().map(TooltipCall::value).toList();
    }

    private static List<TooltipCall> collectCalls(CompoundTag data) {
        return collectCalls(data, LevelStub.create(Map.of()));
    }

    private static List<TooltipCall> collectCalls(CompoundTag data, Level level) {
        BlockAccessor accessor = (BlockAccessor) Proxy.newProxyInstance(
                BlockAccessor.class.getClassLoader(), new Class<?>[]{BlockAccessor.class},
                (_, method, _) -> {
                    if ("getServerData".equals(method.getName())) return data;
                    if ("getLevel".equals(method.getName())) return level;
                    throw new UnsupportedOperationException(method.getName());
                });
        List<TooltipCall> calls = new ArrayList<>();
        ITooltip tooltip = (ITooltip) Proxy.newProxyInstance(
                ITooltip.class.getClassLoader(), new Class<?>[]{ITooltip.class},
                (_, method, args) -> {
                    if (args == null || args.length != 1) return null;
                    if (args[0] instanceof Component || args[0] instanceof LayoutElement) {
                        calls.add(new TooltipCall(method.getName(), args[0]));
                    }
                    return null;
                });
        // JadeUI.smallItem / JadeUI.fluid touch Minecraft.getInstance().font eagerly, which is null
        // under gradle test. The assertions only inspect calls observed before that NPE; swallow it
        // so the recorded label / layout elements can still be asserted.
        try {
            RecipeOutputComponentProvider.INSTANCE.appendTooltip(tooltip, accessor, null);
        } catch (NullPointerException ignored) {
            // downstream rendering failure in unit test context
        }
        return calls;
    }

    private record TooltipCall(String method, Object value) {}

    private static RegistryAccess outputRegistryAccess() {
        MappedRegistry<Enchantment> enchantments = new MappedRegistry<>(Registries.ENCHANTMENT, Lifecycle.stable());
        VanillaRegistries.createLookup().lookupOrThrow(Registries.ENCHANTMENT).listElements()
                .forEach(holder -> Registry.register(enchantments, holder.key().identifier(), holder.value()));
        enchantments.freeze();
        List<Registry<?>> registries = new ArrayList<>();
        RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY)
                .registries().forEach(entry -> registries.add(entry.value()));
        registries.add(enchantments);
        return new RegistryAccess.ImmutableRegistryAccess(registries);
    }

    /** @author howxu <dev@howxu.cn> */
    private record CountingOutput(long amount, float chance) implements MachineOutput {
        private static final Identifier ID = Identifier.fromNamespaceAndPath("mmcr_test", "jade_counting_output");
        private static final AtomicInteger COPIES = new AtomicInteger();
        private static final OutputType<CountingOutput> TYPE = new OutputType.Definition<>(ID,
                RecordCodecBuilder.mapCodec(instance -> instance.group(
                        Codec.STRING.fieldOf("type").forGetter(ignored -> ID.toString()),
                        Codec.LONG.fieldOf("amount").forGetter(CountingOutput::amount),
                        Codec.FLOAT.fieldOf("chance").forGetter(CountingOutput::chance)
                ).apply(instance, (ignored, amount, chance) -> new CountingOutput(amount, chance))),
                (output, chance) -> new CountingOutput(output.amount(), chance),
                (output, modifiers) -> output,
                output -> {
                    COPIES.incrementAndGet();
                    return new CountingOutput(output.amount(), output.chance());
                }, OutputType.Presentation.defaults(ID), ID.toString(),
                (output, tags) -> new EnergyRequirement(IOType.OUTPUT, output.amount(), tags),
                requirement -> false);

        @Override
        public OutputType<CountingOutput> outputType() {
            return TYPE;
        }
    }

    private static Holder.Reference<Chemical> registerChemical(String name) {
        ResourceKey<Chemical> key = ResourceKey.create(MekanismAPI.CHEMICAL_REGISTRY_NAME,
                Identifier.fromNamespaceAndPath("mmcr_test", name));
        MappedRegistry<Chemical> registry = (MappedRegistry<Chemical>) MekanismAPI.CHEMICAL_REGISTRY;
        return registry.get(key).orElseGet(() -> {
            registry.unfreeze(true);
            Chemical value = new Chemical(ChemicalBuilder.builder()) {
                @Override
                public boolean isRadioactive() {
                    return false;
                }
            };
            Registry.register(registry, key.identifier(), value);
            registry.freeze();
            return registry.get(key).orElseThrow();
        });
    }
}
