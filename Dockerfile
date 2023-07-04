FROM 627987680837.dkr.ecr.eu-central-1.amazonaws.com/prd/yolt-openjdk-17-centos:693517
USER root

ARG VAULT_VERSION=1.7.1

# install assorted tools
RUN yum update -y
RUN yum install -y wget jq unzip less

# install jq
RUN yum install -y epel-release
RUN yum install -y jq

# install cloud hsm jce
RUN wget https://s3.amazonaws.com/cloudhsmv2-software/CloudHsmClient/EL7/cloudhsm-jce-latest.el7.x86_64.rpm
RUN yum -y install ./cloudhsm-jce-latest.el7.x86_64.rpm

# install aws cli
RUN wget -q https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip
RUN unzip awscli-exe-linux-x86_64.zip
RUN ./aws/install
RUN aws --version

# Security patching
RUN yum -y update && yum -y clean all
# Cleanup.
RUN yum remove -y unzip && yum clean all

USER yolt

ADD target/providers-*.jar /app.jar
ENTRYPOINT ["sh", "-c", "java -XX:+UnlockExperimentalVMOptions -XX:MaxRAMPercentage=75.0 -XX:-OmitStackTraceInFastThrow -XX:+UseStringDeduplication ${JAVA_OPTS} -Djava.security.egd=file:/dev/./urandom -Djdk.tls.maxHandshakeMessageSize=35000 -jar /app.jar"]
