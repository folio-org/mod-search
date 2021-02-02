package org.folio.search;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

/**
 * Folio search application.
 */
@SpringBootApplication
@EnableCaching
public class SearchApplication {

  /**
   * Runs spring application.
   *
   * @param args command line arguments.
   */
  public static void main(String[] args) {
    SpringApplication.run(SearchApplication.class, args);
  }
}
