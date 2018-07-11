#!/bin/sh

app_name="repchain.jar"

#::=== logger
logger_name="logback"

#::=== config
config_base="conf/"
config_app="system.conf"
config_log="logback.xml"

#::=== arguments

#::=== execute
#-Dconfig.resourse=$config_base/$config_app

java -D$logger_name.configurationFile=$config_base/$config_log -jar $app_name