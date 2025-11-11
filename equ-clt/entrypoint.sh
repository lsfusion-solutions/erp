#!/bin/sh
set -e

if [ -d /equ ]; then
   cp -rf /equ/* ./
   rm -rf /equ 
fi

exec java $JAVA_OPTS -server -cp equ-clt-server.jar equ.clt.EquipmentServerBootstrap