package org.folio.search.model.metadata;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.folio.search.utils.TestUtils.mapOf;
import static org.folio.search.utils.TestUtils.objectField;
import static org.folio.search.utils.TestUtils.plainField;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.is;

import org.folio.search.utils.TestUtils;
import org.folio.spring.test.type.UnitTest;
import org.junit.jupiter.api.Test;

@UnitTest
class PostProcessResourceDescriptionConverterTest {
  private final PostProcessResourceDescriptionConverter converter = new PostProcessResourceDescriptionConverter();

  @Test
  void canFlattenNestedObjectFields() {
    var fields = TestUtils.<String, FieldDescription>mapOf("id", plainField("keyword"),
      "objectField", objectField(mapOf(
        "objectSub1", plainField("keyword"),
        "objectSub2", objectField(mapOf(
          "sub1", plainField("multilang"))))));

    var resourceDescription = new ResourceDescription();
    resourceDescription.setFields(fields);

    converter.convert(resourceDescription);

    assertThat(resourceDescription.getFlattenFields(), allOf(
      hasEntry(is("id"), hasProperty("index", is("keyword"))),
      hasEntry(is("objectField.objectSub1"), hasProperty("index", is("keyword"))),
      hasEntry(is("objectField.objectSub2.sub1"), hasProperty("index", is("multilang")))));
  }

  @Test
  void shouldResolvePlainFieldReferences() {
    var fields = mapOf(
      "id", fieldByType("id"),
      "objectField", objectField(mapOf(
        "objectSub1", fieldByType("objectSub1"),
        "objectSub2", objectField(mapOf(
          "sub1", fieldByType("sub1"))))));

    var fieldTypes = TestUtils.<String, FieldDescription>mapOf(
      "id", plainField("id"),
      "objectSub1", plainField("objectSub1"),
      "sub1", plainField("sub1"));

    var resourceDescription = new ResourceDescription();
    resourceDescription.setFields(fields);
    resourceDescription.setFieldTypes(fieldTypes);

    converter.convert(resourceDescription);

    assertThat(resourceDescription.getFlattenFields(), allOf(
      hasEntry(is("id"), hasProperty("index", is("id"))),
      hasEntry(is("objectField.objectSub1"), hasProperty("index", is("objectSub1"))),
      hasEntry(is("objectField.objectSub2.sub1"), hasProperty("index", is("sub1")))));
  }

  @Test
  void shouldResolveObjectFieldReferences() {
    var fields = mapOf("objectField", fieldByType("objectField"));

    var fieldTypes = TestUtils.<String, FieldDescription>mapOf(
      "objectField", objectField(mapOf(
        "id", plainField("id"),
        "sub", plainField("sub"),
        "object2", objectField(mapOf(
          "sub2", fieldByType("sub2"))))),
      "sub2", plainField("sub2"));

    var resourceDescription = new ResourceDescription();
    resourceDescription.setFields(fields);
    resourceDescription.setFieldTypes(fieldTypes);

    converter.convert(resourceDescription);

    assertThat(resourceDescription.getFlattenFields(), allOf(
      hasEntry(is("objectField.id"), hasProperty("index", is("id"))),
      hasEntry(is("objectField.sub"), hasProperty("index", is("sub"))),
      hasEntry(is("objectField.object2.sub2"), hasProperty("index", is("sub2")))));
  }

  @Test
  void shouldThrowExceptionOfNoFieldTypeExists() {
    var fields = mapOf("objectField", fieldByType("notDefinedType"));

    var resourceDescription = new ResourceDescription();
    resourceDescription.setFields(fields);

    assertThatThrownBy(() -> converter.convert(resourceDescription))
      .isInstanceOf(IllegalStateException.class)
      .hasMessage("No field type found: notDefinedType");
  }

  private static FieldDescription fieldByType(String type) {
    var field = plainField(null);
    field.setFieldType(type);
    return field;
  }
}
