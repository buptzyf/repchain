package rep.crypto.ssl;

import javax.net.SocketFactory;
import javax.net.ssl.*;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.security.Provider;
import java.security.SecureRandom;
import java.security.Security;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;

/**
 * 双向认证
 * @author gmssl.cn
 */
public class Client3
{
	public Client3()
	{}

	public static void main(String[] args)
	{
		SSLSocket socket = null;

		try
		{
			String addr = "127.0.0.1";
			int port = 36789;
        	
			Security.insertProviderAt((Provider)Class.forName("cn.gmssl.jce.provider.GMJCE").newInstance(), 1);
			Security.insertProviderAt((Provider)Class.forName("cn.gmssl.jsse.provider.GMJSSE").newInstance(), 2);

			//pfx/sm2.user1.both.pfx ,121000005l35120456.node1
			SSLContext ctx = gmssl_common.getGMSSLContext("pfx/sm2.super_admin.both.pfx","12345678",
					"pfx/mytruststore.pfx","changeme");
			ctx.getServerSessionContext().setSessionCacheSize(8192);
			ctx.getServerSessionContext().setSessionTimeout(3600);

			//SSLEngine engine = gmssl_common.getGMSSLEngine(ctx,addr,port,true);
			//engine.get
			SSLSocketFactory factory = ctx.getSocketFactory();

			socket = (SSLSocket) factory.createSocket();
			socket.setEnabledCipherSuites(new String[] {"ECDHE_SM4_GCM_SM3"});
			socket.setTcpNoDelay(true);

			socket.connect(new InetSocketAddress(addr, port), 2000);
			socket.setTcpNoDelay(true);
			socket.startHandshake();

			Certificate[] serverCerts = socket.getSession().getPeerCertificates();
			System.out.println("服务端身份信息：");
			for (int i = 0; i < serverCerts.length; i++)
			{
				System.out.println(((X509Certificate)serverCerts[i]).getSubjectDN().getName());
			}

			String[] suites = socket.getSupportedCipherSuites();
			for(int i = 0; i < suites.length; i++){
				System.out.println("support suite:"+suites[i]);
			}



			OutputStream out = socket.getOutputStream();
			out.write("hello111111111111111111\r\n22".getBytes());
			out.flush();
			out.write("hello111111111111111112\r\n33".getBytes());
			out.flush();
			out.close();
			System.out.println("客户端发送完成：" + "hello");

		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
		finally
		{
			try
			{
				socket.close();
			}
			catch (Exception e)
			{}
		}
	}

	public static SSLSocketFactory createSocketFactory(KeyStore kepair, char[] pwd) throws Exception
	{

		KeyManager[] kms = null;
		if (kepair != null)
		{
			KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
			kmf.init(kepair, pwd);
			kms = kmf.getKeyManagers();
		}

		TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance("SunX509");
		KeyStore tkeyStore = KeyStore.getInstance("PKCS12","GMJCE");
		InputStream fin = Files.newInputStream(Paths.get("pfx/mytruststore.pfx"));
		try {
			tkeyStore.load(fin, "changeme".toCharArray());
		} finally{
			fin.close();
		}

		trustManagerFactory.init(tkeyStore);
		TrustManager[] trusts = trustManagerFactory.getTrustManagers();

		SSLContext ctx = SSLContext.getInstance("GMSSLv1.1", "GMJSSE");
		SecureRandom secureRandom = new SecureRandom();
		ctx.init(kms, trusts, secureRandom);

		ctx.getServerSessionContext().setSessionCacheSize(8192);
		ctx.getServerSessionContext().setSessionTimeout(3600);

		SSLSocketFactory factory = ctx.getSocketFactory();
		return factory;
	}
}
