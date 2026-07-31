pipeline {

    agent any

    tools {

        jdk 'jdk17'
        maven 'maven3'

    }

    stages {

        stage('Git Checkout') {

            steps {

                git 'https://github.com/timothyshandy/Food-Ordering-App.git'

            }

        }

        stage('Build') {

            steps {

                sh '''
                cd backend/Online-food
                mvn clean package -DskipTests
                '''
            }

        }

    }

}
