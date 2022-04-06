package rep.crypto.ssl;

import javax.net.ServerSocketFactory;
import javax.net.ssl.*;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.security.Provider;
import java.security.SecureRandom;
import java.security.Security;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Arrays;

/**
 * 国密双向
 * @author gmssl.cn
 */
public class Server3
{
	public Server3()
	{}

	public static void main(String[] args) throws Exception
	{
		ServerSocketFactory fact = null;
		SSLServerSocket serversocket = null;

		int port = 36789;


		Security.insertProviderAt((Provider)Class.forName("cn.gmssl.jce.provider.GMJCE").newInstance(), 1);
		Security.insertProviderAt((Provider)Class.forName("cn.gmssl.jsse.provider.GMJSSE").newInstance(), 2);

		//257091603041653856.super_admin    sm2.server1.both
		SSLContext ctx = gmssl_common.getGMSSLContext("pfx/sm2.super_admin.both.pfx","12345678",
				"pfx/mytruststore.pfx","changeme");
		ctx.getServerSessionContext().setSessionCacheSize(8192);
		ctx.getServerSessionContext().setSessionTimeout(3600);



		fact = ctx.getServerSocketFactory();
		serversocket = (SSLServerSocket) fact.createServerSocket(port);
		serversocket.setNeedClientAuth(true);


		String[] supported = serversocket.getEnabledCipherSuites();// 加密套件
		serversocket.setEnabledCipherSuites(supported);
		System.out.println("启用的加密套件: " + Arrays.asList(supported));

		// 接收消息
		System.out.println("端口已打开，准备接受信息");

		SSLSocket cntSocket = (SSLSocket) serversocket.accept();// 开始接收
		Certificate[] clientCerts = cntSocket.getSession().getPeerCertificates();
		System.out.println("客户端身份信息：");
		for (int i = 0; i < clientCerts.length; i++)
		{
			System.out.println(((X509Certificate)clientCerts[i]).getSubjectDN().getName());
		}
		InputStream in = cntSocket.getInputStream();// 输入流
		byte[] buffer = new byte[1024];
		int a = in.read(buffer);
		// 循环检查是否有消息到达
		System.out.println("来自于客户端：");
		while (a > 0)
		{
			System.out.print(new String(buffer).trim());
			buffer = new byte[1024];
			a = in.read(buffer);
		}

	}

	public static SSLServerSocketFactory createServerSocketFactory(KeyStore kepair, char[] pwd) throws Exception
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

		SSLServerSocketFactory factory = ctx.getServerSocketFactory();
		return factory;
	}
}
