package org.folio.search.configuration;

import static org.folio.spring.integration.XOkapiHeaders.TENANT;
import static org.folio.spring.integration.XOkapiHeaders.TOKEN;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import java.util.Collections;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.folio.spring.FolioExecutionContext;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FeignRequestInterceptor implements RequestInterceptor {

  private final FolioExecutionContext folioExecutionContext;

  @Override
  @SneakyThrows
  public void apply(RequestTemplate template) {
    template.header(TOKEN, Collections.singletonList(folioExecutionContext.getToken()));
    template.header(TENANT, Collections.singletonList(folioExecutionContext.getTenantId()));
  }
}
