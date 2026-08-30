package cn.howxu.mmcr.api.publicapi.controller;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * Optional custom text exposed by a machine controller to Jade.
 *
 * @author howxu <dev@howxu.cn>
 */
public interface JadeText {
    void append(Identifier lineId, Component text);

    default void appendAfter(Identifier lineId, Identifier afterLineId, Component text) {
        append(lineId, text);
    }

    default void replace(Identifier lineId, Component text) {
        append(lineId, text);
    }

    void remove(Identifier lineId);

    void clear();

    static JadeText noop() {
        return Noop.INSTANCE;
    }

    enum Noop implements JadeText {
        INSTANCE;

        @Override
        public void append(Identifier lineId, Component text) {
        }

        @Override
        public void remove(Identifier lineId) {
        }

        @Override
        public void clear() {
        }
    }
}
