# SPID bundle 

## prerequisites

### Prepare secrets

[reference](https://developer.entando.com/next/tutorials/devops/plugin-environment-variables.html)



## Code compile

```shell
sh build.sh -djangalian -v3.19
```

## Bundle setup

```shell
git add deployment/spid_installer.yaml
git commit -m "Bump to version 3.22"
git tag -a v1.0.4 -m "Bump to version 3.22"
git push --tags
ent bundler from-git --name=example-bundle --namespace=demo-forum-pa --repository=https://github.com/MEM2677/spid-pbc-fe --dry-run > spid-provider-installer.yaml
oc apply -f spid-provider-installer.yaml
```

## Update KC installation

Execute the following command to copy the provider into the fixed path

```shell
kc cp spid-provider.jar default-sso-in-namespace-deployment-d595f6c64-qvmdn:/opt/jboss/keycloak/standalone/deployments
```