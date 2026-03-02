package org.folio.search.service.reindex;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

@UnitTest
class ReindexContextTest {

  @AfterEach
  void tearDown() {
    // Clean up after each test to prevent side effects
    ReindexContext.clear();
  }

  @Test
  void reindexMode_defaultValue_shouldBeFalse() {
    assertThat(ReindexContext.isReindexMode()).isFalse();
  }

  @Test
  void setReindexMode_shouldUpdateValue() {
    ReindexContext.setReindexMode(true);
    assertThat(ReindexContext.isReindexMode()).isTrue();

    ReindexContext.setReindexMode(false);
    assertThat(ReindexContext.isReindexMode()).isFalse();
  }

  @Test
  void memberTenantId_defaultValue_shouldBeNull() {
    assertThat(ReindexContext.getMemberTenantId()).isNull();
    assertThat(ReindexContext.isMemberTenantReindex()).isFalse();
  }

  @Test
  void setMemberTenantId_shouldUpdateValue() {
    var tenantId = "test_tenant";
    ReindexContext.setMemberTenantId(tenantId);

    assertThat(ReindexContext.getMemberTenantId()).isEqualTo(tenantId);
    assertThat(ReindexContext.isMemberTenantReindex()).isTrue();
  }

  @Test
  void clearMemberTenantId_shouldResetToNull() {
    ReindexContext.setMemberTenantId("test_tenant");
    ReindexContext.clearMemberTenantId();

    assertThat(ReindexContext.getMemberTenantId()).isNull();
    assertThat(ReindexContext.isMemberTenantReindex()).isFalse();
  }

  @Test
  void clear_shouldResetAllValues() {
    ReindexContext.setReindexMode(true);
    ReindexContext.setMemberTenantId("test_tenant");

    ReindexContext.clear();

    assertThat(ReindexContext.isReindexMode()).isFalse();
    assertThat(ReindexContext.getMemberTenantId()).isNull();
    assertThat(ReindexContext.isMemberTenantReindex()).isFalse();
  }

  @Test
  @SuppressWarnings("checkstyle:MethodLength")
  void threadLocal_shouldIsolateValuesBetweenThreads() throws InterruptedException {
    var mainThreadTenantId = "main_tenant";
    var workerThreadTenantId = "worker_tenant";

    ReindexContext.setMemberTenantId(mainThreadTenantId);
    ReindexContext.setReindexMode(true);

    var latch = new CountDownLatch(1);
    var workerResults = new ArrayList<String>();
    var workerModes = new ArrayList<Boolean>();

    var thread = new Thread(() -> {
      try {
        // Worker thread should have default values initially
        workerResults.add(ReindexContext.getMemberTenantId());
        workerModes.add(ReindexContext.isReindexMode());

        // Set different values in worker thread
        ReindexContext.setMemberTenantId(workerThreadTenantId);
        ReindexContext.setReindexMode(false);

        workerResults.add(ReindexContext.getMemberTenantId());
        workerModes.add(ReindexContext.isReindexMode());
      } finally {
        ReindexContext.clear();
        latch.countDown();
      }
    });

    thread.start();
    var completed = latch.await(5, TimeUnit.SECONDS);
    assertThat(completed).as("Thread should complete within timeout").isTrue();

    // Main thread should still have its original values
    assertThat(ReindexContext.getMemberTenantId()).isEqualTo(mainThreadTenantId);
    assertThat(ReindexContext.isReindexMode()).isTrue();

    // Worker thread should have had isolated values
    assertThat(workerResults.get(0)).isNull(); // Initial value
    assertThat(workerModes.get(0)).isFalse(); // Initial value
    assertThat(workerResults.get(1)).isEqualTo(workerThreadTenantId); // After set
    assertThat(workerModes.get(1)).isFalse(); // After set
  }

  @Test
  @SuppressWarnings("checkstyle:MethodLength")
  void multipleThreads_shouldMaintainIsolation() throws InterruptedException {
    var threadCount = 5;
    var latch = new CountDownLatch(threadCount);
    var results = new ArrayList<String>();

    try (var executor = Executors.newFixedThreadPool(threadCount)) {
      for (int i = 0; i < threadCount; i++) {
        var tenantId = "tenant_" + i;
        executor.submit(() -> {
          try {
            ReindexContext.setMemberTenantId(tenantId);
            ReindexContext.setReindexMode(true);

            synchronized (results) {
              results.add(ReindexContext.getMemberTenantId());
            }
          } finally {
            ReindexContext.clear();
            latch.countDown();
          }
        });
      }

      var completed = latch.await(5, TimeUnit.SECONDS);
      assertThat(completed).as("All threads should complete within timeout").isTrue();
    }

    // Each thread should have read its own tenant ID
    assertThat(results).hasSize(threadCount);
    for (int i = 0; i < threadCount; i++) {
      assertThat(results).contains("tenant_" + i);
    }
  }

  @Test
  void isMemberTenantReindex_shouldReturnFalseWhenTenantIdIsNull() {
    ReindexContext.setMemberTenantId(null);
    assertThat(ReindexContext.isMemberTenantReindex()).isFalse();
  }

  @Test
  void isMemberTenantReindex_shouldReturnTrueWhenTenantIdIsSet() {
    ReindexContext.setMemberTenantId("test_tenant");
    assertThat(ReindexContext.isMemberTenantReindex()).isTrue();
  }

  @Test
  void clearMemberTenantId_shouldNotAffectReindexMode() {
    ReindexContext.setReindexMode(true);
    ReindexContext.setMemberTenantId("test_tenant");

    ReindexContext.clearMemberTenantId();

    assertThat(ReindexContext.isReindexMode()).isTrue();
    assertThat(ReindexContext.getMemberTenantId()).isNull();
  }

  @Test
  void settingEmptyString_shouldBeConsideredAsSet() {
    ReindexContext.setMemberTenantId("");
    assertThat(ReindexContext.isMemberTenantReindex()).isTrue();
    assertThat(ReindexContext.getMemberTenantId()).isEmpty();
  }
}

