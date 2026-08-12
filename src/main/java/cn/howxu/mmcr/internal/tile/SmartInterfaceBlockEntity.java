package cn.howxu.mmcr.internal.tile;

import cn.howxu.mmcr.registry.ModBlockEntities;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Stores the ordered smart-interface bindings owned by one interface block.
 *
 * @author howxu <dev@howxu.cn>
 */
public class SmartInterfaceBlockEntity extends BlockEntity {
    private static final String BINDINGS_KEY = "bindings";
    private static final int BINDING_CHECK_INTERVAL_TICKS = 20;

    private final List<Binding> bindings = new ArrayList<>();

    public SmartInterfaceBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SMART_INTERFACE.get(), pos, state);
    }

    public boolean bind(BlockPos controllerPos, Identifier machineId, String type, float value) {
        if (controllerPos == null || machineId == null || type == null || type.isBlank() || !Float.isFinite(value)
                || bindingFor(controllerPos).isPresent()) {
            return false;
        }
        bindings.add(new Binding(controllerPos.immutable(), machineId, type, value));
        changed();
        return true;
    }

    public boolean unbind(BlockPos controllerPos) {
        Optional<Binding> binding = bindingFor(controllerPos);
        if (binding.isEmpty()) return false;
        bindings.remove(binding.get());
        changed();
        return true;
    }

    public Optional<Binding> binding(int index) {
        return index < 0 || index >= bindings.size() ? Optional.empty() : Optional.of(bindings.get(index));
    }

    public Optional<Binding> bindingFor(BlockPos controllerPos) {
        return controllerPos == null ? Optional.empty() : bindings.stream()
                .filter(binding -> binding.controllerPos().equals(controllerPos))
                .findFirst();
    }

    public boolean setValue(int index, float value) {
        if (!Float.isFinite(value) || index < 0 || index >= bindings.size()) return false;
        Binding current = bindings.get(index);
        if (Float.compare(current.value(), value) == 0) return true;
        bindings.set(index, new Binding(current.controllerPos(), current.machineId(), current.type(), value));
        changed();
        return true;
    }

    public void serverTick() {
        if (level == null || level.isClientSide() || level.getGameTime() % BINDING_CHECK_INTERVAL_TICKS != 0) return;
        if (bindings.removeIf(binding -> level.hasChunkAt(binding.controllerPos())
                && !(level.getBlockEntity(binding.controllerPos()) instanceof MachineControllerBlockEntity))) {
            changed();
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ValueOutput.TypedOutputList<Binding> serialized = output.list(BINDINGS_KEY, Binding.CODEC);
        bindings.forEach(serialized::add);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        bindings.clear();
        input.listOrEmpty(BINDINGS_KEY, Binding.CODEC).forEach(binding -> {
            if (Float.isFinite(binding.value()) && bindingFor(binding.controllerPos()).isEmpty()) {
                bindings.add(binding);
            }
        });
    }

    private void changed() {
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    public record Binding(BlockPos controllerPos, Identifier machineId, String type, float value) {
        public static final Codec<Binding> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                BlockPos.CODEC.fieldOf("controllerPos").forGetter(Binding::controllerPos),
                Identifier.CODEC.fieldOf("machineId").forGetter(Binding::machineId),
                Codec.STRING.fieldOf("type").forGetter(Binding::type),
                Codec.FLOAT.fieldOf("value").forGetter(Binding::value)
        ).apply(instance, Binding::new));
    }
}
