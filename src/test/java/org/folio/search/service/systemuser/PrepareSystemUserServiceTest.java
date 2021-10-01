package org.folio.search.service.systemuser;

import static org.folio.search.model.service.ResultList.asSinglePage;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.folio.search.client.AuthnClient;
import org.folio.search.client.PermissionsClient;
import org.folio.search.client.UsersClient;
import org.folio.search.configuration.properties.FolioSystemUserProperties;
import org.folio.search.model.service.ResultList;
import org.folio.search.service.context.FolioExecutionContextBuilder;
import org.folio.search.utils.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class PrepareSystemUserServiceTest {

  @Mock private UsersClient usersClient;
  @Mock private AuthnClient authnClient;
  @Mock private PermissionsClient permissionsClient;
  @Mock private FolioExecutionContextBuilder contextBuilder;

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
