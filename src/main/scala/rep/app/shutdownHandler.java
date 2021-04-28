package rep.app;

import sun.misc.Signal;
import sun.misc.SignalHandler;

public class shutdownHandler  implements SignalHandler {
    private String sysName = "";
    public shutdownHandler(String sysName){
        this.sysName = sysName;
    }
    @Override
    public void handle(Signal sig) {
        System.err.println("accept close event,start shutdown actorSystem");
        //Runtime.getRuntime().exit(0);
        //RepChainMgr.Stop(sysName);
        System.exit(0);
    }
}