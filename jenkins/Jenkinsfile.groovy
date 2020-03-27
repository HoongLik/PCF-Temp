pipeline {

    /////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // See documentation here to see what else you can do with pipelines https://jenkins.io/doc/book/pipeline/syntax/
    /////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    
    agent { label 'dubinx2-pcf-slave-kubectl' }

    triggers { pollSCM('* * * * *') }

    options {
        buildDiscarder(logRotator(numToKeepStr: '2')) //Number of run results to keep
        disableConcurrentBuilds() //Ensure only one
        timeout(time: 2, unit: 'MINUTES') //Timeout for the whole job
        retry(0) //Retry on failure
        timestamps() //Prepend all console output with timestamps
    }

    stages {

        stage('pre-build cleanup') {
            steps {
                sh 'if [ -d "$WORKSPACE/.repository" ]; then rm -Rf $WORKSPACE/.repository; fi'
            }
        }

        stage('config map') {
            steps {
                withCredentials([file(credentialsId: 'labs-kubernetes-kubeconfig', variable: 'KUBECONFIG')]) {
                    script {

//                        sh """
//                         curl -LO https://storage.googleapis.com/kubernetes-release/release/v1.15.0/bin/linux/amd64/kubectl
//                         chmod a+x kubectl
//                        """

                        def namespace = ['am-pcf-sep19','sm-pcf-sep19']
                        def maps = ['gui-policies-data-model', 'pcf-condition-data-model']

                        for (int i = 0; i < namespace.size(); i++) {

                            //load the config files into the config maps
                            for (int j = 0; j < maps.size(); j++) {
                                sh """
                                kubectl -n ${namespace[i]} delete configmap ${maps[j]}-config --ignore-not-found=true
                                kubectl -n ${namespace[i]} create configmap ${maps[j]}-config --from-file=${maps[j]}.xml=./${maps[j]}.xml
                                """
                            }

                            /// Delete the relevant pods. When they restart they'll pick up the new config
                            sh """
                            kubectl -n ${namespace[i]} delete pod -l app=am-policies
                            kubectl -n ${namespace[i]} delete pod -l app=sm-policies
                            kubectl -n ${namespace[i]} delete pod -l app=ue-policies
                            kubectl -n ${namespace[i]} delete pod -l app=pa-policies
                            kubectl -n ${namespace[i]} delete pod -l app=decision-engine
                            """
                        }
                    }
                }
            }
        }
    }
}
