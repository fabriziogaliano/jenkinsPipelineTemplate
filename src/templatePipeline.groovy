// Pipeline

def DEPLOY_SSH_CUSTOM_PATH = ""
def DOCKER_CUSTOM_OPT = ""
def DOCKER_STACK = ""

// def env = build.getEnvironment()
// def gitCommit = env['GIT_COMMIT']
// def shortGitCommit = gitCommit[0..6]

// def sgc = new ParametersAction([
//   new StringParameterValue("SHORT_GIT_COMMIT", shortGitCommit)
// ])

pipeline {

    agent any

    environment {
        // Assets and scripts
        ASSETS_DIR = "/var/jenkins_home/assets"

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
<<<<<<< HEAD
        DOCKER_REGISTRY_NS = "devcoon/"
=======
        DOCKER_REGISTRY_NS = "zombox/"
>>>>>>> 2fafbc8c4cd7239d2c98869d19de8cf255f5fa5a

        // Git Repository
        GIT_REPOSITORY = 'https://github.com/fabriziogaliano'
        GIT_REPO_CRED_ID = 'aad8cb5b-ddd8-47e3-a8d4-b9f128cf3fd5'
        // SHORT_GIT_COMMIT = `echo "${GIT_COMMIT}" | cut -c1-8`

        // Deploy Env
<<<<<<< HEAD
        DEPLOY_SSH_DEV_TARGET = 'ssh -T -o StrictHostKeyChecking=no root@192.168.0.108' // concat more string "ssh -T -o StrictHostKeyChecking=no root@x.x.x.x" if you pass trough more then 1 host
        DEPLOY_SSH_PROD_TARGET = 'ssh -T -o StrictHostKeyChecking=no root@192.168.0.108' // concat more string "ssh -T -o StrictHostKeyChecking=no root@x.x.x.x" if you pass trough more then 1 host
        DEPLOY_SSH_DEFAULT_PATH = '/mnt/nfs/docker'
=======
        DEPLOY_SSH_DEV_TARGET = 'ssh -T -o StrictHostKeyChecking=no root@192.168.0.108'
        DEPLOY_SSH_PROD_TARGET = 'ssh -T -o StrictHostKeyChecking=no root@192.168.0.108'
        DEPLOY_SSH_DEMO_TARGET = 'ssh -T -o StrictHostKeyChecking=no root@192.168.0.108'
        DEPLOY_SSH_DEFAULT_PATH = '/docker'
>>>>>>> 2fafbc8c4cd7239d2c98869d19de8cf255f5fa5a

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

                // checkout scm: [$class: 'GitSCM', 
                // userRemoteConfigs: [[url: 'https://github.com/fabriziogaliano/${JOB_NAME}.git', 
                // credentialsId: 'aad8cb5b-ddd8-47e3-a8d4-b9f128cf3fd5']], branches: [[name: v0.0.1]]],
                // poll: false

                checkout([$class: 'GitSCM', 
                branches: [[name: "*/${GIT_REF}"]], 
                doGenerateSubmoduleConfigurations: false, 
                extensions: [], 
                submoduleCfg: [], 
                userRemoteConfigs: [[
                credentialsId: "${GIT_REPO_CRED_ID}", 
                url: "${GIT_REPOSITORY}/${JOB_NAME}.git"]]])

                // checkout([$class: 'GitSCM', 
                // branches: [[name: '${GIT_REF}']], 
                // doGenerateSubmoduleConfigurations: false, 
                // extensions: [], 
                // submoduleCfg: [], 
                // userRemoteConfigs: [[
                // credentialsId: 'aad8cb5b-ddd8-47e3-a8d4-b9f128cf3fd5', 
                // refspec: '+refs/heads/*:refs/remotes/*', 
                // url: 'https://github.com/fabriziogaliano/${JOB_NAME}.git']]])

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
                    if (env.GIT_REF == 'master') {
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
<<<<<<< HEAD
        DEPLOY_ENV = 'PRODUCTION!'
=======
>>>>>>> 2fafbc8c4cd7239d2c98869d19de8cf255f5fa5a

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
        sh "${ASSETS_DIR}/npmlogin.sh ${NPM_REG_URL} ${NPM_CRED_USER} ${NPM_CRED_PASSWD} ${NPM_REG_MAIL}"
        }
}

def dockerBuild() {
<<<<<<< HEAD
    node {
        sh "docker build -t ${DOCKER_IMAGE_BUILD_NAME}/${JOB_NAME}:${GIT_REF} ."
=======
    switch(env.DEPLOY_ENV) {
        case "dev":
            node {
                sh "docker build --build-arg buildenv=dev ${DOCKER_CUSTOM_OPT} -t ${DOCKER_IMAGE_BUILD_NAME}/${JOB_NAME}:${GIT_REF} ."
            }
        break
        case "demo":
            node {
                sh "docker build --build-arg buildenv=demo ${DOCKER_CUSTOM_OPT} -t ${DOCKER_IMAGE_BUILD_NAME}/${JOB_NAME}:${GIT_REF} ."
            }
        break
        // case "prod":
        default:
            node {
                sh "docker build --build-arg buildenv=prod ${DOCKER_CUSTOM_OPT} -t ${DOCKER_IMAGE_BUILD_NAME}/${JOB_NAME}:${GIT_REF} ."
            }
>>>>>>> 2fafbc8c4cd7239d2c98869d19de8cf255f5fa5a
    }
}

def dockerTag() {
    node {
        sh "docker tag ${DOCKER_IMAGE_BUILD_NAME}/${JOB_NAME}:${GIT_REF} ${DOCKER_REGISTRY}/${DOCKER_REGISTRY_NS}${JOB_NAME}:latest"
        sh "docker tag ${DOCKER_IMAGE_BUILD_NAME}/${JOB_NAME}:${GIT_REF} ${DOCKER_REGISTRY}/${DOCKER_REGISTRY_NS}${JOB_NAME}:${GIT_REF}"
        // sh 'docker tag ${DOCKER_IMAGE_BUILD_NAME}/${JOB_NAME}:${GIT_REF} ${DOCKER_REGISTRY}/${JOB_NAME}:${SHORT_GIT_COMMIT}'
    }
}

def dockerAwsTag() {
    node {
        sh "docker tag ${DOCKER_IMAGE_BUILD_NAME}/${JOB_NAME}:${GIT_REF} ${DOCKER_AWS_REGISTRY}/${JOB_NAME}:latest"
        sh "docker tag ${DOCKER_IMAGE_BUILD_NAME}/${JOB_NAME}:${GIT_REF} ${DOCKER_AWS_REGISTRY}/${JOB_NAME}:${GIT_REF}"
        // sh 'docker tag ${DOCKER_IMAGE_BUILD_NAME}/${JOB_NAME}:${GIT_REF} ${DOCKER_AWS_REGISTRY}/${JOB_NAME}:${SHORT_GIT_COMMIT}'
    }
}

def dockerPush() {
    node {
        withDockerRegistry(credentialsId: "${DOCKER_REGISTRY_CRED_ID}", url: "https://${DOCKER_REGISTRY}") {
        sh "docker push ${DOCKER_REGISTRY}/${DOCKER_REGISTRY_NS}${JOB_NAME}:${GIT_REF}"
        sh "docker push ${DOCKER_REGISTRY}/${DOCKER_REGISTRY_NS}${JOB_NAME}:latest"
        // sh 'docker push ${DOCKER_REGISTRY}/${JOB_NAME}:${SHORT_GIT_COMMIT}'
        }
    }
}

def dockerAwsPush() {
    node {

        env.AWS_ECR_LOGIN = 'true'

        docker.withRegistry("https://${DOCKER_AWS_REGISTRY}", 'ecr:eu-west-1:aws_registry_credential') {
        sh "docker push ${DOCKER_AWS_REGISTRY}/${JOB_NAME}:${GIT_REF}"
        sh "docker push ${DOCKER_AWS_REGISTRY}/${JOB_NAME}:latest" 
        // docker.image("${DOCKER_AWS_REGISTRY}/${JOB_NAME}").push("latest")
        // docker.image("${DOCKER_AWS_REGISTRY}/${JOB_NAME}").push("${GIT_REF}")
        // docker.image('${DOCKER_AWS_REGISTRY}/${JOB_NAME}').push('${SHORT_GIT_COMMIT}')
        }
    }
}

def cleanUp() {
    node {
        sh "docker rmi ${DOCKER_REGISTRY}/${DOCKER_REGISTRY_NS}${JOB_NAME}:${GIT_REF}"
        sh "docker rmi ${DOCKER_REGISTRY}/${DOCKER_REGISTRY_NS}${JOB_NAME}:latest"
        sh "docker rmi ${DOCKER_IMAGE_BUILD_NAME}/${JOB_NAME}:${GIT_REF}"
        // sh 'docker rmi ${DOCKER_IMAGE_BUILD_NAME}/${JOB_NAME}:${SHORT_GIT_COMMIT}'
    }
}

def cleanAwsUp() {
    node {
        sh "docker rmi ${DOCKER_AWS_REGISTRY}/${JOB_NAME}:${GIT_REF}"
        sh "docker rmi ${DOCKER_AWS_REGISTRY}/${JOB_NAME}:latest"
        sh "docker rmi ${DOCKER_IMAGE_BUILD_NAME}/${JOB_NAME}:${GIT_REF}"
        // sh 'docker rmi ${DOCKER_IMAGE_BUILD_NAME}/${JOB_NAME}:${SHORT_GIT_COMMIT}'
    }
}

def deploy(DEPLOY_SSH_TARGET) {
    if (env.DEPLOY_MODE == "docker-compose") {
        node {
<<<<<<< HEAD
            sh "${DEPLOY_SSH_TARGET} docker-compose -f ${DEPLOY_SSH_DEFAULT_PATH}/${DEPLOY_SSH_CUSTOM_PATH}/${JOB_NAME}/docker-compose.yml up -d --force-recreate"
=======
            sh "${DEPLOY_SSH_TARGET} docker-compose -f ${DEPLOY_SSH_DEFAULT_PATH}/${DEPLOY_SSH_CUSTOM_PATH}${JOB_NAME}.yml up -d --force-recreate"
>>>>>>> 2fafbc8c4cd7239d2c98869d19de8cf255f5fa5a
            echo "Deployed with DOCKER-COMPOSE"
        } 
    } else {
        node {
<<<<<<< HEAD
            sh "${DEPLOY_SSH_TARGET} docker stack up -c ${DEPLOY_SSH_DEFAULT_PATH}/${DEPLOY_SSH_CUSTOM_PATH}/${JOB_NAME}/docker-compose.yml --with-registry-auth stack_${DOCKER_STACK_NAMESPACE}${JOB_NAME}"
=======
            sh "${DEPLOY_SSH_TARGET} docker stack up -c ${DEPLOY_SSH_DEFAULT_PATH}/${DEPLOY_SSH_CUSTOM_PATH}/${DEPLOY_ENV}/${JOB_NAME}.yml --with-registry-auth ${DOCKER_STACK}_${DEPLOY_ENV}_${JOB_NAME}"
>>>>>>> 2fafbc8c4cd7239d2c98869d19de8cf255f5fa5a
            echo "Deployed with SWARM mode"
        }
    }
}
