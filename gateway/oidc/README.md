# About

this composition aims to provide a simple composition to debug the OIDC:

An external OIDC provider is needed, and the following variables are needed in `datadir/gateway/security.yaml`:
- CLIENT_ID
- CLIENT_SECRET
- ISSUER_URL

Please ask the PSC if you need a test account on the georchestra OIDC provider.

# Step to reproduce

1. Launch the docker composition and go to http://localhost:8080
2. make sure the console you're logged out
3. Login


