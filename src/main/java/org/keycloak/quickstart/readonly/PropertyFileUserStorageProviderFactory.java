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

import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.util.Enumeration;
import java.util.Optional;
import java.util.Properties;
import org.jboss.logging.Logger;
import org.keycloak.Config;
import org.keycloak.component.ComponentModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.models.RealmModel;
import org.keycloak.models.RoleModel;
import org.keycloak.models.UserModel;
import org.keycloak.storage.UserStorageProviderFactory;
import org.keycloak.storage.UserStorageProviderModel;
import org.keycloak.storage.user.ImportSynchronization;
import org.keycloak.storage.user.SynchronizationResult;

/**
 * @author <a href="mailto:bill@burkecentral.com">Bill Burke</a>
 * @version $Revision: 1 $
 */
public class PropertyFileUserStorageProviderFactory implements UserStorageProviderFactory<PropertyFileUserStorageProvider>,
    ImportSynchronization {

    private static final Logger logger = Logger.getLogger(PropertyFileUserStorageProviderFactory.class);

    public static final String PROVIDER_NAME = "saas";

    protected Properties properties = new Properties();

    public String getId() {
        return PROVIDER_NAME;
    }


    public void init(Config.Scope config) {
        InputStream is = getClass().getClassLoader().getResourceAsStream("/users.properties");

        if (is == null) {
            logger.warn("Could not find users.properties in classpath");
        } else {
            try {
                properties.load(is);
            } catch (IOException ex) {
                logger.error("Failed to load users.properties file", ex);
            }
        }
    }

    public PropertyFileUserStorageProvider create(KeycloakSession session, ComponentModel model) {
        return new PropertyFileUserStorageProvider(session, model, properties);
    }


  public SynchronizationResult sync(
      KeycloakSessionFactory sessionFactory, String realmId, UserStorageProviderModel model
  ) {


    logger.info("synchronize: " + properties.keys());
    SynchronizationResult synchronizationResult = new SynchronizationResult();
    Enumeration<Object> keys = properties.keys();
    KeycloakSession keycloakSession = sessionFactory.create();
    try {
      keycloakSession.getTransactionManager().begin();

      RealmModel realm = keycloakSession.realms().getRealm(realmId);

      while (keys.hasMoreElements()) {
        String username = keys.nextElement().toString();

        logger.info("sync: username: " + username);
        Optional<RoleModel> realmRolesStream = keycloakSession.roles().getRealmRolesStream(realm)
            .filter(role -> role.getName().equals("montage"))
            .findFirst();
        logger.info("sync: role: " + realmRolesStream);

        if (username.equals("maria.besfamilnaya@gmail.com")) {

        }
        UserModel userModel = keycloakSession.userLocalStorage().addUser(realm, username);
        logger.info("sync: created user: " + userModel);
        realmRolesStream.ifPresent(userModel::grantRole);

        userModel.setEnabled(true);
        userModel.setSingleAttribute("phone", "79031112233");
      }
      keycloakSession.getTransactionManager().commit();
    } catch (RuntimeException ex) {
      logger.error(ex);
      keycloakSession.getTransactionManager().rollback();
    } finally {
      keycloakSession.close();
    }
    synchronizationResult.increaseAdded();
    return synchronizationResult;
  }

  @Override
  public SynchronizationResult syncSince(
      Date lastSync,
      KeycloakSessionFactory sessionFactory,
      String realmId,
      UserStorageProviderModel model
  ) {
    return sync(sessionFactory, realmId, model);
  }

  public void close() {
  }
}
