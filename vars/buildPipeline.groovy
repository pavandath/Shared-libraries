def call() {

    pipeline {

        agent any

        stages {

            stage('Build') {
                steps {
                    echo "************************ RUNNING BUILD STAGE ************************"

                    sh 'rm -rf spring-petclinic || true'
                    sh 'git clone https://github.com/pavandath/spring-petclinic.git'

                    dir('spring-petclinic') {
                        sh 'mvn clean package -DskipTests -Dcyclonedx.skip=true'
                    }
                }
            }

            stage('CodeQuality') {
                steps {
                    echo "************************ RUNNING CODE QUALITY STAGE ************************"

                    dir('spring-petclinic') {

                        withSonarQubeEnv('SonarQube') {

                            sh '''
                                mvn clean verify sonar:sonar \
                                    -Dsonar.projectKey=deploy \
                                    -DskipTests \
                                    -Dcyclonedx.skip=true
                            '''
                        }
                    }
                }
            }

            stage('DockerBuild') {
                steps {
                    script {

                        def imageTag = env.BRANCH_NAME == 'main' ? 'v1' : 'v2'

                        echo "************************ RUNNING DOCKER BUILD STAGE ************************"
                        echo "Building image: java-spring:${imageTag}"

                        dir('spring-petclinic') {

                            writeFile file: 'Dockerfile', text: '''
                                FROM eclipse-temurin:21-jdk-jammy

                                WORKDIR /app

                                COPY target/*.jar spring.jar

                                EXPOSE 8080

                                CMD ["java", "-jar", "spring.jar"]
                            '''

                            sh """
                                docker build -t java-spring:${imageTag} .
                            """
                        }
                    }
                }
            }

            stage('Deploy') {
                steps {
                    script {

                        def imageTag = env.BRANCH_NAME == 'main' ? 'v1' : 'v2'

                        echo "************************ RUNNING DEPLOY STAGE ************************"
                        echo "Deploying java-spring:${imageTag}"

                        sh '''
                            docker rm -f deployment || true
                        '''

                        sh """
                            docker run -d \
                                --name deployment \
                                -p 8080:8080 \
                                java-spring:${imageTag}
                        """
                    }
                }
            }
        }
    }
}
