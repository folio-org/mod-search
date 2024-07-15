package org.folio.search.client;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import java.net.URI;
import java.util.Locale;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.folio.search.model.service.ResultList;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.util.UriComponentsBuilder;

@FeignClient("locations")
public interface LocationsClient {

  /**
   * Retrieves locations and location-unit data from inventory storage.
   *
   * @param uri - uri address to request for as {@link URI} object
   * @param offset - number of resources to skip
   * @param limit - limit of resources to fetch
   * @return {@link ResultList} with Instance objects inside.
   */
  @GetMapping(produces = APPLICATION_JSON_VALUE)
  ResultList<Map<String, Object>> getLocationsData(URI uri, @RequestParam int offset, @RequestParam int limit);

  @Getter
  @AllArgsConstructor
  enum DocumentType {

    LOCATION("http://locations"),
    CAMPUS("http://location-units/campuses"),
    LIBRARY("http://location-units/libraries");

    /**
     * Request URI for feign client.
     */
    private final URI uri;

    /**
     * Required args constructor.
     *
     * @param uriString - string value to create URI from
     */
    DocumentType(String uriString) {
      this.uri = UriComponentsBuilder.fromUriString(uriString).build().toUri();
    }

    @Override
    public String toString() {
      return name().toLowerCase(Locale.ROOT);
    }
  }
}
