# Local setup (Windows + Docker Desktop + Jenkins in Docker)

## Important prerequisites
- Docker Desktop running Linux containers.
- Jenkins container must have Docker CLI + Compose plugin, Git, JDK 21, Maven, and `curl` installed.
- The Jenkins container needs access to the Docker Desktop engine. On a local learning machine this is commonly done by mounting `/var/run/docker.sock` into Jenkins. This gives Jenkins effectively host-level Docker control; use only on a trusted lab machine.
- Ensure the Jenkins container can reach the app's published ports on the Docker host. On Docker Desktop, `host.docker.internal` is usually the correct host name; adapt smoke-test URL if needed.

## 1. Git branches
Create `development` and `production` branches. Put this repository's `Jenkinsfile` at the repository root. Configure a Jenkins Multibranch Pipeline against the GitHub repository and enable branch discovery. Use a GitHub token or SSH key stored in Jenkins credentials, never in the Jenkinsfile.

## 2. Jenkins tools and plugins
Install Pipeline, Git, Credentials Binding, JUnit, and Multibranch Pipeline Branch API plugins. Configure JDK 21 and Maven if using Jenkins-managed tools, or install both in the Jenkins agent/container. The pipeline assumes shell steps (`sh`) and Docker CLI + Compose are available.

## 3. Credentials
Manage Jenkins > Credentials > (global) > Add Credentials:
- `crud-dev-db`: Username with password; username `cruduser`; strong development DB password.
- `crud-prod-db`: Username with password; username `cruduser`; a DIFFERENT strong production DB password.
- `crud-dev-mysql-root`: Secret text; strong development root password.
- `crud-prod-mysql-root`: Secret text; a DIFFERENT strong production root password.
- `jenkins-admins`: Configure this as an actual Jenkins user/group permitted to approve production deployment; change the `submitter` value in Jenkinsfile to match your Jenkins user/group names.

Credentials binding in this starter demonstrates secret retrieval, but Compose environment variables are visible to Docker administrators and may be inspectable. For stronger setups use Docker secrets or an external secrets manager. Never commit real `.env` files or passwords.

## 4. Deploying
For a development build, select `development` in the Multibranch job and run `ACTION=deploy`. It deploys to port 8080 with Compose project `crud-dev`. For production, merge/promote tested code to `production`; the pipeline waits for approval (up to 5 days) then deploys to port 8081 with Compose project `crud-production`.

Visit http://localhost:8080 or http://localhost:8081. CRUD UI is at `/customers`; health endpoint is `/actuator/health`.

## 5. Persistence and database isolation
Compose uses named volume `crud-dev_mysql_data` and `crud-production_mysql_data` (project-scoped), so the environments do not share a database volume. The app uses Flyway migrations; Hibernate is configured with `ddl-auto=validate`. Back up production data before destructive schema migrations.

## Known starter limitations
- The included rollback action is deliberately guarded. Before production use, implement a deployment-history store and require a verified previous immutable image tag, then test rollback on failure.
- `docker-compose.yml` builds locally by default, but Jenkins uses an explicit prebuilt image. Compose's `build: .` is only for a manual local run; remove it in hardened deployments or split into a development override.
- Security scan is a starter dependency-check invocation; review its output and pin/update the plugin configuration rather than treating this as a complete security program.
- If Jenkins cannot reach `localhost:8080` from inside its container, use `host.docker.internal` for the smoke test URL.
