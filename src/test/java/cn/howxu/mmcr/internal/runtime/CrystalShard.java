package cn.howxu.mmcr.internal.runtime;

import cn.howxu.mmcr.api.capability.CapabilityDirections;
import cn.howxu.mmcr.api.capability.CapabilityRequest;
import cn.howxu.mmcr.api.capability.CapabilitySnapshot;
import cn.howxu.mmcr.api.capability.CapabilityType;
import cn.howxu.mmcr.api.capability.CapabilityView;
import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.plan.CapabilityOperation;
import cn.howxu.mmcr.api.capability.plan.CapabilityResult;
import cn.howxu.mmcr.api.capability.plan.PlanningContext;
import cn.howxu.mmcr.api.capability.plan.RequirementPlan;
import cn.howxu.mmcr.api.capability.storage.LongValueStorage;
import cn.howxu.mmcr.api.port.PortDefinition;
import cn.howxu.mmcr.api.port.PortFamily;
import cn.howxu.mmcr.api.recipe.MachineOutput;
import cn.howxu.mmcr.api.recipe.OutputType;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.recipe.requirement.RequirementHandlerSupport;
import cn.howxu.mmcr.api.recipe.requirement.RequirementType;
import cn.howxu.mmcr.api.recipe.requirement.StartMaterial;
import cn.howxu.mmcr.internal.capability.CapabilityFactories;
import cn.howxu.mmcr.internal.port.IOPortKind;
import cn.howxu.mmcr.internal.port.PortFamilyDescriptor;
import cn.howxu.mmcr.internal.tile.IOPortBlockEntity;
import cn.howxu.mmcr.registry.ModBlockEntities;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.util.IOType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.entity.BlockEntityType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** A test-only resource family. Colors share a port but cannot pay for one another.
 * @author howxu <dev@howxu.cn>
 */
record CrystalShard(RecipeModifier.IOType io, String color, int amount, float chance) implements StartMaterial {
    static final Identifier ID = PortFamily.declare(Identifier.parse("mmcr_test:crystal_shard"), true).id();
    static final MapCodec<CrystalShard> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.STRING.fieldOf("type").forGetter(value -> ID.toString()),
            RecipeModifier.IO_TYPE_CODEC.fieldOf("io").forGetter(CrystalShard::io),
            Codec.STRING.fieldOf("color").forGetter(CrystalShard::color),
            Codec.INT.fieldOf("amount").forGetter(CrystalShard::amount),
            Codec.FLOAT.fieldOf("chance").forGetter(CrystalShard::chance)
    ).apply(instance, (id, io, color, amount, chance) -> new CrystalShard(io, color, amount, chance)));
    static final RequirementType<CrystalShard> TYPE = new RequirementType.Definition<>(ID, CODEC, CrystalShard::plan);

    @Override public RequirementType<CrystalShard> type() { return TYPE; }
    @Override public float consumeChance() { return io.isInput() ? chance : 1F; }
    @Override public CrystalShard retainedInput() { return new CrystalShard(io, color, amount, 0F); }

    private RequirementPlan plan(List<MachineCapability> capabilities, PlanningContext context) {
        for (MachineCapability capability : capabilities) {
            if (!(capability instanceof Store store)) continue;
            LongValueStorage cell = store.cell(color);
            long available = io.isInput() ? cell.amount() : cell.capacity() - cell.amount();
            if (available < amount) continue;
            List<CapabilityOperation> operations = isCatalyst() ? List.of() : List.of(tx -> {
                long moved = io.isInput() ? cell.extract(amount, tx) : cell.insert(amount, tx);
                return moved == amount ? CapabilityResult.successful() : CapabilityResult.failure(
                        RequirementHandlerSupport.blocked(this, context, shortageReason()));
            });
            return new RequirementPlan(context.requirementIndex(), 1, operations, null);
        }
        return RequirementHandlerSupport.blockedPlan(this, context, shortageReason());
    }

    record Output(String color, int quantity, float chance) implements MachineOutput {
        static final OutputType<Output> TYPE = new OutputType.Definition<>(ID,
                CrystalShard.CODEC.xmap(value -> new Output(value.color, value.amount, value.chance), Output::asRequirement),
                (value, chance) -> new Output(value.color, value.quantity, chance), (value, mods) -> value,
                value -> value, OutputType.Presentation.defaults(ID), ID.toString(),
                (output, tags) -> output.asRequirement(),
                value -> value instanceof CrystalShard shard && !shard.io.isInput(),
                value -> value instanceof CrystalShard shard && !shard.io.isInput()
                        ? new Output(shard.color, shard.amount, shard.chance) : null);
        CrystalShard asRequirement() { return new CrystalShard(RecipeModifier.IOType.OUTPUT, color, quantity, chance); }
        @Override public OutputType<Output> outputType() { return TYPE; }
        @Override public long amount() { return quantity; }
        @Override public Object resourceIdentity() { return color; }
    }

    static final class Store implements MachineCapability {
        private final Map<String, LongValueStorage> cells = new HashMap<>();
        private final CapabilityView view = CapabilityFactories.view(new CapabilityType(ID), CapabilityDirections.input());
        LongValueStorage cell(String color) {
            return cells.computeIfAbsent(color, ignored -> new LongValueStorage(64L, 64L, null));
        }
        @Override public CapabilityType type() { return view.type(); }
        @Override public CapabilityDirections directions() { return view.directions(); }
        @Override public CapabilityView view() { return view; }
        @Override public CapabilityOperation prepare(CapabilityRequest request) { throw new UnsupportedOperationException(); }
    }

    enum Kind implements IOPortKind {
        INPUT;
        // Borrow a registered test host block; the family, storage and handler are shard-only.
        @Override public String id() { return "item_input_bus"; }
        @Override public IOType ioType() { return IOType.INPUT; }
        @Override public PortDefinition definition() { return PortDefinition.of(ID, List.of()); }
        @Override public List<PortFamilyDescriptor> families() {
            return List.of(new PortFamilyDescriptor(ID, ioType(), 0, List.of()));
        }
        @Override public BlockEntityType.BlockEntitySupplier<? extends IOPortBlockEntity> entityFactory() {
            return (pos, state) -> new Port(pos);
        }
    }

    static final class Port extends IOPortBlockEntity {
        final Store store = new Store();
        final CapabilitySnapshot snapshot = new CapabilitySnapshot(List.of(store));
        Port(BlockPos pos) {
            super(ModBlockEntities.BES.get("item_input_bus").get(), pos, ModBlocks.BLOCKS.get("item_input_bus").get().defaultBlockState());
        }
        @Override public CapabilitySnapshot capabilitySnapshot() { return snapshot; }
        @Override public IOPortKind kind() { return Kind.INPUT; }
        @Override public IOType ioType() { return IOType.INPUT; }
    }
}
