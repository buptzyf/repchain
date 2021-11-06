package rep.app;

import rep.network.tools.transpool.TransactionPoolMgr;
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
        System.exit(0);
    }
}