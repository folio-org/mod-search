package org.folio.search.repository;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.opensearch.client.RequestOptions.DEFAULT;

import java.io.IOException;
import org.folio.search.exception.SearchOperationException;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opensearch.action.admin.cluster.storedscripts.GetStoredScriptRequest;
import org.opensearch.action.admin.cluster.storedscripts.GetStoredScriptResponse;
import org.opensearch.action.admin.cluster.storedscripts.PutStoredScriptRequest;
import org.opensearch.action.support.master.AcknowledgedResponse;
import org.opensearch.client.RestHighLevelClient;
import org.opensearch.core.rest.RestStatus;

@UnitTest
@ExtendWith(MockitoExtension.class)
class ScriptRepositoryTest {

  @InjectMocks
  private ScriptRepository scriptRepository;
  @Mock
  private RestHighLevelClient esClient;

  @Test
  void saveScript_positive() throws IOException {
    var esResponse = mock(AcknowledgedResponse.class);

    when(esResponse.isAcknowledged()).thenReturn(true);
    when(esClient.putScript(any(PutStoredScriptRequest.class), eq(DEFAULT))).thenReturn(esResponse);

    var scriptContent = "{\"script\": {\"lang\": \"painless\",\"source\": \"scriptContent\"}}";
    var actual = scriptRepository.saveScript("scriptId", scriptContent);

    assertTrue(actual);
  }

  @Test
  void saveScript_negative() throws IOException {
    when(esClient.putScript(any(PutStoredScriptRequest.class), eq(DEFAULT))).thenThrow(new IOException("err"));

    var scriptContent = "{\"script\": {\"lang\": \"painless\",\"source\": \"scriptContent\"}}";

    assertThatThrownBy(() -> scriptRepository.saveScript("scriptId", scriptContent))
      .isInstanceOf(SearchOperationException.class)
      .hasCauseExactlyInstanceOf(IOException.class)
      .hasMessage("Failed to perform elasticsearch request "
        + "[index=null, type=putScript, message: err]");
  }

  @Test
  void scriptExists_positive_scriptExists() throws IOException {
    var responseMock = mock(GetStoredScriptResponse.class);

    when(responseMock.status()).thenReturn(RestStatus.OK);
    when(esClient.getScript(any(GetStoredScriptRequest.class), eq(DEFAULT))).thenReturn(responseMock);

    var actual = scriptRepository.scriptExists("scriptId");

    assertTrue(actual);
  }

  @Test
  void scriptExists_positive_scriptNotExists() throws IOException {
    var responseMock = mock(GetStoredScriptResponse.class);

    when(responseMock.status()).thenReturn(RestStatus.NOT_FOUND);
    when(esClient.getScript(any(GetStoredScriptRequest.class), eq(DEFAULT))).thenReturn(responseMock);

    var actual = scriptRepository.scriptExists("scriptId");

    assertFalse(actual);
  }
}
