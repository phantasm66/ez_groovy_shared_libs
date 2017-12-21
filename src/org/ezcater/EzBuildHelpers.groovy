package org.ezcater
import static groovy.io.FileType.FILES

class EzBuildHelpers implements Serializable {
    def steps
    EzBuildHelpers(steps) {this.steps = steps}

    /* */
    /* NOTE: when this is ready, we need to change all occurences of phantasm66 to ezcater in this file */
    /* */

    def commitId() {
        def id = "git rev-parse HEAD".execute().text.trim().take(10)
        return id
    }

    def specificRepoFilesChanged(String gitCommitId) {
        def results = false
        def localFiles = ['Dockerfile', 'Jenkinsfile', 'docker-entrypoint.sh', 'docker-compose.test.yml']
        def changeSet = "git diff-tree --no-commit-id --name-only -r ${gitCommitId}".execute().text.tokenize()

        new File('tests').eachFileRecurse(FILES) { foundFile ->
            if (foundFile.name.endsWith('.rb')) {
                localFiles.add(foundFile.getPath())
            }
        }

        localFiles.each { localFile ->
            if (changeSet.contains(localFile)) {
                results = true
                steps.echo("Build related file has changed: ${localFile} - running all image builder steps")
            }
        }
        return results
    }

    def buildDockerImage(String appName, String dockerHubUser, String dockerHubPass) {
        String gitCommitId = this.commitId()

        if (this.specificRepoFilesChanged(gitCommitId)) {
            steps.echo "Building docker image and tagging it: phantasm66/${appName}:${gitCommitId}"
            steps.sh "/usr/bin/docker build -t phantasm66/${appName}:${gitCommitId} ."

            /* set $IMAGE_TAG env var so we use this brand new image */
            steps.sh "export IMAGE_TAG=phantasm66/${appName}:${gitCommitId}"
            steps.sh "/usr/bin/docker-compose -f docker-compose.tests.yml config"

            steps.echo "Launching app locally from docker-compose.test.yml"
            steps.sh "/usr/bin/docker-compose -f docker-compose.tests.yml up -d"

            steps.echo "Running tests against the app"
            /* NEED TO RESEARCH THE LOGISTICS OF *HOW* TO RUN THE RSPEC/ETC TESTS AGAINST THIS APP LOCALLY */

            steps.echo "Bringing down locally composed app"
            steps.sh "/usr/bin/docker-compose -f docker-compose.tests.yml down"

            steps.echo "Authenticating w/ docker registry and pushing new image"
            steps.sh "/usr/bin/docker login -u ${dockerHubUser} -p ${dockerHubPass}"
            steps.sh "/usr/bin/docker push phantasm66/${appName}:${gitCommitId}"
        } else {
            steps.echo "No build related files have changed... skipping image build stage"
        }
    }

    def executeDeploy(String kubeNamespace) {
        /* prepare the kube deploy YAMLs from the ezdeploy.yml file by shelling out to ezdeploy.rb */
        /* deploy kube rendered YAMLs to kube (including correct namespace for env (prd or staging)) - also using ezdeploy.rb */
    }

}
