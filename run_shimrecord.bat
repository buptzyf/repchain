TASKKILL /FI "WINDOWTITLE eq shim"
TASKKILL /FI "WINDOWTITLE eq shimrecord"
rm -rf d:/repchaindata
start "shimrecord" java -Djdk.tls.namedGroups=secp256k1 -Dlogback.configurationFile=conf/logback.xml -Xmx1024m -Xms1024m -jar RepChain_shimrecord.jar