package org.folio.search.service.system;

import static org.folio.search.model.types.ResourceType.INSTANCE_SUBJECT;
import static org.folio.search.model.types.ResourceType.UNKNOWN;
import static org.folio.search.utils.TestConstants.CENTRAL_TENANT_ID;
import static org.folio.search.utils.TestConstants.TENANT_ID;
import static org.folio.search.utils.TestUtils.resourceDescription;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
import org.folio.search.model.entity.TenantEntity;
import org.folio.search.service.IndexService;
import org.folio.search.service.browse.CallNumberBrowseRangeService;
import org.folio.search.service.consortium.LanguageConfigServiceDecorator;
import org.folio.search.service.metadata.ResourceDescriptionService;
import org.folio.search.service.reindex.jdbc.TenantRepository;
import org.folio.spring.FolioExecutionContext;
import org.folio.spring.FolioModuleMetadata;
import org.folio.spring.liquibase.FolioSpringLiquibase;
import org.folio.spring.service.PrepareSystemUserService;
import org.folio.spring.testing.type.UnitTest;
import org.folio.spring.tools.kafka.KafkaAdminService;
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
  @InjectMocks
  private SearchTenantService searchTenantService;
  @Mock
  private IndexService indexService;
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
  @Mock
  private TenantRepository tenantRepository;

  @Test
  void createOrUpdateTenant_positive() {
    when(searchConfigurationProperties.getInitialLanguages()).thenReturn(Set.of("eng"));
    when(context.getTenantId()).thenReturn(TENANT_ID);
    when(context.getFolioModuleMetadata()).thenReturn(metadata);
    when(resourceDescriptionService.getResourceTypes()).thenReturn(List.of(UNKNOWN));
    doNothing().when(prepareSystemUserService).setupSystemUser();
    doNothing().when(kafkaAdminService).createTopics(TENANT_ID);
    doNothing().when(kafkaAdminService).restartEventListeners();

    searchTenantService.createOrUpdateTenant(tenantAttributes());

    verify(tenantRepository).saveTenant(new TenantEntity(TENANT_ID, null, true));
    verify(languageConfigService).create(new LanguageConfig().code("eng"));
    verify(indexService).createIndexIfNotExist(UNKNOWN, TENANT_ID);
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

    verify(tenantRepository).saveTenant(new TenantEntity(TENANT_ID, CENTRAL_TENANT_ID, true));
    verifyNoInteractions(languageConfigService);
    verifyNoInteractions(indexService);
    verify(kafkaAdminService).createTopics(TENANT_ID);
    verify(kafkaAdminService).restartEventListeners();
    verify(prepareSystemUserService).setupSystemUser();
  }

  @Test
  void initializeTenant_positive() {
    when(searchConfigurationProperties.getInitialLanguages()).thenReturn(Set.of("eng"));
    when(context.getTenantId()).thenReturn(TENANT_ID);
    when(resourceDescriptionService.getResourceTypes()).thenReturn(List.of(UNKNOWN));
    doNothing().when(prepareSystemUserService).setupSystemUser();
    doNothing().when(kafkaAdminService).createTopics(TENANT_ID);
    doNothing().when(kafkaAdminService).restartEventListeners();

    searchTenantService.afterTenantUpdate(tenantAttributes());

    verify(languageConfigService).create(new LanguageConfig().code("eng"));
    verify(indexService).createIndexIfNotExist(UNKNOWN, TENANT_ID);
    verify(indexService, never()).reindexInventory(TENANT_ID, null);
    verify(kafkaAdminService).createTopics(TENANT_ID);
    verify(kafkaAdminService).restartEventListeners();
  }

  @Test
  void initializeTenant_shouldNotCreateLanguageIfAlreadyExist() {
    when(context.getTenantId()).thenReturn(TENANT_ID);
    when(searchConfigurationProperties.getInitialLanguages()).thenReturn(Set.of("eng", "fre"));
    when(languageConfigService.getAllLanguageCodes()).thenReturn(Set.of("eng"));
    when(resourceDescriptionService.getResourceTypes()).thenReturn(List.of(UNKNOWN));
    doNothing().when(kafkaAdminService).createTopics(TENANT_ID);
    doNothing().when(kafkaAdminService).restartEventListeners();

    searchTenantService.afterTenantUpdate(tenantAttributes());

    verify(languageConfigService, never()).create(new LanguageConfig().code("eng"));
    verify(languageConfigService).create(new LanguageConfig().code("fre"));
    verify(indexService).createIndexIfNotExist(UNKNOWN, TENANT_ID);
    verify(kafkaAdminService).createTopics(TENANT_ID);
    verify(kafkaAdminService).restartEventListeners();
  }

  @Test
  void shouldFailToRunReindexOnSupportsReindexParamPresentButNotSupportedByApi() {
    when(context.getTenantId()).thenReturn(TENANT_ID);
    when(resourceDescriptionService.getResourceTypes()).thenReturn(List.of(INSTANCE_SUBJECT, UNKNOWN));
    when(resourceDescriptionService.get(INSTANCE_SUBJECT)).thenReturn(resourceDescription(INSTANCE_SUBJECT));
    var attributes = tenantAttributes().addParametersItem(new Parameter().key("runReindex").value("true"));

    var ex = assertThrows(IllegalArgumentException.class, () -> searchTenantService.afterTenantUpdate(attributes));

    assertEquals("Unexpected value '%s'".formatted(INSTANCE_SUBJECT.getName()), ex.getMessage());
    verify(indexService, never()).reindexInventory(any(), any());
  }

  @Test
  void shouldNotRunReindexOnTenantParamPresentFalse() {
    when(context.getTenantId()).thenReturn(TENANT_ID);
    when(resourceDescriptionService.getResourceTypes()).thenReturn(List.of(UNKNOWN));
    var attributes = tenantAttributes().addParametersItem(new Parameter().key("runReindex").value("false"));

    searchTenantService.afterTenantUpdate(attributes);

    verify(indexService, never()).reindexInventory(TENANT_ID, null);
  }

  @Test
  void shouldNotRunReindexOnTenantParamPresentWrong() {
    when(context.getTenantId()).thenReturn(TENANT_ID);
    when(resourceDescriptionService.getResourceTypes()).thenReturn(List.of(UNKNOWN));
    var attributes = tenantAttributes().addParametersItem(new Parameter().key("runReindexx").value("true"));

    searchTenantService.afterTenantUpdate(attributes);

    verify(indexService, never()).reindexInventory(TENANT_ID, null);
  }

  @Test
  void deleteTenant_positive() {
    when(context.getTenantId()).thenReturn(TENANT_ID);
    when(resourceDescriptionService.getResourceTypes()).thenReturn(List.of(UNKNOWN));
    when(context.getFolioModuleMetadata()).thenReturn(metadata);
    when(jdbcTemplate.query(anyString(), any(ResultSetExtractor.class), any())).thenReturn(true);

    searchTenantService.deleteTenant(tenantAttributes());

    verify(tenantRepository).saveTenant(new TenantEntity(TENANT_ID, null, false));
    verify(jdbcTemplate).execute(anyString());
    verify(callNumberBrowseRangeService).evictRangeCache(TENANT_ID);
    verify(indexService).dropIndex(UNKNOWN, TENANT_ID);
    verify(kafkaAdminService).deleteTopics(TENANT_ID);
  }

  @Test
  void deleteTenant_positive_onlyDeleteKafkaTopicsWhenConsortiumMemberTenant() {
    when(context.getTenantId()).thenReturn(TENANT_ID);

    searchTenantService.deleteTenant(tenantAttributes().addParametersItem(centralTenantParameter()));

    verify(tenantRepository).saveTenant(new TenantEntity(TENANT_ID, CENTRAL_TENANT_ID, false));
    verify(kafkaAdminService).deleteTopics(TENANT_ID);
    verifyNoInteractions(jdbcTemplate);
    verifyNoInteractions(callNumberBrowseRangeService);
    verifyNoInteractions(indexService);
  }

  @Test
  void disableTenant_positive() {
    when(context.getTenantId()).thenReturn(TENANT_ID);
    when(resourceDescriptionService.getResourceTypes()).thenReturn(List.of(UNKNOWN));
    doNothing().when(callNumberBrowseRangeService).evictRangeCache(TENANT_ID);

    searchTenantService.afterTenantDeletion(tenantAttributes());

    verify(indexService).dropIndex(UNKNOWN, TENANT_ID);
    verify(kafkaAdminService).deleteTopics(TENANT_ID);
    verifyNoMoreInteractions(indexService);
  }

  private TenantAttributes tenantAttributes() {
    return new TenantAttributes().moduleTo("mod-search");
  }

  private Parameter centralTenantParameter() {
    return new Parameter("centralTenantId").value(CENTRAL_TENANT_ID);
  }
}
