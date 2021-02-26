package org.folio.search.service.systemuser;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.folio.search.client.AuthnClient;
import org.folio.search.client.PermissionsClient;
import org.folio.search.client.UsersClient;
import org.folio.search.configuration.properties.FolioSystemUserProperties;
import org.folio.search.model.SystemUser;
import org.folio.search.repository.SystemUserRepository;
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
  private SystemUserRepository repository;

  @Test
  void shouldCreateSystemUserWhenNotExist() {
    when(repository.getByTenantId(any())).thenReturn(Optional.empty());
    when(usersClient.query(any())).thenReturn(userNotExistResponse());
    when(authnClient.getApiKey(any())).thenReturn(ResponseEntity.status(200)
      .header(XOkapiHeaders.TOKEN, "token").build());

    prepareSystemUser(systemUser());

    verify(usersClient).saveUser(any());
    verify(permissionsClient).assignPermissionsToUser(any());

    var captor = ArgumentCaptor.forClass(SystemUser.class);
    verify(repository).save(captor.capture());

    assertThat(captor.getValue().getUsername(), is(systemUser().getUsername()));
    assertThat(captor.getValue().getPassword(), is(systemUser().getPassword()));
    assertThat(captor.getValue().getOkapiToken(), is("token"));
  }

  @Test
  void shouldNotCreateSystemUserWhenExists() {
    when(repository.getByTenantId(any())).thenReturn(Optional.empty());
    when(usersClient.query(any())).thenReturn(userExistsResponse());
    when(authnClient.getApiKey(any())).thenReturn(ResponseEntity.status(200)
      .header(XOkapiHeaders.TOKEN, "token2").build());

    prepareSystemUser(systemUser());

    verify(permissionsClient, times(2)).addPermission(any(), any());

    var captor = ArgumentCaptor.forClass(SystemUser.class);
    verify(repository).save(captor.capture());

    assertThat(captor.getValue().getUsername(), is(systemUser().getUsername()));
    assertThat(captor.getValue().getPassword(), is(systemUser().getPassword()));
    assertThat(captor.getValue().getOkapiToken(), is("token2"));
  }

  @Test
  void canUpdateUserIfEmptyPermissions() {
    when(repository.getByTenantId(any())).thenReturn(Optional.empty());
    when(usersClient.query(any())).thenReturn(userNotExistResponse());
    when(authnClient.getApiKey(any())).thenReturn(ResponseEntity.status(200)
      .header(XOkapiHeaders.TOKEN, "token2").build());

    prepareSystemUser(systemUserNoPermissions());

    verify(usersClient).saveUser(any());
    verifyNoInteractions(permissionsClient);
    verify(repository, times(1)).save(any());
  }

  @Test
  void canCreateUserIfEmptyPermissions() {
    when(repository.getByTenantId(any())).thenReturn(Optional.empty());
    when(usersClient.query(any())).thenReturn(userExistsResponse());
    when(authnClient.getApiKey(any())).thenReturn(ResponseEntity.status(200)
      .header(XOkapiHeaders.TOKEN, "token2").build());

    prepareSystemUser(systemUserNoPermissions());

    verify(usersClient).query(any());
    verifyNoInteractions(permissionsClient);
    verify(repository).save(any());
  }

  @Test
  void shouldIgnoreErrorWhenPermissionExists() {
    when(repository.getByTenantId(any())).thenReturn(Optional.empty());
    when(usersClient.query(any())).thenReturn(userExistsResponse());
    when(authnClient.getApiKey(any())).thenReturn(ResponseEntity.status(200)
      .header(XOkapiHeaders.TOKEN, "token").build());

    doThrow(new RuntimeException("Permission exists"))
      .when(permissionsClient).addPermission(any(), any());

    prepareSystemUser(systemUser());

    verify(repository).save(any());
  }

  @Test
  void shouldReturnSystemUserFromRepository() {
    when(repository.getByTenantId(any()))
      .thenReturn(Optional.of(new SystemUser()));

    var user = systemUserService(null).getSystemUserParameters("tenant");

    assertThat(user, notNullValue());
  }

  @Test
  void shouldThrowExceptionIfNoUser() {
    when(repository.getByTenantId(any())).thenReturn(Optional.empty());

    var systemUserService = systemUserService(null);

    assertThrows(IllegalArgumentException.class,
      () -> systemUserService.getSystemUserParameters("tenant"));
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
      repository, properties, executionContext);
  }

  private void prepareSystemUser(FolioSystemUserProperties properties) {
    systemUserService(properties).prepareSystemUser();
  }
}
