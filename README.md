# flume-agent

Author: li_jian@gozap.com

log4j socket 集中收集日志，并将日志写入文件，还可以输出到flume

## java run:
    java -jar /home/flume-agent/flume-agent-${version}.jar

## docker run
   docker REPOSITORY: registry.gozap.com/log4j.ezhe.com


1. docker-compose.yml

````
log4j:
    image: registry.gozap.com/log4j.ezhe.com
    ports:
        - 4560:4560 
    environment:
        LC_ALL: en_US.UTF-8
    volumes:
        - /etc/sysconfig/
        - /data/flume-agent/logs/
        - /data/flume-agent/conf/:/data/log4j/conf/
        
````

````
docker-compose up -d
````
   
2. docker-log4j.sh

````sh
#!/bin/sh

dockerRepo=registry.gozap.com
appname=log4j.ezhe.com
appport=4560
if [ -n "$appname" -a -n "$appport" ]; then
    echo "docker pull $appname "
    docker pull $dockerRepo/$appname
    echo "docker run $dockerRepo/$appname "
    docker ps -a | grep $appname | awk '{print $1}' | xargs docker rm -f
    docker run -d -p $appport:4560 -h $appname --name $appname-$appport -v /etc/sysconfig:/etc/sysconfig -v /data/flume-agent/logs:/data/flume-agent/logs -v /data/flume-agent/conf/:/data/log4j/conf/ $dockerRepo/$appname
else 
    echo "usage : "
    echo "    ./docker-log4j.sh"
    echo " "
fi
````

    


