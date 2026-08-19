package cn.howxu.mmcr.internal.sync;

import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.api.machine.DynamicPatternSpec;
import cn.howxu.mmcr.api.machine.MachineStructureDefinition;
import cn.howxu.mmcr.api.machine.MachineStructureRequirements;
import cn.howxu.mmcr.api.machine.PortRequirementSpec;
import cn.howxu.mmcr.api.machine.PortTierRequirementSpec;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.recipe.modifier.SingleBlockModifierReplacement;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import net.minecraft.core.registries.Registries;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Network codec for server-authored runtime machine structure definitions.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class MachineStructureSyncCodec {

    private static final int MAX_DECLARATIONS = 1024;
    private static final int MAX_BLOCKS = 65536;
    private static final int MAX_TAGS = 1024;
    private static final int MAX_PORT_REQUIREMENTS = 1024;
    private static final int MAX_TIER_REQUIREMENTS = 1024;
    private static final int MAX_DYNAMIC_PATTERNS = 1024;
    private static final int MAX_REPLACEMENTS = 1024;
    private static final int MAX_SYMBOL_REQUIREMENTS = 256;
    private static final int MAX_CHILD_PREDICATES = 1024;
    private static final int MAX_MODIFIERS = 1024;
    private static final int MAX_FACES = Direction.values().length;

    private MachineStructureSyncCodec() {
    }

    public static void encode(RegistryFriendlyByteBuf buf, MachineStructureDefinition value) {
        Identifier.STREAM_CODEC.encode(buf, value.machineId());
        checkSize(value.declarations().size(), MAX_DECLARATIONS, "declaration");
        buf.writeVarInt(value.declarations().size());
        for (MachineStructureDefinition.Declaration declaration : value.declarations()) {
            writeDeclaration(buf, declaration);
        }
    }

    public static MachineStructureDefinition decode(RegistryFriendlyByteBuf buf) {
        Identifier machineId = Identifier.STREAM_CODEC.decode(buf);
        int count = buf.readVarInt();
        checkSize(count, MAX_DECLARATIONS, "declaration");
        List<MachineStructureDefinition.Declaration> declarations = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            declarations.add(readDeclaration(buf));
        }
        return new MachineStructureDefinition(machineId, declarations);
    }

    private static void writeDeclaration(RegistryFriendlyByteBuf buf, MachineStructureDefinition.Declaration declaration) {
        buf.writeEnum(declaration.kind());
        writeBlockArray(buf, declaration.pattern());
        writePortRequirements(buf, declaration.portRequirements());
        writePortTierRequirements(buf, declaration.portTierRequirements());
        writeDynamicPatterns(buf, declaration.dynamicPatterns());
        writeRequirements(buf, declaration.requirements());
    }

    private static MachineStructureDefinition.Declaration readDeclaration(RegistryFriendlyByteBuf buf) {
        MachineStructureDefinition.Declaration.Kind kind = buf.readEnum(MachineStructureDefinition.Declaration.Kind.class);
        BlockArray pattern = readBlockArray(buf);
        PortRequirementSpec portRequirements = readPortRequirements(buf);
        PortTierRequirementSpec portTierRequirements = readPortTierRequirements(buf);
        List<DynamicPatternSpec> dynamicPatterns = readDynamicPatterns(buf);
        MachineStructureRequirements requirements = readRequirements(buf);
        return new MachineStructureDefinition.Declaration(kind, pattern, portRequirements, portTierRequirements,
                dynamicPatterns, requirements);
    }

    private static void writeBlockArray(RegistryFriendlyByteBuf buf, BlockArray value) {
        checkSize(value.pattern().size(), MAX_BLOCKS, "block pattern");
        buf.writeVarInt(value.pattern().size());
        for (var entry : value.pattern().entrySet()) {
            buf.writeBlockPos(entry.getKey());
            writeBlockPredicate(buf, entry.getValue());
        }
        checkSize(value.tagsByPosition().size(), MAX_TAGS, "block tag position");
        buf.writeVarInt(value.tagsByPosition().size());
        for (var entry : value.tagsByPosition().entrySet()) {
            buf.writeBlockPos(entry.getKey());
            writeStringList(buf, entry.getValue());
        }
        checkSize(value.symbolsByPosition().size(), MAX_BLOCKS, "block symbol position");
        buf.writeVarInt(value.symbolsByPosition().size());
        for (var entry : value.symbolsByPosition().entrySet()) {
            buf.writeBlockPos(entry.getKey());
            buf.writeChar(entry.getValue());
        }
    }

    private static BlockArray readBlockArray(RegistryFriendlyByteBuf buf) {
        int patternCount = buf.readVarInt();
        checkSize(patternCount, MAX_BLOCKS, "block pattern");
        Map<BlockPos, BlockPredicate> pattern = new LinkedHashMap<>();
        for (int i = 0; i < patternCount; i++) {
            pattern.put(buf.readBlockPos(), readBlockPredicate(buf));
        }

        int tagCount = buf.readVarInt();
        checkSize(tagCount, MAX_TAGS, "block tag position");
        Map<BlockPos, List<String>> tags = new LinkedHashMap<>();
        for (int i = 0; i < tagCount; i++) {
            tags.put(buf.readBlockPos(), readStringList(buf, MAX_TAGS, "block tag"));
        }
        int symbolCount = buf.readVarInt();
        checkSize(symbolCount, MAX_BLOCKS, "block symbol position");
        Map<BlockPos, Character> symbols = new LinkedHashMap<>();
        for (int i = 0; i < symbolCount; i++) {
            symbols.put(buf.readBlockPos(), buf.readChar());
        }
        return new BlockArray(pattern, tags, symbols);
    }

    private static void writeBlockPredicate(RegistryFriendlyByteBuf buf, BlockPredicate predicate) {
        switch (predicate) {
            case BlockPredicate.MachineCoupler ignored -> buf.writeEnum(PredicateKind.MACHINE_COUPLER);
            case BlockPredicate.Air ignored -> buf.writeEnum(PredicateKind.AIR);
            case BlockPredicate.Any ignored -> buf.writeEnum(PredicateKind.ANY);
            case BlockPredicate.OfBlock ofBlock -> {
                buf.writeEnum(PredicateKind.BLOCK);
                Identifier.STREAM_CODEC.encode(buf, BuiltInRegistries.BLOCK.getKey(ofBlock.block()));
            }
            case BlockPredicate.OfBlockState ofState -> {
                buf.writeEnum(PredicateKind.BLOCK_STATE);
                writeBlockState(buf, ofState.state());
            }
            case BlockPredicate.OfTag ofTag -> {
                buf.writeEnum(PredicateKind.TAG);
                Identifier.STREAM_CODEC.encode(buf, ofTag.tag().location());
            }
            case BlockPredicate.AnyOf anyOf -> {
                buf.writeEnum(PredicateKind.ANY_OF);
                checkSize(anyOf.children().size(), MAX_CHILD_PREDICATES, "child predicate");
                buf.writeVarInt(anyOf.children().size());
                for (BlockPredicate child : anyOf.children()) {
                    writeBlockPredicate(buf, child);
                }
            }
        }
    }

    private static BlockPredicate readBlockPredicate(RegistryFriendlyByteBuf buf) {
        return switch (buf.readEnum(PredicateKind.class)) {
            case MACHINE_COUPLER -> BlockPredicate.machineCoupler();
            case AIR -> new BlockPredicate.Air();
            case ANY -> new BlockPredicate.Any();
            case BLOCK -> new BlockPredicate.OfBlock(BuiltInRegistries.BLOCK.getValue(Identifier.STREAM_CODEC.decode(buf)));
            case BLOCK_STATE -> new BlockPredicate.OfBlockState(readBlockState(buf));
            case TAG -> new BlockPredicate.OfTag(TagKey.create(Registries.BLOCK,
                    Identifier.STREAM_CODEC.decode(buf)));
            case ANY_OF -> {
                int count = buf.readVarInt();
                checkSize(count, MAX_CHILD_PREDICATES, "child predicate");
                List<BlockPredicate> children = new ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                    children.add(readBlockPredicate(buf));
                }
                yield new BlockPredicate.AnyOf(children);
            }
        };
    }

    private static void writeBlockState(RegistryFriendlyByteBuf buf, BlockState state) {
        Identifier.STREAM_CODEC.encode(buf, BuiltInRegistries.BLOCK.getKey(state.getBlock()));
        checkSize(state.getProperties().size(), 64, "block state property");
        buf.writeVarInt(state.getProperties().size());
        for (Property<?> property : state.getProperties()) {
            ByteBufCodecs.STRING_UTF8.encode(buf, property.getName());
            ByteBufCodecs.STRING_UTF8.encode(buf, propertyValueName(state, property));
        }
    }

    private static BlockState readBlockState(RegistryFriendlyByteBuf buf) {
        Block block = BuiltInRegistries.BLOCK.getValue(Identifier.STREAM_CODEC.decode(buf));
        BlockState state = block.defaultBlockState();
        int propertyCount = buf.readVarInt();
        checkSize(propertyCount, 64, "block state property");
        for (int i = 0; i < propertyCount; i++) {
            String propertyName = ByteBufCodecs.STRING_UTF8.decode(buf);
            String valueName = ByteBufCodecs.STRING_UTF8.decode(buf);
            Property<?> property = state.getBlock().getStateDefinition().getProperty(propertyName);
            if (property != null) state = setPropertyValue(state, property, valueName);
        }
        return state;
    }

    private static <T extends Comparable<T>> String propertyValueName(BlockState state, Property<T> property) {
        return property.getName(state.getValue(property));
    }

    private static <T extends Comparable<T>> BlockState setPropertyValue(BlockState state, Property<T> property, String valueName) {
        return property.getValue(valueName).map(value -> state.setValue(property, value)).orElse(state);
    }

    private static void writePortRequirements(RegistryFriendlyByteBuf buf, PortRequirementSpec value) {
        checkSize(value.requirements().size(), MAX_PORT_REQUIREMENTS, "port requirement");
        buf.writeVarInt(value.requirements().size());
        for (var entry : value.requirements().entrySet()) {
            ByteBufCodecs.STRING_UTF8.encode(buf, entry.getKey());
            buf.writeVarInt(entry.getValue().min());
            buf.writeBoolean(entry.getValue().max().isPresent());
            entry.getValue().max().ifPresent(buf::writeVarInt);
        }
    }

    private static PortRequirementSpec readPortRequirements(RegistryFriendlyByteBuf buf) {
        int count = buf.readVarInt();
        checkSize(count, MAX_PORT_REQUIREMENTS, "port requirement");
        Map<String, PortRequirementSpec.CountRange> requirements = new LinkedHashMap<>();
        for (int i = 0; i < count; i++) {
            String id = ByteBufCodecs.STRING_UTF8.decode(buf);
            int min = buf.readVarInt();
            requirements.put(id, buf.readBoolean()
                    ? PortRequirementSpec.CountRange.range(min, buf.readVarInt())
                    : PortRequirementSpec.CountRange.min(min));
        }
        return requirements.isEmpty() ? PortRequirementSpec.none() : new PortRequirementSpec(requirements);
    }

    private static void writePortTierRequirements(RegistryFriendlyByteBuf buf, PortTierRequirementSpec value) {
        checkSize(value.requirements().size(), MAX_TIER_REQUIREMENTS, "port tier requirement");
        buf.writeVarInt(value.requirements().size());
        for (PortTierRequirementSpec.Requirement requirement : value.requirements()) {
            buf.writeEnum(requirement.category());
            buf.writeEnum(requirement.ioType());
            buf.writeVarInt(requirement.minTier());
            ByteBufCodecs.STRING_UTF8.encode(buf, requirement.minTierId());
        }
    }

    private static PortTierRequirementSpec readPortTierRequirements(RegistryFriendlyByteBuf buf) {
        int count = buf.readVarInt();
        checkSize(count, MAX_TIER_REQUIREMENTS, "port tier requirement");
        List<PortTierRequirementSpec.Requirement> requirements = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            requirements.add(new PortTierRequirementSpec.Requirement(buf.readEnum(PortTierRequirementSpec.PortCategory.class),
                    buf.readEnum(IOType.class), buf.readVarInt(), ByteBufCodecs.STRING_UTF8.decode(buf)));
        }
        return requirements.isEmpty() ? PortTierRequirementSpec.none() : new PortTierRequirementSpec(requirements);
    }

    private static void writeDynamicPatterns(RegistryFriendlyByteBuf buf, List<DynamicPatternSpec> values) {
        checkSize(values.size(), MAX_DYNAMIC_PATTERNS, "dynamic pattern");
        buf.writeVarInt(values.size());
        for (DynamicPatternSpec value : values) {
            ByteBufCodecs.STRING_UTF8.encode(buf, value.name());
            writeBlockArray(buf, value.startPattern());
            buf.writeBoolean(value.endPattern() != null);
            if (value.endPattern() != null) writeBlockArray(buf, value.endPattern());
            buf.writeVarInt(value.minSize());
            buf.writeVarInt(value.maxSize());
            buf.writeBlockPos(value.offsetStart());
            buf.writeBlockPos(value.structureSizeOffset());
            checkSize(value.allowedFaces().size(), MAX_FACES, "allowed face");
            buf.writeVarInt(value.allowedFaces().size());
            for (Direction face : value.allowedFaces()) {
                buf.writeEnum(face);
            }
        }
    }

    private static List<DynamicPatternSpec> readDynamicPatterns(RegistryFriendlyByteBuf buf) {
        int count = buf.readVarInt();
        checkSize(count, MAX_DYNAMIC_PATTERNS, "dynamic pattern");
        List<DynamicPatternSpec> values = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String name = ByteBufCodecs.STRING_UTF8.decode(buf);
            BlockArray startPattern = readBlockArray(buf);
            BlockArray endPattern = buf.readBoolean() ? readBlockArray(buf) : null;
            int minSize = buf.readVarInt();
            int maxSize = buf.readVarInt();
            BlockPos offsetStart = buf.readBlockPos();
            BlockPos structureSizeOffset = buf.readBlockPos();
            int faceCount = buf.readVarInt();
            checkSize(faceCount, MAX_FACES, "allowed face");
            EnumSet<Direction> faces = EnumSet.noneOf(Direction.class);
            for (int j = 0; j < faceCount; j++) {
                faces.add(buf.readEnum(Direction.class));
            }
            values.add(new DynamicPatternSpec(name, startPattern, endPattern, minSize, maxSize,
                    offsetStart, structureSizeOffset, faces));
        }
        return List.copyOf(values);
    }

    private static void writeRequirements(RegistryFriendlyByteBuf buf, MachineStructureRequirements value) {
        writeModifierReplacements(buf, value.modifierReplacements());
        writeLevelSlots(buf, value.levelSlots());
    }

    private static MachineStructureRequirements readRequirements(RegistryFriendlyByteBuf buf) {
        return new MachineStructureRequirements(readModifierReplacements(buf), readLevelSlots(buf));
    }

    private static void writeModifierReplacements(RegistryFriendlyByteBuf buf,
            Map<Character, List<SingleBlockModifierReplacement>> values) {
        checkSize(values.size(), MAX_SYMBOL_REQUIREMENTS, "modifier replacement symbol");
        buf.writeVarInt(values.size());
        for (var entry : values.entrySet()) {
            buf.writeChar(entry.getKey());
            checkSize(entry.getValue().size(), MAX_REPLACEMENTS, "modifier replacement");
            buf.writeVarInt(entry.getValue().size());
            for (SingleBlockModifierReplacement replacement : entry.getValue()) {
                writeReplacement(buf, replacement);
            }
        }
    }

    private static Map<Character, List<SingleBlockModifierReplacement>> readModifierReplacements(RegistryFriendlyByteBuf buf) {
        int count = buf.readVarInt();
        checkSize(count, MAX_SYMBOL_REQUIREMENTS, "modifier replacement symbol");
        Map<Character, List<SingleBlockModifierReplacement>> values = new LinkedHashMap<>();
        for (int i = 0; i < count; i++) {
            char symbol = buf.readChar();
            int replacementCount = buf.readVarInt();
            checkSize(replacementCount, MAX_REPLACEMENTS, "modifier replacement");
            List<SingleBlockModifierReplacement> replacements = new ArrayList<>(replacementCount);
            for (int j = 0; j < replacementCount; j++) {
                replacements.add(readReplacement(buf));
            }
            values.put(symbol, List.copyOf(replacements));
        }
        return Map.copyOf(values);
    }

    private static void writeReplacement(RegistryFriendlyByteBuf buf, SingleBlockModifierReplacement replacement) {
        ByteBufCodecs.STRING_UTF8.encode(buf, replacement.getModifierName());
        writeBlockPredicate(buf, replacement.getReplacement());
        writeRecipeModifiers(buf, replacement.getModifiers());
        writeItemStack(buf, replacement.getDescriptiveStack());
    }

    private static SingleBlockModifierReplacement readReplacement(RegistryFriendlyByteBuf buf) {
        return new SingleBlockModifierReplacement(ByteBufCodecs.STRING_UTF8.decode(buf),
                readBlockPredicate(buf), readRecipeModifiers(buf), readItemStack(buf));
    }

    private static void writeItemStack(RegistryFriendlyByteBuf buf, ItemStack stack) {
        if (stack.isEmpty()) {
            buf.writeVarInt(0);
            return;
        }
        buf.writeVarInt(stack.getCount());
        Identifier.STREAM_CODEC.encode(buf, BuiltInRegistries.ITEM.getKey(stack.getItem()));
        DataComponentPatch.STREAM_CODEC.encode(buf, stack.getComponentsPatch());
    }

    private static ItemStack readItemStack(RegistryFriendlyByteBuf buf) {
        int count = buf.readVarInt();
        if (count <= 0) return ItemStack.EMPTY;
        var item = BuiltInRegistries.ITEM.getValue(Identifier.STREAM_CODEC.decode(buf));
        return new ItemStack(item.builtInRegistryHolder(), count, DataComponentPatch.STREAM_CODEC.decode(buf));
    }

    private static void writeRecipeModifiers(RegistryFriendlyByteBuf buf, List<RecipeModifier> values) {
        checkSize(values.size(), MAX_MODIFIERS, "recipe modifier");
        buf.writeVarInt(values.size());
        for (RecipeModifier value : values) {
            ByteBufCodecs.STRING_UTF8.encode(buf, value.getTarget());
            buf.writeEnum(value.getIOTarget());
            buf.writeFloat(value.getModifier());
            buf.writeEnum(value.getOperation());
            buf.writeBoolean(value.affectsChance());
        }
    }

    private static List<RecipeModifier> readRecipeModifiers(RegistryFriendlyByteBuf buf) {
        int count = buf.readVarInt();
        checkSize(count, MAX_MODIFIERS, "recipe modifier");
        List<RecipeModifier> values = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            values.add(new RecipeModifier(ByteBufCodecs.STRING_UTF8.decode(buf), buf.readEnum(RecipeModifier.IOType.class),
                    buf.readFloat(), buf.readEnum(RecipeModifier.Operation.class), buf.readBoolean()));
        }
        return List.copyOf(values);
    }

    private static void writeLevelSlots(RegistryFriendlyByteBuf buf, Map<Character, Identifier> values) {
        checkSize(values.size(), MAX_SYMBOL_REQUIREMENTS, "level slot");
        buf.writeVarInt(values.size());
        for (var entry : values.entrySet()) {
            buf.writeChar(entry.getKey());
            Identifier.STREAM_CODEC.encode(buf, entry.getValue());
        }
    }

    private static Map<Character, Identifier> readLevelSlots(RegistryFriendlyByteBuf buf) {
        int count = buf.readVarInt();
        checkSize(count, MAX_SYMBOL_REQUIREMENTS, "level slot");
        Map<Character, Identifier> values = new LinkedHashMap<>();
        for (int i = 0; i < count; i++) {
            values.put(buf.readChar(), Identifier.STREAM_CODEC.decode(buf));
        }
        return Map.copyOf(values);
    }

    private static void writeStringList(RegistryFriendlyByteBuf buf, List<String> values) {
        checkSize(values.size(), MAX_TAGS, "string");
        buf.writeVarInt(values.size());
        for (String value : values) {
            ByteBufCodecs.STRING_UTF8.encode(buf, value);
        }
    }

    private static List<String> readStringList(RegistryFriendlyByteBuf buf, int max, String label) {
        int count = buf.readVarInt();
        checkSize(count, max, label);
        List<String> values = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            values.add(ByteBufCodecs.STRING_UTF8.decode(buf));
        }
        return List.copyOf(values);
    }

    private static void checkSize(int size, int max, String label) {
        if (size < 0 || size > max) throw new IllegalArgumentException("Invalid " + label + " count: " + size);
    }

    private enum PredicateKind {
        MACHINE_COUPLER,
        AIR,
        ANY,
        BLOCK,
        BLOCK_STATE,
        TAG,
        ANY_OF
    }
}
