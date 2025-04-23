#!/bin/bash
iptables -t nat -A OUTPUT -o lo -p tcp --dport 80 -j REDIRECT --to-port 8080
runuser -l ubuntu -c 'export JAVA_HOME=/usr;export CATALINA_HOME=/home/ubuntu/apache-tomcat-8.5.63;sh $CATALINA_HOME/bin/catalina.sh start'
exit 0
