package org.folio.search.service.system;

import liquibase.exception.LiquibaseException;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.search.configuration.properties.SystemProperties;
import org.folio.spring.liquibase.FolioSpringLiquibase;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.liquibase.LiquibaseProperties;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Log4j2
public class SystemSchemaInitializer implements InitializingBean {

  private final LiquibaseProperties liquibaseProperties;
  private final FolioSpringLiquibase folioSpringLiquibase;
  private final SystemProperties systemProperties;

  /**
   * Performs database update using {@link FolioSpringLiquibase} and then returns previous configuration for this bean.
   *
   * @throws LiquibaseException - if liquibase update failed.
   */
  @Override
  public void afterPropertiesSet() throws LiquibaseException {
    log.info("Starting liquibase update for system");

    folioSpringLiquibase.setChangeLog(systemProperties.getChangeLog());
    folioSpringLiquibase.setDefaultSchema(systemProperties.getSchemaName());

    folioSpringLiquibase.performLiquibaseUpdate();

    folioSpringLiquibase.setChangeLog(liquibaseProperties.getChangeLog());
    folioSpringLiquibase.setDefaultSchema(liquibaseProperties.getDefaultSchema());

    log.info("Completed liquibase update for system");
  }
}
