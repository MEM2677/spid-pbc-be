package org.entando.pbc.spid;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.fabric8.kubernetes.api.model.Pod;
import io.micrometer.core.instrument.util.StringUtils;
import org.entando.pbc.spid.dto.ConnectionInfo;
import org.entando.pbc.spid.model.keycloak.AuthenticationFlow;
import org.entando.pbc.spid.model.keycloak.Execution;
import org.entando.pbc.spid.model.keycloak.IdentityProvider;
import org.entando.pbc.spid.model.keycloak.Token;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;

import static org.entando.pbc.spid.Constants.*;

public class Installer {

  private final static Logger logger = LoggerFactory.getLogger(Installer.class);

  protected static void configureWithoutDiscovery() {
    Map<String, String> env2 = System.getenv();


    Boolean enabled = Boolean.parseBoolean(env2.get("SPID_CONFIG_ACTIVE"));
    String instances = "forumpa.apps.psdemo.eng-entando.com"; // new String(env2.get("KEYCLOACK_HOSTS_CSV_LIST"));
    String username = "entando_keycloak_admin"; // env2.get("KEYCLOACK_USERNAME");
    String passwordB64 = "Mzc2M2U1NjkyNDViNGE4OQ=="; // env2.get("KEYCLOACK_PASSWORD");
    String password = new String(Base64.getDecoder().decode(passwordB64));

    if (enabled
      && StringUtils.isNotBlank(instances)
      && StringUtils.isNotBlank(username)
      && StringUtils.isNotBlank(password)) {

      String hosts[] = instances.split(",");
      String host = hosts[0];

      logger.debug("Configuring host {}", host);
      ConnectionInfo kcc = new ConnectionInfo(host);
      kcc.setUsername(username);
      kcc.setPassword(password);
      Token token = RestApiOps.getAdminAccessToken(kcc.getHost(), kcc.getUsername(), kcc.getPassword(), KEYCLOAK_CLIENT_ID, KEYCLOAK_CLIENT_SECRET);
      if (token != null && StringUtils.isNotBlank(token.getAccessToken())) {
        logger.debug("Access token acquired");
        configureKeycloak(host, token);
      } else {
        logger.error("Cannot complete SPID installation");
      }
    } else {
      if (enabled) {
        logger.debug("Configuration aborted, check bundle settings");
      } else {
        logger.info("Configuration aborted as requested");
      }
    }

/*
    final String HOST = "spid-entando.192-168-49-2.nip.io";

    ConnectionInfo kcc = new ConnectionInfo(HOST);
    kcc.setUsername("entando_keycloak_admin");
    kcc.setPassword("7fa789114e7041e3");
    Token token = RestApiOps.getAdminAccessToken(kcc.getHost(), kcc.getUsername(), kcc.getPassword(), KEYCLOAK_CLIENT_ID, KEYCLOAK_CLIENT_SECRET);
    if (StringUtils.isNotBlank(token.getAccessToken())) {
      logger.debug("Access token acquired");
      configureKeycloak(HOST, token);
    } else {
      logger.error("Cannot complete SPID installation");
    }
 */
  }

  private static boolean updateExecutionRequirement(String host, Token token, Execution[] executions, String executionName, String requirement) {
    AuthenticationFlow updated = null;
    Optional<Execution> execOpt = findExecution(executions, executionName);

    if (execOpt.isPresent()) {
      Execution execution = execOpt.get();
      execution.setRequirement(requirement); // TODO constant
      updated = RestApiOps.updateExecution(host, token, execution);
    } else {
      logger.error("Cannot find target execution " + executionName + ", aborting setup" );
    }
    return updated != null;
  }

  private static void configureKeycloak(String host, Token token) {
    Execution[] executions;
    try {

      // 1 - configure authentication flow
      if (!RestApiOps.duplicateAuthFlow(host, token)) {
        logger.error("could not create authentication flow, aborting configuration");
        return;
      }
      logger.info("Keycloak config: authorization flow created with name [{}]", KEYCLOAK_NEW_AUTH_FLOW_NAME);
      // 2 - add executable
      if (!RestApiOps.addExecutable(host, token)) {
        logger.error("could not add execution to the authentication flow, aborting configuration");
        return;
      }
      logger.info("Added the new executor to the authentication flow");
      // 3 - get the id of the newly created execution
      executions = RestApiOps.getExecutions(host, token);
      if (executions == null || executions.length == 0
        || !executions[executions.length - 1].getDisplayName().equals(KEYCLOAK_EXECUTION_EXPECTED_DISPLAY_NAME)) {
        logger.error("could not obtain the execution for the flow, aborting configuration");
        return;
      }
      String id = executions[executions.length - 1].getId();
      logger.debug("Target execution ID is: " + id);
      //  4 - move executable to its position
      for (int i = 0; i < 2; i++) {
        if (!RestApiOps.raiseExecutionPriority(host, token, id)) {
          logger.error("Could not raise the execution level of the target execution " + id);
          break;
        }
      }
      // 5 - edit requirements of the given executables

      // 5A - REQUIRED for Automatically "Set Existing User"
      if (!updateExecutionRequirement(host, token, executions, KEYCLOAK_EXECUTION_EXPECTED_DISPLAY_NAME, "REQUIRED")) {
        logger.error("Cannot find target execution [" + KEYCLOAK_EXECUTION_EXPECTED_DISPLAY_NAME + "], aborting setup" );
        return;
      }
      // 5B - DISABLED for "Confirm Link Existing Account"
      if (!updateExecutionRequirement(host, token, executions, KEYCLOAK_EXECUTION_CONFIRM_LINK_DISPLAY_NAME, "DISABLED")) {
        logger.error("Cannot find target execution [" + KEYCLOAK_EXECUTION_CONFIRM_LINK_DISPLAY_NAME + "], aborting setup" );
        return;
      }
      // 5C - DISABLED for "SPID first broker login Account verification options"
      if (!updateExecutionRequirement(host, token, executions, KEYCLOAK_EXECUTION_VERIFICATION_OPTIONS_DISPLAY_NAME, "DISABLED")) {
        logger.error("Cannot find target execution [" + KEYCLOAK_EXECUTION_VERIFICATION_OPTIONS_DISPLAY_NAME + "], aborting setup" );
        return;
      }
      // 6 - configure identity provider TODO customize data!
      ObjectMapper objectMapper = new ObjectMapper();
      IdentityProvider idp = objectMapper.readValue(TEST_LOCAL_IdP, IdentityProvider.class);
      if (!RestApiOps.createIdentityProvider(host, token, idp)) {
        logger.error("Cannot configure the service provider [" + KEYCLOAK_IDP_DISPLAY_NAME + "], aborting setup" );
        return;
      }
      // 7 - create mapping for SPID
    } catch (Throwable t) {
      logger.error("unexpected error in configureKeycloak", t);
    }
  }

  protected static Optional<Execution> findExecution(Execution[] executions, String displayName) {
    return Arrays.asList(executions)
      .stream()
//      .peek(e -> System.out.println(">?> " + e.getDisplayName()))
      .filter(e -> e.getDisplayName().equals(displayName))
      .findFirst();
  }

  /**
   * Configure the keycloak pod first copying the provider JAR
   * @param namespace
   * @param pod
   */
  private static void configurePod(String namespace, Pod pod) {
    String podName = pod.getMetadata().getName();
    // check for KC POD
    if (isKeycloakPod(podName)) {
      logger.info("analysing pod " + podName);
      // copy the provider to the Keycloak POD
      if (!ClusterOps.checkFileExists(namespace, podName, PROVIDER_FILE_DESTINATION_PATH)) {
        logger.info("installing {} in pod {}", PROVIDER_FILENAME, podName);
        ClusterOps.copyFile(namespace, podName, PROVIDER_FILE_LOCAL_PATH, PROVIDER_FILE_DESTINATION_PATH);
        logger.info("waiting for provider setup...");
        // wait for installation completed
        do {
          try {
            Thread.sleep(1000);
          } catch (InterruptedException e) {
            logger.error("error in configurePod", e);
          }
        } while (!ClusterOps.checkFileExists(namespace, podName, DEPLOYED_PROVIDER_FILE_DESTINATION_PATH));
        logger.info("...installation of the provider completed");
      } else {
        logger.info("Provider already installed in pod {}", podName);
      }
      // get ingress for Keycloak
      String host = ClusterOps.getIngressHost(namespace, INSTANCE_INGRESS_NAME);
      logger.debug("keycloak host is {}", host);
      // obtain keycloak admin username and password by reading the secret
      ConnectionInfo info = new ConnectionInfo(host);
      ClusterOps.readSecret(info, namespace, KEYCLOAK_SECRET_NAME);
      // get the admin access token
      Token token = RestApiOps.getAdminAccessToken(info.getHost(), info.getUsername(), info.getPassword(),
        KEYCLOAK_CLIENT_ID, KEYCLOAK_CLIENT_SECRET);
//      if (token != null) {
//        logger.info("HAVE TOKEN");
//        logger.info("ACCESS TOKEN " + token.getAccessToken());
//      }
      // configure keycloak itself
      configureKeycloak(host, token);
    }
  }

  private static boolean isKeycloakPod(String podName) {
    return podName.contains(KEYCLOACK_POD_NAME_SIGNATURE);
  }

  private static String getDefaultNamespace() {
    String ns = "";
    try {
      File file = new File(NAMESPACE_FILE);
      if  (file.exists()) {
        FileReader fr = new FileReader(file);
        BufferedReader br = new BufferedReader(fr);

        if ((ns = br.readLine()) != null) {
          logger.debug("Default namespace is {}", ns);
          ns = ns.trim(); // paranoid, I know
        }
      } else {
        logger.warn(NAMESPACE_FILE + " does not exist");
      }
    } catch (Throwable t) {
      logger.error("error getting the default namespace", t);
    }
    return ns;
  }

  public static void install() {
    try {
      logger.info("SPID provider installer started");/*
      File installer = new File(PROVIDER_FILE_LOCAL_PATH);
      if (installer.exists()) {
        logger.debug("Provider file found: " + PROVIDER_FILE_LOCAL_PATH);
        String namespace = getDefaultNamespace();
        // get pods for the given namespace
        List<Pod> list = ClusterOps.getPodsInNamespace(namespace);
        if (null != list) {
          for (Pod current : list) {
            configurePod(namespace, current);
          }
        } else {
          logger.debug("No pod detected");
        }
      } else {
        logger.error("Provider not found! Rebuild the image or fix the filename");
      } */
      configureWithoutDiscovery();
    } catch (Throwable t) {
      logger.error("Installer error detected", t);
    }
//    while (logger != null);
    logger.info("SPID provider installer completed execution");
  }


}
