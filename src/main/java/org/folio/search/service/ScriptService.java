package org.folio.search.service;

import static org.folio.search.utils.SearchUtils.INSTANCE_CONTRIBUTORS_UPSERT_SCRIPT;
import static org.folio.search.utils.SearchUtils.INSTANCE_CONTRIBUTORS_UPSERT_SCRIPT_ID;
import static org.folio.search.utils.SearchUtils.INSTANCE_SUBJECT_UPSERT_SCRIPT;
import static org.folio.search.utils.SearchUtils.INSTANCE_SUBJECT_UPSERT_SCRIPT_ID;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.search.repository.ScriptRepository;
import org.springframework.stereotype.Service;

@Log4j2
@Service
@RequiredArgsConstructor
public class ScriptService {

  private static final Map<String, String> SCRIPTS = Map.of(
    INSTANCE_SUBJECT_UPSERT_SCRIPT_ID, INSTANCE_SUBJECT_UPSERT_SCRIPT,
    INSTANCE_CONTRIBUTORS_UPSERT_SCRIPT_ID, INSTANCE_CONTRIBUTORS_UPSERT_SCRIPT
  );

  private final ScriptRepository scriptRepository;

  public void saveScripts() {
    for (var script : SCRIPTS.entrySet()) {
      var scriptId = script.getKey();
      log.info("Saving stored script [id: {}]", scriptId);
      scriptRepository.saveScript(scriptId, script.getValue());
    }
  }
}
