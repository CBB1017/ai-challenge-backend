pipeline {
    agent any

    environment {
        IMAGE_NAME = 'attendance_back'
        IMAGE_TAG  = "${BUILD_NUMBER}"
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Build & Test') {
            steps {
                sh 'chmod +x gradlew'
                sh './gradlew clean build'
            }
            post {
                always {
                    junit '**/build/test-results/test/*.xml'
                }
            }
        }

        stage('Build Docker Image') {
            steps {
                sh """
                    docker build \
                      --file Dockerfile \
                      --tag ${IMAGE_NAME}:${IMAGE_TAG} \
                      .
                """
            }
        }

        stage('Deploy with Compose (Local)') {
            steps {
                // 로컬 빌드된 이미지만 사용
                sh 'docker compose up -d --no-build'
            }
        }
    }

    post {
        success {
            echo "✅ 배포 완료: ${IMAGE_NAME}:${IMAGE_TAG}"
        }
        failure {
            echo "❌ 배포 실패!"
        }
    }
}
