// Pipeline v0.0.1-b

def DEPLOY_SSH_CUSTOM_PATH = ""
def DOCKER_CUSTOM_OPT = ""
def DOCKER_STACK = ""

pipeline {

    agent any

    environment {
        // Assets and scripts
        ASSETS_DIR = "/docker/tools"

        // Default Docker build image name
        DOCKER_IMAGE_BUILD_NAME = 'ciserver'
    
        // NPM Registry
        NPM_REG_URL = "https://npm.zombox.it"
        NPM_REG_MAIL = "fgaliano@zombox.it"
        NPM_CRED_ID = "npm_registry_cred"

        // Docker Registry
        DOCKER_REGISTRY = 'registry.zombox.it'
        DOCKER_AWS_REGISTRY = '628245238960.dkr.ecr.eu-west-1.amazonaws.com'
        DOCKER_REGISTRY_CRED_ID = '655afa6d-5a19-4f15-97ce-29ac43336234'
        DOCKER_REGISTRY_NS = "zombox/"

        // Git Repository
        GIT_REPOSITORY = 'https://github.com/fabriziogaliano'
        GIT_REPO_CRED_ID = 'aad8cb5b-ddd8-47e3-a8d4-b9f128cf3fd5'
        // SHORT_GIT_COMMIT = `echo "${GIT_COMMIT}" | cut -c1-8`

        // Deploy Env
        DEPLOY_SSH_DEV_TARGET = 'ssh -T -o StrictHostKeyChecking=no root@192.168.0.108'
        DEPLOY_SSH_PROD_TARGET = 'ssh -T -o StrictHostKeyChecking=no root@192.168.0.108'
        DEPLOY_SSH_DEMO_TARGET = 'ssh -T -o StrictHostKeyChecking=no root@192.168.0.108'
        DEPLOY_SSH_DEFAULT_PATH = '/docker'

        // DEPLOY_SSH_CUSTOM_PATH = null
    }

    // Git checkout

    stages {
        stage('Git Checkout') {
            agent any
            steps {
                echo "--------------------------------------------------------------"
                echo "----------------------> Project Update <----------------------"
                echo "--------------------------------------------------------------"

                initCheckout()

                echo "----------------------> Project Updated <---------------------"
            }
        }

        // Build Docker image

        stage('Docker Build') {
            steps {
                script {
                    echo "---------------------------------------------------------"
                    echo "----------------------> NPM LOGIN <----------------------"
                    echo "---------------------------------------------------------"
                    npmLogin()
                    echo "-------------------------------------------------------------"
                    echo "----------------------> Project Build <----------------------"
                    echo "-------------------------------------------------------------"
                    dockerBuild()
                    echo "----------------------> Project Builded <--------------------"
                }
            }
        }

        // Tag/Push docker images, conditional steps to check if Dev/prod environment

        stage('Docker Tag/Push') {
            steps {
                script {
                    if (env.GIT_REV == 'master') {
                        echo "-------------------------------------------------------------------"
                        echo "----------------------> Docker AWS Tag/Push <----------------------"
                        echo "-------------------------------------------------------------------"
                        // Internal Registry
                        dockerTag()
                        dockerPush()
                        // External Registry
                        dockerAwsTag()
                        dockerAwsPush()
                        echo "----------------------> Docker AWS Tag/Push OK <-------------------"
                        cleanAwsUp()
                        echo "---------------------------------------------------------------------------------"
                        echo "----------------------> Old images removed from CI Server <----------------------"
                        echo "---------------------------------------------------------------------------------"
                    } else {
                        echo "---------------------------------------------------------------"
                        echo "----------------------> Docker Tag/Push <----------------------"
                        echo "---------------------------------------------------------------"
                        // Internal Registry
                        dockerTag()
                        dockerPush()
                        echo "----------------------> Docker Tag/Push OK <-------------------"
                        cleanUp()
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

// Functions

def initCheckout() {
        checkout([$class: 'GitSCM', 
        branches: [[name: "${GIT_REV}"]],
        poll: false,
        userRemoteConfigs: [[
        credentialsId: "${GIT_REPO_CRED_ID}", 
        url: "${GIT_REPOSITORY}/${JOB_NAME}.git"]]])
}

def deployInf() {
    if ( env.DEPLOY_ENV == 'dev' ) {

        DEPLOY_SSH_TARGET = "${DEPLOY_SSH_DEV_TARGET}"
      
        echo "---------------> Deploy Infrastructure ------> DEVELOPMENT!"

        echo "-------------------------------------------------------"
        echo "----------------------> Deploy! <----------------------"
        echo "-------------------------------------------------------"
        deploy(DEPLOY_SSH_TARGET)
        echo "------> Deploy OK to DEVELOPMENT Environment <-------"
    } 

    else {

        DEPLOY_SSH_TARGET = "${DEPLOY_SSH_PROD_TARGET}"

        echo "---------------> Deploy Infrastructure ------> PRODUCTION"

        echo "-------------------------------------------------------"
        echo "----------------------> Deploy! <----------------------"
        echo "-------------------------------------------------------"
        deploy(DEPLOY_SSH_TARGET)
        echo "------> Deploy OK to PRODUCTION Environment <-------"
    }
}

def npmLogin() {
    withCredentials([usernamePassword(credentialsId: "${NPM_CRED_ID}", usernameVariable: "NPM_CRED_USER", passwordVariable: "NPM_CRED_PASSWD")]) {
        sh "${ASSETS_DIR}/npm/npmlogin.sh ${NPM_REG_URL} ${NPM_CRED_USER} ${NPM_CRED_PASSWD} ${NPM_REG_MAIL}"
        }
}

def dockerBuild() {
    switch(env.DEPLOY_ENV) {
        case "dev":
            node {
                sh "docker build \
                --label JENKINS_BUILD_NUMBER=${env.BUILD_NUMBER} \
                --label GIT_REF=${GIT_REV} \
                --label buildenv=dev \
                --build-arg buildenv=dev ${DOCKER_CUSTOM_OPT} \
                -t ${DOCKER_IMAGE_BUILD_NAME}/${JOB_NAME}:${GIT_REV} \
                ."
            }
        break
        case "demo":
            node {
                sh "docker build \
                --label JENKINS_BUILD_NUMBER=${env.BUILD_NUMBER} \
                --label GIT_REF=${GIT_REV} \
                --label buildenv=demo \
                --build-arg buildenv=demo ${DOCKER_CUSTOM_OPT} \
                -t ${DOCKER_IMAGE_BUILD_NAME}/${JOB_NAME}:${GIT_REV} \
                ."
            }
        break
        default:
            node {
                sh "docker build \
                --label JENKINS_BUILD_NUMBER=${env.BUILD_NUMBER} \
                --label GIT_REF=${GIT_REV} \
                --label buildenv=prod \
                --build-arg buildenv=prod ${DOCKER_CUSTOM_OPT} \
                -t ${DOCKER_IMAGE_BUILD_NAME}/${JOB_NAME}:${GIT_REV} \
                ."
            }
    }
}

def dockerTag() {
    node {
        sh "docker tag ${DOCKER_IMAGE_BUILD_NAME}/${JOB_NAME}:${GIT_REV} ${DOCKER_REGISTRY}/${DOCKER_REGISTRY_NS}${JOB_NAME}:latest"
        sh "docker tag ${DOCKER_IMAGE_BUILD_NAME}/${JOB_NAME}:${GIT_REV} ${DOCKER_REGISTRY}/${DOCKER_REGISTRY_NS}${JOB_NAME}:${GIT_REV}"
        // sh 'docker tag ${DOCKER_IMAGE_BUILD_NAME}/${JOB_NAME}:${GIT_REV} ${DOCKER_REGISTRY}/${JOB_NAME}:${SHORT_GIT_COMMIT}'
    }
}

def dockerAwsTag() {
    node {
        sh "docker tag ${DOCKER_IMAGE_BUILD_NAME}/${JOB_NAME}:${GIT_REV} ${DOCKER_AWS_REGISTRY}/${JOB_NAME}:latest"
        sh "docker tag ${DOCKER_IMAGE_BUILD_NAME}/${JOB_NAME}:${GIT_REV} ${DOCKER_AWS_REGISTRY}/${JOB_NAME}:${GIT_REV}"
        // sh 'docker tag ${DOCKER_IMAGE_BUILD_NAME}/${JOB_NAME}:${GIT_REV} ${DOCKER_AWS_REGISTRY}/${JOB_NAME}:${SHORT_GIT_COMMIT}'
    }
}

def dockerPush() {
    node {
        withDockerRegistry(credentialsId: "${DOCKER_REGISTRY_CRED_ID}", url: "https://${DOCKER_REGISTRY}") {
        sh "docker push ${DOCKER_REGISTRY}/${DOCKER_REGISTRY_NS}${JOB_NAME}:${GIT_REV}"
        sh "docker push ${DOCKER_REGISTRY}/${DOCKER_REGISTRY_NS}${JOB_NAME}:latest"
        // sh 'docker push ${DOCKER_REGISTRY}/${JOB_NAME}:${SHORT_GIT_COMMIT}'
        }
    }
}

def dockerAwsPush() {
    node {

        env.AWS_ECR_LOGIN = 'true'

        docker.withRegistry("https://${DOCKER_AWS_REGISTRY}", 'ecr:eu-west-1:aws_registry_credential') {
        sh "docker push ${DOCKER_AWS_REGISTRY}/${JOB_NAME}:${GIT_REV}"
        sh "docker push ${DOCKER_AWS_REGISTRY}/${JOB_NAME}:latest" 
        // docker.image("${DOCKER_AWS_REGISTRY}/${JOB_NAME}").push("latest")
        // docker.image("${DOCKER_AWS_REGISTRY}/${JOB_NAME}").push("${GIT_REV}")
        // docker.image('${DOCKER_AWS_REGISTRY}/${JOB_NAME}').push('${SHORT_GIT_COMMIT}')
        }
    }
}

def cleanUp() {
    node {
        sh "docker rmi ${DOCKER_REGISTRY}/${DOCKER_REGISTRY_NS}${JOB_NAME}:${GIT_REV}"
        sh "docker rmi ${DOCKER_REGISTRY}/${DOCKER_REGISTRY_NS}${JOB_NAME}:latest"
        sh "docker rmi ${DOCKER_IMAGE_BUILD_NAME}/${JOB_NAME}:${GIT_REV}"
        // sh 'docker rmi ${DOCKER_IMAGE_BUILD_NAME}/${JOB_NAME}:${SHORT_GIT_COMMIT}'
    }
}

def cleanAwsUp() {
    node {
        sh "docker rmi ${DOCKER_AWS_REGISTRY}/${JOB_NAME}:${GIT_REV}"
        sh "docker rmi ${DOCKER_AWS_REGISTRY}/${JOB_NAME}:latest"
        sh "docker rmi ${DOCKER_IMAGE_BUILD_NAME}/${JOB_NAME}:${GIT_REV}"
        // sh 'docker rmi ${DOCKER_IMAGE_BUILD_NAME}/${JOB_NAME}:${SHORT_GIT_COMMIT}'
    }
}

def deploy(DEPLOY_SSH_TARGET) {
    if (env.DEPLOY_MODE == "docker-compose") {
        node {
            sh "${DEPLOY_SSH_TARGET} docker-compose \
            -f ${DEPLOY_SSH_DEFAULT_PATH}/${DEPLOY_SSH_CUSTOM_PATH}${JOB_NAME}.yml \
            up -d \
            --force-recreate"
            echo "Deployed with DOCKER-COMPOSE"
        } 
    } else {
        node {
            sh "${DEPLOY_SSH_TARGET} docker stack up \
            -c ${DEPLOY_SSH_DEFAULT_PATH}/${DEPLOY_SSH_CUSTOM_PATH}/${DEPLOY_ENV}/${JOB_NAME}.yml \
            --with-registry-auth \
            ${DOCKER_STACK}_${DEPLOY_ENV}_${JOB_NAME}"
            echo "Deployed with SWARM mode"
        }
    }
}
