package org.folio.search.service;

import static java.lang.Boolean.parseBoolean;

import java.util.Collection;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.extern.log4j.Log4j2;
import org.folio.search.configuration.properties.SearchConfigurationProperties;
import org.folio.search.domain.dto.LanguageConfig;
import org.folio.search.domain.dto.ReindexRequest;
import org.folio.search.service.browse.CallNumberBrowseRangeService;
import org.folio.search.service.consortium.LanguageConfigServiceDecorator;
import org.folio.search.service.metadata.ResourceDescriptionService;
import org.folio.spring.FolioExecutionContext;
import org.folio.spring.liquibase.FolioSpringLiquibase;
import org.folio.spring.service.PrepareSystemUserService;
import org.folio.spring.service.TenantService;
import org.folio.spring.tools.kafka.KafkaAdminService;
import org.folio.tenant.domain.dto.Parameter;
import org.folio.tenant.domain.dto.TenantAttributes;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Log4j2
@Primary
@Service
public class SearchTenantService extends TenantService {

  private static final String REINDEX_PARAM_NAME = "runReindex";
  private static final String CENTRAL_TENANT_ID_PARAM_NAME = "centralTenantId";

  private final IndexService indexService;
  private final ScriptService scriptService;
  private final KafkaAdminService kafkaAdminService;
  private final PrepareSystemUserService prepareSystemUserService;
  private final LanguageConfigServiceDecorator languageConfigService;
  private final ResourceDescriptionService resourceDescriptionService;
  private final CallNumberBrowseRangeService callNumberBrowseRangeService;
  private final SearchConfigurationProperties searchConfigurationProperties;

  public SearchTenantService(JdbcTemplate jdbcTemplate, FolioExecutionContext context,
                             FolioSpringLiquibase folioSpringLiquibase, KafkaAdminService kafkaAdminService,
                             IndexService indexService, ScriptService scriptService,
                             PrepareSystemUserService prepareSystemUserService,
                             LanguageConfigServiceDecorator languageConfigService,
                             CallNumberBrowseRangeService callNumberBrowseRangeService,
                             ResourceDescriptionService resourceDescriptionService,
                             SearchConfigurationProperties searchConfigurationProperties) {
    super(jdbcTemplate, context, folioSpringLiquibase);
    this.kafkaAdminService = kafkaAdminService;
    this.indexService = indexService;
    this.scriptService = scriptService;
    this.prepareSystemUserService = prepareSystemUserService;
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
   * </ul>
   *
   * <p>This method additionally if it's not a consortium member tenant:</p>
   * <ul>
   *   <li>Creates database schemas</li>
   *   <li>Add default languages to the tenant configuration</li>
   *   <li>Creates Elasticsearch indexes and corresponding mappings for supported record types</li>
   *   <li>Starts reindexing process for inventory (if it's specified)</li>
   * </ul>
   *
   * @param tenantAttributes - tenant attributes comes from {@code POST /_/tenant} request.
   */
  @Override
  public synchronized void createOrUpdateTenant(TenantAttributes tenantAttributes) {
    var tenantId = context.getTenantId();
    var centralTenant = centralTenant(tenantId, tenantAttributes);
    if (tenantId.equals(centralTenant)) {
      super.createOrUpdateTenant(tenantAttributes);
    } else {
      log.info("Not executing full tenant init for not central tenant {}.", tenantId);
      baseAfterTenantUpdate();
    }
  }

  private void baseAfterTenantUpdate() {
    kafkaAdminService.createTopics(context.getTenantId());
    kafkaAdminService.restartEventListeners();
    prepareSystemUserService.setupSystemUser();
    log.info("Tenant base init has been completed");
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
    baseAfterTenantUpdate();
    createLanguages();
    createIndexesAndReindex(tenantAttributes);
    log.info("Tenant init has been completed");
  }

  /**
   * Removes database schemas.
   * Removes elasticsearch indices for all supported record types and cleaning related caches
   * if it's not a consortium member tenant.
   * Deletes kafka topics.
   *
   * @param tenantAttributes - tenant attributes comes from {@code POST /_/tenant} request.
   */
  @Override
  public void deleteTenant(TenantAttributes tenantAttributes) {
    var tenantId = context.getTenantId();
    var centralTenant = centralTenant(tenantId, tenantAttributes);
    if (tenantId.equals(centralTenant)) {
      super.deleteTenant(tenantAttributes);
    } else {
      log.info("Not executing full tenant destroy for not central tenant {}.", tenantId);
      baseAfterTenantDeletion(tenantId);
    }
  }

  private void baseAfterTenantDeletion(String tenantId) {
    kafkaAdminService.deleteTopics(tenantId);
  }

  /**
   * Removes elasticsearch indices for all supported record types and cleaning related caches
   * if it's not a consortium member tenant.
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

    baseAfterTenantDeletion(tenantId);
  }

  private void createIndexesAndReindex(TenantAttributes tenantAttributes) {
    scriptService.saveScripts();
    var resourceNames = resourceDescriptionService.getResourceNames();
    resourceNames.forEach(resourceName -> indexService.createIndexIfNotExist(resourceName, context.getTenantId()));
    Stream.ofNullable(tenantAttributes.getParameters())
      .flatMap(Collection::stream)
      .filter(parameter -> parameter.getKey().equals(REINDEX_PARAM_NAME) && parseBoolean(parameter.getValue()))
      .findFirst()
      .ifPresent(parameter -> resourceNames.forEach(resource -> {
        if (resourceDescriptionService.get(resource).isReindexSupported()) {
          indexService.reindexInventory(context.getTenantId(),
            new ReindexRequest().resourceName(ReindexRequest.ResourceNameEnum.fromValue(resource)));
        }
      }));
  }

  private void createLanguages() {
    var existingLanguages = languageConfigService.getAllLanguageCodes();
    var initialLanguages = searchConfigurationProperties.getInitialLanguages();
    log.debug("Attempts to create Languages by [initialLanguages={}, existingLanguages={}]",
      initialLanguages, existingLanguages);

    initialLanguages.stream()
      .filter(code -> !existingLanguages.contains(code))
      .map(code -> new LanguageConfig().code(code))
      .forEach(languageConfigService::create);
  }

  private String centralTenant(String contextTenantId, TenantAttributes tenantAttributes) {
    return Optional.ofNullable(tenantAttributes.getParameters())
      .flatMap(parameters -> parameters.stream()
        .filter(parameter -> parameter.getKey().equals(CENTRAL_TENANT_ID_PARAM_NAME))
        .findFirst()
        .map(Parameter::getValue))
      .orElse(contextTenantId);
  }

}
