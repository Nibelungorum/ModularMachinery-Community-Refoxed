package cn.howxu.mmcr.client.preview;

/**
 * Restores scene callback state after an exceptional PiP render.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class PreviewRenderStateScope {
    private PreviewRenderStateScope() {
    }

    public static void run(State state, Object color, Object depth, Object projection, Runnable scene) {
        Object previousColor = state.color();
        Object previousDepth = state.depth();
        Object previousProjection = state.projection();
        int previousModelViewDepth = state.modelViewDepth();
        state.color(color);
        state.depth(depth);
        state.projection(projection);
        try {
            scene.run();
        } finally {
            state.color(previousColor);
            state.depth(previousDepth);
            state.projection(previousProjection);
            state.modelViewDepth(previousModelViewDepth);
        }
    }

    public interface State {
        Object color();
        void color(Object color);
        Object depth();
        void depth(Object depth);
        Object projection();
        void projection(Object projection);
        int modelViewDepth();
        void modelViewDepth(int depth);
    }

    public static final class TestState implements State {
        public Object color;
        public Object depth;
        public Object projection;
        public int modelViewDepth;

        public TestState(Object color, Object depth, Object projection, int modelViewDepth) {
            this.color = color;
            this.depth = depth;
            this.projection = projection;
            this.modelViewDepth = modelViewDepth;
        }

        @Override public Object color() { return color; }
        @Override public void color(Object color) { this.color = color; }
        @Override public Object depth() { return depth; }
        @Override public void depth(Object depth) { this.depth = depth; }
        @Override public Object projection() { return projection; }
        @Override public void projection(Object projection) { this.projection = projection; }
        @Override public int modelViewDepth() { return modelViewDepth; }
        @Override public void modelViewDepth(int depth) { modelViewDepth = depth; }
    }
}
