package org.folio.search.support.api;

import static com.github.tomakehurst.wiremock.http.Response.Builder.like;
import static java.util.Collections.emptyList;
import static org.folio.search.utils.TestUtils.OBJECT_MAPPER;

import com.github.tomakehurst.wiremock.common.FileSource;
import com.github.tomakehurst.wiremock.extension.Parameters;
import com.github.tomakehurst.wiremock.extension.ResponseTransformer;
import com.github.tomakehurst.wiremock.http.Request;
import com.github.tomakehurst.wiremock.http.Response;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;

@RequiredArgsConstructor
public class InventoryViewResponseBuilder extends ResponseTransformer {
  @Override
  @SneakyThrows
  public Response transform(Request request, Response response, FileSource files, Parameters parameters) {
    return like(response)
      .body(OBJECT_MAPPER.writeValueAsString(Map.of("instances", emptyList())))
      .build();
  }

  @Override
  public String getName() {
    return "inventory-view-transformer";
  }

  @Override
  public boolean applyGlobally() {
    return false;
  }
}
