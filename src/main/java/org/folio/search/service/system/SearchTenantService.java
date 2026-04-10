package org.folio.search.service.system;

import static java.lang.Boolean.parseBoolean;

import java.util.Collection;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.extern.log4j.Log4j2;
import org.folio.search.configuration.properties.SearchConfigurationProperties;
import org.folio.search.domain.dto.LanguageConfig;
import org.folio.search.domain.dto.ReindexRequest;
import org.folio.search.model.entity.TenantEntity;
import org.folio.search.model.types.QueryVersion;
import org.folio.search.model.types.ReindexEntityType;
import org.folio.search.model.types.ResourceType;
import org.folio.search.service.IndexFamilyService;
import org.folio.search.service.IndexService;
import org.folio.search.service.consortium.LanguageConfigServiceDecorator;
import org.folio.search.service.metadata.ResourceDescriptionService;
import org.folio.search.service.reindex.ReindexService;
import org.folio.search.service.reindex.jdbc.TenantRepository;
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
  private final ReindexService reindexService;
  private final KafkaAdminService kafkaAdminService;
  private final PrepareSystemUserService prepareSystemUserService;
  private final LanguageConfigServiceDecorator languageConfigService;
  private final ResourceDescriptionService resourceDescriptionService;
  private final SearchConfigurationProperties searchConfigurationProperties;
  private final TenantRepository tenantRepository;
  private final IndexFamilyService indexFamilyService;

  public SearchTenantService(JdbcTemplate jdbcTemplate, FolioExecutionContext context,
                             FolioSpringLiquibase folioSpringLiquibase, KafkaAdminService kafkaAdminService,
                             IndexService indexService, ReindexService reindexService,
                             PrepareSystemUserService prepareSystemUserService,
                             LanguageConfigServiceDecorator languageConfigService,
                             ResourceDescriptionService resourceDescriptionService,
                             SearchConfigurationProperties searchConfigurationProperties,
                             TenantRepository tenantRepository,
                             IndexFamilyService indexFamilyService) {
    super(jdbcTemplate, context, folioSpringLiquibase);
    this.kafkaAdminService = kafkaAdminService;
    this.indexService = indexService;
    this.reindexService = reindexService;
    this.prepareSystemUserService = prepareSystemUserService;
    this.languageConfigService = languageConfigService;
    this.resourceDescriptionService = resourceDescriptionService;
    this.searchConfigurationProperties = searchConfigurationProperties;
    this.tenantRepository = tenantRepository;
    this.indexFamilyService = indexFamilyService;
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
    var isCentral = tenantId.equals(centralTenant);
    var tenantEntity = new TenantEntity(tenantId, isCentral ? null : centralTenant, true);
    tenantRepository.saveTenant(tenantEntity);
    if (isCentral) {
      super.createOrUpdateTenant(tenantAttributes);
    } else {
      log.info("Not executing full tenant init for not central tenant {}.", tenantId);
      baseAfterTenantUpdate();
    }
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
    var isCentral = tenantId.equals(centralTenant);
    var tenantEntity = new TenantEntity(tenantId, isCentral ? null : centralTenant, false);
    tenantRepository.saveTenant(tenantEntity);
    if (isCentral) {
      super.deleteTenant(tenantAttributes);
    } else {
      log.info("Not executing full tenant destroy for not central tenant {}.", tenantId);
      baseAfterTenantDeletion(tenantId);
    }
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
   * Removes elasticsearch indices for all supported record types and cleaning related caches
   * if it's not a consortium member tenant.
   *
   * @param tenantAttributes - tenant attributes comes from {@code POST /_/tenant} request.
   */
  @Override
  protected void afterTenantDeletion(TenantAttributes tenantAttributes) {
    var tenantId = context.getTenantId();
    resourceDescriptionService.getResourceTypes().forEach(name -> {
      log.info("Removing elasticsearch index [resourceName={}, tenant={}]", name, tenantId);
      indexService.dropIndex(name, tenantId);
    });

    baseAfterTenantDeletion(tenantId);
  }

  private void baseAfterTenantUpdate() {
    kafkaAdminService.createTopics(context.getTenantId());
    kafkaAdminService.restartEventListeners();
    prepareSystemUserService.setupSystemUser();
    log.info("Tenant base init has been completed");
  }

  private void baseAfterTenantDeletion(String tenantId) {
    kafkaAdminService.deleteTopics(tenantId);
  }

  private void createIndexesAndReindex(TenantAttributes tenantAttributes) {
    var tenantId = context.getTenantId();
    var resourceNames = resourceDescriptionService.getResourceTypes();

    var runReindex = Stream.ofNullable(tenantAttributes.getParameters())
      .flatMap(Collection::stream)
      .anyMatch(parameter -> parameter.getKey().equals(REINDEX_PARAM_NAME) && parseBoolean(parameter.getValue()));

    // Create indexes for non-INSTANCE resources (INSTANCE is handled via V1 family bootstrap or full reindex)
    resourceNames.forEach(resourceName -> {
      if (!resourceName.equals(ResourceType.INSTANCE)) {
        indexService.createIndexIfNotExist(resourceName, tenantId);
      }
    });

    // Bootstrap V1 family only when NOT doing a full reindex.
    // Full reindex creates the physical instance index directly (pre-migration state)
    // and the V1 ACTIVE family guard in submitFullReindex would reject the reindex.
    if (!runReindex) {
      bootstrapV1Family(tenantId);
    }

    if (runReindex) {
      resourceNames.forEach(resource -> {
        if (!resourceDescriptionService.get(resource).isReindexSupported()) {
          return;
        }
        if (resource.getName().equals(ReindexEntityType.INSTANCE.getType())) {
          reindexService.submitFullReindex(tenantId, null);
        } else {
          indexService.reindexInventory(tenantId,
            new ReindexRequest().resourceName(ReindexRequest.ResourceNameEnum.fromValue(resource.getName())));
        }
      });
    }
  }

  private void bootstrapV1Family(String tenantId) {
    // Check if a V1 ACTIVE family already exists
    if (indexFamilyService.findActiveFamily(tenantId, QueryVersion.V1).isPresent()) {
      log.info("bootstrapV1Family:: V1 family already active [tenant: {}]", tenantId);
      return;
    }

    // Check if physical index already exists (existing/pre-migration tenant)
    var aliasName = indexFamilyService.getAliasName(tenantId, QueryVersion.V1);
    if (indexFamilyService.physicalIndexExists(aliasName)) {
      // Existing tenant — defer migration to explicit streaming reindex
      log.info("bootstrapV1Family:: physical index exists, deferring migration [tenant: {}, index: {}]",
        tenantId, aliasName);
      // Create the legacy index via IndexService (it already exists, so this is a no-op)
      indexService.createIndexIfNotExist(ResourceType.INSTANCE, tenantId);
      return;
    }

    // New tenant — allocate V1 family, create alias, mark ACTIVE
    log.info("bootstrapV1Family:: creating V1 family for new tenant [tenant: {}]", tenantId);
    var family = indexFamilyService.allocateNewFamily(tenantId, QueryVersion.V1, null);
    indexFamilyService.createAliasDirectly(aliasName, family.getIndexName(), family.getId());
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
