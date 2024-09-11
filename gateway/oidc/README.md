# About

this composition aims to provide a simple composition to debug the OIDC and rabbitmq issues:

An external OIDC provider is needed, and the following variables are needed in `datadir/gateway/security.yaml`:
- CLIENT_ID
- CLIENT_SECRET
- ISSUER_URL

Please ask the PSC if you need a test account on the georchestra OIDC provider.

# Env file

1. Copy the `.oidc-env.sample` to `.oidc-env`
2. Change the necessary variables

# Step to reproduce

1. Launch the docker composition and go to http://localhost:8080
2. make sure the console you're logged out
3. Login
4. Check mails by going to http://localhost:8081

