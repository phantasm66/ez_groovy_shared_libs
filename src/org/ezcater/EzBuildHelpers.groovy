package org.ezcater
import static groovy.io.FileType.FILES

class EzBuildHelpers implements Serializable {
    def steps
    EzBuildHelpers(steps) {this.steps = steps}

    def commitId() {
        def gitCommitIdFile = new File('GIT_COMMIT')

        /* check if we've done this already */
        if (!gitCommitIdFile.exists()) {
            steps.sh "git rev-parse HEAD > ${gitCommitIdFile}"
        }

        def gitCommitId = gitCommitIdFile.text.take(10)
        return gitCommitId
    }

    def specificRepoFilesChanged() {
        def results = false
        def gitChangeSetFile = new File('CHANGE_SET')
        def localFiles = ['Dockerfile', 'Jenkinsfile', 'docker-entrypoint.sh', 'docker-compose.test.yml']

        steps.sh "git diff-tree --no-commit-id --name-only -r ${this.commitId()} > CHANGE_SET"
        def changeSet = gitChangeSetFile.text.tokenize()

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
        if (this.specificRepoFilesChanged()) {
            /* NOTE: will need to change docker hub account from phantasm66 to ezcater eventually */

            steps.echo "Building docker image and tagging it: phantasm66/${appName}:${this.commitId()}"
            steps.sh "/usr/bin/docker build -t phantasm66/${appName}:${this.commitId()} ."

            /* set $IMAGE_TAG env var so we use this brand new image */
            steps.sh "export IMAGE_TAG=phantasm66/${appName}:${this.commitId()}"
            steps.sh "/usr/bin/docker-compose -f docker-compose.tests.yml config"

            steps.echo "Launching app locally from docker-compose.test.yml"
            steps.sh "/usr/bin/docker-compose -f docker-compose.tests.yml up -d"

            steps.echo "Running tests against the app"
            /* NEED TO RESEARCH THE LOGISTICS OF *HOW* TO RUN THE RSPEC/ETC TESTS AGAINST THIS APP LOCALLY */

            steps.echo "Bringing down locally composed app"
            steps.sh "/usr/bin/docker-compose -f docker-compose.tests.yml down"

            steps.echo "Authenticating w/ docker registry and pushing new image"
            steps.sh "/usr/bin/docker login -u ${dockerHubUser} -p ${dockerHubPass}"
            steps.sh "/usr/bin/docker push phantasm66/${appName}:${this.commitId()}"
        } else {
            steps.echo "No build related files have changed... skipping image build stage"
        }
    }

    def executeDeploy(String namespace) {
        /* prepare the kube deploy YAMLs from the ezdeploy.yml file by shelling out to ezdeploy.rb */
        /* deploy kube rendered YAMLs to kube (including correct namespace for env (prd or staging)) - also using ezdeploy.rb */
    }

}
