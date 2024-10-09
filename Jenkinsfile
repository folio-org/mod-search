buildMvn {
  publishModDescriptor = true
  mvnDeploy = true
  doKubeDeploy = true
  doUploadApidocs = true
  buildNode = 'jenkins-agent-java21'

  doDocker = {
    buildDocker {
      publishMaster = true
      healthChk = false
      healthChkCmd = 'wget --no-verbose --tries=1 --spider http://localhost:8081/admin/health || exit 1'
    }
  }
}
