package example.addon;

import cn.howxu.mmcr.publicapi.recipe.*;
import cn.howxu.mmcr.publicapi.recipe.component.ComponentConstraints;
import cn.howxu.mmcr.publicapi.recipe.extension.PlanningView;
import cn.howxu.mmcr.publicapi.recipe.extension.RequirementPlanSpec;
import cn.howxu.mmcr.publicapi.recipe.modifier.RecipeAdjustmentSpec;
import cn.howxu.mmcr.publicapi.recipe.requirement.*;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** A downstream extension using only the released API and vanilla item buses.
 * @author howxu <dev@howxu.cn>
 */
public final class ShardAddon {
    private ShardAddon() {}
    public record Shard(boolean red, boolean output, int count, float chance, boolean projected) {
        static final MapCodec<Shard> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.BOOL.fieldOf("red").forGetter(Shard::red), Codec.BOOL.fieldOf("output").forGetter(Shard::output),
                Codec.intRange(1, 64).fieldOf("count").forGetter(Shard::count), Codec.FLOAT.fieldOf("chance").forGetter(Shard::chance),
                Codec.BOOL.fieldOf("projected").forGetter(Shard::projected)).apply(i, Shard::new));
        Shard withChance(float c) { return new Shard(red, output, count, c, projected); }
        RequirementSpec item() {
            var item = red ? Items.RED_DYE : Items.BLUE_DYE;
            return Requirements.item(output ? IoDirection.OUTPUT : IoDirection.INPUT, output ? null : Ingredient.of(item),
                    output ? 0 : count, output ? new ItemStack(item, count) : ItemStack.EMPTY, output ? chance : 1F,
                    List.of(), ComponentConstraints.EMPTY, output ? 1F : chance);
        }
    }
    private static final RequirementExecution<Shard> MATERIAL = new RequirementExecution<>() {
        public RequirementPlanSpec plan(Shard value, PlanningView context) { return context.plan(value.item()); }
        public boolean paysAtStart(Shard value) { return !value.output(); }
        public Shard asRetainedInput(Shard value) { return value.withChance(0F); }
        public long batchAmount(Shard value, int duration) { return value.projected() ? value.count() : -1; }
        public ResourceLocation batchIdentity(Shard value) { return ResourceLocation.parse(value.red() ? "test:red" : "test:blue"); }
        public float consumptionProbability(Shard value) { return value.chance(); }
        public RequirementSpec searchProjection(Shard value) { return value.projected() ? value.item() : null; }
    };
    public static final RequirementKind<Shard> INPUT = register("shard", MATERIAL);
    public static final RequirementKind<Shard> LEGACY = register("legacy_shard", (value, context) -> context.plan(value.item()));
    private static RequirementKind<Shard> register(String name, RequirementExecution<Shard> execution) {
        return RequirementKinds.register(new RequirementExtension<Shard>() {
            public ResourceLocation id() { return ResourceLocation.fromNamespaceAndPath("test", name); }
            public Set<ResourceLocation> capabilityIds() { return Set.of(ResourceLocation.parse("minecraft:item")); }
            public MapCodec<Shard> codec() { return Shard.CODEC; }
            public IoDirection io(Shard value) { return value.output() ? IoDirection.OUTPUT : IoDirection.INPUT; }
            public List<String> tags(Shard value) { return List.of(); }
            public Shard copy(Shard value) { return value; }
            public RequirementExecution<Shard> execution() { return execution; }
        });
    }
    public static final OutputKind<Shard> OUTPUT = OutputKinds.register(new OutputExtension<Shard>() {
        public ResourceLocation id() { return INPUT.id(); }
        public MapCodec<Shard> codec() { return Shard.CODEC; }
        public Shard copy(Shard value) { return value; }
        public float chance(Shard value) { return value.chance(); }
        public long amount(Shard value) { return value.count(); }
        public ResourceLocation aggregationIdentity(Shard value) { return ResourceLocation.parse(value.red() ? "test:red" : "test:blue"); }
        public Shard withChance(Shard value, float chance) { return value.withChance(chance); }
        public Shard applyModifiers(Shard value, List<RecipeAdjustmentSpec> modifiers) { return value; }
        public RequirementSpec toRequirement(Shard value, List<String> tags) { return Requirements.extension(INPUT, value); }
        public boolean matchesRequirement(RequirementSpec requirement) { return fromRequirement(requirement).isPresent(); }
        public Optional<Shard> fromRequirement(RequirementSpec requirement) { return INPUT.payload(requirement).filter(Shard::output); }
    });
}
