def call() {
  /*
    **********************************
    ** LOAD THIS LIBRARY IMPLICITLY **
    **********************************
    This entire library is a pipeline for building, testing and deploying apps as
    containers in a kubernetes cluster (a few configuration lines in the Jenkins UI).
    This library must exist in a git repo under a main "vars/" directory. It will
    become available to all Jenkinsfile jobs once configured under:

        Manage Jenkins => Global Pipeline Libraries...

    An app's Jenkinsfile need only call the following to run this pipeline:

        build()

    Everything specific to the app being built will be set dynamically.

    All configuration of the build slave (docker image to use, kube namespace to
    launch the build slave containers in, k8s build slave pod instance and resource
    limits, etc..) can be made below in the podTemplate or containerTemplate blocks
    (ref: https://jenkins.io/doc/pipeline/steps/kubernetes/)

    ******************************************************
    ** REQUIRED PLUGINS (install on the jenkins master) **
    ******************************************************
      - kubernetes
          ~dynamically provisions Jenkins build slaves in a k8s cluster

      - workflow-aggregator
          ~the "pipeline" plugin

      - github-branch-source
          ~scans an org's github repos and creates jobs from all discovered Jenkinsfiles
          ~jobs are named after the app's git repo
  */

  def commitId
  def changeSet

  def appName = env.JOB_NAME
  def orgName = 'phantasm66'              /* change to 'ezcater' eventually */
  def kubernetesNamespace = 'sandbox'     /* change to whatever namespace we end up using (jenkins?) */

  def localFiles = [
    'Dockerfile',
    'Jenkinsfile',
    'docker-entrypoint.sh',
    'docker-compose.test.yml'
  ]

  podTemplate(
    name: 'jnlp',
    label: 'build-pod',
    namespace: kubernetesNamespace,
    instanceCap: 5,
    idleMinutes: 5,
    nodeUsageMode: 'EXCLUSIVE',

    containers: [
      containerTemplate(
        name: 'jnlp',
        image: "${orgName}/ez_jnlp_slave",
        alwaysPullImage: true,
        workingDir: '/home/jenkins',
        ttyEnabled: true
      )
    ],

    volumes: [
      hostPathVolume(
        hostPath: '/var/run/docker.sock',
        mountPath: '/var/run/docker.sock'
      )
    ]) {

    node('build-pod') {

      withCredentials([[$class: 'UsernamePasswordMultiBinding',
                         credentialsId: 'docker-hub-credentials',
                         usernameVariable: 'dockerHubUser',
                         passwordVariable: 'dockerHubPass']]) {

        container('jnlp') {

          stage('clone') {
            git(url: "https://github.com/${orgName}/${appName}.git")

            sh("git rev-parse HEAD > GIT_COMMIT")
            commitId = readFile('GIT_COMMIT').take(10)

            sh("git diff-tree --no-commit-id --name-only -r ${commitId} > CHANGE_SET")
            changeSet = readFile('CHANGE_SET').tokenize()
          }

          stage('build') {
            String imageTag = "${orgName}/${appName}:${commitId}"

            def testFiles = findFiles(glob: '**/**/*.rb')
            testFiles.each { testFile -> localFiles.add(testFile.path) }

            for (String localFile: localFiles) {
              if (changeSet.contains(localFile)) {
                echo("Build related file has changed: ${localFile} - running all image builder steps")

                echo("Building docker image and tagging it: ${imageTag}")
                sh("/usr/bin/docker build -t ${imageTag} .")

                echo("Setting IMAGE_TAG .env file var for docker-compose file interpolation")
                sh("echo 'IMAGE_TAG=${imageTag}' > .env")
                sh("/usr/bin/docker-compose -f docker-compose.tests.yml config")

                echo("Launching app and all dependencies locally using docker-compose.test.yml")
                sh("/usr/bin/docker-compose -f docker-compose.tests.yml up -d")
                echo("Letting things percolate for a few before kicking off our tests")
                sleep(30)

                echo("Running tests against the locally spawned app container set")
                /* NEED TO RESEARCH THE LOGISTICS OF *HOW* TO RUN THE RSPEC/ETC TESTS AGAINST THIS APP LOCALLY */

                echo("Bringing down locally spawned app container set")
                sh("/usr/bin/docker-compose -f docker-compose.tests.yml down")

                echo("Authenticating w/ private docker registry and pushing ${orgName}/${appName}:${commitId}")
                sh("/usr/bin/docker login -u ${dockerHubUser} -p ${dockerHubPass}")
                sh("/usr/bin/docker push ${orgName}/${appName}:${commitId}")

                /* we only need to do this once (eg: if mutliple files changed) */
                break
              }
            }
          }

          stage('deploy') {
            echo("Deploying ${orgName}/${appName}:${commitId} to production Kubernetes")
            /* add ezdeploy prepare and deploy steps here */
          }
        }
      }
    }
  }
}
