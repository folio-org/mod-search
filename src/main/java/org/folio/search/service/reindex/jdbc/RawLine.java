package org.folio.search.service.reindex.jdbc;

import java.util.Map;

public record RawLine(String rawJson, Map<String, Object> data) {
}
