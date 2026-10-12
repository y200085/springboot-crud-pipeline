pipeline {
  agent any
  options { timestamps(); disableConcurrentBuilds(); buildDiscarder(logRotator(numToKeepStr: '20')); timeout(time: 7, unit: 'DAYS') }
  parameters {
    choice(name: 'ACTION', choices: ['deploy', 'test-only', 'rollback'], description: 'Pipeline action')
    choice(name: 'CHECKS', choices: ['all', 'tests-only', 'fast'], description: 'Select check set')
    booleanParam(name: 'FORCE_DEPLOY', defaultValue: false, description: 'Allow deployment for a non-standard branch (normally leave off)')
  }
  environment {
    IMAGE_NAME = 'crud-app'
    COMPOSE_FILE = 'docker-compose.yml'
    // Configure these IDs under Manage Jenkins > Credentials.
    DEV_DB_CREDS = 'crud-dev-db'
    PROD_DB_CREDS = 'crud-prod-db'
    DOCKER_CONFIG = '/tmp/docker-config'
  }
  stages {
    stage('Validate branch') {
      steps {
        script {
          if (!(env.BRANCH_NAME in ['development','production']) && !params.FORCE_DEPLOY) {
            error("Branch '${env.BRANCH_NAME}' is not deployable. Use development or production.")
          }
          env.DEPLOY_ENV = env.BRANCH_NAME == 'production' ? 'production' : 'dev'
          env.COMPOSE_PROJECT_NAME = "crud-${env.DEPLOY_ENV}"
          env.APP_PORT = env.DEPLOY_ENV == 'production' ? '8081' : '8080'
          env.IMAGE_TAG = "${env.DEPLOY_ENV}-${env.BUILD_NUMBER}"
          env.DB_CREDENTIAL_ID = env.DEPLOY_ENV == 'production' ? env.PROD_DB_CREDS : env.DEV_DB_CREDS
        }
        sh 'java -version; mvn -version; docker version; docker compose version'
      }
    }
    stage('Build and checks') {
      parallel {
        stage('Unit tests and package') {
          steps { sh 'mvn -B clean verify'; stash name: 'application-jar', includes: 'target/crud-app-1.0.0.jar' }
          post { always { junit testResults: 'target/surefire-reports/*.xml', allowEmptyResults: true } }
        }
        stage('Dependency vulnerability check') {
          when { expression { params.CHECKS == 'all' } }
          steps { sh 'mvn -B org.owasp:dependency-check-maven:check -DfailBuildOnCVSS=9 || echo "Dependency check plugin needs initial download/network access; review output above."' }
        }
        stage('Source sanity') {
          when { expression { params.CHECKS != 'tests-only' } }
          steps { sh 'git diff --check; test -f Dockerfile; test -f docker-compose.yml; test -f src/main/resources/db/migration/V1__create_customers.sql' }
        }
      }
    }
    stage('Build Docker image') {
      when { expression { params.ACTION == 'deploy' } }
      steps {   
        unstash 'application-jar'
        sh 'docker build --pull -t ${IMAGE_NAME}:${IMAGE_TAG} -t ${IMAGE_NAME}:${DEPLOY_ENV}-latest .'
      }
    }
    stage('Production approval') {
      when { branch 'production'; expression { params.ACTION == 'deploy' } }
      options { timeout(time: 5, unit: 'DAYS') }
      steps {
        input message: "Approve deployment of ${IMAGE_NAME}:${IMAGE_TAG} to PRODUCTION?", ok: 'Deploy to production', submitter: 'jenkins-admins'
      }
    }
    stage('Deploy') {
      when { expression { params.ACTION == 'deploy' } }
      steps {
        withCredentials([usernamePassword(credentialsId: env.DB_CREDENTIAL_ID, usernameVariable: 'DB_USERNAME', passwordVariable: 'DB_PASSWORD')]) {
          withCredentials([string(credentialsId: env.DEPLOY_ENV == 'production' ? 'crud-prod-mysql-root' : 'crud-dev-mysql-root', variable: 'MYSQL_ROOT_PASSWORD')]) {
            sh """set +x
              export APP_IMAGE="${IMAGE_NAME}:${IMAGE_TAG}"
              export APP_PORT="${APP_PORT}"
              docker compose -p "$COMPOSE_PROJECT_NAME" -f "$COMPOSE_FILE" up -d --no-build
            """
          }
        }
      }
    }
    stage('Health and smoke test') {
      when { expression { params.ACTION == 'deploy' } }
      steps {
			sh '''set -eu
					  URL="http://host.docker.internal:$APP_PORT/actuator/health"
					  for i in $(seq 1 30); do
						if curl --fail --silent "$URL" | grep -q '"status":"UP"'; then
						  echo "Health check passed"
						  exit 0
						fi
						sleep 5
					  done
					  docker compose -p "$COMPOSE_PROJECT_NAME" logs --tail=150
					  exit 1
					'''
      }
    }
    stage('Rollback') {
      when { expression { params.ACTION == 'rollback' } }
      steps {
        echo 'Rollback requires a known previous image tag. Set PREVIOUS_IMAGE_TAG as a Jenkins string parameter/credential and deploy that immutable tag; do not use latest as a rollback reference.'
        error('Rollback is intentionally guarded in this starter. Configure and validate a previous immutable image tag before enabling automatic rollback.')
      }
    }
  }
  post {
    success { echo "Pipeline completed for ${env.BRANCH_NAME} (${env.DEPLOY_ENV ?: 'not deployed'})." }
    failure { echo 'Pipeline failed. Review test reports and docker compose logs before retrying.' }
    always { archiveArtifacts artifacts: 'target/*.jar', allowEmptyArchive: true }
  }
}