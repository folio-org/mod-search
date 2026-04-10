package org.folio.search.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.folio.search.service.QueryVersionResolver;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class QueryVersionRequestHelper {

  public static final String PRIMARY_HEADER = "X-Query-Version";
  public static final String LEGACY_HEADER = "X-Search-Query-Version";

  private final HttpServletRequest request;
  private final HttpServletResponse response;
  private final QueryVersionResolver queryVersionResolver;

  public String resolve(String tenantId) {
    var resolvedVersion = queryVersionResolver.resolveVersion(getQueryVersion(), tenantId);
    response.setHeader(PRIMARY_HEADER, resolvedVersion);
    return resolvedVersion;
  }

  private String getQueryVersion() {
    var primary = request.getHeader(PRIMARY_HEADER);
    return StringUtils.isNotBlank(primary) ? primary : request.getHeader(LEGACY_HEADER);
  }
}
