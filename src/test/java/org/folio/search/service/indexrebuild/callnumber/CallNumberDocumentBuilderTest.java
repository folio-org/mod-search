package org.folio.search.service.indexrebuild.callnumber;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.search.utils.SearchUtils.CALL_NUMBER_FIELD;
import static org.folio.search.utils.SearchUtils.CALL_NUMBER_PREFIX_FIELD;
import static org.folio.search.utils.SearchUtils.CALL_NUMBER_SUFFIX_FIELD;
import static org.folio.search.utils.SearchUtils.CALL_NUMBER_TYPE_ID_FIELD;
import static org.folio.search.utils.SearchUtils.EFFECTIVE_LOCATION_ID_FIELD;
import static org.folio.search.utils.SearchUtils.INSTANCE_ID_FIELD;
import static org.folio.search.utils.SearchUtils.SHARED_FIELD_NAME;
import static org.folio.search.utils.SearchUtils.TENANT_ID_FIELD_NAME;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.folio.search.utils.ShaUtils;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.Test;

@UnitTest
class CallNumberDocumentBuilderTest {

  @Test
  @SuppressWarnings("unchecked")
  void build_positive_groupsInstancesByTenantSharedAndLocation() {
    var items = List.of(item("i1", "t1", true, "loc"), item("i2", "t1", true, "loc"), item("i1", "t1", true, "loc"),
      item("i3", "t2", false, "loc"));

    assertThat(CallNumberDocumentBuilder.build(items)).hasValueSatisfying(document -> {
      assertThat(document)
        .containsEntry("id", ShaUtils.sha("A1", "pre", "suf", "type"))
        .containsEntry("fullCallNumber", "A1 suf")
        .containsEntry("callNumberPrefix", "pre")
        .containsEntry("callNumberTypeId", "type");
      assertThat((List<Map<String, Object>>) document.get("instances")).containsExactlyInAnyOrder(
        Map.of("instanceId", List.of("i1", "i2"), "tenantId", "t1", "shared", true, "locationId", "loc"),
        Map.of("instanceId", List.of("i3"), "tenantId", "t2", "shared", false, "locationId", "loc"));
    });
  }

  @Test
  void build_negative_itemsWithoutCallNumber() {
    var item = new HashMap<String, Object>();
    item.put("callNumber", " ");

    assertThat(CallNumberDocumentBuilder.build(List.of(item))).isEmpty();
  }

  private static Map<String, Object> item(String instanceId, String tenant, boolean shared, String location) {
    var item = new HashMap<String, Object>();
    item.put(CALL_NUMBER_FIELD, "A1");
    item.put(CALL_NUMBER_PREFIX_FIELD, "pre");
    item.put(CALL_NUMBER_SUFFIX_FIELD, "suf");
    item.put(CALL_NUMBER_TYPE_ID_FIELD, "type");
    item.put(INSTANCE_ID_FIELD, instanceId);
    item.put(TENANT_ID_FIELD_NAME, tenant);
    item.put(SHARED_FIELD_NAME, shared);
    item.put(EFFECTIVE_LOCATION_ID_FIELD, location);
    return item;
  }
}
