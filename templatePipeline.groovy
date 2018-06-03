def myJenkinsPipelineTemplate(Map templateParams) {
        pipeline {
            agent any
            parameters {
                string(name: 'DockerRegistry', description: 'Docker registry URL, without Protocol')
                string(name: 'Project name')
            }
            environment {
                DOCKER_REGISTRY = 'registry.zombox.it'
            }
            stages {
                stage('Git pull') {
                    agent any
                    steps {
                        checkout([$class: 'GitSCM', branches: [[name: '*/${GIT_REF}']], doGenerateSubmoduleConfigurations: false, extensions: [], submoduleCfg: [], userRemoteConfigs: [[credentialsId: 'aad8cb5b-ddd8-47e3-a8d4-b9f128cf3fd5', url: 'https://github.com/fabriziogaliano/${JOB_NAME}.git']]])
                        echo "Project Updated"
                    }
                }
                stage('Docker Build') {
                    steps {
                        dockerBuild()
                        echo "Build Completed"
                    }
                }
                stage('Docker Tag') {
                    steps {
                        dockerTag()
                        echo "Tag Completed"
                    }
                }
                stage('Docker Push') {
                    steps {
                        dockerPush()
                        echo "Push Complete"
                    }
                    }
                stage('Deploy') {
                    steps {
                        sh 'docker-compose -f /docker/nginx_ci/docker-compose.yml up -d --force-recreate'
                        echo "Application Deployed to ${DEPLOY_ENV}"
                    }
                }
            }
        }

        def dockerBuild() {
            node {
                sh 'docker build -t ${DOCKER_REGISTRY}/${JOB_NAME}:${GIT_REF} .'
            }
        }

        def dockerTag() {
            node {
                sh 'docker tag ${DOCKER_REGISTRY}/${JOB_NAME}:${GIT_REF} ${DOCKER_REGISTRY}/${JOB_NAME}:latest'
            }
        }

        def dockerPush() {
            node {
                withDockerRegistry(credentialsId: '655afa6d-5a19-4f15-97ce-29ac43336234', url: 'https://registry.zombox.it') {
                sh 'docker push ${DOCKER_REGISTRY}/${JOB_NAME}:${GIT_REF}'
                sh 'docker push ${DOCKER_REGISTRY}/${JOB_NAME}:latest'
            }
            }
        }

        def cleanUp() {
            node {
                sh 'docker rmi ${DOCKER_REGISTRY}/${JOB_NAME}:${GIT_REF} '
                sh 'docker rmi ${DOCKER_REGISTRY}/${JOB_NAME}:latest '
            }
        }
}