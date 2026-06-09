FROM mcr.microsoft.com/devcontainers/java:1-21-bookworm

# Work around a broken third-party apt source baked into the base image:
# the yarn Debian repo signing key rotated, so `apt-get update` now fails with
#   NO_PUBKEY 62D54FD4003F6525 / "repository ... is not signed".
# That breaks every Dev Container Feature that installs apt packages
# (docker-in-docker, azure-cli, ...). yarn is not used by this Java + azd
# project, so remove the stale yarn source before any Feature runs.
RUN rm -f /etc/apt/sources.list.d/yarn.list \
 && find /etc/apt/sources.list.d -type f -name '*.list' -exec sed -i '\#dl.yarnpkg.com#d' {} +
