package cn.howxu.mmcr.api.network;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;

import java.util.Objects;

/** Persistent source endpoint and machine identity stored by a network key card.
 * @author howxu <dev@howxu.cn>
 */
public record KeyCardBinding(GlobalPos interfacePos, MachineReference machine) {
    public static final Codec<KeyCardBinding> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            GlobalPos.CODEC.fieldOf("interfacePos").forGetter(KeyCardBinding::interfacePos),
            Identifier.CODEC.fieldOf("machineType").forGetter(binding -> binding.machine().type()),
            Codec.LONG.fieldOf("machineHash").forGetter(binding -> binding.machine().hash())
    ).apply(instance, (interfacePos, machineType, machineHash) ->
            new KeyCardBinding(interfacePos, new MachineReference(machineType, machineHash))));

    public static final StreamCodec<net.minecraft.network.FriendlyByteBuf, KeyCardBinding> STREAM_CODEC =
            StreamCodec.composite(GlobalPos.STREAM_CODEC, KeyCardBinding::interfacePos,
                    Identifier.STREAM_CODEC, binding -> binding.machine().type(),
                    ByteBufCodecs.LONG, binding -> binding.machine().hash(),
                    (interfacePos, machineType, machineHash) ->
                            new KeyCardBinding(interfacePos, new MachineReference(machineType, machineHash)));

    public KeyCardBinding {
        Objects.requireNonNull(interfacePos, "interfacePos");
        Objects.requireNonNull(machine, "machine");
    }
}
