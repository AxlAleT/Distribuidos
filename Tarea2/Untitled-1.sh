<pre>sudo vi /etc/rc.local</pre>
<p>2. Agregar al archivo lo siguiente:</p>
<code>#!/bin/bash</code><br><code>iptables -t nat -A OUTPUT -o lo -p tcp --dport 80 -j REDIRECT --to-port 8080</code><br><code>runuser -l ubuntu -c 'export JAVA_HOME=/usr;export CATALINA_HOME=/home/ubuntu/apache-tomcat-8.5.63;sh $CATALINA_HOME/bin/catalina.sh start'</code><br><code>exit 0</code><br><br>