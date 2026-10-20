package cn.howxu.mmcr.api.controller;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * Optional custom text exposed by a machine controller to Jade.
 *
 * @author howxu <dev@howxu.cn>
 */
public interface JadeText {
    void append(ResourceLocation lineId, Component text);

    default void append(String lineId, Component text) {
        append(ResourceLocation.parse(lineId), text);
    }

    default void appendAfter(ResourceLocation lineId, ResourceLocation afterLineId, Component text) {
        append(lineId, text);
    }

    default void replace(ResourceLocation lineId, Component text) {
        append(lineId, text);
    }

    default void appendAfter(String lineId, String afterLineId, Component text) {
        appendAfter(ResourceLocation.parse(lineId), ResourceLocation.parse(afterLineId), text);
    }

    default void replace(String lineId, Component text) {
        replace(ResourceLocation.parse(lineId), text);
    }

    void remove(ResourceLocation lineId);

    default void remove(String lineId) {
        remove(ResourceLocation.parse(lineId));
    }

    void clear();

    static JadeText noop() {
        return Noop.INSTANCE;
    }

    enum Noop implements JadeText {
        INSTANCE;

        @Override
        public void append(ResourceLocation lineId, Component text) {
        }

        @Override
        public void remove(ResourceLocation lineId) {
        }

        @Override
        public void clear() {
        }
    }
}
