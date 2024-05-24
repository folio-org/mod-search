package org.folio.search.service.setter.item;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.search.model.types.CallNumberType.LC;

import java.util.List;
import java.util.UUID;
import org.folio.search.domain.dto.Instance;
import org.folio.search.domain.dto.Item;
import org.folio.search.domain.dto.ItemEffectiveCallNumberComponents;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class CallNumberTypeProcessorTest {

  private @InjectMocks CallNumberTypeProcessor processor;

  @Test
  void getFieldValue_multipleValue_positive() {
    var folioTypeId = UUID.randomUUID().toString();
    var eventBody = instance(item("95467209-6d7b-468b-94df-0f5d7ad2747d"), item(folioTypeId),
      item(UUID.randomUUID().toString()));

    var actual = processor.getFieldValue(eventBody);
    assertThat(actual).containsExactly(LC.toString());
  }

  private static Instance instance(Item... items) {
    return new Instance().items(List.of(items));
  }

  private static Item item(String typeId) {
    return new Item().effectiveCallNumberComponents(new ItemEffectiveCallNumberComponents().typeId(typeId));
  }
}
