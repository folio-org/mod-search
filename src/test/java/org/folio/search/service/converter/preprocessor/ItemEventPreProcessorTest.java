package org.folio.search.service.converter.preprocessor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.search.domain.dto.ResourceEventType.CREATE;
import static org.folio.search.domain.dto.ResourceEventType.DELETE;
import static org.folio.search.model.types.ResourceType.ITEM;
import static org.folio.support.TestConstants.RESOURCE_ID;
import static org.folio.support.utils.TestUtils.mapOf;
import static org.folio.support.utils.TestUtils.resourceEvent;

import java.util.Map;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.Test;

@UnitTest
class ItemEventPreProcessorTest {

  private final ItemEventPreProcessor preProcessor = new ItemEventPreProcessor();

  @Test
  void preProcess_positive_flattensNestedFields() {
    var source = mapOf("id", RESOURCE_ID, "barcode", "b1",
      "status", mapOf("name", "Available"),
      "effectiveCallNumberComponents", mapOf("callNumber", "cn", "prefix", "p", "suffix", "s", "typeId", "t"));
    var event = resourceEvent(RESOURCE_ID, ITEM, CREATE, source, null);

    var result = preProcessor.preProcess(event);

    assertThat(result).hasSize(1);
    assertThat(newMap(result.getFirst()))
      .containsEntry("id", RESOURCE_ID)
      .containsEntry("barcode", "b1")
      .containsEntry("statusName", "Available")
      .containsEntry("callNumber", "cn")
      .containsEntry("callNumberPrefix", "p")
      .containsEntry("callNumberSuffix", "s")
      .containsEntry("callNumberTypeId", "t")
      .containsKeys("status", "effectiveCallNumberComponents");
    assertThat(source).doesNotContainKey("statusName");
    assertThat(result.getFirst().getId()).isEqualTo(RESOURCE_ID);
    assertThat(result.getFirst().getTenant()).isEqualTo(event.getTenant());
  }

  @Test
  void preProcess_positive_missingNestedObjects() {
    var event = resourceEvent(RESOURCE_ID, ITEM, CREATE, mapOf("id", RESOURCE_ID), null);

    var result = preProcessor.preProcess(event);

    assertThat(newMap(result.getFirst()))
      .containsEntry("id", RESOURCE_ID)
      .doesNotContainKeys("statusName", "callNumber", "callNumberPrefix", "callNumberSuffix", "callNumberTypeId");
  }

  @Test
  void preProcess_positive_partialCallNumberComponents() {
    var source = mapOf("id", RESOURCE_ID, "status", mapOf("date", "x"),
      "effectiveCallNumberComponents", mapOf("callNumber", "cn"));

    var result = preProcessor.preProcess(resourceEvent(RESOURCE_ID, ITEM, CREATE, source, null));

    assertThat(newMap(result.getFirst()))
      .containsEntry("callNumber", "cn")
      .doesNotContainKeys("statusName", "callNumberPrefix", "callNumberSuffix", "callNumberTypeId");
  }

  @Test
  void preProcess_positive_deletePassthrough() {
    var event = resourceEvent(RESOURCE_ID, ITEM, DELETE);

    var result = preProcessor.preProcess(event);

    assertThat(result).containsExactly(event);
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> newMap(org.folio.search.domain.dto.ResourceEvent event) {
    return (Map<String, Object>) event.getNew();
  }
}
