package org.folio.search.service;

import static org.folio.search.utils.TestConstants.CONSORTIUM_TENANT_ID;
import static org.folio.search.utils.TestConstants.RESOURCE_NAME;
import static org.folio.search.utils.TestConstants.TENANT_ID;
import static org.folio.search.utils.TestUtils.resourceDescription;
import static org.folio.search.utils.TestUtils.secondaryResourceDescription;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;
import org.folio.search.configuration.properties.SearchConfigurationProperties;
import org.folio.search.domain.dto.LanguageConfig;
import org.folio.search.domain.dto.ReindexRequest;
import org.folio.search.service.browse.CallNumberBrowseRangeService;
import org.folio.search.service.consortium.LanguageConfigServiceDecorator;
import org.folio.search.service.metadata.ResourceDescriptionService;
import org.folio.spring.FolioExecutionContext;
import org.folio.spring.FolioModuleMetadata;
import org.folio.spring.liquibase.FolioSpringLiquibase;
import org.folio.spring.test.type.UnitTest;
import org.folio.spring.tools.kafka.KafkaAdminService;
import org.folio.spring.tools.systemuser.PrepareSystemUserService;
import org.folio.tenant.domain.dto.Parameter;
import org.folio.tenant.domain.dto.TenantAttributes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;

@UnitTest
@ExtendWith(MockitoExtension.class)
class SearchTenantServiceTest {

  @InjectMocks
  private SearchTenantService searchTenantService;
  @Mock
  private IndexService indexService;
  @Mock
  private ScriptService scriptService;
  @Mock
  private FolioExecutionContext context;
  @Mock
  private PrepareSystemUserService prepareSystemUserService;
  @Mock
  private LanguageConfigServiceDecorator languageConfigService;
  @Mock
  private CallNumberBrowseRangeService callNumberBrowseRangeService;
  @Mock
  private ResourceDescriptionService resourceDescriptionService;
  @Mock
  private SearchConfigurationProperties searchConfigurationProperties;
  @Mock
  private KafkaAdminService kafkaAdminService;
  @Mock
  private FolioSpringLiquibase folioSpringLiquibase;
  @Mock
  private JdbcTemplate jdbcTemplate;

  private final FolioModuleMetadata metadata = new FolioModuleMetadata() {
    @Override
    public String getModuleName() {
      return null;
    }

    @Override
    public String getDBSchemaName(String tenantId) {
      return null;
    }
  };

  @Test
  void createOrUpdateTenant_positive() {
    when(searchConfigurationProperties.getInitialLanguages()).thenReturn(Set.of("eng"));
    when(context.getTenantId()).thenReturn(TENANT_ID);
    when(context.getFolioModuleMetadata()).thenReturn(metadata);
    when(resourceDescriptionService.getResourceNames()).thenReturn(List.of(RESOURCE_NAME));
    doNothing().when(prepareSystemUserService).setupSystemUser();
    doNothing().when(kafkaAdminService).createTopics(TENANT_ID);
    doNothing().when(kafkaAdminService).restartEventListeners();

    searchTenantService.createOrUpdateTenant(tenantAttributes());

    verify(languageConfigService).create(new LanguageConfig().code("eng"));
    verify(indexService).createIndexIfNotExist(RESOURCE_NAME, TENANT_ID);
    verify(scriptService).saveScripts();
    verify(indexService, never()).reindexInventory(TENANT_ID, null);
    verify(kafkaAdminService).createTopics(TENANT_ID);
    verify(kafkaAdminService).restartEventListeners();
  }

  @Test
  void createOrUpdateTenant_positive_onlyKafkaAndSystemUserWhenConsortiumMemberTenant() {
    when(context.getTenantId()).thenReturn(TENANT_ID);
    doNothing().when(prepareSystemUserService).setupSystemUser();
    doNothing().when(kafkaAdminService).createTopics(TENANT_ID);
    doNothing().when(kafkaAdminService).restartEventListeners();

    searchTenantService.createOrUpdateTenant(tenantAttributes().addParametersItem(centralTenantParameter()));

    verifyNoInteractions(languageConfigService);
    verifyNoInteractions(indexService);
    verifyNoInteractions(scriptService);
    verify(kafkaAdminService).createTopics(TENANT_ID);
    verify(kafkaAdminService).restartEventListeners();
    verify(prepareSystemUserService).setupSystemUser();
  }

  @Test
  void initializeTenant_positive() {
    when(searchConfigurationProperties.getInitialLanguages()).thenReturn(Set.of("eng"));
    when(context.getTenantId()).thenReturn(TENANT_ID);
    when(resourceDescriptionService.getResourceNames()).thenReturn(List.of(RESOURCE_NAME));
    doNothing().when(prepareSystemUserService).setupSystemUser();
    doNothing().when(kafkaAdminService).createTopics(TENANT_ID);
    doNothing().when(kafkaAdminService).restartEventListeners();

    searchTenantService.afterTenantUpdate(tenantAttributes());

    verify(languageConfigService).create(new LanguageConfig().code("eng"));
    verify(indexService).createIndexIfNotExist(RESOURCE_NAME, TENANT_ID);
    verify(scriptService).saveScripts();
    verify(indexService, never()).reindexInventory(TENANT_ID, null);
    verify(kafkaAdminService).createTopics(TENANT_ID);
    verify(kafkaAdminService).restartEventListeners();
  }

  @Test
  void initializeTenant_shouldNotCreateLanguageIfAlreadyExist() {
    when(context.getTenantId()).thenReturn(TENANT_ID);
    when(searchConfigurationProperties.getInitialLanguages()).thenReturn(Set.of("eng", "fre"));
    when(languageConfigService.getAllLanguageCodes()).thenReturn(Set.of("eng"));
    when(resourceDescriptionService.getResourceNames()).thenReturn(List.of(RESOURCE_NAME));
    doNothing().when(kafkaAdminService).createTopics(TENANT_ID);
    doNothing().when(kafkaAdminService).restartEventListeners();

    searchTenantService.afterTenantUpdate(tenantAttributes());

    verify(languageConfigService, never()).create(new LanguageConfig().code("eng"));
    verify(languageConfigService).create(new LanguageConfig().code("fre"));
    verify(indexService).createIndexIfNotExist(RESOURCE_NAME, TENANT_ID);
    verify(scriptService).saveScripts();
    verify(kafkaAdminService).createTopics(TENANT_ID);
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
  void deleteTenant_positive() {
    when(context.getTenantId()).thenReturn(TENANT_ID);
    when(resourceDescriptionService.getResourceNames()).thenReturn(List.of(RESOURCE_NAME));
    when(context.getFolioModuleMetadata()).thenReturn(metadata);
    when(jdbcTemplate.query(anyString(), any(ResultSetExtractor.class), any())).thenReturn(true);

    searchTenantService.deleteTenant(tenantAttributes());

    verify(jdbcTemplate).execute(anyString());
    verify(callNumberBrowseRangeService).evictRangeCache(TENANT_ID);
    verify(indexService).dropIndex(RESOURCE_NAME, TENANT_ID);
    verify(kafkaAdminService).deleteTopics(TENANT_ID);
  }

  @Test
  void deleteTenant_positive_onlyDeleteKafkaTopicsWhenConsortiumMemberTenant() {
    when(context.getTenantId()).thenReturn(TENANT_ID);

    searchTenantService.deleteTenant(tenantAttributes().addParametersItem(centralTenantParameter()));

    verify(kafkaAdminService).deleteTopics(TENANT_ID);
    verifyNoInteractions(jdbcTemplate);
    verifyNoInteractions(callNumberBrowseRangeService);
    verifyNoInteractions(indexService);
  }

  @Test
  void disableTenant_positive() {
    when(context.getTenantId()).thenReturn(TENANT_ID);
    when(resourceDescriptionService.getResourceNames()).thenReturn(List.of(RESOURCE_NAME));
    doNothing().when(callNumberBrowseRangeService).evictRangeCache(TENANT_ID);

    searchTenantService.afterTenantDeletion(tenantAttributes());

    verify(indexService).dropIndex(RESOURCE_NAME, TENANT_ID);
    verify(kafkaAdminService).deleteTopics(TENANT_ID);
    verifyNoMoreInteractions(indexService);
  }

  private TenantAttributes tenantAttributes() {
    return new TenantAttributes().moduleTo("mod-search");
  }

  private Parameter centralTenantParameter() {
    return new Parameter("centralTenantId").value(CONSORTIUM_TENANT_ID);
  }
}
