package org.folio.search.repository;

import static org.folio.search.utils.SearchUtils.performExceptionalOperation;

import java.io.IOException;
import java.nio.ByteBuffer;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.search.exception.SearchOperationException;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.admin.cluster.storedscripts.GetStoredScriptRequest;
import org.opensearch.action.admin.cluster.storedscripts.PutStoredScriptRequest;
import org.opensearch.client.RequestOptions;
import org.opensearch.client.RestHighLevelClient;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.common.bytes.BytesReference;
import org.opensearch.core.rest.RestStatus;
import org.springframework.stereotype.Repository;

@Log4j2
@Repository
@RequiredArgsConstructor
public class ScriptRepository {

  private final RestHighLevelClient elasticsearchClient;

  public boolean saveScript(String scriptId, String scriptContent) {
    var putStoredScriptRequest = new PutStoredScriptRequest()
      .id(scriptId)
      .content(BytesReference.fromByteBuffer(ByteBuffer.wrap(scriptContent.getBytes())), XContentType.JSON);
    var createIndexResponse = performExceptionalOperation(
      () -> elasticsearchClient.putScript(putStoredScriptRequest, RequestOptions.DEFAULT),
      null, "putScript");

    return createIndexResponse.isAcknowledged();
  }

  public boolean scriptExists(String scriptId) {
    log.info("Checking that script exists [index: {}]", scriptId);
    var request = new GetStoredScriptRequest(scriptId);
    try {
      return elasticsearchClient.getScript(request, RequestOptions.DEFAULT).status() == RestStatus.OK;
    } catch (OpenSearchStatusException e) {
      if (e.status() == RestStatus.NOT_FOUND) {
        return false;
      }
      throw getSearchOperationException(e);
    } catch (IOException e) {
      throw getSearchOperationException(e);
    }
  }

  private SearchOperationException getSearchOperationException(Exception e) {
    return new SearchOperationException(String.format(
      "Failed to perform elasticsearch request [type=scriptExists, message: %s]", e.getMessage()), e);
  }
}
