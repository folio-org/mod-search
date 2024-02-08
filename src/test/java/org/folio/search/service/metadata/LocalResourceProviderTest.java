package org.folio.search.service.metadata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.folio.search.utils.TestConstants.RESOURCE_NAME;
import static org.folio.search.utils.TestUtils.array;
import static org.folio.search.utils.TestUtils.mapOf;
import static org.folio.search.utils.TestUtils.resourceDescription;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.type.TypeReference;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import org.folio.search.exception.ResourceDescriptionException;
import org.folio.search.model.metadata.ResourceDescription;
import org.folio.search.model.metadata.SearchFieldType;
import org.folio.search.utils.JsonConverter;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;

@UnitTest
@ExtendWith(MockitoExtension.class)
class LocalResourceProviderTest {

  @InjectMocks
  private LocalResourceProvider localResourceProvider;

  @Mock
  private JsonConverter jsonConverter;
  @Mock
  private LocalFileProvider localFileProvider;
  @Mock
  private ResourcePatternResolver patternResolver;

  @Test
  void getResourceDescriptions_positive() throws IOException {
    var r1 = mock(Resource.class);
    var r2 = mock(Resource.class);
    var desc = new ResourceDescription();
    var r1InputStream = mock(InputStream.class);

    when(r1.isReadable()).thenReturn(true);
    when(r2.isReadable()).thenReturn(false);
    when(r1.getInputStream()).thenReturn(r1InputStream);
    when(patternResolver.getResources("classpath*:/model/*.json")).thenReturn(array(r1, r2));
    when(jsonConverter.readJson(r1InputStream, ResourceDescription.class)).thenReturn(desc);

    var resourceDescriptions = localResourceProvider.getResourceDescriptions();
    assertThat(resourceDescriptions).isEqualTo(List.of(desc));

    // second call must return locally cached resource descriptions
    var resourceDescriptions2 = localResourceProvider.getResourceDescriptions();
    assertThat(resourceDescriptions2).isEqualTo(List.of(desc));

    verify(patternResolver).getResources("classpath*:/model/*.json");
  }

  @Test
  void getResourceDescriptions_negative_throwsException() throws IOException {
    when(patternResolver.getResources("classpath*:/model/*.json")).thenThrow(new IOException("error"));

    assertThatThrownBy(() -> localResourceProvider.getResourceDescriptions())
      .isInstanceOf(ResourceDescriptionException.class)
      .hasMessageContaining("Failed to read local files [pattern: classpath*:/model/*.json]");
  }

  @Test
  void getResourceDescriptions_negative_resourceWithException() throws IOException {
    var resource = mock(Resource.class);

    when(resource.isReadable()).thenReturn(true);
    when(resource.getFilename()).thenReturn("file.json");
    when(resource.getInputStream()).thenThrow(new IOException("err"));
    when(patternResolver.getResources("classpath*:/model/*.json")).thenReturn(array(resource));

    assertThatThrownBy(() -> localResourceProvider.getResourceDescriptions())
      .isInstanceOf(ResourceDescriptionException.class)
      .hasMessageContaining("Failed to read resource [file: file.json]");
  }

  @Test
  void getResourceDescription_positive_resourceWithException() throws IOException {
    var r1 = mock(Resource.class);
    var resourceDescription = resourceDescription(RESOURCE_NAME);
    var r1InputStream = mock(InputStream.class);

    when(r1.isReadable()).thenReturn(true);
    when(r1.getInputStream()).thenReturn(r1InputStream);
    when(patternResolver.getResources("classpath*:/model/*.json")).thenReturn(array(r1));
    when(jsonConverter.readJson(r1InputStream, ResourceDescription.class)).thenReturn(resourceDescription);

    var actual = localResourceProvider.getResourceDescription(RESOURCE_NAME);
    assertThat(actual).isPresent().get().isEqualTo(resourceDescription);

    // second call must return locally cached resource descriptions
    var actual2 = localResourceProvider.getResourceDescription(RESOURCE_NAME);
    assertThat(actual2).isPresent().get().isEqualTo(resourceDescription);

    verify(patternResolver).getResources("classpath*:/model/*.json");
  }

  @Test
  void getResourceDescription_negative_resourceNotFoundByName() throws IOException {
    var r1 = mock(Resource.class);
    var resourceDescription = resourceDescription(RESOURCE_NAME);
    var r1InputStream = mock(InputStream.class);

    when(r1.isReadable()).thenReturn(true);
    when(r1.getInputStream()).thenReturn(r1InputStream);
    when(patternResolver.getResources("classpath*:/model/*.json")).thenReturn(array(r1));
    when(jsonConverter.readJson(r1InputStream, ResourceDescription.class)).thenReturn(resourceDescription);

    var actual = localResourceProvider.getResourceDescription("unknown");

    assertThat(actual).isEmpty();
    verify(patternResolver).getResources("classpath*:/model/*.json");
  }

  @Test
  void getSearchFieldTypes_positive() {
    var fieldTypeMap = mapOf("field", new SearchFieldType());
    //noinspection unchecked
    when(localFileProvider.readAsObject(eq("elasticsearch/index-field-types.json"),
      any(TypeReference.class))).thenReturn(fieldTypeMap);

    var actual = localResourceProvider.getSearchFieldTypes();
    assertThat(actual).isEqualTo(fieldTypeMap);
  }

  @Test
  void getSearchFieldTypes_negative() {
    //noinspection unchecked
    when(localFileProvider.readAsObject(eq("elasticsearch/index-field-types.json"),
      any(TypeReference.class))).thenReturn(null);

    assertThatThrownBy(() -> localResourceProvider.getSearchFieldTypes())
      .isInstanceOf(ResourceDescriptionException.class)
      .hasMessageContaining("Failed to load search field types "
        + "[path: elasticsearch/index-field-types.json]");
  }
}
