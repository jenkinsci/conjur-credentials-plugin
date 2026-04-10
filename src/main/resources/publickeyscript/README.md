# public-keys update
## Requirement
+ Jenkins Server
+ Conjur Secrets plugin
+ Conjur Server
+ Conjur Policies
  * **`Note`**: make sure that the **`host`** has required permissions to **`update`** the **`resource [public-keys]`** variable value in the Conjur Policies
  * Here an Example
    ```
    - !permit
      resource: !variable conjur/authn-jwt/jenkins/public-keys
      privileges: [ update ]
      roles: !host jenkins/projects/jenkins
    ```

## Usage instructions

### - Step by step guide
![image](https://github.com/ManithejaCyberark/public-keys-update/assets/109070761/1549416a-83c7-440f-8565-f95b3ccc1e6a)
<img width="900" alt="image" src="https://github.com/ManithejaCyberark/public-keys-update/assets/109070761/3aa4b0ac-83c5-4df2-9cf1-edbc0201e801">


### - Usage from Jenkins freestyle project
- Create jenkins freestyle project and To bind to the secret created for the host or user, use the option "Use secret text(s) or file(s)" in the "Build Environment" section of a Freestyle project
  <img width="1000" alt="image" src="https://github.com/ManithejaCyberark/public-keys-update/assets/109070761/ebcbe9e0-315b-4c9d-a24c-fa168eb6a840">
- Build steps to update the public-keys variable value
```
# Instructions for Conjur OS and conjur Enterprise:
#!/bin/bash

CONT_SESSION_TOKEN=$(curl --header "Accept-Encoding: base64" --data "$LOGINCREDENTIALSTOCONJUR" \
      http://conjur_server/authn/myConjurAccount/host%2Fjenkins%2Fprojects%2Fjenkins/authenticate)
      
#get the public-keys

publickey=$(curl -k http://<jenkins_url>/jwtauth/conjur-jwk-set)
export secretVar='{
    "type": "jwks",
    "value": '${publickey}'
  }'
echo $secretVar > publickeysfile

#update the public-keys or set a secret

curl -H "Authorization: Token token=\"$CONT_SESSION_TOKEN\"" \
    --data "$(cat publickeysfile)" \
     http://conjur_server/secrets/myConjurAccount/variable/conjur%2Fauthn-jwt%2Fjenkins%2Fpublic-keys

```

```
# Instructions for Conjur Cloud

#!/bin/bash

CONJUR_ACCESS_TOKEN=$(curl --header "Accept-Encoding: base64" --data "$CONJURCLOUDHOST1APIKEY" \
  	   https://<subdomain>.secretsmgr.cyberark.cloud/api/authn/conjur/host%2Fdata%2Fconjur-cloud-host1/authenticate)

#get the public-keys

publickey=$(curl -k http://jenkins:8080/jwtauth/conjur-jwk-set)
export secretVar='{
    "type": "jwks",
    "value": '${publickey}'
  }'
echo $secretVar > publickeysfile

#update the public-keys or set a secret

curl -H "Authorization: Token token=\"$CONJUR_ACCESS_TOKEN\"" \
     --data "$(cat publickeysfile)" \
     https://<subdomain>.secretsmgr.cyberark.cloud/api/secrets/conjur/variable/conjur/authn-jwt/<service_id>/public-keys

```
## Example 
<img width="1087" alt="image" src="https://github.com/ManithejaCyberark/public-keys-update/assets/109070761/9941ffcf-ca4c-476c-b1c1-c22cc1899928">


- **Restart the jenkins** and build/run the freestyle project will **update** the **public-keys** variable value by connecting to the conjur cloud using ***conjur_access token***. 
  ```
  for Conjur OS:
  - conjur variable value conjur/authn-jwt/<service-id>/public-keys
  for Conjur Enterprise and Conjur Cloud:
  - conjur variable get -i conjur/authn-jwt/<service-id>/public-keys
  - conjur variable get -i conjur/authn-jwt/<service-id>/public-keys
  ```

