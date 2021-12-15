package org.folio.search.service.context;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import org.folio.spring.FolioExecutionContext;
import org.folio.spring.FolioModuleMetadata;

@Data
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SystemFolioExecutionContext implements FolioExecutionContext {

  private String token;
  private String okapiUrl;
  private String tenantId;
  private String userName;
  private FolioModuleMetadata folioModuleMetadata;

  @Builder.Default private Map<String, Collection<String>> allHeaders = Collections.emptyMap();
  @Builder.Default private Map<String, Collection<String>> okapiHeaders = Collections.emptyMap();
}
