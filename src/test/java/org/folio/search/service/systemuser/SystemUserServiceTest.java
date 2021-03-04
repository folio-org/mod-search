package org.folio.search.service.systemuser;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.folio.search.client.AuthnClient;
import org.folio.search.client.PermissionsClient;
import org.folio.search.client.UsersClient;
import org.folio.search.configuration.properties.FolioSystemUserProperties;
import org.folio.search.model.SystemUser;
import org.folio.search.repository.SystemUserRepository;
import org.folio.search.repository.SystemUserTokenCache;
import org.folio.search.service.context.FolioExecutionContextBuilder;
import org.folio.search.utils.types.UnitTest;
import org.folio.spring.FolioExecutionContext;
import org.folio.spring.integration.XOkapiHeaders;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

@UnitTest
@ExtendWith(MockitoExtension.class)
class SystemUserServiceTest {
  @Mock
  private UsersClient usersClient;
  @Mock
  private AuthnClient authnClient;
  @Mock
  private PermissionsClient permissionsClient;
  @Mock
  private FolioExecutionContext executionContext;
  @Mock
  private FolioExecutionContextBuilder contextBuilder;
  @Mock
  private SystemUserRepository repository;
  @Mock
  private SystemUserTokenCache tokenCache;

  @Test
  void shouldCreateSystemUserWhenNotExist() {
    when(usersClient.query(any())).thenReturn(userNotExistResponse());

    prepareSystemUser(systemUser());

    verify(usersClient).saveUser(any());
    verify(permissionsClient).assignPermissionsToUser(any());

    var captor = ArgumentCaptor.forClass(SystemUser.class);
    verify(repository).save(captor.capture());

    assertThat(captor.getValue().getUsername(), is(systemUser().getUsername()));
    assertThat(captor.getValue().getToken(), nullValue());
  }

  @Test
  void shouldNotCreateSystemUserWhenExists() {
    when(usersClient.query(any())).thenReturn(userExistsResponse());
    when(permissionsClient.getUserPermissions(any())).thenReturn(new PermissionsClient.Permissions());

    prepareSystemUser(systemUser());

    verify(permissionsClient, times(2)).addPermission(any(), any());

    var captor = ArgumentCaptor.forClass(SystemUser.class);
    verify(repository).save(captor.capture());

    assertThat(captor.getValue().getUsername(), is(systemUser().getUsername()));
    assertThat(captor.getValue().getToken(), nullValue());
  }

  @Test
  void cannotUpdateUserIfEmptyPermissions() {
    when(usersClient.query(any())).thenReturn(userNotExistResponse());

    assertThrows(IllegalStateException.class, () -> prepareSystemUser(systemUserNoPermissions()));

    verifyNoInteractions(permissionsClient);
    verify(repository, times(0)).save(any());
  }

  @Test
  void cannotCreateUserIfEmptyPermissions() {
    when(usersClient.query(any())).thenReturn(userExistsResponse());

    assertThrows(IllegalStateException.class,
      () -> prepareSystemUser(systemUserNoPermissions()));
  }

  @Test
  void shouldAddOnlyNewPermissions() {
    when(usersClient.query(any())).thenReturn(userExistsResponse());
    when(permissionsClient.getUserPermissions(any()))
      .thenReturn(PermissionsClient.Permissions.of(null, null,
        List.of("inventory-storage.instance.item.get")));

    prepareSystemUser(systemUser());

    verify(permissionsClient, times(1)).addPermission(any(), any());
    verify(permissionsClient, times(0))
      .addPermission(any(), eq(PermissionsClient.Permission.of("inventory-storage.instance.item.get")));
    verify(permissionsClient, times(1))
      .addPermission(any(), eq(PermissionsClient.Permission.of("inventory-storage.instance.item.post")));
  }

  @Test
  void getSystemUser_shouldLogInUserWhenNoToken() {
    when(repository.findOneByUsername(any())).thenReturn(Optional.of(new SystemUser()));
    when(tokenCache.hasTokenForTenant(any())).thenReturn(false);
    when(tokenCache.getByTenant(any())).thenReturn(new SystemUser().withToken("token"));
    when(authnClient.getApiKey(any())).thenReturn(ResponseEntity.status(200)
      .header(XOkapiHeaders.TOKEN, "token").build());

    var user = systemUserService(systemUser()).getSystemUser("tenant");

    assertThat(user.getToken(), is("token"));
    verify(authnClient, times(1)).getApiKey(any());
  }

  @Test
  void getSystemUser_shouldNotLogInUserWhenTokenExist() {
    when(repository.findOneByUsername(any())).thenReturn(Optional.of(new SystemUser()));
    when(tokenCache.hasTokenForTenant(any())).thenReturn(true);
    when(tokenCache.getByTenant(any())).thenReturn(new SystemUser().withToken("existing-token"));

    var user = systemUserService(systemUser()).getSystemUser("tenant");

    assertThat(user.getToken(), is("existing-token"));
    verifyNoInteractions(authnClient);
  }

  @Test
  void shouldThrowExceptionIfNoUser() {
    when(repository.findOneByUsername(any())).thenReturn(Optional.empty());

    var systemUserService = systemUserService(systemUser());

    assertThrows(IllegalStateException.class,
      () -> systemUserService.getSystemUser("tenant"));
  }

  private FolioSystemUserProperties systemUser() {
    return FolioSystemUserProperties.builder()
      .password("password")
      .username("username")
      .permissionsFilePath("classpath:permissions/test-permissions.csv")
      .build();
  }

  private FolioSystemUserProperties systemUserNoPermissions() {
    return FolioSystemUserProperties.builder()
      .password("password")
      .username("username")
      .permissionsFilePath("classpath:permissions/empty-permissions.csv")
      .build();
  }

  private UsersClient.Users userExistsResponse() {
    return UsersClient.Users.builder()
      .user(new UsersClient.User())
      .build();
  }

  private UsersClient.Users userNotExistResponse() {
    return new UsersClient.Users();
  }

  private SystemUserService systemUserService(FolioSystemUserProperties properties) {
    return new SystemUserService(permissionsClient, usersClient, authnClient,
      repository, contextBuilder, properties, tokenCache);
  }

  private void prepareSystemUser(FolioSystemUserProperties properties) {
    systemUserService(properties).prepareSystemUser(executionContext);
  }
}
