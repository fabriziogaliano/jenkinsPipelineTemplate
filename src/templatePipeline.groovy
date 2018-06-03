
pipeline {
    agent any
    environment {
        DOCKER_REGISTRY = 'registry.zombox.it'
        GIT_REPOSITORY = 'https://github.com/fabriziogaliano'
        DEPLOY_SSH_TARGET = '192.168.0.109'
        DEPLOY_SSH_USER = 'root'
        DEPLOY_SSH_DEFAULT_PATH = '/docker'
    }
    stages {
        stage('Git pull') {
            agent any
            steps {
                checkout([$class: 'GitSCM', branches: [[name: '*/${GIT_REF}']], doGenerateSubmoduleConfigurations: false, extensions: [], submoduleCfg: [], userRemoteConfigs: [[credentialsId: 'aad8cb5b-ddd8-47e3-a8d4-b9f128cf3fd5', url: '${GIT_REPOSITORY}/${JOB_NAME}.git']]])
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
                deploy()
                echo "Application Deployed to ${DEPLOY_ENV}"
            }
        }
        stage('Clean') {
            steps {
                cleanUp()
                echo "Old images removed from CI Server"
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

def deploy() {
    node {
        sh 'ssh ${DEPLOY_SSH_USER}@${DEPLOY_SSH_TARGET} "docker-compose -f ${DEPLOY_SSH_DEFAULT_PATH/${JOB_NAME} up -d --force-recreate"'
    }
}