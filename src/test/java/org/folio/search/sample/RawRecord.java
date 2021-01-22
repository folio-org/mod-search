package org.folio.search.sample;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import java.util.HashMap;
import java.util.Map;
import lombok.Data;
import lombok.Getter;

@Data
public class RawRecord {
  private String id;
  @Getter(onMethod_ = @__(@JsonAnyGetter))
  private Map<String, Object> unknown = new HashMap<>();

  @JsonAnySetter
  public void set(String name, Object value) {
    unknown.put(name, value);
  }
}
