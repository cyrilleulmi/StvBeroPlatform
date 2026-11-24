#!/bin/sh
# Start of the container of the hosted test environments (E-91).
#
# Without STV_DB_URL a throwaway PostgreSQL starts inside the container: a new, empty cluster on every start,
# reachable only on 127.0.0.1. The data is lost when the machine stops, which is fine for seed data (profile
# preview). The defaults of application.properties (localhost:5432, database and user «stv») point to it.
# With STV_DB_URL (later for production) the app uses that database and no PostgreSQL starts here.
set -eu

if [ -z "${STV_DB_URL:-}" ]; then
  PGBIN="$(ls -d /usr/lib/postgresql/*/bin | sort -V | tail -n 1)"
  PGDATA=/tmp/pgdata
  rm -rf "$PGDATA"
  "$PGBIN/initdb" -D "$PGDATA" -U stv --auth=trust -E UTF8 --locale=C.UTF-8 >/dev/null
  "$PGBIN/pg_ctl" -D "$PGDATA" -w -l /tmp/postgres.log \
    -o "-c listen_addresses=127.0.0.1 -c unix_socket_directories=$PGDATA -c max_connections=40 -c shared_buffers=32MB -c fsync=off -c synchronous_commit=off" \
    start >/dev/null
  "$PGBIN/createdb" -h 127.0.0.1 -U stv stv
  echo "start: PostgreSQL $("$PGBIN/postgres" --version | awk '{print $3}') ready inside the container"
fi

# shellcheck disable=SC2086 # JAVA_OPTS holds several options
exec java $JAVA_OPTS -jar quarkus-run.jar
