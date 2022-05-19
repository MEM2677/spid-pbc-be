package org.entando.pbc.spid;

import org.apache.http.HttpStatus;
import org.entando.pbc.spid.model.keycloak.AuthenticationFlow;
import org.entando.pbc.spid.model.keycloak.Execution;
import org.entando.pbc.spid.model.keycloak.IdentityProvider;
import org.entando.pbc.spid.model.keycloak.Token;
import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.jackson.JacksonFeature;
import org.glassfish.jersey.logging.LoggingFeature;
import org.glassfish.jersey.media.multipart.MultiPartFeature;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.Form;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import static org.entando.pbc.spid.Constants.*;

public class RestApiOps {


  private final static Logger logger = LoggerFactory.getLogger(RestApiOps.class);

  protected static ClientConfig createClientConfig() {
    ClientConfig config = new ClientConfig();
    config.register(new LoggingFeature(java.util.logging.Logger.getLogger(LoggingFeature.DEFAULT_LOGGER_NAME),
      java.util.logging.Level.INFO, LoggingFeature.Verbosity.PAYLOAD_ANY, 10000));
    return config;
  }

  public static Token getAdminAccessToken(String host, String username, String password, String clientId, String clientSecret) {
    final String REST_URI
      = PROTO + "://" + host + "/auth/realms/master/protocol/openid-connect/token";
    Token token = null;
    Client client = null;

    final Form form = new Form();
    form.param("username", username);
    form.param("password", password);
    form.param("grant_type", "password");
    form.param("client_id", clientId);
    form.param("client_secret", clientSecret);
    form.param("scope", "openid");

    try {
      client = ClientBuilder.newClient(REST_API_DEBUG_ENABLED ? createClientConfig(): new ClientConfig())
        .register(MultiPartFeature.class)
        .register(JacksonFeature.class);

      try (Response response = client
        .target(REST_URI)
        .request(MediaType.APPLICATION_JSON)
        .post(Entity.entity(form, MediaType.APPLICATION_FORM_URLENCODED))) {
        if (response.getStatus() == HttpStatus.SC_OK) {
          token = response.readEntity(Token.class);
        } else {
          logger.debug("Unexpected status: " + response.getStatus());
        }
      }
//      logger.debug("URL {}", REST_URI);
//      logger.debug("response status: {}", response.getStatus());
//      logger.debug("AAT: {}",response.readEntity(String.class));
    } catch (Throwable t) {
      logger.error("error getting the admin access token", t);
    } finally {
      if (client != null) {
        client.close();
      }
    }
    return token;
  }

  public static boolean duplicateAuthFlow(String host, Token token) {
    final String REST_URI
      = PROTO + "://" + host + "/auth/admin/realms/"+ KEYCLOAK_DEFAULT_REALM + "/authentication/flows/" + KEYCLOAK_DEFAULT_AUTH_FLOW+ "/copy";
    Client client = null;
    // for a simple payload there's no need to disturb Jackson
    String payload = "{\"newName\":\""+ KEYCLOAK_NEW_AUTH_FLOW_NAME+ "\"}";
    boolean created = false;

    try {
      client = ClientBuilder.newClient(REST_API_DEBUG_ENABLED ? createClientConfig(): new ClientConfig())
        .register(JacksonFeature.class);
      try (Response response = client
        .target(REST_URI)
        .request(MediaType.APPLICATION_JSON)
        .header("Authorization", "Bearer " + token.getAccessToken())
        .post(Entity.entity(payload, MediaType.APPLICATION_JSON))) {

        if (response.getStatus() == HttpStatus.SC_CREATED) {
  //        String result = response.readEntity(String.class);
          created = true;
        } else {
          logger.error("Unexpected status: " + response.getStatus());
        }
      }
    } catch (Throwable t) {
      logger.error("error in duplicateAuthFlow", t);
    } finally {
      if (client != null) {
        client.close();
      }
    }
    return created;
  }

  public static boolean addExecutable(String host, Token token) {
    final String REST_URI
      = PROTO + "://" + host + "/auth/admin/realms/"+ KEYCLOAK_DEFAULT_REALM + "/authentication/flows/" + KEYCLOAK_EXECUTION_HANDLE_EXISTING_ACCOUNT_NAME + "/executions/execution";
    Client client = null;
    // for a simple payload there's no need to disturb Jackson
    String payload = "{\"provider\":\"idp-auto-link\"}";
    boolean created = false;

    try {
      client = ClientBuilder.newClient(REST_API_DEBUG_ENABLED ? createClientConfig(): new ClientConfig())
        .register(JacksonFeature.class);
      try (Response response = client
        .target(REST_URI)
        .request(MediaType.APPLICATION_JSON)
        .header("Authorization", "Bearer " + token.getAccessToken())
        .post(Entity.entity(payload, MediaType.APPLICATION_JSON))) {

        if (response.getStatus() == HttpStatus.SC_CREATED) {
  //        String result = response.readEntity(String.class);
          created = true;
        } else {
          logger.error("Unexpected status: " + response.getStatus());
        }
      }
    } catch (Throwable t) {
      logger.error("error in duplicateAuthFlow", t);
    } finally {
      if (client != null) {
        client.close();
      }
    }
    return created;
  }

  public static Execution[] getExecutions(String host, Token token) {
    Execution[] result = null;
    final String REST_URI
      = PROTO + "://" + host + "/auth/admin/realms/" + KEYCLOAK_DEFAULT_REALM + "/authentication/flows/" + KEYCLOAK_NEW_AUTH_FLOW_NAME + "/executions";
    Client client = null;

    try {
      client = ClientBuilder.newClient(REST_API_DEBUG_ENABLED ? createClientConfig(): new ClientConfig())
        .register(JacksonFeature.class);
      Response response = client
        .target(REST_URI)
        .request(MediaType.APPLICATION_JSON)
        .header("Authorization", "Bearer " + token.getAccessToken())
        .get();

      if (response.getStatus() == HttpStatus.SC_OK) {
        result = response.readEntity(Execution[].class);
      } else {
        logger.error("Unexpected status: " + response.getStatus());
      }
    } catch (Throwable t) {
      logger.error("error in getExecutions", t);
    } finally {
      if (client != null) {
        client.close();
      }
    }
    return result;
  }

  public static boolean raiseExecutionPriority(String host, Token token, String id) {
    final String REST_URI
      = PROTO + "://" + host + "/auth/admin/realms/" + KEYCLOAK_DEFAULT_REALM + "/authentication/executions/" + id + "/raise-priority";
    Client client = null;
    boolean isOk = true;

    try {
      client = ClientBuilder.newClient(REST_API_DEBUG_ENABLED ? createClientConfig(): new ClientConfig())
        .register(JacksonFeature.class);
      try (Response response = client
        .target(REST_URI)
        .request(MediaType.APPLICATION_JSON)
        .header("Authorization", "Bearer " + token.getAccessToken())
        .post(Entity.json(null))) {

        if (response.getStatus() != HttpStatus.SC_NO_CONTENT) {
          logger.debug("Unexpected status: " + response.getStatus());
          isOk = false;
        }
      }
    } catch (Throwable t) {
      logger.error("error in raiseExecutionPriority", t);
    } finally {
      if (client != null) {
        client.close();
      }
    }
    return isOk;
  }

  public static AuthenticationFlow updateExecution(String host, Token token, Execution execution) {
    final String REST_URI
      = PROTO + "://" + host + "/auth/admin/realms/" + KEYCLOAK_DEFAULT_REALM + "/authentication/flows/"+ KEYCLOAK_NEW_AUTH_FLOW_NAME+ "/executions";
    AuthenticationFlow result = null;
    Client client = null;

    try {
      client = ClientBuilder.newClient(REST_API_DEBUG_ENABLED ? createClientConfig(): new ClientConfig())
        .register(JacksonFeature.class);
      try (Response response = client
        .target(REST_URI)
        .request(MediaType.APPLICATION_JSON)
        .header("Authorization", "Bearer " + token.getAccessToken())
        .put(Entity.entity(execution, MediaType.APPLICATION_JSON))) {

        if (response.getStatus() == HttpStatus.SC_ACCEPTED) {
          result = response.readEntity(AuthenticationFlow.class);
        } else {
          logger.debug("Unexpected status: " + response.getStatus());
        }
      }
    } catch (Throwable t) {
      logger.error("error in updateExecution", t);
    } finally {
      if (client != null) {
        client.close();
      }
    }
    return result;
  }

  public static boolean createIdentityProvider(String host, Token token, IdentityProvider idp) {
    final String REST_URI
      = PROTO + "://" + host + "/auth/admin/realms/" + KEYCLOAK_DEFAULT_REALM + "/identity-provider/instances";
    Client client = null;
    boolean isOk = false;
    try {
      client = ClientBuilder.newClient(REST_API_DEBUG_ENABLED ? createClientConfig(): new ClientConfig())
        .register(JacksonFeature.class);
      try (Response response = client
        .target(REST_URI)
        .request(MediaType.APPLICATION_JSON)
        .header("Authorization", "Bearer " + token.getAccessToken())
        .post(Entity.entity(idp, MediaType.APPLICATION_JSON))) {

        if (response.getStatus() == HttpStatus.SC_CREATED) {
          isOk = true;
        } else {
          logger.debug("Unexpected status: " + response.getStatus());
        }
      }
    } catch (Throwable t) {
      logger.error("error in createIdentityProvider", t);
    } finally {
      if (client != null) {
        client.close();
      }
    }
    return isOk;
  }

  public static boolean addMapperUsername(String host, Token token) {
    return addMapperElement(host, token, USERNAME_MAPPER_CFG);
  }

  public static boolean addMapperGeneric(String host, Token token,
                                  String name, String attributeName, String userAttributeName) {
    String payload = ATTRIBUTE_MAPPER_CFG
      .replace("_ATTRIBUTE_NAME_", attributeName)
      .replace("_USER_ATTRIBUTE_",userAttributeName)
      .replace("_NAME_", name);
    return addMapperElement(host, token, payload);
  }

  private static boolean addMapperElement(String host, Token token, String payload) {
    final String REST_URI
      = PROTO + "://" + host + "/auth/admin/realms/" + KEYCLOAK_DEFAULT_REALM + "/identity-provider/instances/" + KEYCLOAK_IDP_ALIAS + "/mappers";
    boolean isOk = false;
    Client client = null;

    try {
      client = ClientBuilder.newClient(REST_API_DEBUG_ENABLED ? createClientConfig(): new ClientConfig())
        .register(JacksonFeature.class);
      try (Response response = client
        .target(REST_URI)
        .request(MediaType.APPLICATION_JSON)
        .header("Authorization", "Bearer " + token.getAccessToken())
        .post(Entity.entity(payload, MediaType.APPLICATION_JSON))) {

        if (response.getStatus() == HttpStatus.SC_CREATED) {
          isOk = true;
        } else {
          logger.debug("Unexpected status: " + response.getStatus());
        }
      }
    } catch (Throwable t) {
      logger.error("error in addMapperElement", t);
    } finally {
      if (client != null) {
        client.close();
      }
    }
    return isOk;
  }

  public static void another(String host, Token token) {
    final String REST_URI
      = PROTO + "://" + host + "/auth/realms/master/protocol/openid-connect/token";
    Client client = null;
    try {

    } catch (Throwable t) {
      logger.error("error in ", t);
    } finally {
      if (client != null) {
        client.close();
      }
    }
  }

  // templates for mappers
  public static String USERNAME_MAPPER_CFG= "{\n" +
    "   \"identityProviderAlias\":\"" + KEYCLOAK_IDP_ALIAS + "\",\n" +
    "   \"config\":{\n" +
    "      \"syncMode\":\"INHERIT\",\n" +
    "      \"template\":\"${ATTRIBUTE.fiscalNumber}\"\n" +
    "   },\n" +
    "   \"name\":\"User Name\",\n" +
    "   \"identityProviderMapper\":\"spid-saml-username-idp-mapper\"\n" +
    "}";

  public static String ATTRIBUTE_MAPPER_CFG= "{\n" +
    "   \"identityProviderAlias\":\""+ KEYCLOAK_IDP_ALIAS + "\",\n" +
    "   \"config\":{\n" +
    "      \"syncMode\":\"INHERIT\",\n" +
    "      \"attribute.name\":\"_ATTRIBUTE_NAME_\",\n" +
    "      \"user.attribute\":\"_USER_ATTRIBUTE_\"\n" +
    "   },\n" +
    "   \"name\":\"_NAME_\",\n" +
    "   \"identityProviderMapper\":\"spid-user-attribute-idp-mapper\"\n" +
    "}";

}
