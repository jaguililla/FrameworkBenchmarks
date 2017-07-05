
#!/bin/bash

fw_depends java postgresql

gradle/wrapper -x test
export DBSTORE='postgresql'

rm -rf $RESIN_HOME/webapps/*
cp build/libs/ROOT.war $RESIN_HOME/webapps
resinctl start

