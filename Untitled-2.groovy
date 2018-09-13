pipeline {
    agent{
        node{label 'EJAV'}
    }
    parameters {
        string(name: 'StackName', defaultValue: 'VZM-EJAV-BASEAMI-<date>', description: 'Name of the cloudformation Stack')

        choice(name: 'ENVIRONMENT', choices: 'NONPROD\nSTAGING\nPROD\nTEST\n', description: 'Envronment Mode')

        choice(name: 'TYPE', choices: 't2.micro\nt2.small\nt2.medium\nt2.large', description: 'The Size of the ec2 stack')

        choice(name: 'AWSRegion', choices: 'us-east-1\nus-west-2\n', description: 'AWS Region')

        choice(name: 'Account', choices: 'svc-aws_cicd_np\nsvc-pc_cicd_np\n', description: 'AWS account for hpsa stage')

        choice(name: 'Subnet', choices: 'a\nb\nc\n', description:'Select any of the choices')

        string(name: 'AMIID', defaultValue: 'ami-XXXXXXXX', description: 'Please use only centos')

        string(name: 'Playbook', defaultValue: 'playbooks/ebs_volume.yml', description: 'Playbook excute the hpsa module')

        string(name: 'Stashrepourl', defaultValue: 'ssh://onestash.verizon.com:7999/ejavb/ejav.git', description: 'Repo url')
    
        string(name: 'Volsize', defaultValue: '50', description: 'Size of the EBS Volume')
    }
    stages{
        stage('Configuration'){
            steps{
                sh 'echo $StackName'
            }
        }
        stage('Checkout'){
            steps{
                git branch: 'sinni9n_changes',
                credentialsId: '262c7dbe-f30a-4cce-bfb9-6be823ac7d6a',
                url: 'ssh://git@onestash.verizon.com:7999/ejavb/ejav.git'
            }
        }
        stage('Subnet'){
            steps{
                sh 'python scripts/env_baseec2_setup.py'
            }
        }        
        stage('FileVariables'){            
            steps{               
                script{
                    def workspace = env.WORKSPACE
                    def path_to_script = '/EJAV_JOB.variables'
                    def new_path = workspace + path_to_script
                    def lines = readFile(new_path)
                    println lines
                    def lines_variables = lines.tokenize()
                    println lines_variables
                    withEnv(lines_variables){
                        //sh 'env'
                        //vzAWS_CF_CICD_V2 awsenv: 'NONPROD', parameter: '', playbook: '', region: 'us-west-2', role: 'App', stackName: '$StackName', stashBranch: 'sinni9n_changes', stashUrl: 'ssh://onestash.verizon.com:7999/ejavb/ejav.git', templateName: 'cloudformation/vzw_ejav_ecs.json', templateParameter: '{"Volsize":"${Volsize}","Environment":"${ENVIRONMENT}","InstanceType":"${TYPE}","AMI":"${AMIID}","AWSAccount":"VZW","SUBNET":"subnet-edfb9d8a","AWSAccount":"VZW","Role":"App","AppID":"EJAV","SecurityGroupIds":"sg-c0db25ba,sg-74a0d30f,sg-95c52cee"}'
                    }
                }
            }
        }
        stage('ansible Plybook execution'){
            steps{
                script{
                    sh 'python scripts/getServerFromStack.py -s ${StackName}'
                    def path_new_script = '/ConfigureHost.variables'
                    def hpsa_file = workspace + path_new_script
                    def hpsa_variables = readFile(hpsa_file)
                    println hpsa_variables
                    def hpsa_variables_changed = hpsa_variables.tokenize()
                    println hpsa_variables_changed
                    withEnv(hpsa_variables_changed){
                        sh 'env'
                        vzAnsiblePlusPlaybookExtendedPipelineParallel env: 'AWS', os: 'Linux', parameter: '', password: '', playbook: "${params.Playbook}", servers: '${Server}', stashBranch: 'sinni9n_changes', stashUrl:'ssh://onestash.verizon.com:7999/ejavb/ejav.git' , username: '$Account'
                    }
                }
            }
        }
    }
} 