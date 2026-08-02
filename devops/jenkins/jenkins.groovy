pipeline {

    agent any

    environment {

        BACKEND_IMAGE = "whoistimothyshandy/food-backend"
        FRONTEND_IMAGE = "whoistimothyshandy/food-frontend"
        
        IMAGE_TAG = "${BUILD_NUMBER}"

        AWS_REGION = "ap-south-1"
        CLUSTER_NAME = "food-app-cluster"
    }

    options {
        timestamps()
        buildDiscarder(logRotator(numToKeepStr: '10'))
    }

    stages {

        stage('Checkout') {

            steps {

                git branch: 'main',
                url: 'https://github.com/timothyshandy/Food-Ordering-App.git'

            }

        }

        stage('Build Backend') {

            steps {

                dir('backend/Online-food') {

                    sh '''
                    mvn clean package -DskipTests
                    '''

                }

            }

        }

        stage('SonarQube Analysis') {
    steps {
        dir('backend/Online-food') {
            withSonarQubeEnv('SonarQube') {
                sh '''
                mvn sonar:sonar \
                  -Dsonar.projectKey=food-app \
                  -Dsonar.host.url=$SONAR_HOST_URL \
                  -Dsonar.token=$SONAR_AUTH_TOKEN
                '''
            }
        }
    }
}


        stage('Quality Gate') {

            steps {

                timeout(time: 5, unit: 'MINUTES') {

                    waitForQualityGate abortPipeline: true

                }

            }

        }

        stage('Build Frontend') {

            steps {

                dir('frontend') {

                    sh '''
                    npm install
                    npm run build
                    '''

                }

            }

        }

        stage('Build Backend Docker Image') {

            steps {

                sh '''

                docker build \
                -t ${BACKEND_IMAGE}:${IMAGE_TAG} \
                -t ${BACKEND_IMAGE}:latest \
                backend/Online-food

                '''

            }

        }

        stage('Build Frontend Docker Image') {

            steps {

                sh '''

                docker build \
                -t ${FRONTEND_IMAGE}:${IMAGE_TAG} \
                -t ${FRONTEND_IMAGE}:latest \
                frontend

                '''

            }

        }

        stage('Docker Login') {

            steps {

                withCredentials([
                    usernamePassword(
                        credentialsId: 'dockerhub-creds',
                        usernameVariable: 'DOCKER_USERNAME',
                        passwordVariable: 'DOCKER_PASSWORD'
                    )
                ]) {

                    sh '''

                    echo "$DOCKER_PASSWORD" | docker login \
                    -u "$DOCKER_USERNAME" \
                    --password-stdin

                    '''

                }

            }

        }

        stage('Push Images') {

            steps {

                sh '''

                docker push ${BACKEND_IMAGE}:${IMAGE_TAG}
                docker push ${BACKEND_IMAGE}:latest

                docker push ${FRONTEND_IMAGE}:${IMAGE_TAG}
                docker push ${FRONTEND_IMAGE}:latest

                '''

            }

        }

        stage('Deploy to EKS') {

            steps {

                withCredentials([
                    [
                        $class: 'AmazonWebServicesCredentialsBinding',
                        credentialsId: 'aws-creds'
                    ]
                ]) {

                    sh '''

                    aws eks update-kubeconfig \
                    --region ${AWS_REGION} \
                    --name ${CLUSTER_NAME}

                    kubectl apply -f devops/kubernetes/

                    kubectl rollout restart deployment/backend -n food-app

                    kubectl rollout restart deployment/frontend -n food-app

                    kubectl rollout status deployment/backend \
                    -n food-app \
                    --timeout=300s

                    kubectl rollout status deployment/frontend \
                    -n food-app \
                    --timeout=300s

                    '''

                }

            }

        }

        stage('Verify Deployment') {

            steps {

                sh '''

                kubectl get pods -n food-app

                kubectl get svc -n food-app

                kubectl get ingress -n food-app

                '''

            }

        }

    }

    post {

        always {

            sh '''
            docker image prune -f
            '''

        }

        success {

            echo "Food Ordering App deployed successfully."

        }

        failure {

            echo "Deployment failed."

        }

    }

}