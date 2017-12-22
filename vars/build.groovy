def call() {

  /*
    load this library implicitly
  */

  node('build-pod') {

    withCredentials([[$class: 'UsernamePasswordMultiBinding',
                       credentialsId: 'docker-hub-credentials',
                       usernameVariable: 'dockerHubUser',
                       passwordVariable: 'dockerHubPass']]) {

      container('jnlp') {
        def commitId
        def changeSet
        def appName = env.JOB_NAME

        def localFiles = [
          'Dockerfile',
          'Jenkinsfile',
          'docker-entrypoint.sh',
          'docker-compose.test.yml'
        ]

        stage('clone') {
          git(url: "https://github.com/phantasm66/${appName}.git")

          sh("git rev-parse HEAD > GIT_COMMIT")
          commitId = readFile('GIT_COMMIT').take(10)

          sh("git diff-tree --no-commit-id --name-only -r ${commitId} > CHANGE_SET")
          changeSet = readFile('CHANGE_SET').tokenize()
        }

        stage('build') {
          def testFiles = findFiles(glob: '**/**/*.rb')
          testFiles.each { testFile -> localFiles.add(testFile.path) }

          for (String localFile: localFiles) {
            if (changeSet.contains(localFile)) {
              echo("Build related file has changed: ${localFile} - running all image builder steps")

              echo("Building docker image and tagging it: phantasm66/${appName}:${commitId}")
              sh("/usr/bin/docker build -t phantasm66/${appName}:${commitId} .")

              echo("Setting IMAGE_TAG env var for docker-compose")
              sh("export IMAGE_TAG=phantasm66/${appName}:${commitId}")
              sh("/usr/bin/docker-compose -f docker-compose.tests.yml config")

              /* DEBUGGING */
              sh("cat docker-compose.tests.yml")


              echo("Launching app and all dependencies locally using docker-compose.test.yml")
              sh("/usr/bin/docker-compose -f docker-compose.tests.yml up -d")
              echo("Letting things percolate for a few before kicking off our tests")
              sleep(30)

              echo("Running tests against the locally spawned app container set")
              /* NEED TO RESEARCH THE LOGISTICS OF *HOW* TO RUN THE RSPEC/ETC TESTS AGAINST THIS APP LOCALLY */

              echo("Bringing down locally spawned app container set")
              sh("/usr/bin/docker-compose -f docker-compose.tests.yml down")

              echo("Authenticating w/ private docker registry and pushing phantasm66/${appName}:${commitId}")
              sh("/usr/bin/docker login -u ${dockerHubUser} -p ${dockerHubPass}")
              sh("/usr/bin/docker push phantasm66/${appName}:${commitId}")

              /* we only need to do this once (eg: if mutliple files changed) */
              break
            }
          }
        }

        stage('deploy') {
          echo("Deploying phantasm66/${appName}:${commitId} to production Kubernetes")
          /* add ezdeploy prepare and deploy steps here */
        }
      }
    }
  }
}
