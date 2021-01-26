package org.folio.search.sample;

import java.util.List;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class InstanceBuilder {
  @Builder.Default
  private final UUID id = UUID.randomUUID();
  private final String title;
  private final List<String> languages;
  @Builder.Default
  private final UUID instanceTypeId = UUID.randomUUID();
}
