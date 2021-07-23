package org.folio.search;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * Folio search application.
 */
@EnableFeignClients
@SpringBootApplication
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
