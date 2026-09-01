package cn.howxu.mmcr.api.recipe;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import net.minecraft.network.RegistryFriendlyByteBuf;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.BiConsumer;

/**
 * Bounded binary representation owned by a registered recipe requirement or output type.
 *
 * @author howxu <dev@howxu.cn>
 */
public interface RecipeSyncCodec<T> {
    int DEFAULT_MAX_PAYLOAD_SIZE = 1_000_000;

    void encode(RegistryFriendlyByteBuf buffer, T value);

    T decode(RegistryFriendlyByteBuf buffer);

    int maxPayloadSize();

    void validate(T value);

    static <T> RecipeSyncCodec<T> json(Codec<T> codec) {
        return json(codec, ignored -> {
        });
    }

    static <T> RecipeSyncCodec<T> json(Codec<T> codec, Consumer<T> validator) {
        Objects.requireNonNull(codec, "codec");
        return of(DEFAULT_MAX_PAYLOAD_SIZE,
                (buffer, value) -> {
                    JsonElement encoded = codec.encodeStart(
                            buffer.registryAccess().createSerializationContext(JsonOps.INSTANCE), value)
                            .getOrThrow(message -> new IllegalArgumentException("Failed to encode recipe sync value: " + message));
                    buffer.writeUtf(encoded.toString());
                },
                buffer -> codec.parse(buffer.registryAccess().createSerializationContext(JsonOps.INSTANCE),
                        JsonParser.parseString(buffer.readUtf()))
                        .getOrThrow(message -> new IllegalArgumentException("Failed to decode recipe sync value: " + message)),
                validator);
    }

    static <T> RecipeSyncCodec<T> of(int maxPayloadSize, BiConsumer<RegistryFriendlyByteBuf, T> encoder,
                                     Function<RegistryFriendlyByteBuf, T> decoder, Consumer<T> validator) {
        if (maxPayloadSize < 1 || maxPayloadSize > DEFAULT_MAX_PAYLOAD_SIZE) {
            throw new IllegalArgumentException("Invalid recipe sync payload limit: " + maxPayloadSize);
        }
        Objects.requireNonNull(encoder, "encoder");
        Objects.requireNonNull(decoder, "decoder");
        Objects.requireNonNull(validator, "validator");
        return new RecipeSyncCodec<>() {
            @Override
            public void encode(RegistryFriendlyByteBuf buffer, T value) {
                encoder.accept(buffer, value);
            }

            @Override
            public T decode(RegistryFriendlyByteBuf buffer) {
                return decoder.apply(buffer);
            }

            @Override
            public int maxPayloadSize() {
                return maxPayloadSize;
            }

            @Override
            public void validate(T value) {
                validator.accept(value);
            }
        };
    }
}
