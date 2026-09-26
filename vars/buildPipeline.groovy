def call(String imageTag, String hostPort) {

    pipeline {

        agent any

        stages {

            stage('Build') {
                steps {
                    echo "************************ BUILD ************************"

                    sh 'mvn clean package -DskipTests -Dcyclonedx.skip=true'
                }
            }

            stage('Code Quality') {
                steps {
                    echo "************************ CODE QUALITY ************************"

                    withSonarQubeEnv('SonarQube') {
                        sh '''
                            mvn sonar:sonar \
                                -Dsonar.projectKey=deploy \
                                -DskipTests \
                                -Dcyclonedx.skip=true
                        '''
                    }
                }
            }

            stage('Docker Build') {
                steps {
                    echo "************************ DOCKER BUILD ************************"

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
