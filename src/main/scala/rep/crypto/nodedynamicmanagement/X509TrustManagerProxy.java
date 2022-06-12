package rep.crypto.nodedynamicmanagement;

import rep.log.RepLogger;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509ExtendedTrustManager;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicBoolean;

public class X509TrustManagerProxy implements InvocationHandler {
    private TrustManager  manager  =  null;
    private X509ExtendedTrustManager target = null;
    private X509ExtendedTrustManager update = null;
    private AtomicBoolean isUpdated = new AtomicBoolean(false);
    private Object lock = new Object();
    private String systemName = null;

    public X509TrustManagerProxy(String systemName,X509ExtendedTrustManager target){
        this.systemName = systemName;
        this.target = target;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if(this.isUpdated.get()){
            synchronized (this.lock) {
                if(this.isUpdated.get()){
                    this.target = this.update;
                    this.isUpdated.set(false);
                    System.out.println(systemName+" X509TrustManagerProxy 调用时接收改变,method="+method.getName());
                    RepLogger.trace(RepLogger.System_Logger(), "X509TrustManagerProxy 调用时接收改变,method="+method.getName());
                }
                return method.invoke(this.target,args);
            }
        }else{
            System.out.println(systemName+" X509TrustManagerProxy 直接调用，没有改变,method="+method.getName());
            RepLogger.trace(RepLogger.System_Logger(), "X509TrustManagerProxy 直接调用，没有改变,method="+method.getName());
            return method.invoke(target,args);
        }
    }

    public void setTarget(X509ExtendedTrustManager input){
        synchronized (this.lock) {
            this.update = input;
            if(this.target == null){
                this.target = this.update;
            }
            this.isUpdated.set(true);
            System.out.println(systemName+" X509TrustManagerProxy 通知更新");
            RepLogger.trace(RepLogger.System_Logger(), "X509TrustManagerProxy 通知更新");
        }
    }

    private X509ExtendedTrustManager getRepresentedObject(){
        return this.target;
    }

    public synchronized TrustManager Wrapper(){
        if(this.manager == null){
            X509ExtendedTrustManager xtm = this.getRepresentedObject();
            Object obj = Proxy.newProxyInstance(xtm.getClass().getClassLoader(), xtm.getClass().getInterfaces(),this);
            this.manager = (TrustManager)obj;
        }
        return  this.manager;
    }
}



