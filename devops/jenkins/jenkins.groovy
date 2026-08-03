pipeline {

    agent any

    environment {

        BACKEND_IMAGE = "whoistimothyshandy/food-backend"
        FRONTEND_IMAGE = "whoistimothyshandy/food-frontend"

        IMAGE_TAG = "${BUILD_NUMBER}"

        AWS_REGION = "ap-south-1"
        CLUSTER_NAME = "food-app-cluster"
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
                    sh 'mvn clean package -DskipTests'
                }
            }
        }

        stage('SonarQube Analysis') {
            steps {
                dir('backend/Online-food') {
                    withSonarQubeEnv('SonarQube') {
                        sh '''
                        mvn org.sonarsource.scanner.maven:sonar-maven-plugin:3.9.1.2184:sonar \
                        -Dsonar.projectKey=food-app
                        '''
                    }
                }
            }
        }

        stage('Build Frontend') {
            steps {
                dir('frontend') {
                    sh '''
                    npm install
                    CI=false npm run build
                    '''
                }
            }
        }

        stage('Build Docker Images') {
            steps {

                sh '''
                docker build -t ${BACKEND_IMAGE}:${IMAGE_TAG} backend/Online-food
                docker tag ${BACKEND_IMAGE}:${IMAGE_TAG} ${BACKEND_IMAGE}:latest

                docker build -t ${FRONTEND_IMAGE}:${IMAGE_TAG} frontend
                docker tag ${FRONTEND_IMAGE}:${IMAGE_TAG} ${FRONTEND_IMAGE}:latest
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
                    echo $DOCKER_PASSWORD | docker login \
                    -u $DOCKER_USERNAME \
                    --password-stdin
                    '''
                }
            }
        }

        stage('Push Docker Images') {
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

                withCredentials([[
                    $class: 'AmazonWebServicesCredentialsBinding',
                    credentialsId: 'aws-creds'
                ]]) {

                    sh '''

                    aws eks update-kubeconfig \
                    --region ${AWS_REGION} \
                    --name ${CLUSTER_NAME}

                    kubectl apply -f devops/kubernetes/

                    kubectl set image deployment/backend \
                    backend=${BACKEND_IMAGE}:${IMAGE_TAG} \
                    -n food-app

                    kubectl set image deployment/frontend \
                    frontend=${FRONTEND_IMAGE}:${IMAGE_TAG} \
                    -n food-app

                    kubectl rollout status deployment/backend \
                    -n food-app \
                    --timeout=300s

                    kubectl rollout status deployment/frontend \
                    -n food-app \
                    --timeout=300s

                    kubectl get pods -n food-app
                    kubectl get svc -n food-app

                    '''
                }
            }
        }
    }

    post {

        success {
            echo "Food Ordering App deployed successfully."
        }

        failure {
            echo "Pipeline failed."
        }
    }
}
