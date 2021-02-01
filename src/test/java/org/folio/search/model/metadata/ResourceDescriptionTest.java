package org.folio.search.model.metadata;

import static org.folio.search.utils.TestUtils.mapOf;
import static org.folio.search.utils.TestUtils.objectField;
import static org.folio.search.utils.TestUtils.plainField;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.is;

import java.util.Map;
import org.folio.search.utils.types.UnitTest;
import org.junit.jupiter.api.Test;

@UnitTest
class ResourceDescriptionTest {
  @Test
  void canFlattenNestedObjectFields() {
    final ResourceDescription resourceDescription = new ResourceDescription();
    resourceDescription.setFields(testFields());

    assertThat(resourceDescription.getFlattenFields(), allOf(
      hasEntry(is("id"), hasProperty("index", is("keyword"))),
      hasEntry(is("objectField.objectSub1"), hasProperty("index", is("keyword"))),
      hasEntry(is("objectField.objectSub2.sub1"), hasProperty("index", is("multilang")))
    ));
  }

  private Map<String, FieldDescription> testFields() {
    return mapOf(
      "id", plainField("keyword"),
      "objectField", objectField(mapOf(
        "objectSub1", plainField("keyword"),
        "objectSub2", objectField(mapOf(
          "sub1", plainField("multilang")))))
    );
  }
}
