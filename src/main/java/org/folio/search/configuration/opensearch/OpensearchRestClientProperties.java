/*
 * Copyright 2012-2022 the original author or authors.
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

package org.folio.search.configuration.opensearch;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "spring.opensearch.restclient")
public class OpensearchRestClientProperties {

  private final Sniffer sniffer = new Sniffer();

  public Sniffer getSniffer() {
    return this.sniffer;
  }

  public static class Sniffer {

    private Duration interval = Duration.ofMinutes(5);

    private Duration delayAfterFailure = Duration.ofMinutes(1);

    public Duration getInterval() {
      return this.interval;
    }

    public void setInterval(Duration interval) {
      this.interval = interval;
    }

    public Duration getDelayAfterFailure() {
      return this.delayAfterFailure;
    }

    public void setDelayAfterFailure(Duration delayAfterFailure) {
      this.delayAfterFailure = delayAfterFailure;
    }

  }

}
