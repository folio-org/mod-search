package org.folio.search.configuration.properties;

import jakarta.annotation.PostConstruct;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@NoArgsConstructor
@AllArgsConstructor(staticName = "of")
@ConfigurationProperties(prefix = "folio.reindex")
@Log4j2
public class ReindexConfigurationProperties {

  private static final java.util.regex.Pattern WORK_MEM_VALIDATION_PATTERN =
    java.util.regex.Pattern.compile("^\\d+\\s*(KB|MB|GB)$");

  /**
   * Defines number of locations to retrieve per inventory http request on locations reindex process.
   */
  private Integer locationBatchSize = 1_000;

  private Integer uploadRangeSize = 1_000;

  @Min(1)
  private Integer uploadRangeLevel = 3;

  private Integer mergeRangeSize = 1_000;

  private Integer mergeRangePublisherCorePoolSize = 3;

  private Integer mergeRangePublisherMaxPoolSize = 6;

  private long mergeRangePublisherRetryIntervalMs = 1000;

  private int mergeRangePublisherRetryAttempts = 5;

  /**
   * Defines the PostgreSQL work_mem value to set for migration operations.
   * This controls the amount of memory used by query operations before PostgreSQL
   * starts writing data to temporary disk files. Default is '64MB'.
   * Format must be a number followed by KB, MB, or GB (e.g., "64MB", "512KB", "1GB").
   */
  @Pattern(regexp = "^\\d+\\s*(KB|MB|GB)$",
           message = "work_mem must be a number followed by KB, MB, or GB (e.g., '64MB', '512KB', '1GB')")
  private String migrationWorkMem = "64MB";

  /**
   * Validates the configuration properties at startup.
   * This ensures that any invalid configuration fails fast during application startup.
   */
  @PostConstruct
  public void validateConfiguration() {
    log.info("Validating reindex configuration properties...");

    // Validate work_mem format
    if (!WORK_MEM_VALIDATION_PATTERN.matcher(migrationWorkMem).matches()) {
      var errorMsg = "Invalid work_mem configuration: " + migrationWorkMem
        + ". Must be a number followed by KB, MB, or GB (e.g., '64MB', '512KB', '1GB')";
      log.error(errorMsg);
      throw new IllegalArgumentException(errorMsg);
    }

    log.info("Reindex configuration validated successfully. Migration work_mem: {}", migrationWorkMem);
  }
}
