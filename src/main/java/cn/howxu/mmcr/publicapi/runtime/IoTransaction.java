package cn.howxu.mmcr.publicapi.runtime;

import cn.howxu.mmcr.publicapi.data.DataStore;
import cn.howxu.mmcr.publicapi.recipe.requirement.RequirementSpec;
import org.jetbrains.annotations.ApiStatus;
import java.util.List;
import java.util.function.Consumer;

/** MMCR-provided one-shot IO plan. Simulate before commit. Callback exceptions propagate
 * and journaled writes roll back; ordinary writes and world operations do not.
 * @author howxu <dev@howxu.cn>
 */
@ApiStatus.NonExtendable
public interface IoTransaction {
    IoSnapshot view();
    IoTransaction addInput(RequirementSpec requirement);
    IoTransaction addOutput(RequirementSpec requirement, OutputMode mode);
    IoTransaction add(RequirementSpec requirement);
    List<RequirementSpec> requirements();
    IoSimulation simulate();
    IoCommitResult commit();
    IoCommitResult commit(Consumer<DataStore.Transaction> writes);
    IoCommitResult commitData(Consumer<DataStore.Transaction> writes);
    List<OutputAcceptance> outputSimulations();
    boolean inputsSatisfied();
    boolean energySatisfied();
}
