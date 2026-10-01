package cn.howxu.mmcr.publicapi.data;

import cn.howxu.mmcr.api.data.view.DataRepository;
import cn.howxu.mmcr.api.data.view.DataValue;
import cn.howxu.mmcr.api.data.view.DataValueType;
import cn.howxu.mmcr.api.data.view.DataReservation;
import cn.howxu.mmcr.api.data.view.DataRepositoryContext;
import cn.howxu.mmcr.api.data.view.DataRepositoryRequest;
import cn.howxu.mmcr.api.data.view.DataStorage;

import cn.howxu.mmcr.MMCR;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies public data repository contracts.
 *
 * @author howxu <dev@howxu.cn>
 */
class DataRepositoryApiTest {
    @Test
    void request_retains_public_value_type_immutable_position_and_reservation() {
        BlockPos controllerPos = new BlockPos(1, 2, 3);
        DataReservation reservation = new DataReservation() {
            @Override
            public boolean commit() {
                return true;
            }

            @Override
            public void cancel() {
            }
        };

        DataRepositoryRequest request = DataRepositoryRequest.available(MMCR.id("repository"), controllerPos,
                "energy", DataValueType.LONG, DataValue.of(12L), reservation);
        controllerPos = controllerPos.above();

        assertThat(request.controllerPos()).isEqualTo(new BlockPos(1, 2, 3));
        assertThat(request.requestedValue().type()).isEqualTo(DataValueType.LONG);
        assertThat(request.reservation()).containsSame(reservation);
    }

    @Test
    void repository_uses_only_public_context_and_can_return_unavailable_request() {
        DataRepository repository = new DataRepository() {
            @Override
            public ResourceLocation id() {
                return MMCR.id("repository");
            }

            @Override
            public DataRepositoryRequest request(DataRepositoryContext context) {
                return DataRepositoryRequest.unavailable(id(), context.controllerPos(), context.key(),
                        context.requestedType(), DataValue.of(1L));
            }
        };

        DataRepositoryContext context = new DataRepositoryContext(MMCR.id("machine"), BlockPos.ZERO, "energy",
                DataValueType.LONG);

        assertThat(repository.request(context).reservation()).isEmpty();
        assertThat(context.requestedType()).isEqualTo(DataValueType.LONG);
    }
}
