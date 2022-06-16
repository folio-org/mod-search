package org.folio.search.service;

import static java.lang.Boolean.parseBoolean;

import java.util.Collection;
import java.util.stream.Stream;
import lombok.extern.log4j.Log4j2;
import org.folio.search.configuration.properties.SearchConfigurationProperties;
import org.folio.search.domain.dto.LanguageConfig;
import org.folio.search.domain.dto.ReindexRequest;
import org.folio.search.service.browse.CallNumberBrowseRangeService;
import org.folio.search.service.metadata.ResourceDescriptionService;
import org.folio.search.service.systemuser.SystemUserService;
import org.folio.spring.FolioExecutionContext;
import org.folio.spring.liquibase.FolioSpringLiquibase;
import org.folio.spring.service.TenantService;
import org.folio.tenant.domain.dto.TenantAttributes;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Log4j2
@Primary
@Service
public class SearchTenantService extends TenantService {

  private static final String REINDEX_PARAM_NAME = "runReindex";

  private final IndexService indexService;
  private final FolioExecutionContext context;
  private final KafkaAdminService kafkaAdminService;
  private final SystemUserService systemUserService;
  private final LanguageConfigService languageConfigService;
  private final ResourceDescriptionService resourceDescriptionService;
  private final CallNumberBrowseRangeService callNumberBrowseRangeService;
  private final SearchConfigurationProperties searchConfigurationProperties;

  public SearchTenantService(JdbcTemplate jdbcTemplate, FolioExecutionContext context,
                             FolioSpringLiquibase folioSpringLiquibase, KafkaAdminService kafkaAdminService,
                             IndexService indexService, SystemUserService systemUserService,
                             LanguageConfigService languageConfigService,
                             CallNumberBrowseRangeService callNumberBrowseRangeService,
                             ResourceDescriptionService resourceDescriptionService,
                             SearchConfigurationProperties searchConfigurationProperties) {
    super(jdbcTemplate, context, folioSpringLiquibase);
    this.kafkaAdminService = kafkaAdminService;
    this.indexService = indexService;
    this.context = context;
    this.systemUserService = systemUserService;
    this.languageConfigService = languageConfigService;
    this.callNumberBrowseRangeService = callNumberBrowseRangeService;
    this.resourceDescriptionService = resourceDescriptionService;
    this.searchConfigurationProperties = searchConfigurationProperties;
  }

  /**
   * Initializes tenant using given {@link TenantAttributes}.
   *
   * <p>This method:</p>
   * <ul>
   *   <li>Creates Kafka topics</li>
   *   <li>Creates a system user to perform record indexing</li>
   *   <li>Add default languages to the tenant configuration</li>
   *   <li>Creates Elasticsearch indexes and corresponding mappings for supported record types</li>
   *   <li>Starts reindexing process for inventory (if it's specified)</li>
   * </ul>
   *
   * @param tenantAttributes - tenant attributes comes from {@code POST /_/tenant} request.
   */
  @Override
  protected void afterTenantUpdate(TenantAttributes tenantAttributes) {
    kafkaAdminService.createKafkaTopics();
    kafkaAdminService.restartEventListeners();
    systemUserService.prepareSystemUser();
    createLanguages();
    createIndexesAndReindex(tenantAttributes);
    log.info("Tenant init has been completed");
  }

  /**
   * Removes elasticsearch indices for all supported record types and cleaning related caches.
   *
   * @param tenantAttributes - tenant attributes comes from {@code POST /_/tenant} request.
   */
  @Override
  protected void afterTenantDeletion(TenantAttributes tenantAttributes) {
    var tenantId = context.getTenantId();
    callNumberBrowseRangeService.evictRangeCache(tenantId);
    resourceDescriptionService.getResourceNames().forEach(name -> {
      log.info("Removing elasticsearch index [resourceName={}, tenant={}]", name, tenantId);
      indexService.dropIndex(name, tenantId);
    });
  }

  private void createIndexesAndReindex(TenantAttributes tenantAttributes) {
    var resourceNames = resourceDescriptionService.getResourceNames();
    resourceNames.forEach(resourceName -> indexService.createIndexIfNotExist(resourceName, context.getTenantId()));
    Stream.ofNullable(tenantAttributes.getParameters())
      .flatMap(Collection::stream)
      .filter(parameter -> parameter.getKey().equals(REINDEX_PARAM_NAME) && parseBoolean(parameter.getValue()))
      .findFirst()
      .ifPresent(parameter -> resourceNames.forEach(resource -> {
        if (resourceDescriptionService.get(resource).isReindexSupported()) {
          indexService.reindexInventory(context.getTenantId(), new ReindexRequest().resourceName(resource));
        }
      }));
  }

  private void createLanguages() {
    var existingLanguages = languageConfigService.getAllLanguageCodes();

    var initialLanguages = searchConfigurationProperties.getInitialLanguages();
    log.info("Initializing tenant [initialLanguages={}, existingLanguages={}]", initialLanguages, existingLanguages);

    initialLanguages.stream()
      .filter(code -> !existingLanguages.contains(code))
      .map(code -> new LanguageConfig().code(code))
      .forEach(languageConfigService::create);
  }

}
