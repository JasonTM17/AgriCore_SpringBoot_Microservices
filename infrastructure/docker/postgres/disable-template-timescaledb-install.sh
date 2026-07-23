#!/bin/sh
set -eu

# The upstream image installs TimescaleDB in postgres and template1. AgriCore
# provisions it explicitly in agricore_iot so unrelated service databases stay
# plain PostgreSQL.
:
