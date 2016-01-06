#!/bin/sh
# set ff=unix
# Better OS/400 detection: see Bugzilla 31132
os400=false
case "`uname`" in
OS400*) os400=true;;
esac
# resolve links - $0 may be a softlink
PRG="$0"

while [ -h "$PRG" ] ; do
  ls=`ls -ld "$PRG"`
  link=`expr "$ls" : '.*-> \(.*\)$'`
  if expr "$link" : '/.*' > /dev/null; then
    PRG="$link"
  else
    PRG=`dirname "$PRG"`/"$link"
  fi
done

PRGDIR=`dirname "$PRG"`
PRGDIR=`dirname "$PRGDIR"`
EXECUTABLE=${project.artifactId}.jar
THIS_DIR=`pwd`

echo $THIS_DIR
echo $PRGDIR
#export PATH=$PRGDIR:$PATH
cd $PRGDIR
bin/daemon java -jar ./$EXECUTABLE start -n ${project.name} -f ./conf/log4j.xml -p 4562 $@
ps aux | grep log4j
cd $THIS_DIR

#bin/su daemon -c $PRGDIR"/bin/daemon java -jar /"$EXECUTABLE" start -n gozap -f ./conf/log4j.xml -p 4562 "$@""



