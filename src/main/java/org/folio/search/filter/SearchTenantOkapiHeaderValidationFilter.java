package org.folio.search.filter;

import java.io.IOException;
import java.util.Collections;
import java.util.Set;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.folio.spring.filter.TenantOkapiHeaderValidationFilter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component("tenantOkapiHeaderValidationFilter")
@EnableConfigurationProperties(SearchTenantOkapiHeaderValidationFilter.ValidationProperties.class)
public class SearchTenantOkapiHeaderValidationFilter extends TenantOkapiHeaderValidationFilter {
  private final ValidationProperties validationProperties;

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
    throws IOException, ServletException {

    if (isPathExcluded(request)) {
      chain.doFilter(request, response);
    } else {
      super.doFilter(request, response, chain);
    }
  }

  private boolean isPathExcluded(ServletRequest request) {
    return validationProperties.getExcludePaths()
      .contains(((HttpServletRequest) request).getRequestURI());
  }

  @Data
  @ConfigurationProperties("folio.tenant.validation")
  @AllArgsConstructor(staticName = "of")
  @NoArgsConstructor
  public static class ValidationProperties {
    private Set<String> excludePaths = Collections.emptySet();
  }
}
