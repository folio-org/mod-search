package org.folio.search.client;

import java.net.URI;
import java.util.Locale;
import lombok.Getter;
import org.folio.search.model.client.CqlQuery;
import org.folio.search.model.service.ReferenceRecord;
import org.folio.search.model.service.ResultList;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.util.UriComponentsBuilder;

@HttpExchange
public interface InventoryReferenceDataClient {

  /**
   * Fetches reference data by given URI and {@link CqlQuery} object.
   *
   * @param uri   - uri address to request for as {@link URI} object
   * @param query - cql query as {@link CqlQuery} object
   * @return {@link  ResultList} with {@link  ReferenceRecord} object
   */
  @GetExchange
  ResultList<ReferenceRecord> getReferenceData(URI uri, @RequestParam CqlQuery query, @RequestParam int limit);

  @Getter
  enum ReferenceDataType {

    IDENTIFIER_TYPES("identifier-types"),
    ALTERNATIVE_TITLE_TYPES("alternative-title-types"),
    CALL_NUMBER_TYPES("call-number-types"),
    CLASSIFICATION_TYPES("classification-types");

    /**
     * Request URI for feign client.
     */
    private final URI uri;

    /**
     * Required args constructor.
     *
     * @param uriString - string value to create URI from
     */
    ReferenceDataType(String uriString) {
      this.uri = UriComponentsBuilder.fromUriString(uriString).build().toUri();
    }

    @Override
    public String toString() {
      return name().toLowerCase(Locale.ROOT);
    }
  }
}
