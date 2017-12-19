def call() {
    /* Git SHA commit id (for tagging) */
    sh 'git rev-parse HEAD > GIT_COMMIT'
    imageTag = readFile('GIT_COMMIT').take(10)

    /* Filenames changed in commit - (ref: buildStageFilesChanged) */
    sh "git diff-tree --no-commit-id --name-only -r ${imageTag} > CHANGE_SET"
    def changeSet = readFile('CHANGE_SET').tokenize()

    def testFiles = findFiles(glob: '**/**/*.rb')
    testFiles.each { testFile -> localFiles.add("tests/${testFile.name}") }

    /* Set buildStageFilesChanged */
    localFiles.each { localFile ->
        if (changeSet.contains(localFile)) {
            buildStageFilesChanged = true
            echo("Build related file has changed: ${localFile} - running all image builder steps")
        }
    }
}
