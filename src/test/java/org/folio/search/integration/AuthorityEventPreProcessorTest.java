package org.folio.search.integration;

import static org.apache.commons.collections4.MapUtils.getString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.search.model.metadata.PlainFieldDescription.STANDARD_FIELD_TYPE;
import static org.folio.search.utils.AuthoritySearchUtils.expectedAuthorityAsMap;
import static org.folio.search.utils.SearchUtils.AUTHORITY_RESOURCE;
import static org.folio.search.utils.SearchUtils.ID_FIELD;
import static org.folio.search.utils.TestConstants.RESOURCE_ID;
import static org.folio.search.utils.TestUtils.keywordField;
import static org.folio.search.utils.TestUtils.mapOf;
import static org.folio.search.utils.TestUtils.objectField;
import static org.folio.search.utils.TestUtils.resourceEvent;
import static org.folio.search.utils.TestUtils.standardField;
import static org.folio.search.utils.TestUtils.toMap;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.folio.search.domain.dto.Authority;
import org.folio.search.domain.dto.AuthorityIdentifiers;
import org.folio.search.domain.dto.ResourceEvent;
import org.folio.search.model.metadata.AuthorityFieldDescription;
import org.folio.search.model.metadata.FieldDescription;
import org.folio.search.model.metadata.ResourceDescription;
import org.folio.search.service.metadata.ResourceDescriptionService;
import org.folio.search.utils.TestUtils;
import org.folio.search.utils.types.UnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class AuthorityEventPreProcessorTest {

  @InjectMocks private AuthorityEventPreProcessor eventPreProcessor;
  @Mock private ResourceDescriptionService resourceDescriptionService;

  @BeforeEach
  void setUp() {
    when(resourceDescriptionService.get(AUTHORITY_RESOURCE)).thenReturn(authorityResourceDescription());
    eventPreProcessor.init();
  }

  @Test
  void process_positive() {
    var authority = fullAuthorityRecord();
    var actual = eventPreProcessor.process(resourceEvent(AUTHORITY_RESOURCE, toMap(authority)));
    assertThat(actual).isEqualTo(List.of(
      event("personalName0", expectedAuthorityAsMap(authority, true, "personalName")),
      event("sftPersonalName0", expectedAuthorityAsMap(authority, "sftPersonalName[0]")),
      event("sftPersonalName1", expectedAuthorityAsMap(authority, "sftPersonalName[1]")),
      event("saftPersonalName0", expectedAuthorityAsMap(authority, "saftPersonalName[0]")),
      event("corporateName0", expectedAuthorityAsMap(authority, "corporateName")),
      event("sftCorporateName0", expectedAuthorityAsMap(authority, "sftCorporateName[0]")),
      event("saftCorporateName0", expectedAuthorityAsMap(authority, "saftCorporateName[0]")),
      event("saftCorporateName1", expectedAuthorityAsMap(authority, "saftCorporateName[1]")),
      event("uniformTitle0", expectedAuthorityAsMap(authority, "uniformTitle")),
      event("sftUniformTitle0", expectedAuthorityAsMap(authority, "sftUniformTitle[0]")),
      event("saftUniformTitle0", expectedAuthorityAsMap(authority, "saftUniformTitle[0]"))
    ));
  }

  @Test
  void process_positive_onlyPersonalIsPopulated() {
    var authority = new Authority().id(RESOURCE_ID).personalName("a personal name");
    var actual = eventPreProcessor.process(resourceEvent(AUTHORITY_RESOURCE, toMap(authority)));
    assertThat(actual).isEqualTo(List.of(
      event("personalName0", expectedAuthorityAsMap(authority, true, "personalName"))));
  }

  @Test
  void process_positive_onlyCommonFieldsArePopulated() {
    var authority = new Authority().id(RESOURCE_ID).subjectHeadings("a subject headings")
      .identifiers(List.of(new AuthorityIdentifiers().value("an authority identifier")));
    var actual = eventPreProcessor.process(resourceEvent(AUTHORITY_RESOURCE, toMap(authority)));
    assertThat(actual).isEqualTo(List.of(event("other0", expectedAuthorityAsMap(authority, true))));
  }

  private static ResourceEvent event(String prefix, Map<String, Object> body) {
    return resourceEvent(prefix + "_" + getString(body, ID_FIELD), AUTHORITY_RESOURCE, body);
  }

  private static Authority fullAuthorityRecord() {
    return new Authority()
      .id(RESOURCE_ID)
      .personalName("a personal name")
      .sftPersonalName(List.of("a sft personal name 1", "a sft personal name 2"))
      .saftPersonalName(List.of("a saft personal name"))
      .corporateName("a corporate name")
      .sftCorporateName(List.of("a sft corporate name"))
      .saftCorporateName(List.of("a saft corporate name 1", "a saft corporate name 2"))
      .uniformTitle("an uniform title")
      .sftUniformTitle(List.of("a sft uniform title"))
      .saftUniformTitle(List.of("a saft uniform title"))
      .subjectHeadings("a subject heading")
      .identifiers(List.of(new AuthorityIdentifiers()
        .value("an identifier value")
        .identifierTypeId("an identifier type id")));
  }

  private static ResourceDescription authorityResourceDescription() {
    return TestUtils.resourceDescription(AUTHORITY_RESOURCE, mapOf(
      "id", keywordField(),
      "subjectHeadings", standardField(),
      "identifiers", objectField(mapOf(
        "identifierTypeId", keywordField(),
        "value", keywordField())),
      "personalName", authorityField("personalName"),
      "sftPersonalName", authorityField("sftPersonalName"),
      "saftPersonalName", authorityField("saftPersonalName"),
      "corporateName", authorityField("corporateName"),
      "sftCorporateName", authorityField("sftCorporateName"),
      "saftCorporateName", authorityField("saftCorporateName"),
      "uniformTitle", authorityField("uniformTitle"),
      "sftUniformTitle", authorityField("sftUniformTitle"),
      "saftUniformTitle", authorityField("saftUniformTitle")
    ));
  }

  private static FieldDescription authorityField(String distinctType) {
    var fieldDescription = new AuthorityFieldDescription();
    fieldDescription.setDistinctType(distinctType);
    fieldDescription.setIndex(STANDARD_FIELD_TYPE);
    return fieldDescription;
  }
}
