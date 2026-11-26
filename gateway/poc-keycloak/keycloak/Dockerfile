# Keycloak 26.8.1 for geOrchestra:
# - the organization extension (event listener "georchestra-organization")
# - an admin console patched with an "Est un organisme" checkbox in the group
#   create / edit dialog (admin-ui/*.patch, sets the isOrganization group attribute)
# - the geOrchestra login / account theme (themes/georchestra)
ARG KEYCLOAK_VERSION=26.8.1

# --- organization extension
FROM maven:3.9-eclipse-temurin-21 AS extension
WORKDIR /build
COPY organization-extension/pom.xml .
RUN mvn -B -q dependency:go-offline
COPY organization-extension/src src
RUN mvn -B -q package

# --- admin console, built from the Keycloak sources with the patches
FROM node:22 AS admin-ui-build
ARG KEYCLOAK_VERSION
RUN git clone -q --depth 1 --branch ${KEYCLOAK_VERSION} --filter=blob:none --sparse \
      https://github.com/keycloak/keycloak /src \
 && git -C /src sparse-checkout set js
COPY admin-ui/*.patch /patches/
RUN git -C /src apply /patches/*.patch
WORKDIR /src/js
ENV COREPACK_ENABLE_DOWNLOAD_PROMPT=0
RUN corepack enable \
 && pnpm install --frozen-lockfile --filter '@keycloak/keycloak-admin-ui...' \
 && pnpm --filter @keycloak/keycloak-admin-ui run build

FROM quay.io/keycloak/keycloak:${KEYCLOAK_VERSION} AS keycloak

# --- official admin console jar, with the rebuilt resources
FROM eclipse-temurin:21 AS admin-ui
ARG KEYCLOAK_VERSION
WORKDIR /work
COPY --from=keycloak /opt/keycloak/lib/lib/main/org.keycloak.keycloak-admin-ui-${KEYCLOAK_VERSION}.jar official.jar
COPY --from=admin-ui-build /src/js/apps/admin-ui/target/classes/theme/keycloak.v2/admin/resources resources
RUN mkdir jar && cd jar && jar xf ../official.jar \
 && rm -rf theme/keycloak.v2/admin/resources && cp -r ../resources theme/keycloak.v2/admin/resources \
 && jar cfm ../admin-ui.jar META-INF/MANIFEST.MF .

FROM keycloak
ARG KEYCLOAK_VERSION
COPY --from=extension /build/target/georchestra-organization.jar /opt/keycloak/providers/georchestra-organization.jar
COPY --from=admin-ui /work/admin-ui.jar /opt/keycloak/lib/lib/main/org.keycloak.keycloak-admin-ui-${KEYCLOAK_VERSION}.jar
COPY themes/georchestra /opt/keycloak/themes/georchestra
# pre-create the H2 dir owned by keycloak (uid 1000), otherwise a fresh named volume
# mounted there is root-owned and Keycloak cannot write its database
RUN mkdir -p /opt/keycloak/data/h2
