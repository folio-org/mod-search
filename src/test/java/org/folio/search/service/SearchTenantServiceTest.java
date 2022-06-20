package org.folio.search.service;

import static org.folio.search.utils.TestConstants.RESOURCE_NAME;
import static org.folio.search.utils.TestConstants.TENANT_ID;
import static org.folio.search.utils.TestUtils.resourceDescription;
import static org.folio.search.utils.TestUtils.secondaryResourceDescription;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;
import org.folio.search.configuration.properties.SearchConfigurationProperties;
import org.folio.search.domain.dto.LanguageConfig;
import org.folio.search.domain.dto.ReindexRequest;
import org.folio.search.service.browse.CallNumberBrowseRangeService;
import org.folio.search.service.metadata.ResourceDescriptionService;
import org.folio.search.service.systemuser.SystemUserService;
import org.folio.search.utils.types.UnitTest;
import org.folio.spring.FolioExecutionContext;
import org.folio.tenant.domain.dto.Parameter;
import org.folio.tenant.domain.dto.TenantAttributes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class SearchTenantServiceTest {

  @InjectMocks
  private SearchTenantService searchTenantService;
  @Mock
  private IndexService indexService;
  @Mock
  private FolioExecutionContext context;
  @Mock
  private SystemUserService systemUserService;
  @Mock
  private LanguageConfigService languageConfigService;
  @Mock
  private CallNumberBrowseRangeService callNumberBrowseRangeService;
  @Mock
  private ResourceDescriptionService resourceDescriptionService;
  @Mock
  private SearchConfigurationProperties searchConfigurationProperties;
  @Mock
  private KafkaAdminService kafkaAdminService;

  @Test
  void initializeTenant_positive() {
    when(searchConfigurationProperties.getInitialLanguages()).thenReturn(Set.of("eng"));
    when(context.getTenantId()).thenReturn(TENANT_ID);
    when(resourceDescriptionService.getResourceNames()).thenReturn(List.of(RESOURCE_NAME));
    doNothing().when(systemUserService).prepareSystemUser();
    doNothing().when(kafkaAdminService).createKafkaTopics();
    doNothing().when(kafkaAdminService).restartEventListeners();

    searchTenantService.afterTenantUpdate(tenantAttributes());

    verify(languageConfigService).create(new LanguageConfig().code("eng"));
    verify(indexService).createIndexIfNotExist(RESOURCE_NAME, TENANT_ID);
    verify(indexService, never()).reindexInventory(TENANT_ID, null);
    verify(kafkaAdminService).createKafkaTopics();
    verify(kafkaAdminService).restartEventListeners();
  }

  @Test
  void initializeTenant_shouldNotCreateLanguageIfAlreadyExist() {
    when(context.getTenantId()).thenReturn(TENANT_ID);
    when(searchConfigurationProperties.getInitialLanguages()).thenReturn(Set.of("eng", "fre"));
    when(languageConfigService.getAllLanguageCodes()).thenReturn(Set.of("eng"));
    when(resourceDescriptionService.getResourceNames()).thenReturn(List.of(RESOURCE_NAME));
    doNothing().when(systemUserService).prepareSystemUser();
    doNothing().when(kafkaAdminService).createKafkaTopics();
    doNothing().when(kafkaAdminService).restartEventListeners();

    searchTenantService.afterTenantUpdate(tenantAttributes());

    verify(languageConfigService, never()).create(new LanguageConfig().code("eng"));
    verify(languageConfigService).create(new LanguageConfig().code("fre"));
    verify(indexService).createIndexIfNotExist(RESOURCE_NAME, TENANT_ID);
    verify(kafkaAdminService).createKafkaTopics();
    verify(kafkaAdminService).restartEventListeners();
  }

  @Test
  void shouldRunReindexOnTenantParamPresentForResourcesThatSupportsReindex() {
    var resourceDescription = secondaryResourceDescription("secondary", RESOURCE_NAME);
    when(context.getTenantId()).thenReturn(TENANT_ID);
    when(resourceDescriptionService.getResourceNames()).thenReturn(List.of(RESOURCE_NAME, "secondary"));
    when(resourceDescriptionService.get(RESOURCE_NAME)).thenReturn(resourceDescription(RESOURCE_NAME));
    when(resourceDescriptionService.get("secondary")).thenReturn(resourceDescription);
    var attributes = tenantAttributes().addParametersItem(new Parameter().key("runReindex").value("true"));

    searchTenantService.afterTenantUpdate(attributes);

    verify(indexService).reindexInventory(TENANT_ID, new ReindexRequest().resourceName(RESOURCE_NAME));
    verify(indexService, never()).reindexInventory(TENANT_ID, new ReindexRequest().resourceName("secondary"));
  }

  @Test
  void shouldNotRunReindexOnTenantParamPresentFalse() {
    when(context.getTenantId()).thenReturn(TENANT_ID);
    when(resourceDescriptionService.getResourceNames()).thenReturn(List.of(RESOURCE_NAME));
    var attributes = tenantAttributes().addParametersItem(new Parameter().key("runReindex").value("false"));

    searchTenantService.afterTenantUpdate(attributes);

    verify(indexService, never()).reindexInventory(TENANT_ID, null);
  }

  @Test
  void shouldNotRunReindexOnTenantParamPresentWrong() {
    when(context.getTenantId()).thenReturn(TENANT_ID);
    when(resourceDescriptionService.getResourceNames()).thenReturn(List.of(RESOURCE_NAME));
    var attributes = tenantAttributes().addParametersItem(new Parameter().key("runReindexx").value("true"));

    searchTenantService.afterTenantUpdate(attributes);

    verify(indexService, never()).reindexInventory(TENANT_ID, null);
  }

  @Test
  void disableTenant_positive() {
    when(context.getTenantId()).thenReturn(TENANT_ID);
    when(resourceDescriptionService.getResourceNames()).thenReturn(List.of(RESOURCE_NAME));
    doNothing().when(callNumberBrowseRangeService).evictRangeCache(TENANT_ID);

    searchTenantService.afterTenantDeletion(tenantAttributes());

    verify(indexService).dropIndex(RESOURCE_NAME, TENANT_ID);
    verifyNoMoreInteractions(indexService);
  }

  private TenantAttributes tenantAttributes() {
    return new TenantAttributes().moduleTo("mod-search");
  }
}
