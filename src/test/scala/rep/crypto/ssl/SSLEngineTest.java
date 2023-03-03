package rep.crypto.ssl;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLSession;
import java.nio.ByteBuffer;
import java.security.Provider;
import java.security.Security;

public class SSLEngineTest {

    private static boolean logging = true;
    private static boolean debug = true;

    private SSLContext sslc;

    private SSLEngine clientEngine;
    private ByteBuffer clientOut;
    private ByteBuffer clientIn;

    private SSLEngine serverEngine;
    private ByteBuffer serverOut;
    private ByteBuffer serverIn;

    private ByteBuffer cTOs;
    private ByteBuffer sTOc;



    public static void main(String args[]) throws Exception {
        if (debug) {
            System.setProperty("javax.net.debug", "all");
        }

        Security.insertProviderAt((Provider)Class.forName("cn.gmssl.jce.provider.GMJCE").newInstance(), 1);
        Security.insertProviderAt((Provider)Class.forName("cn.gmssl.jsse.provider.GMJSSE").newInstance(), 2);

        SSLEngineTest demo = new SSLEngineTest();
        demo.runDemo();

        System.out.println("Demo Completed.");
    }

    /*
     * Create an initialized SSLContext to use for this demo.
     */
    public SSLEngineTest() throws Exception {
        sslc = gmssl_common.getGMSSLContext("pfx/sm2.super_admin.both.pfx","12345678",
                "pfx/mytruststore.pfx","changeme");;
    }


    private void runDemo() throws Exception {
        boolean dataDone = false;

        createSSLEngines();
        createBuffers();

        SSLEngineResult clientResult;
        SSLEngineResult serverResult;


        while (!isEngineClosed(clientEngine) || !isEngineClosed(serverEngine)) {

            log("================");

            clientResult = clientEngine.wrap(clientOut, cTOs);
            log("client wrap: ", clientResult);
            runDelegatedTasks(clientResult, clientEngine);

            serverResult = serverEngine.wrap(serverOut, sTOc);
            log("server wrap: ", serverResult);
            runDelegatedTasks(serverResult, serverEngine);

            cTOs.flip();
            sTOc.flip();

            log("----");

            clientResult = clientEngine.unwrap(sTOc, clientIn);
            log("client unwrap: ", clientResult);
            runDelegatedTasks(clientResult, clientEngine);

            serverResult = serverEngine.unwrap(cTOs, serverIn);
            log("server unwrap: ", serverResult);
            runDelegatedTasks(serverResult, serverEngine);

            cTOs.compact();
            sTOc.compact();


            if (!dataDone && (clientOut.limit() == serverIn.position())
                    && (serverOut.limit() == clientIn.position())) {


                checkTransfer(serverOut, clientIn);
                checkTransfer(clientOut, serverIn);

                log("\tClosing clientEngine's *OUTBOUND*...");
                clientEngine.closeOutbound();
                // serverEngine.closeOutbound();
                dataDone = true;
            }
        }
    }


    private void createSSLEngines() throws Exception {

        serverEngine = sslc.createSSLEngine("127.0.0.1",36789);
        serverEngine.setUseClientMode(false);
        serverEngine.setNeedClientAuth(true);
        serverEngine.setEnabledProtocols(new String[]{"GMSSLv1.1"});
        serverEngine.setEnabledCipherSuites(new String[]{"ECDHE_SM4_GCM_SM3"});
        serverEngine.setEnabledCipherSuites(new String[]{"ECDHE_SM4_GCM_SM3"});

        clientEngine = sslc.createSSLEngine("127.0.0.1", 36789);
        clientEngine.setUseClientMode(true);
        clientEngine.setEnabledCipherSuites(new String[]{"ECDHE_SM4_GCM_SM3"});
        clientEngine.setEnabledProtocols(new String[]{"GMSSLv1.1"});

    }


    private void createBuffers() {
        SSLSession session = clientEngine.getSession();
        int appBufferMax = session.getApplicationBufferSize();
        int netBufferMax = session.getPacketBufferSize();

        clientIn = ByteBuffer.allocate(appBufferMax + 50);
        serverIn = ByteBuffer.allocate(appBufferMax + 50);

        cTOs = ByteBuffer.allocateDirect(netBufferMax);
        sTOc = ByteBuffer.allocateDirect(netBufferMax);

        clientOut = ByteBuffer.wrap("Hi Server, I'm Client".getBytes());
        serverOut = ByteBuffer.wrap("Hello Client, I'm Server".getBytes());
    }


    private static void runDelegatedTasks(SSLEngineResult result,
                                          SSLEngine engine) throws Exception {

        if (result.getHandshakeStatus() == SSLEngineResult.HandshakeStatus.NEED_TASK) {
            Runnable runnable;
            while ((runnable = engine.getDelegatedTask()) != null) {
                log("\trunning delegated task...");
                runnable.run();
            }
            SSLEngineResult.HandshakeStatus hsStatus = engine.getHandshakeStatus();
            if (hsStatus == SSLEngineResult.HandshakeStatus.NEED_TASK) {
                throw new Exception("handshake shouldn't need additional tasks");
            }
            log("\tnew HandshakeStatus: " + hsStatus);
        }
    }

    private static boolean isEngineClosed(SSLEngine engine) {
        return (engine.isOutboundDone() && engine.isInboundDone());
    }

    private static void checkTransfer(ByteBuffer a, ByteBuffer b)
            throws Exception {
        a.flip();
        b.flip();

        if (!a.equals(b)) {
            throw new Exception("Data didn't transfer cleanly");
        } else {
            log("\tData transferred cleanly");
        }

        a.position(a.limit());
        b.position(b.limit());
        a.limit(a.capacity());
        b.limit(b.capacity());
    }


    private static boolean resultOnce = true;

    private static void log(String str, SSLEngineResult result) {
        if (!logging) {
            return;
        }
        if (resultOnce) {
            resultOnce = false;
            System.out.println("The format of the SSLEngineResult is: \n"
                    + "\t\"getStatus() / getHandshakeStatus()\" +\n"
                    + "\t\"bytesConsumed() / bytesProduced()\"\n");
        }
        SSLEngineResult.HandshakeStatus hsStatus = result.getHandshakeStatus();
        log(str + result.getStatus() + "/" + hsStatus + ", "
                + result.bytesConsumed() + "/" + result.bytesProduced()
                + " bytes");
        if (hsStatus == SSLEngineResult.HandshakeStatus.FINISHED) {
            log("\t...ready for application data");
        }
    }

    private static void log(String str) {
        if (logging) {
            System.out.println(str);
        }
    }
}