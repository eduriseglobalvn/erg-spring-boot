# Docker Native Deployment Design

## Goal

Provide one Docker-based workflow that supports:

- Local infrastructure for running the Spring Boot source from a Windows IDE.
- A production-like local stack using the same Linux amd64 native image deployed to the server.
- Multiple stateless backend instances behind Nginx.
- Native compilation on the developer machine only. The production server only pulls and runs a prebuilt image.

## Environments

### Local IDE development

Docker Compose starts PostgreSQL, Redis, and Nginx. The Spring Boot application runs from the Windows IDE on port 1606. Nginx reaches it through `host.docker.internal:1606` and exposes the same public entry point used by the containerized stack.

PostgreSQL and Redis use named volumes so local data survives container restarts. Their host ports are published on loopback for IDE access. Health checks gate dependent services.

### Local native verification

Docker Desktop builds a Linux amd64 native image from the current source using a multi-stage Dockerfile. Compose runs that image as multiple backend containers, a dedicated background worker, and Nginx, together with the same PostgreSQL and Redis services.

The API containers disable scheduled outbox, reporting, and notification processing. The single worker container enables those jobs so scaling the HTTP tier does not duplicate background work.

### Production

The developer or CI machine builds and pushes an immutable Linux amd64 image tag to a Docker Registry. The server uses a production environment file to select that exact tag, runs `docker compose pull`, and recreates the services. No compiler, Maven dependency download, GraalVM build, or Docker build runs on the server.

Rollback consists of selecting the previous immutable image tag and recreating the stack.

## Files And Commands

- A multi-stage `Dockerfile` builds the native executable and copies it into a minimal non-root runtime image.
- A base Compose file defines PostgreSQL, Redis, private networks, volumes, backend, worker, and shared health checks.
- Development and production-like Compose overlays select the appropriate Nginx configuration and published ports.
- Nginx development configuration proxies to `host.docker.internal:1606`.
- Nginx container configuration resolves all healthy backend replicas through Docker DNS and balances requests across them.
- PowerShell commands support Windows development; shell commands support Linux server operation.

The supported operations are:

- `dev`: start PostgreSQL, Redis, and development Nginx for an IDE-run backend.
- `build`: build the Linux amd64 native image locally.
- `push`: push a versioned native image to the configured registry.
- `up`: start the production-like stack with a configurable backend replica count.
- `deploy`: pull an already-built tag and recreate services on the server without building.
- `down`, `logs`, and `config`: operate and validate the stack.

## Networking And Load Balancing

Only Nginx exposes the application HTTP port in production. PostgreSQL, Redis, backend replicas, and the worker communicate over private Compose networks. Local development may publish PostgreSQL and Redis on loopback for the IDE.

Nginx forwards standard proxy headers and uses the Docker embedded DNS resolver so scaled backend containers can be discovered without generating a static upstream list. WebSocket upgrade headers and conservative connection/read timeouts are included for GraphQL and future streaming endpoints.

Actuator readiness is used by container health checks. Nginx itself exposes a simple health endpoint for host-level checks.

## Configuration And Secrets

Example environment files contain safe placeholders only. Real database passwords, JWT secrets, registry credentials, and external service keys remain outside Git. Runtime environment variables configure datasource and Redis URLs using Compose service names.

Images are tagged with a release identifier such as a Git commit SHA, never deployed by relying only on `latest`. The production Compose workflow accepts the full registry image reference through `ERG_APP_IMAGE`.

## Failure Handling

- PostgreSQL and Redis must pass health checks before backend startup.
- Backend containers restart unless stopped and report readiness through Actuator.
- Nginx retries another upstream for connection errors, timeouts, and selected gateway failures.
- A failed native build or image push leaves the currently deployed server image untouched.
- A failed deployment can be rolled back by restoring the previous image tag.

## Verification

Configuration verification includes Docker Compose rendering for both local IDE and native/production modes, Nginx syntax checks, and Dockerfile build-stage inspection.

Runtime verification includes starting local dependencies, confirming PostgreSQL and Redis health, confirming Nginx can proxy to an IDE-run backend, building the Linux amd64 image, scaling at least two backend containers, and observing requests through Nginx. Native compilation may expose application AOT issues and is therefore reported separately from static Compose validation.

## Scope

This design uses plain Docker Compose and Nginx. It assumes an existing Docker Hub, GHCR, or private Registry account and does not provision a Registry service. It does not introduce Docker Swarm, Kubernetes, TLS certificate automation, or database high availability. Those can be added independently when operational requirements justify them.
