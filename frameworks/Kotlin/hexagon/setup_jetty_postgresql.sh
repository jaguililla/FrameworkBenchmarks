
#!/bin/bash

fw_depends java postgresql

gradle/wrapper -x test
export DBSTORE='postgresql'
nohup build/install/hexagon/bin/hexagon &
