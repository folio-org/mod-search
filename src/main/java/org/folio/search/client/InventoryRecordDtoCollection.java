package org.folio.search.client;

import java.util.List;

public record InventoryRecordDtoCollection<T>(List<T> records, int totalRecords) {}
