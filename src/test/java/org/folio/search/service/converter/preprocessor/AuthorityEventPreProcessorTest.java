package org.folio.search.service.converter.preprocessor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.search.domain.dto.ResourceEventType.CREATE;
import static org.folio.search.domain.dto.ResourceEventType.DELETE;
import static org.folio.search.domain.dto.ResourceEventType.REINDEX;
import static org.folio.search.domain.dto.ResourceEventType.UPDATE;
import static org.folio.search.model.metadata.PlainFieldDescription.STANDARD_FIELD_TYPE;
import static org.folio.search.utils.AuthoritySearchUtils.expectedAuthorityAsMap;
import static org.folio.search.utils.SearchUtils.AUTHORITY_RESOURCE;
import static org.folio.search.utils.TestConstants.RESOURCE_ID;
import static org.folio.search.utils.TestConstants.TENANT_ID;
import static org.folio.search.utils.TestUtils.keywordField;
import static org.folio.search.utils.TestUtils.mapOf;
import static org.folio.search.utils.TestUtils.objectField;
import static org.folio.search.utils.TestUtils.resourceEvent;
import static org.folio.search.utils.TestUtils.standardField;
import static org.folio.search.utils.TestUtils.toMap;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.folio.search.domain.dto.Authority;
import org.folio.search.domain.dto.Identifier;
import org.folio.search.domain.dto.ResourceEvent;
import org.folio.search.model.metadata.AuthorityFieldDescription;
import org.folio.search.model.metadata.FieldDescription;
import org.folio.search.model.metadata.ResourceDescription;
import org.folio.search.service.consortium.ConsortiumTenantService;
import org.folio.search.service.metadata.ResourceDescriptionService;
import org.folio.search.utils.TestUtils;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class AuthorityEventPreProcessorTest {

  @InjectMocks
  private AuthorityEventPreProcessor eventPreProcessor;
  @Mock
  private ResourceDescriptionService resourceDescriptionService;
  @Mock
  private ConsortiumTenantService consortiumTenantService;

  @BeforeEach
  void setUp() {
    when(resourceDescriptionService.get(AUTHORITY_RESOURCE)).thenReturn(authorityResourceDescription());
    lenient().when(consortiumTenantService.getCentralTenant(TENANT_ID)).thenReturn(Optional.empty());
    eventPreProcessor.init();
  }

  @Test
  void process_positive() {
    var authority = fullAuthorityRecord();
    var actual = eventPreProcessor.preProcess(resourceEvent(AUTHORITY_RESOURCE, toMap(authority)));
    assertThat(actual).isEqualTo(List.of(
      event("personalName0", expectedAuthorityAsMap(authority, "personalName")),
      event("sftPersonalName0", expectedAuthorityAsMap(authority, "sftPersonalName[0]")),
      event("sftPersonalName1", expectedAuthorityAsMap(authority, "sftPersonalName[1]")),
      event("saftPersonalName0", expectedAuthorityAsMap(authority, "saftPersonalName[0]")),
      event("corporateName0", expectedAuthorityAsMap(authority, "corporateName")),
      event("sftCorporateName0", expectedAuthorityAsMap(authority, "sftCorporateName[0]")),
      event("saftCorporateName0", expectedAuthorityAsMap(authority, "saftCorporateName[0]")),
      event("saftCorporateName1", expectedAuthorityAsMap(authority, "saftCorporateName[1]")),
      event("personalNameTitle0", expectedAuthorityAsMap(authority, "personalNameTitle")),
      event("sftPersonalNameTitle0", expectedAuthorityAsMap(authority, "sftPersonalNameTitle[0]")),
      event("sftPersonalNameTitle1", expectedAuthorityAsMap(authority, "sftPersonalNameTitle[1]")),
      event("saftPersonalNameTitle0", expectedAuthorityAsMap(authority, "saftPersonalNameTitle[0]")),
      event("corporateNameTitle0", expectedAuthorityAsMap(authority, "corporateNameTitle")),
      event("sftCorporateNameTitle0", expectedAuthorityAsMap(authority, "sftCorporateNameTitle[0]")),
      event("saftCorporateNameTitle0", expectedAuthorityAsMap(authority, "saftCorporateNameTitle[0]")),
      event("saftCorporateNameTitle1", expectedAuthorityAsMap(authority, "saftCorporateNameTitle[1]")),
      event("uniformTitle0", expectedAuthorityAsMap(authority, "uniformTitle")),
      event("sftUniformTitle0", expectedAuthorityAsMap(authority, "sftUniformTitle[0]")),
      event("saftUniformTitle0", expectedAuthorityAsMap(authority, "saftUniformTitle[0]"))
    ));
  }

  @Test
  void process_positive_onlyPersonalIsPopulated() {
    var authority = new Authority().id(RESOURCE_ID).personalName("a personal name");
    var actual = eventPreProcessor.preProcess(resourceEvent(AUTHORITY_RESOURCE, toMap(authority)));
    assertThat(actual).isEqualTo(List.of(
      event("personalName0", expectedAuthorityAsMap(authority, "personalName"))));
  }

  @Test
  void process_positive_reindexEvent() {
    var authority = new Authority().id(RESOURCE_ID).uniformTitle("uniform title");
    var event = resourceEvent(AUTHORITY_RESOURCE, toMap(authority)).type(REINDEX);
    var actual = eventPreProcessor.preProcess(event);
    assertThat(actual).isEqualTo(List.of(
      event("uniformTitle0", expectedAuthorityAsMap(authority, "uniformTitle")).type(REINDEX)));
  }

  @Test
  void process_positive_deleteEvent() {
    var oldAuthority = new Authority().id(RESOURCE_ID).personalName("personal").corporateNameTitle("corporate");
    var event = resourceEvent(AUTHORITY_RESOURCE, null).type(DELETE).old(toMap(oldAuthority));
    var actual = eventPreProcessor.preProcess(event);
    assertThat(actual).isEqualTo(List.of(deleteEvent("personalName0"), deleteEvent("corporateNameTitle0")));
  }

  @Test
  void process_positive_updateEvent() {
    var newAuthority = new Authority().id(RESOURCE_ID).personalNameTitle("personal")
      .saftCorporateNameTitle(List.of("a new saft corporate name")).corporateNameTitle("corporate");
    var oldAuthority = new Authority().id(RESOURCE_ID).personalNameTitle("personal").uniformTitle("uniform title")
      .saftCorporateNameTitle(List.of("saft corp 1", "saft corp 2"));
    var event = resourceEvent(AUTHORITY_RESOURCE, toMap(newAuthority)).type(UPDATE).old(toMap(oldAuthority));
    var actual = eventPreProcessor.preProcess(event);
    assertThat(actual).isEqualTo(List.of(
      event("personalNameTitle0", expectedAuthorityAsMap(newAuthority, "personalNameTitle")),
      event("corporateNameTitle0", expectedAuthorityAsMap(newAuthority, "corporateNameTitle")),
      event("saftCorporateNameTitle0", expectedAuthorityAsMap(newAuthority, "saftCorporateNameTitle[0]")),
      deleteEvent("saftCorporateNameTitle1"),
      deleteEvent("uniformTitle0")));
  }

  @Test
  void process_positive_shouldSetSharedFlag() {
    var newAuthority = new Authority().id(RESOURCE_ID).personalNameTitle("personal");
    var event = resourceEvent(AUTHORITY_RESOURCE, toMap(newAuthority)).type(CREATE);

    when(consortiumTenantService.getCentralTenant(TENANT_ID)).thenReturn(Optional.of(TENANT_ID));

    var actual = eventPreProcessor.preProcess(event);
    assertThat(actual).isEqualTo(List.of(
      event("personalNameTitle0", expectedAuthorityAsMap(newAuthority, true, "personalNameTitle"))));
  }

  private static ResourceEvent event(String prefix, Map<String, Object> body) {
    return resourceEvent(prefix + "_" + RESOURCE_ID, AUTHORITY_RESOURCE, body);
  }

  private static ResourceEvent deleteEvent(String prefix) {
    return resourceEvent(prefix + "_" + RESOURCE_ID, AUTHORITY_RESOURCE, DELETE);
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
      .personalNameTitle("a personal name")
      .sftPersonalNameTitle(List.of("a sft personal name 1", "a sft personal name 2"))
      .saftPersonalNameTitle(List.of("a saft personal name"))
      .corporateNameTitle("a corporate name")
      .sftCorporateNameTitle(List.of("a sft corporate name"))
      .saftCorporateNameTitle(List.of("a saft corporate name 1", "a saft corporate name 2"))
      .uniformTitle("an uniform title")
      .sftUniformTitle(List.of("a sft uniform title"))
      .saftUniformTitle(List.of("a saft uniform title"))
      .subjectHeadings("a subject heading")
      .identifiers(List.of(new Identifier()
        .value("an identifier value")
        .identifierTypeId("an identifier type id")));
  }

  private static ResourceDescription authorityResourceDescription() {
    return TestUtils.resourceDescription(AUTHORITY_RESOURCE, mapOf(
      "id", keywordField(),
      "tenantId", keywordField(),
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
      "personalNameTitle", authorityField("personalNameTitle"),
      "sftPersonalNameTitle", authorityField("sftPersonalNameTitle"),
      "saftPersonalNameTitle", authorityField("saftPersonalNameTitle"),
      "corporateNameTitle", authorityField("corporateNameTitle"),
      "sftCorporateNameTitle", authorityField("sftCorporateNameTitle"),
      "saftCorporateNameTitle", authorityField("saftCorporateNameTitle"),
      "uniformTitle", authorityField("uniformTitle"),
      "sftUniformTitle", authorityField("sftUniformTitle"),
      "saftUniformTitle", authorityField("saftUniformTitle"),
      "shared", standardField()
    ));
  }

  private static FieldDescription authorityField(String distinctType) {
    var fieldDescription = new AuthorityFieldDescription();
    fieldDescription.setDistinctType(distinctType);
    fieldDescription.setIndex(STANDARD_FIELD_TYPE);
    return fieldDescription;
  }
}
