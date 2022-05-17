# Provider installation for Keycloak 15.1.1

## update KC installation

Execute the following command to copy the provider into the fixed path 

```shell
kc cp spid-provider.jar default-sso-in-namespace-deployment-d595f6c64-qvmdn:/opt/jboss/keycloak/standalone/deployments
```