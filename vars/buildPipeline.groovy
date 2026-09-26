def call(String imageTag, String hostPort) {

    pipeline {

        agent any

        stages {

            stage('Build') {
                steps {
                    echo "************************ BUILD ************************"

                    sh 'rm -rf spring-petclinic || true'
                    sh 'git clone https://github.com/pavandath/spring-petclinic.git'

                    dir('spring-petclinic') {
                        sh 'mvn clean package -DskipTests -Dcyclonedx.skip=true'
                    }
                }
            }

            stage('Code Quality') {
                steps {
                    echo "************************ CODE QUALITY ************************"

                    dir('spring-petclinic') {
                        withSonarQubeEnv('SonarQube') {
                            sh '''
                                mvn org.sonarsource.scanner.maven:sonar-maven-plugin:sonar \
                                    -Dsonar.projectKey=deploy \
                                    -DskipTests \
                                    -Dcyclonedx.skip=true
                            '''
                        }
                    }
                }
            }

            stage('Docker Build') {
                steps {
                    echo "************************ DOCKER BUILD ************************"

                    dir('spring-petclinic') {

                        writeFile file: 'Dockerfile', text: '''
                            FROM eclipse-temurin:21-jdk-jammy

                            WORKDIR /app

                            COPY target/*.jar spring.jar

                            EXPOSE 8080

                            CMD ["java", "-jar", "spring.jar"]
                        '''

                        sh "docker build -t java-spring:${imageTag} ."
                    }
                }
            }

            stage('Deploy') {
                steps {
                    echo "************************ DEPLOY ************************"

                    sh """
                        docker rm -f deployment-${imageTag} || true

                        docker run -d \
                            --name deployment-${imageTag} \
                            -p ${hostPort}:8080 \
                            java-spring:${imageTag}
                    """
                }
            }
        }
    }
}
