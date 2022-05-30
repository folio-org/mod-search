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
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties("spring.opensearch")
public class OpensearchProperties {

  private List<String> uris = new ArrayList<>(Collections.singletonList("http://localhost:9200"));

  private String username;

  private String password;

  private Duration connectionTimeout = Duration.ofSeconds(1);

  private Duration socketTimeout = Duration.ofSeconds(30);

  private String pathPrefix;

  public List<String> getUris() {
    return this.uris;
  }

  public void setUris(List<String> uris) {
    this.uris = uris;
  }

  public String getUsername() {
    return this.username;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  public String getPassword() {
    return this.password;
  }

  public void setPassword(String password) {
    this.password = password;
  }

  public Duration getConnectionTimeout() {
    return this.connectionTimeout;
  }

  public void setConnectionTimeout(Duration connectionTimeout) {
    this.connectionTimeout = connectionTimeout;
  }

  public Duration getSocketTimeout() {
    return this.socketTimeout;
  }

  public void setSocketTimeout(Duration socketTimeout) {
    this.socketTimeout = socketTimeout;
  }

  public String getPathPrefix() {
    return this.pathPrefix;
  }

  public void setPathPrefix(String pathPrefix) {
    this.pathPrefix = pathPrefix;
  }

}
