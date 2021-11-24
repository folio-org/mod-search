package org.folio.search.service.systemuser;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.folio.search.model.service.ResultList.asSinglePage;
import static org.folio.search.utils.TestConstants.TENANT_ID;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.folio.search.client.AuthnClient;
import org.folio.search.client.AuthnClient.UserCredentials;
import org.folio.search.client.PermissionsClient;
import org.folio.search.client.UsersClient;
import org.folio.search.configuration.properties.FolioSystemUserProperties;
import org.folio.search.model.SystemUser;
import org.folio.search.model.service.ResultList;
import org.folio.search.service.context.FolioExecutionContextBuilder;
import org.folio.search.utils.types.UnitTest;
import org.folio.spring.integration.XOkapiHeaders;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

@UnitTest
@ExtendWith(MockitoExtension.class)
class PrepareSystemUserServiceTest {

  @Mock private UsersClient usersClient;
  @Mock private AuthnClient authnClient;
  @Mock private PermissionsClient permissionsClient;
  @Mock private FolioExecutionContextBuilder contextBuilder;
  @Mock private ResponseEntity<String> expectedResponse;

  @Test
  void shouldCreateSystemUserWhenNotExist() {
    when(usersClient.query(any())).thenReturn(userNotExistResponse());

    prepareSystemUser(systemUser());

    verify(usersClient).saveUser(any());
    verify(permissionsClient).assignPermissionsToUser(any());
  }

  @Test
  void shouldNotCreateSystemUserWhenExists() {
    when(usersClient.query(any())).thenReturn(userExistsResponse());
    when(permissionsClient.getUserPermissions(any())).thenReturn(ResultList.empty());

    prepareSystemUser(systemUser());

    verify(permissionsClient, times(2)).addPermission(any(), any());
  }

  @Test
  void cannotUpdateUserIfEmptyPermissions() {
    var systemUser = systemUserNoPermissions();
    when(usersClient.query(any())).thenReturn(userNotExistResponse());

    assertThrows(IllegalStateException.class, () -> prepareSystemUser(systemUser));

    verifyNoInteractions(permissionsClient);
  }

  @Test
  void cannotCreateUserIfEmptyPermissions() {
    var systemUser = systemUserNoPermissions();
    when(usersClient.query(any())).thenReturn(userExistsResponse());

    assertThrows(IllegalStateException.class, () -> prepareSystemUser(systemUser));
  }

  @Test
  void shouldAddOnlyNewPermissions() {
    when(usersClient.query(any())).thenReturn(userExistsResponse());
    when(permissionsClient.getUserPermissions(any()))
      .thenReturn(asSinglePage("inventory-storage.instance.item.get"));

    prepareSystemUser(systemUser());

    verify(permissionsClient, times(1)).addPermission(any(), any());
    verify(permissionsClient, times(0))
      .addPermission(any(), eq(PermissionsClient.Permission.of("inventory-storage.instance.item.get")));
    verify(permissionsClient, times(1))
      .addPermission(any(), eq(PermissionsClient.Permission.of("inventory-storage.instance.item.post")));
  }

  @Test
  void loginSystemUser_positive() {
    var expectedAuthToken = "x-okapi-token-value";
    var expectedHeaders = new HttpHeaders();
    expectedHeaders.add(XOkapiHeaders.TOKEN, expectedAuthToken);

    when(authnClient.getApiKey(UserCredentials.of("username", "password"))).thenReturn(expectedResponse);
    when(expectedResponse.getHeaders()).thenReturn(expectedHeaders);

    var systemUser = SystemUser.builder().username("username").okapiUrl("http://okapi").tenantId(TENANT_ID).build();
    var actual = systemUserService(systemUser()).loginSystemUser(systemUser);
    assertThat(actual).isEqualTo(expectedAuthToken);
  }

  @Test
  void loginSystemUser_negative_emptyHeaders() {
    when(authnClient.getApiKey(UserCredentials.of("username", "password"))).thenReturn(expectedResponse);
    when(expectedResponse.getHeaders()).thenReturn(new HttpHeaders());

    var systemUser = SystemUser.builder().username("username").okapiUrl("http://okapi").tenantId(TENANT_ID).build();
    var systemUserService = systemUserService(systemUser());
    assertThatThrownBy(() -> systemUserService.loginSystemUser(systemUser))
      .isInstanceOf(IllegalStateException.class)
      .hasMessage("User [username] cannot log in");
  }

  @Test
  void loginSystemUser_negative_headersDoesNotContainsRequiredValue() {
    when(authnClient.getApiKey(UserCredentials.of("username", "password"))).thenReturn(expectedResponse);
    var expectedHeaders = new HttpHeaders();
    expectedHeaders.put(XOkapiHeaders.TOKEN, emptyList());
    when(expectedResponse.getHeaders()).thenReturn(expectedHeaders);

    var systemUser = SystemUser.builder().username("username").okapiUrl("http://okapi").tenantId(TENANT_ID).build();
    var systemUserService = systemUserService(systemUser());
    assertThatThrownBy(() -> systemUserService.loginSystemUser(systemUser))
      .isInstanceOf(IllegalStateException.class)
      .hasMessage("User [username] cannot log in");
  }

  private FolioSystemUserProperties systemUser() {
    return FolioSystemUserProperties.builder()
      .password("password")
      .username("username")
      .permissionsFilePath("permissions/test-permissions.csv")
      .build();
  }

  private FolioSystemUserProperties systemUserNoPermissions() {
    return FolioSystemUserProperties.builder()
      .password("password")
      .username("username")
      .permissionsFilePath("permissions/empty-permissions.csv")
      .build();
  }

  private ResultList<UsersClient.User> userExistsResponse() {
    return asSinglePage(new UsersClient.User());
  }

  private ResultList<UsersClient.User> userNotExistResponse() {
    return ResultList.empty();
  }

  private PrepareSystemUserService systemUserService(FolioSystemUserProperties properties) {
    return new PrepareSystemUserService(permissionsClient,
      usersClient, authnClient, contextBuilder, properties);
  }

  private void prepareSystemUser(FolioSystemUserProperties properties) {
    systemUserService(properties).setupSystemUser();
  }
}
