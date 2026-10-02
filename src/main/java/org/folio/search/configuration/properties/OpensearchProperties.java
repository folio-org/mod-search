/*
 * Copyright 2012-2021 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.folio.search.configuration.properties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties("spring.opensearch")
public class OpensearchProperties {

  private List<String> uris = new ArrayList<>(Collections.singletonList("http://localhost:9200"));

  private String username;

  private String password;

  private Duration connectionTimeout = Duration.ofSeconds(1);

  private Duration socketTimeout = Duration.ofSeconds(30);

  private String pathPrefix;

  private boolean compressionEnabled = true;

  /**
   * Maximum number of connections per route in the async connection pool.
   * Defaults to 25. Increase for high-concurrency environments.
   */
  private int maxConnPerRoute = 25;

  /**
   * Maximum total number of connections in the async connection pool.
   * Defaults to 100. Should be at least maxConnPerRoute * number-of-opensearch-nodes.
   */
  private int maxConnTotal = 100;

  /**
   * Maximum time a connection may be kept alive (TTL). A null value means no TTL
   * (connections are reused indefinitely). Expired connections are closed and recreated,
   * which also helps rebalancing after an OpenSearch cluster restart.
   * Defaults to 5 minutes.
   */
  private Duration connectionTimeToLive = Duration.ofMinutes(5);

  /**
   * Maximum time a connection may stay idle in the pool before a background evictor closes it.
   * Set this lower than the idle timeout of any firewall, NAT or load balancer between mod-search
   * and OpenSearch, so that silently dropped connections are never reused.
   * A null value disables idle connection eviction. Defaults to 60 seconds.
   */
  private Duration maxIdleTime = Duration.ofSeconds(60);

  /**
   * How long a connection may remain idle in the pool before it is validated
   * with a TCP check on next use. Null (the default) disables validation,
   * which is appropriate when the application issues frequent requests.
   * Setting this too low (e.g. PT5S) causes a validation round-trip on
   * nearly every reused connection and degrades throughput under moderate load.
   */
  private Duration validateAfterInactivity;

  /**
   * Specifies the number of retry attempts for search requests on transient connection errors.
   */
  private int searchRetryAttempts = 3;

  /**
   * Specifies time in milliseconds to wait before reattempting a failed search request.
   */
  private long searchRetryIntervalMs = 500;
}
