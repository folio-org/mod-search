package org.folio.search.service.setter.item;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.search.client.InventoryReferenceDataClient.ReferenceDataType.CALL_NUMBER_TYPES;
import static org.folio.search.model.client.CqlQueryParam.SOURCE;
import static org.folio.search.model.types.CallNumberType.LC;
import static org.folio.search.model.types.CallNumberType.LOCAL;
import static org.folio.search.service.browse.CallNumberBrowseService.FOLIO_CALL_NUMBER_TYPES_SOURCES;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.folio.search.domain.dto.Instance;
import org.folio.search.domain.dto.Item;
import org.folio.search.domain.dto.ItemEffectiveCallNumberComponents;
import org.folio.search.integration.folio.ReferenceDataService;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class CallNumberTypeProcessorTest {

  private @Mock ReferenceDataService referenceDataService;
  private @InjectMocks CallNumberTypeProcessor processor;

  @Test
  void getFieldValue_multipleValue_positive() {
    var folioTypeId = UUID.randomUUID().toString();
    var eventBody = instance(item("95467209-6d7b-468b-94df-0f5d7ad2747d"), item(folioTypeId),
      item(UUID.randomUUID().toString()));
    when(referenceDataService.fetchReferenceData(CALL_NUMBER_TYPES, SOURCE, FOLIO_CALL_NUMBER_TYPES_SOURCES))
      .thenReturn(Set.of(folioTypeId));

    var actual = processor.getFieldValue(eventBody);
    assertThat(actual).containsExactly(LC.toString(), LOCAL.toString());
  }

  private static Instance instance(Item... items) {
    return new Instance().items(List.of(items));
  }

  private static Item item(String typeId) {
    return new Item().effectiveCallNumberComponents(new ItemEffectiveCallNumberComponents().typeId(typeId));
  }
}
