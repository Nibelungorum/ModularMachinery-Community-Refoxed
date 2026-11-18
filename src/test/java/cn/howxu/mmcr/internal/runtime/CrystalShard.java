package cn.howxu.mmcr.internal.runtime;

import cn.howxu.mmcr.api.capability.async.AsyncCapabilityOperation;
import cn.howxu.mmcr.api.capability.async.AsyncCapabilityPlanner;
import cn.howxu.mmcr.api.capability.async.AsyncCapabilitySnapshot;
import cn.howxu.mmcr.api.capability.async.AsyncResourceValue;
import cn.howxu.mmcr.api.capability.facet.AsyncPlanningFacet;
import cn.howxu.mmcr.api.capability.facet.CapabilityFacet;
import cn.howxu.mmcr.api.capability.facet.ResourceFacet;
import cn.howxu.mmcr.api.capability.storage.ResourceStorage;
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
import cn.howxu.mmcr.api.recipe.requirement.SearchDemand;
import cn.howxu.mmcr.api.recipe.requirement.RequirementHandler;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
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

import java.util.Optional;
import java.util.Set;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** A test-only resource family. Colors share a port but cannot pay for one another.
 * @author howxu <dev@howxu.cn>
 */
public record CrystalShard(RecipeModifier.IOType io, String color, int amount, float chance) implements StartMaterial {
    static final Identifier ID = PortFamily.declare(Identifier.parse("mmcr_test:crystal_shard"), true).id();
    static final MapCodec<CrystalShard> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.STRING.fieldOf("type").forGetter(value -> ID.toString()),
            RecipeModifier.IO_TYPE_CODEC.fieldOf("io").forGetter(CrystalShard::io),
            Codec.STRING.fieldOf("color").forGetter(CrystalShard::color),
            Codec.INT.fieldOf("amount").forGetter(CrystalShard::amount),
            Codec.FLOAT.fieldOf("chance").forGetter(CrystalShard::chance)
    ).apply(instance, (id, io, color, amount, chance) -> new CrystalShard(io, color, amount, chance)));
    public static final RequirementType<CrystalShard> TYPE = new RequirementType.Definition<>(ID, CODEC, new RequirementHandler<>() {
        @Override public RequirementPlan plan(CrystalShard shard, List<MachineCapability> capabilities, PlanningContext context) {
            return shard.plan(capabilities, context);
        }

        @Override public SearchDemand captureSearch(CrystalShard shard) {
            if (!shard.io.isInput() || shard.consumeChance() != 1F) return null;
            var value = identity(shard.color);
            return new SearchDemand(SearchDemand.Kind.RESOURCE, shard.amount, 0D, 1F, 1F, true,
                    shard.shortageReason(), null, new SearchDemand.Matcher(value, ignored -> Set.of(value)),
                    SearchDemand.Ports.STANDARD);
        }
    });

    private static AsyncResourceValue identity(String color) {
        return new AsyncResourceValue(ID.withPath(color), "{}");
    }

    @Override public RequirementType<CrystalShard> type() { return TYPE; }
    @Override public java.util.OptionalLong ioAmount() { return java.util.OptionalLong.of(amount); }
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

    public record Output(String color, int quantity, float chance) implements MachineOutput {
        public static final OutputType<Output> TYPE = new OutputType.Definition<>(ID,
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

    public static final class Store implements MachineCapability, ResourceFacet<String>, ResourceStorage<String> {
        private static final List<String> COLORS = List.of("blue", "amber");
        private final AsyncPlanningFacet search = new AsyncPlanningFacet() {
            @Override protected AsyncCapabilitySnapshot captureSnapshotOnServerThread() {
                return new AsyncCapabilitySnapshot.Resource(ID, COLORS.stream().map(color ->
                        new AsyncCapabilitySnapshot.ResourceSlot(Optional.of(identity(color)), cell(color).amount(),
                                cell(color).capacity())).toList());
            }
            @Override protected AsyncCapabilityPlanner workerPlannerOnServerThread() { throw new UnsupportedOperationException(); }
            @Override protected CapabilityResult commitOnServerThread(AsyncCapabilityOperation op, TransactionContext tx) {
                throw new UnsupportedOperationException();
            }
        };
        @Override public <F extends CapabilityFacet> Optional<F> facet(Class<F> type) {
            if (type == AsyncPlanningFacet.class) return Optional.of(type.cast(search));
            if (type == ResourceFacet.class) return Optional.of(type.cast(this));
            return MachineCapability.super.facet(type);
        }
        @Override public ResourceStorage<String> storage() { return this; }
        @Override public Class<String> resourceType() { return String.class; }
        @Override public int size() { return COLORS.size(); }
        @Override public String resource(int slot) { return COLORS.get(slot); }
        @Override public long amount(int slot) { return cell(resource(slot)).amount(); }
        @Override public long capacity(int slot, String resource) { return cell(resource(slot)).capacity(); }
        @Override public boolean isValid(int slot, String resource) { return resource(slot).equals(resource); }
        @Override public long insert(int slot, String resource, long amount, TransactionContext tx) {
            return isValid(slot, resource) ? cell(resource).insert(amount, tx) : 0L;
        }
        @Override public long extract(int slot, String resource, long amount, TransactionContext tx) {
            return isValid(slot, resource) ? cell(resource).extract(amount, tx) : 0L;
        }
        private final Map<String, LongValueStorage> cells = new HashMap<>();
        private final CapabilityView view = CapabilityFactories.view(new CapabilityType(ID), CapabilityDirections.input());
        public LongValueStorage cell(String color) {
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
