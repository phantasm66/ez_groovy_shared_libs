// vars/pipelineConditional.groovy
def call(String filename) {
  def changeLogSets = currentBuild.rawBuild.changeSets

  for (int i = 0; i < changeLogSets.size(); i++) {
    def entries = changeLogSets[i].items

    for (int j = 0; j < entries.length; j++) {
      //def entry = entries[j]
      //echo "${entry.commitId} by ${entry.author} on ${new Date(entry.timestamp)}: ${entry.msg}"
      //def files = new ArrayList(entry.affectedFiles)
      def files = new ArrayList(entries[j].affectedFiles)

      for (int k = 0; k < files.size(); k++) {
        def file = files[k]

        // FOR DEBUGGING:
        echo(file.path)
        echo(filename)

        // return true if $filename changed
        if (file.path.indexOf(filename) >= 0) {
          return true
        }
      }
    }
  }
  return false
}

