package cn.howxu.mmcr.api.data;

import cn.howxu.mmcr.MMCR;
import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Verifies the future data-repository API without a repository implementation.
 * @author howxu <dev@howxu.cn>
 */
class DataRepositoryApiTest {
    @Test
    void request_retains_immutable_identity_and_value_without_a_result() {
        BlockPos controllerPos = new BlockPos(1, 2, 3);
        DataValue requestedValue = DataValue.of(12L);
        DataRepositoryRequest request = new DataRepositoryRequest(
                MMCR.id("future_repository"), controllerPos, "energy", DataValueType.LONG, requestedValue);

        assertThat(request.repositoryId()).isEqualTo(MMCR.id("future_repository"));
        assertThat(request.controllerPos()).isEqualTo(controllerPos);
        assertThat(request.key()).isEqualTo("energy");
        assertThat(request.requestedType()).isEqualTo(DataValueType.LONG);
        assertThat(request.requestedValue()).isEqualTo(requestedValue);
        assertThat(request.reservation()).isEmpty();
    }

    @Test
    void unavailable_request_is_explicit_and_available_request_retains_test_reservation() {
        DataReservation reservation = new DataReservation() {
            @Override
            public boolean commit(TransactionContext transaction) {
                return true;
            }

            @Override
            public void cancel() {
            }
        };
        DataRepositoryRequest request = DataRepositoryRequest.available(
                MMCR.id("future_repository"), BlockPos.ZERO, "energy", DataValueType.LONG, DataValue.of(12L), reservation);

        assertThat(DataRepositoryRequest.unavailable(
                MMCR.id("future_repository"), BlockPos.ZERO, "energy", DataValueType.LONG, DataValue.of(12L))
                .reservation()).isEmpty();
        assertThat(request.reservation()).containsSame(reservation);
        assertThat(reservation.commit(null)).isTrue();
    }

    @Test
    void repository_and_context_can_be_implemented_without_world_lookup() {
        DataRepository repository = new DataRepository() {
            @Override
            public net.minecraft.resources.Identifier id() {
                return MMCR.id("future_repository");
            }

            @Override
            public DataRepositoryRequest request(DataRepositoryContext context) {
                return DataRepositoryRequest.unavailable(id(), context.controllerPos(), context.key(),
                        context.requestedType(), DataValue.of(1L));
            }
        };
        DataRepositoryContext context = new DataRepositoryContext(
                MMCR.id("machine"), BlockPos.ZERO, "energy", DataValueType.LONG);

        assertThat(repository.id()).isEqualTo(MMCR.id("future_repository"));
        assertThat(repository.request(context).reservation()).isEmpty();
        assertThat(context.machineId()).isEqualTo(MMCR.id("machine"));
        assertThat(context.controllerPos()).isEqualTo(BlockPos.ZERO);
    }

    @Test
    void contracts_reject_null_identity_fields() {
        assertThatThrownBy(() -> new DataRepositoryContext(null, BlockPos.ZERO, "key", DataValueType.LONG))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DataRepositoryRequest(
                MMCR.id("repository"), BlockPos.ZERO, "", DataValueType.LONG, DataValue.of(1L)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
