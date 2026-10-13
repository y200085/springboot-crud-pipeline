
pipeline {
    agent any

    options {
        disableConcurrentBuilds()
        buildDiscarder(logRotator(numToKeepStr: '20'))
        timeout(time: 7, unit: 'DAYS')
        skipStagesAfterUnstable()
    }

    parameters {
        choice(
            name: 'ACTION',
            choices: ['deploy', 'test-only', 'rollback'],
            description: 'Choose the pipeline action'
        )

        choice(
            name: 'CHECKS',
            choices: ['all', 'tests-only', 'fast'],
            description: 'Choose which checks to run'
        )

        booleanParam(
            name: 'FORCE_DEPLOY',
            defaultValue: false,
            description: 'Allow deployment from a non-standard branch'
        )
    }

    environment {
        IMAGE_NAME = 'crud-app'
        COMPOSE_FILE = 'docker-compose.yml'
        DEV_DB_CREDS = 'crud-dev-db'
        PROD_DB_CREDS = 'crud-prod-db'
    }

    stages {

        stage('Validate branch and tools') {
            steps {
                script {
                    if (!(env.BRANCH_NAME in ['development', 'production'])
                        && !params.FORCE_DEPLOY) {
                        error("Branch ${env.BRANCH_NAME} is not allowed")
                    }

                    if (params.ACTION == 'deploy'
                        && !(env.BRANCH_NAME in ['development', 'production'])) {
                        error('Deployment is allowed only from development or production')
                    }

                    env.DEPLOY_ENV =
                        env.BRANCH_NAME == 'production' ? 'production' : 'dev'

                    env.COMPOSE_PROJECT_NAME = "crud-${env.DEPLOY_ENV}"

                    env.APP_PORT =
                        env.DEPLOY_ENV == 'production' ? '8081' : '8080'

                    env.IMAGE_TAG =
                        "${env.DEPLOY_ENV}-${env.BUILD_NUMBER}"

                    env.DB_CREDENTIAL_ID =
                        env.DEPLOY_ENV == 'production'
                            ? env.PROD_DB_CREDS
                            : env.DEV_DB_CREDS

                    if (params.ACTION == 'rollback') {
                        error(
                            'Rollback is not configured. ' +
                            'Implement previous-image tracking before using this action.'
                        )
                    }
                }

                sh '''
                    set -eu
                    java -version
                    mvn -version
                    docker version
                    docker compose version
                '''
            }
        }

        stage('Build and checks') {
            when {
                expression { params.ACTION != 'rollback' }
            }

            parallel {
                stage('Unit tests and package') {
                    steps {
                        sh 'mvn -B clean verify'

                        stash(
                            name: 'application-jar',
                            includes: 'target/crud-app-1.0.0.jar'
                        )
                    }

                    post {
                        always {
                            junit(
                                testResults: 'target/surefire-reports/*.xml',
                                allowEmptyResults: true
                            )
                        }
                    }
                }

                stage('Source sanity') {
                    when {
                        expression { params.CHECKS != 'tests-only' }
                    }

                    steps {
                        sh '''
                            set -eu
                            git diff --check
                            test -f Dockerfile
                            test -f docker-compose.yml
                            test -f src/main/resources/db/migration/V1__create_customers.sql
                        '''
                    }
                }
            }
        }

        stage('Build Docker image') {
            when {
                expression { params.ACTION == 'deploy' }
            }

            steps {
                unstash 'application-jar'

                sh '''
                    set -eu

                    docker build --pull \
                        -t "${IMAGE_NAME}:${IMAGE_TAG}" \
                        -t "${IMAGE_NAME}:${DEPLOY_ENV}-latest" .
                '''
            }
        }

        stage('Production approval') {
            when {
                allOf {
                    branch 'production'
                    expression { params.ACTION == 'deploy' }
                }
            }

            options {
                timeout(time: 5, unit: 'DAYS')
            }

            steps {
                input(
                    message: "Approve ${IMAGE_NAME}:${IMAGE_TAG} for production deployment?",
                    ok: 'Deploy to production',
                    submitter: 'jenkins-admins'
                )
            }
        }

        stage('Deploy') {
            when {
                expression { params.ACTION == 'deploy' }
            }

            steps {
                script {
                    def rootCredentialId =
                        env.DEPLOY_ENV == 'production'
                            ? 'crud-prod-mysql-root'
                            : 'crud-dev-mysql-root'

                    withCredentials([
                        usernamePassword(
                            credentialsId: env.DB_CREDENTIAL_ID,
                            usernameVariable: 'DB_USERNAME',
                            passwordVariable: 'DB_PASSWORD'
                        ),
                        usernamePassword(
                            credentialsId: rootCredentialId,
                            usernameVariable: 'MYSQL_ROOT_CREDS_USR',
                            passwordVariable: 'MYSQL_ROOT_PASSWORD'
                        )
                    ]) {
                        sh '''
                            set -eu
                            set +x

                            export APP_IMAGE="${IMAGE_NAME}:${IMAGE_TAG}"

                            docker compose \
                                -p "$COMPOSE_PROJECT_NAME" \
                                -f "$COMPOSE_FILE" \
                                up -d --no-build
                        '''
                    }
                }
            }
        }

        stage('Health and smoke test') {
            when {
                expression { params.ACTION == 'deploy' }
            }

            steps {
                sh '''
                    set -eu

                    URL="http://host.docker.internal:${APP_PORT}/actuator/health"

                    for i in $(seq 1 30); do
                        if curl --fail --silent "$URL" \
                            | grep -q '"status":"UP"'; then
                            echo "Health check passed"
                            exit 0
                        fi

                        sleep 5
                    done

                    docker compose \
                        -p "$COMPOSE_PROJECT_NAME" \
                        -f "$COMPOSE_FILE" logs --tail=150

                    exit 1
                '''
            }
        }
    }

    post {
        success {
            echo "Pipeline succeeded for ${env.BRANCH_NAME}"
        }

        failure {
            echo 'Pipeline failed. Check the console output and Docker logs.'
        }

        aborted {
            echo 'Pipeline was aborted or approval timed out.'
        }

        always {
            archiveArtifacts(
                artifacts: 'target/*.jar',
                allowEmptyArchive: true
            )
        }
    }
}