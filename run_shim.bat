TASKKILL /FI "WINDOWTITLE eq shim"
TASKKILL /FI "WINDOWTITLE eq shimrecord"
rm -rf d:/repchaindata
start "shim" java -Djdk.tls.namedGroups=secp256k1 -Dlogback.configurationFile=conf/logback.xml -Xmx1024m -Xms1024m -jar RepChain_shim.jar