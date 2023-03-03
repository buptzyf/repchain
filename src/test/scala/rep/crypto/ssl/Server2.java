package rep.crypto.ssl;

import javax.net.ServerSocketFactory;
import javax.net.ssl.*;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.security.KeyStore;
import java.security.Provider;
import java.security.SecureRandom;
import java.security.Security;

/**
 * 国密双向
 * @author gmssl.cn
 */
public class Server2
{
	public Server2()
	{}

	public static void main(String[] args) throws Exception
	{
		ServerSocketFactory fact = null;
		SSLServerSocket serversocket = null;

		System.out.println("Usage: java -cp GMExample.jar server.Server2 port");
		int port = 8444;
		if(args.length > 0)
		{
			port = Integer.parseInt(args[0]);
		}

		String pfxfile = "pfx/sm2.server1.both.pfx";
		String pwdpwd = "12345678";

		Security.insertProviderAt((Provider)Class.forName("cn.gmssl.jce.provider.GMJCE").newInstance(), 1);
		Security.insertProviderAt((Provider)Class.forName("cn.gmssl.jsse.provider.GMJSSE").newInstance(), 2);

		KeyStore pfx = KeyStore.getInstance("PKCS12", "GMJSSE");
		pfx.load(new FileInputStream(pfxfile), pwdpwd.toCharArray());

		fact = createServerSocketFactory(pfx, pwdpwd.toCharArray());
		serversocket = (SSLServerSocket) fact.createServerSocket(port);
		serversocket.setNeedClientAuth(true);

		while (true)
		{
			SSLSocket socket = null;
			try
			{
				socket = (SSLSocket)serversocket.accept();

				DataInputStream in = new DataInputStream(socket.getInputStream());
				DataOutputStream out = new DataOutputStream(socket.getOutputStream());

				byte[] buf = new byte[8192];
				int len = in.read(buf);
				if (len == -1)
				{
					System.out.println("eof");
				}
				System.out.println(new String(buf, 0, len));
				
				byte[] body = "this is a gm server".getBytes();
				byte[] resp = ("HTTP/1.1 200 OK\r\nServer: GMSSL/1.0\r\nContent-Length:"+body.length+"\r\nContent-Type: text/plain\r\nConnection: close\r\n\r\n").getBytes();
				out.write(resp, 0, resp.length);
				out.write(body, 0, body.length);
				out.flush();

				javax.security.cert.X509Certificate[] cs = socket.getSession().getPeerCertificateChain();
				System.out.println("client certs len=" + cs.length);
				for (int i = 0; i < cs.length; i++)
				{
					System.out.println(cs[i]);
				}
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
	}

	public static SSLServerSocketFactory createServerSocketFactory(KeyStore kepair, char[] pwd) throws Exception
	{
		TrustManager[] trust = { new TrustAllManager() };

		KeyManager[] kms = null;
		if (kepair != null)
		{
			KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
			kmf.init(kepair, pwd);
			kms = kmf.getKeyManagers();
		}

		SSLContext ctx = SSLContext.getInstance("GMSSLv1.1", "GMJSSE");
		SecureRandom secureRandom = new SecureRandom();
		ctx.init(kms, trust, secureRandom);
		
		ctx.getServerSessionContext().setSessionCacheSize(8192);
		ctx.getServerSessionContext().setSessionTimeout(3600);

		SSLServerSocketFactory factory = ctx.getServerSocketFactory();
		return factory;
	}
}
