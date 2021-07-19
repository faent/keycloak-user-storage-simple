/*
 * Copyright 2016 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.keycloak.quickstart.readonly;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import org.jboss.logging.Logger;
import org.keycloak.component.ComponentModel;
import org.keycloak.credential.CredentialInput;
import org.keycloak.credential.CredentialInputValidator;
import org.keycloak.credential.CredentialModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.RoleModel;
import org.keycloak.models.UserCredentialModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.credential.PasswordCredentialModel;
import org.keycloak.storage.StorageId;
import org.keycloak.storage.UserStorageProvider;
import org.keycloak.storage.adapter.AbstractUserAdapter;
import org.keycloak.storage.user.UserLookupProvider;
import org.keycloak.storage.user.UserRegistrationProvider;

/**
 * @author <a href="mailto:bill@burkecentral.com">Bill Burke</a>
 * @version $Revision: 1 $
 */
public class PropertyFileUserStorageProvider implements
    UserStorageProvider,
    UserLookupProvider,
    CredentialInputValidator,
    UserRegistrationProvider {
  private static final Logger logger = Logger.getLogger(
      PropertyFileUserStorageProviderFactory.class);

  protected KeycloakSession session;
  protected Properties properties;
  protected ComponentModel model;
  // map of loaded users in this transaction
  protected Map<String, UserModel> loadedUsers = new HashMap<>();

  public PropertyFileUserStorageProvider(
      KeycloakSession session,
      ComponentModel model,
      Properties properties
  ) {
    this.session = session;
    this.model = model;
    this.properties = properties;
  }

  // UserLookupProvider methods

  @Override
  public UserModel getUserByUsername(String username, RealmModel realm) {
    String password = properties.getProperty(username);
    if (password != null) {
      return createAdapter(realm, username);
    }
    return null;
  }

  protected UserModel createAdapter(RealmModel realm, final String username) {
    return new AbstractUserAdapter(session, realm, model) {
      @Override
      public String getUsername() {
        return username;
      }
    };
  }

  @Override
  public UserModel getUserById(String id, RealmModel realm) {
    logger.info(String.format("getUserById: id=%s, realm=%s", id, realm));
    StorageId storageId = new StorageId(id);
    String username = storageId.getExternalId();
    return getUserByUsername(username, realm);
  }

  @Override
  public UserModel getUserByEmail(String email, RealmModel realm) {
    logger.info(String.format("getUserByEmail: email=%s, realm=%s", email, realm));

    return null;
  }


  // CredentialInputValidator methods

  @Override
  public boolean isConfiguredFor(RealmModel realm, UserModel user, String credentialType) {
    logger.info(String.format("isConfiguredFor: user=%s, realm=%s, credentialType=%s",
                              user, realm, credentialType
    ));
    String password = properties.getProperty(user.getUsername());
    return credentialType.equals(CredentialModel.PASSWORD) && password != null;
  }

  @Override
  public boolean supportsCredentialType(String credentialType) {
    logger.info(String.format(
        "supportsCredentialType: credentialType=%s",
        credentialType
    ));
    return credentialType.equals(CredentialModel.PASSWORD);
  }

  @Override
  public boolean isValid(RealmModel realm, UserModel user, CredentialInput input) {
    logger.info(String.format("isValid: user=%s, realm=%s, credentialInput=%s",
                              user, realm, input
    ));

    if (!supportsCredentialType(input.getType()) || !(input instanceof UserCredentialModel)) {
      return false;
    }

    UserCredentialModel cred = (UserCredentialModel) input;
    String password = properties.getProperty(user.getUsername());
    if (password == null) {
      return false;
    }
    boolean equals = password.equals(cred.getValue());
    if (!equals) {
      return equals;
    }

    logger.info("isValid: adding user");

    Optional<RoleModel> realmRolesStream = realm.getRolesStream()
        .filter(role -> role.getName().equals("montage"))
        .findFirst();
    logger.info("sync: role: " + realmRolesStream);

    UserModel userModel = session.userLocalStorage().addUser(realm, user.getUsername());
    logger.info("sync: created user: " + userModel);
    realmRolesStream.ifPresent(userModel::grantRole);

    userModel.setEnabled(true);
    userModel.setSingleAttribute("phone", "79031112233");

    CredentialModel credentialModel = new CredentialModel();
    credentialModel.setType(PasswordCredentialModel.TYPE);
    credentialModel.setValue(cred.getValue());
    session.userCredentialManager().createCredential(realm, userModel, credentialModel);

    return equals;
  }

  // CredentialInputUpdater methods

  public boolean updateCredential(RealmModel realm, UserModel user, CredentialInput input) {
    logger.info(String.format("updateCredential: user=%s, realm=%s, input=%s", user, realm, input));
    return false;
  }

  public void disableCredentialType(RealmModel realm, UserModel user, String credentialType) {
    logger.info(String.format(
        "disableCredentialType: user=%s, realm=%s, credentialType=%s",
        user,
        realm
        ,
        credentialType
    ));

  }

  public Set<String> getDisableableCredentialTypes(RealmModel realm, UserModel user) {
    logger.info(String.format("getDisableableCredentialTypes: user=%s, realm=%s", user, realm));

    return Collections.EMPTY_SET;
  }


  @Override
  public void close() {

  }

  @Override
  public UserModel addUser(RealmModel realm, String username) {
    return null;
  }

  @Override
  public boolean removeUser(RealmModel realm, UserModel user) {
    return false;
  }
}
