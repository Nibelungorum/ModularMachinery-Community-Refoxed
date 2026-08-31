package cn.howxu.mmcr.api.recipe;

/**
 * An output supplied by an extension type.
 *
 * @author howxu <dev@howxu.cn>
 */
public interface CustomOutput extends MachineOutput {
    @Override
    OutputType<? extends CustomOutput> outputType();
}
