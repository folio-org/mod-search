package org.folio.search.support.api;

import static com.github.tomakehurst.wiremock.http.Response.Builder.like;
import static org.folio.search.support.api.InventoryApi.getInventoryView;
import static org.folio.search.utils.TestUtils.OBJECT_MAPPER;
import static org.folio.spring.integration.XOkapiHeaders.TENANT;

import com.github.tomakehurst.wiremock.common.FileSource;
import com.github.tomakehurst.wiremock.extension.Parameters;
import com.github.tomakehurst.wiremock.extension.ResponseTransformer;
import com.github.tomakehurst.wiremock.http.Request;
import com.github.tomakehurst.wiremock.http.Response;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.apache.commons.collections.MapUtils;

@RequiredArgsConstructor
public class InventoryViewResponseBuilder extends ResponseTransformer {

  @Override
  @SneakyThrows
  public Response transform(Request request, Response response, FileSource files, Parameters parameters) {
    var tenant = request.header(TENANT).firstValue();
    var instanceViews = getInstanceIdsFromRequest(request)
      .map(id -> getInventoryView(tenant, id))
      .flatMap(Optional::stream)
      .map(instance -> Map.of(
        "instance", instance,
        "holdingsRecords", MapUtils.getObject(instance, "holdings"),
        "items", MapUtils.getObject(instance, "items")))
      .limit(Integer.parseInt(request.queryParameter("limit").firstValue()))
      .collect(Collectors.toList());

    return like(response).body(OBJECT_MAPPER.writeValueAsString(Map.of("instances", instanceViews))).build();
  }

  @Override
  public String getName() {
    return "inventory-view-transformer";
  }

  @Override
  public boolean applyGlobally() {
    return false;
  }

  private static Stream<String> getInstanceIdsFromRequest(Request request) {
    return Stream.of(request.queryParameter("query").firstValue()
        .replaceAll("id==\\(", "")
        .replaceAll("\"", "")
        .replace(")", "")
        .split(" or "))
      .map(String::trim);
  }
}
