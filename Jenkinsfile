pipeline {
    agent any

    options {
        timestamps()
        disableConcurrentBuilds()
    }

    environment {
        GRADLE_USER_HOME = "${WORKSPACE}\\.gradle"
        COMPOSE_FILE = "infrastructure\\docker\\compose-dev.yaml"
    }

    stages {
        stage('Java / Gradle Check') {
            steps {
                bat 'java -version'
                bat 'gradlew.bat --version'
            }
        }
        
        stage('SonarQube analysis') {
            steps {
                script {
                    try {
                        withSonarQubeEnv('sonar') {
                            bat 'gradlew.bat --no-daemon :apps:master-service:sonar'
                        }
                    } catch (e) {
                        echo "품질검사 실패"
                    }
                }
            }
        }

        stage('BootJar Apps') {
            steps {
                bat '''
                gradlew.bat --no-daemon --parallel ^
                  :apps:config-server:bootJar ^
                  :apps:discovery-server:bootJar ^
                  :apps:gateway:bootJar ^
                  :apps:master-service:bootJar ^
                  :apps:telemetry-service:bootJar ^
                  :apps:realtime-service:bootJar ^
                  :apps:job-service:bootJar ^
                  :apps:ems-service:bootJar ^
                  :apps:auth-service:bootJar ^
                  :apps:autonomous-service:bootJar ^
                  :apps:pms-service:bootJar
                '''
            }
        }

        stage('Docker Cleanup Containers') {
            steps {
                bat '''
                for %%C in (
                  kafka
                  config-server
                  discovery-server
                  gateway
                  master-service
                  telemetry-service
                  realtime-service
                  job-service
                  ems-service
                  auth-service
                  autonomous-service
                  pms-service
                ) do (
                  docker rm -f %%C 2>NUL
                )
                '''
            }
        }

        stage('Docker Compose') {
            steps {
                bat 'docker compose -f "%COMPOSE_FILE%" up -d --build --force-recreate'
            }
        }
    }

    post {
        always {
            echo '정수장 전문가'
        }
        success {
            echo '이제 정수장은 전부 니꺼야'
        }
        failure {
            echo '정수장뿐만 아니라 도시침수도 해보자 싫은걸 해야하는거야'
        }
    }
}
