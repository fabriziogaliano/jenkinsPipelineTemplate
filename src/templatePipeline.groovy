// Pipeline

pipeline {

    agent any

    environment {
        // Default Docker build image name
        DOCKER_IMAGE_BUILD_NAME = 'ciserver'
        // Docker Registry
        DOCKER_REGISTRY = 'registry.zombox.it'
        DOCKER_AWS_REGISTRY = 'xxxxxxxxxxx.dkr.ecr.eu-west-1.amazonaws.com'
        DOCKER_REGISTRY_CRED_ID = '655afa6d-5a19-4f15-97ce-29ac43336234'
        // Git Repository
        GIT_REPOSITORY = 'https://github.com/fabriziogaliano/'
        GIT_REPO_CRED_ID = 'aad8cb5b-ddd8-47e3-a8d4-b9f128cf3fd5'
        // Deploy Env
        DEPLOY_SSH_DEV_TARGET = 'root@192.168.0.107'
        DEPLOY_SSH_PROD_TARGET = 'root@192.168.0.107'
        DEPLOY_SSH_DEFAULT_PATH = '/docker'
        DEPLOY_SSH_CUSTOM_PATH = null

    }

    // Git checkout

    stages {
        stage('Git Checkout') {
            agent any
            steps {
                echo "--------------------------------------------------------------"
                echo "----------------------> Project Update <----------------------"
                echo "--------------------------------------------------------------"
                checkout([$class: 'GitSCM', branches: [[name: '*/${GIT_REF}']], doGenerateSubmoduleConfigurations: false, extensions: [], submoduleCfg: [], userRemoteConfigs: [[credentialsId: 'aad8cb5b-ddd8-47e3-a8d4-b9f128cf3fd5', url: 'https://github.com/fabriziogaliano/${JOB_NAME}.git']]])
                echo "----------------------> Project Updated <---------------------"
            }
        }

        // Build Docker image

        stage('Docker Build') {
            steps {
                echo "-------------------------------------------------------------"
                echo "----------------------> Project Build <----------------------"
                echo "-------------------------------------------------------------"
                dockerBuild()
                echo "----------------------> Project Builded <--------------------"
            }
        }

        // Tag/Push docker images, conditional steps to check if Dev/prod environment

        stage('Docker Tag/Push') {
            steps {
                script {
                    if (env.GIT_REF == 'develop') {
                        echo "---------------------------------------------------------------"
                        echo "----------------------> Docker Tag/Push <----------------------"
                        echo "---------------------------------------------------------------"
                        dockerTag()
                        dockerPush()
                        echo "----------------------> Docker Tag/Push OK <-------------------"
                        cleanUp()
                        echo "---------------------------------------------------------------------------------"
                        echo "----------------------> Old images removed from CI Server <----------------------"
                        echo "---------------------------------------------------------------------------------"
                    } else {
                        echo "-------------------------------------------------------------------"
                        echo "----------------------> Docker AWS Tag/Push <----------------------"
                        echo "-------------------------------------------------------------------"
                        dockerAwsTag()
                        dockerAwsPush()
                        echo "----------------------> Docker AWS Tag/Push OK <-------------------"
                        cleanAwsUp()
                        echo "---------------------------------------------------------------------------------"
                        echo "----------------------> Old images removed from CI Server <----------------------"
                        echo "---------------------------------------------------------------------------------"
                    }
                }
            }
        }

        // Project Deploy, conditional steps to check if Dev/prod environment

        stage('Deploy') {
            steps {
                    deployInf()
                }
            }

    }
}

// Main functions

def deployInf() {
    if ( env.DEPLOY_ENV == 'dev' ) {

        DEPLOY_SSH_TARGET = 'root@192.168.0.107'
        DEPLOY_ENV = 'DEVELOPMENT!'
      
        echo "---------------> Deploy Infrastructure ------> ${DEPLOY_ENV}"

        echo "-------------------------------------------------------"
        echo "----------------------> Deploy! <----------------------"
        echo "-------------------------------------------------------"
        deploy(DEPLOY_SSH_TARGET)
        echo "------> Deploy OK to ${DEPLOY_ENV} Environment <-------"
    } 
    
    else {

        DEPLOY_SSH_TARGET = 'root@192.168.0.107'
        DEPLOY_ENV = 'PRODUCTION!'

        echo "---------------> Deploy Infrastructure ------> ${DEPLOY_ENV}"

        echo "-------------------------------------------------------"
        echo "----------------------> Deploy! <----------------------"
        echo "-------------------------------------------------------"
        deploy(DEPLOY_SSH_TARGET)
        echo "------> Deploy OK to ${DEPLOY_ENV} Environment <-------"
    }
}

def dockerBuild() {
    node {
        sh 'docker build -t ${DOCKER_IMAGE_BUILD_NAME}/${JOB_NAME}:${GIT_REF} .'
    }
}

def dockerTag() {
    node {
        sh 'docker tag ${DOCKER_IMAGE_BUILD_NAME}/${JOB_NAME}:${GIT_REF} ${DOCKER_REGISTRY}/${JOB_NAME}:latest'
        sh 'docker tag ${DOCKER_IMAGE_BUILD_NAME}/${JOB_NAME}:${GIT_REF} ${DOCKER_REGISTRY}/${JOB_NAME}:${GIT_REF}'
    }
}

def dockerAwsTag() {
    node {
        sh 'docker tag ${DOCKER_IMAGE_BUILD_NAME}/${JOB_NAME}:${GIT_REF} ${DOCKER_AWS_REGISTRY}/${JOB_NAME}:latest'
        sh 'docker tag ${DOCKER_IMAGE_BUILD_NAME}/${JOB_NAME}:${GIT_REF} ${DOCKER_AWS_REGISTRY}/${JOB_NAME}:${GIT_REF}'
    }
}

def dockerPush() {
    node {
        withDockerRegistry(credentialsId: '655afa6d-5a19-4f15-97ce-29ac43336234', url: 'https://${DOCKER_REGISTRY}') {
        sh 'docker push ${DOCKER_REGISTRY}/${JOB_NAME}:${GIT_REF}'
        sh 'docker push ${DOCKER_REGISTRY}/${JOB_NAME}:latest'
    }
    }
}

def dockerAwsPush() {
    node {

        env.AWS_ECR_LOGIN = 'true'

        docker.withRegistry('https://${DOCKER_AWS_REGISTRY}', 'ecr:eu-west-1:aws_registry_credential') {
        docker.image('${DOCKER_AWS_REGISTRY}/${JOB_NAME}').push('latest')
        docker.image('${DOCKER_AWS_REGISTRY}/${JOB_NAME}').push('${GIT_REF}')
        }
    }
}

def cleanUp() {
    node {
        sh 'docker rmi ${DOCKER_REGISTRY}/${JOB_NAME}:${GIT_REF} '
        sh 'docker rmi ${DOCKER_REGISTRY}/${JOB_NAME}:latest '
        sh 'docker rmi ${DOCKER_IMAGE_BUILD_NAME}/${JOB_NAME}:${GIT_REF} '
    }
}

def cleanAwsUp() {
    node {
        sh 'docker rmi ${DOCKER_AWS_REGISTRY}/${JOB_NAME}:${GIT_REF} '
        sh 'docker rmi ${DOCKER_AWS_REGISTRY}/${JOB_NAME}:latest'
        sh 'docker rmi ${DOCKER_IMAGE_BUILD_NAME}/${JOB_NAME}:${GIT_REF} '
    }
}

def deploy(DEPLOY_SSH_TARGET) {
    if (env.DEPLOY_MODE == "docker-compose") {
        node {
            sh 'ssh -T -o StrictHostKeyChecking=no ${DEPLOY_SSH_TARGET} docker-compose -f ${DEPLOY_SSH_DEFAULT_PATH}/${DEPLOY_SSH_CUSTOM_PATH}${JOB_NAME}/docker-compose.yml up -d --force-recreate'
            echo "Deployed with DOCKER-COMPOSE"
        } 
    } else {
        node {
            echo "ssh -T -o StrictHostKeyChecking=no ${DEPLOY_SSH_TARGET} docker stack up -c ${DEPLOY_SSH_DEFAULT_PATH}/${DEPLOY_SSH_CUSTOM_PATH}${JOB_NAME}/docker-compose.yml --with-registry-auth stack_${DOCKER_STACK_NAMESPACE}${JOB_NAME}"
            echo "Deployed with SWARM mode"
        }
    }
}