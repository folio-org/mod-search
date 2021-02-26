package org.folio.search.integration.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import org.folio.search.model.service.ResultList;
import org.folio.search.utils.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class InventoryClientTest {

  @InjectMocks private InventoryClient inventoryClient;

  @Test
  void getInstances_positive() {
    var actual = inventoryClient.getInstances(Collections.emptyList());
    assertThat(actual).isEqualTo(ResultList.empty());
  }
}
