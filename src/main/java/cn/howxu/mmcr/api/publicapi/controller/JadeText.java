package cn.howxu.mmcr.api.publicapi.controller;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * Optional custom text exposed by a machine controller to Jade.
 *
 * @author howxu <dev@howxu.cn>
 */
public interface JadeText {
    void append(ResourceLocation lineId, Component text);

    default void appendAfter(ResourceLocation lineId, ResourceLocation afterLineId, Component text) {
        append(lineId, text);
    }

    default void replace(ResourceLocation lineId, Component text) {
        append(lineId, text);
    }

    void remove(ResourceLocation lineId);

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
