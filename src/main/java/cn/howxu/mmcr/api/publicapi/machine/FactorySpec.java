package cn.howxu.mmcr.api.publicapi.machine;

import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Factory parallelism and thread declaration.
 *
 * @author howxu <dev@howxu.cn>
 */
public record FactorySpec(boolean hasFactory, int threadLimit, List<ThreadSpec> threads) {

    public FactorySpec {
        if (threadLimit < 1) throw new IllegalArgumentException("threadLimit must be positive");
        threads = List.copyOf(threads == null ? List.of() : threads);
    }

    public static Builder builder() {
        return new Builder();
    }

    public record ThreadSpec(String name, List<Identifier> recipeIds) {
        public ThreadSpec {
            if (name == null || name.isBlank()) throw new IllegalArgumentException("name blank");
            recipeIds = List.copyOf(recipeIds == null ? List.of() : recipeIds);
        }
    }

    /**
     * Builder for factory parallelism and thread declarations.
     *
     * @author howxu <dev@howxu.cn>
     */
    public static final class Builder {
        private boolean hasFactory;
        private int threadLimit = 1;
        private final List<ThreadSpec> threads = new ArrayList<>();

        public Builder hasFactory(boolean hasFactory) {
            this.hasFactory = hasFactory;
            return this;
        }

        public Builder threadLimit(int threadLimit) {
            this.threadLimit = threadLimit;
            return this;
        }

        public Builder thread(String name, Identifier... recipeIds) {
            threads.add(new ThreadSpec(name, recipeIds == null ? List.of() : List.of(recipeIds)));
            return this;
        }

        public FactorySpec build() {
            return new FactorySpec(hasFactory || !threads.isEmpty(), threadLimit, threads);
        }
    }
}
