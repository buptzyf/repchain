package rep.crypto.ssl;

import javax.net.SocketFactory;
import javax.net.ssl.*;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.InetSocketAddress;
import java.security.KeyStore;
import java.security.Provider;
import java.security.SecureRandom;
import java.security.Security;

/**
 * 单向认证
 * @author gmssl.cn
 */
public class Client1
{
	public Client1()
	{}

	public static void main(String[] args)
	{
		SocketFactory fact = null;
		SSLSocket socket = null;

		System.out.println("Usage: java -cp GMExample.jar client.Client1 addr port");
		
		try
		{
			String addr = "127.0.0.1";//demo.gmssl.cn
			int port = 8444;
        	String uri = "/";
        	if(args.length > 0)
        	{
        		addr = args[0];
        		port = Integer.parseInt(args[1]);
        	}

			Security.insertProviderAt((Provider)Class.forName("cn.gmssl.jce.provider.GMJCE").newInstance(), 1);
			Security.insertProviderAt((Provider)Class.forName("cn.gmssl.jsse.provider.GMJSSE").newInstance(), 2);

			fact = createSocketFactory(null, null);
			socket = (SSLSocket) fact.createSocket();
    		socket.setEnabledCipherSuites(new String[] {"ECC_SM4_CBC_SM3"});//GMSSL_SM2_SM4_SM3,ECC_SM4_CBC_SM3,ECDHE_SM4_GCM_SM3
			socket.setTcpNoDelay(true);

			socket.connect(new InetSocketAddress(addr, port), 2000);
			socket.setTcpNoDelay(true);
			socket.startHandshake();
			
			DataInputStream in = new DataInputStream(socket.getInputStream());
			DataOutputStream out = new DataOutputStream(socket.getOutputStream());

            String s = "GET " + uri + " HTTP/1.1\r\n";
            s+= "Accept: */*\r\n";
            s+= "User-Agent: Mozilla/4.0 (compatible; MSIE 8.0; Windows NT 5.1; Trident/4.0)\r\n";
            s+= "Host: " + addr + (port == 443 ? "" : ":"+port) + "\r\n";
            s+= "Connection: Close\r\n";
            s+= "\r\n";
            out.write(s.getBytes());
            out.flush();

			System.out.println("####"+socket.getSession().getCipherSuite());
			
			byte[] buf = new byte[8192];
			int len = in.read(buf);
			if (len == -1)
			{
				System.out.println("eof");
				return;
			}
			System.out.println("%%%%%"+new String(buf, 0, len));
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
		TrustAllManager[] trust = { new TrustAllManager() };

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

		SSLSocketFactory factory = ctx.getSocketFactory();
		return factory;
	}
}
