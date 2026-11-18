package example.addon;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;
import cn.howxu.mmcr.api.recipe.MachineOutput;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.adapter.InterfaceFamilyContributor;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.jei.PatternEncodingContribution;
import cn.howxu.mmcr.compat.jei.MachineRecipeDisplay;
import cn.howxu.mmcr.compat.jei.JeiDisplayEntry;
import cn.howxu.mmcr.compat.jei.JeiIngredientAdapter;
import cn.howxu.mmcr.compat.jei.RecipeIoEntry;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import net.minecraft.world.item.Items;
import cn.howxu.mmcr.publicapi.recipe.Outputs;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/** Optional consumers installed by the test addon, not by any MMCR family table.
 * @author howxu <dev@howxu.cn>
 */
public final class ShardConsumers implements InterfaceFamilyContributor, PatternEncodingContribution {
    private static final ResourceLocation ID = ResourceLocation.parse("test:shard");
    public static final class Key extends AEKey {
        public static final Key RED = new Key(true), BLUE = new Key(false);
        private final boolean red;
        private Key(boolean red) { this.red = red; }
        public AEKeyType getType() { return TYPE; }
        public AEKey dropSecondary() { return this; }
        public Object getPrimaryKey() { return this; }
        public ResourceLocation getId() { return ResourceLocation.parse(red ? "test:red" : "test:blue"); }
        public boolean hasComponents() { return false; }
        protected Component computeDisplayName() { return Component.literal(getId().toString()); }
        public CompoundTag toTag(HolderLookup.Provider registries) { var tag = new CompoundTag(); tag.putBoolean("red", red); return tag; }
        public void writeToPacket(RegistryFriendlyByteBuf data) { data.writeBoolean(red); }
        public void addDrops(long amount, List<ItemStack> drops, Level level, BlockPos pos) {}
    }
    public static final AEKeyType TYPE = new AEKeyType(ID, Key.class, Component.literal("test:shard")) {
        public MapCodec<Key> codec() { return Codec.BOOL.xmap(r -> r ? Key.RED : Key.BLUE, k -> k.red).fieldOf("red"); }
        public Key readFromPacket(RegistryFriendlyByteBuf input) { return input.readBoolean() ? Key.RED : Key.BLUE; }
    };
    private static Optional<ShardAddon.Shard> mapped(MachineRequirement requirement) {
        if (!requirement.type().id().equals(ID)) return Optional.empty();
        return InterfaceFamilyContributor.payload(ShardAddon.INPUT, requirement).filter(ShardAddon.Shard::projected);
    }
    public static final class Display implements JeiIngredientAdapter {
        public ResourceLocation typeId() { return ID; }
        public IIngredientType<?> ingredientType() { return VanillaTypes.ITEM_STACK; }
        public Optional<IRecipeTransferHandler<?, ?>> transferHandler() { return Optional.empty(); }
        public Optional<JeiDisplayEntry> display(RecipeIoEntry entry) {
            return entry.requirement().flatMap(ShardAddon.INPUT::payload).map(shard ->
                    new JeiDisplayEntry(entry.role(), ID, ingredientType(), new ItemStack(shard.red() ? Items.RED_DYE : Items.BLUE_DYE),
                            shard.count(), shard.chance(), null, false, shard.count()));
        }
    }
    @Override public boolean owns(AEKeyType type) { return type == TYPE; }
    @Override public boolean acceptsPatternKey(AEKey key) { return key instanceof Key; }
    @Override public MachineOutput patternOutput(AEKey key, long amount) {
        if (!(key instanceof Key shard) || amount < 1 || amount > 64) return null;
        return InterfaceFamilyContributor.patternOutput(Outputs.extension(ShardAddon.OUTPUT, new ShardAddon.Shard(shard.red, true, (int) amount, 1F, true)));
    }
    @Override public Predicate<AEKey> inputMatcher(MachineRequirement input) {
        return mapped(input).<Predicate<AEKey>>map(value -> key -> key == (value.red() ? Key.RED : Key.BLUE)).orElse(null);
    }
    @Override public List<Slot> slots(MachineRecipeDisplay display) {
        return display.recipe().runtimeRequirements().stream().flatMap(value -> mapped(value).stream())
                .map(shard -> new Slot(shard.output(), new GenericStack(shard.red() ? Key.RED : Key.BLUE, shard.count()))).toList();
    }
}
