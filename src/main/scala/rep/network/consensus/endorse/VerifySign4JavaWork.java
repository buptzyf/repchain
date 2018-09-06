package rep.network.consensus.endorse;

import rep.protos.peer.Transaction;
import rep.network.consensus.block.BlockHelper;
import rep.storage.ImpDataAccess;
import java.util.concurrent.Executors;
import java.util.concurrent.CountDownLatch;
import java.util.Vector;

public class VerifySign4JavaWork {
	private final static int childnum = 10;
	private java.util.concurrent.Executor executor = Executors.newFixedThreadPool(childnum);
	private ImpDataAccess sr  = null;
	private String systag = "";
	
	//private Transaction[] tls = null;
	//private boolean[] tresult = null;
	//private CountDownLatch latch = null;
	
	
	public VerifySign4JavaWork(String systag) {
		sr = ImpDataAccess.GetDataAccess(systag);
		this.systag = systag;
	}
	
	public boolean StartVerify(Transaction[] itls) {
		boolean b = true;
		if(itls == null) return b;
		
		Transaction[]  tls = itls;
		int size = tls.length;
		int len = size / childnum;
		int m = size % childnum;
		CountDownLatch latch = null;
		if(size > childnum) {
			latch = new CountDownLatch(childnum);
		}else {
			latch = new CountDownLatch(size);
		}
		
		Vector<Boolean> tresult = new Vector<Boolean>(size);
		//boolean[] tresult = new boolean[size];
		for(int i = 0; i < size; i++) {
			//tresult[i] = false;
			tresult.addElement(false);
		}
		
		if(size > childnum){
	        	for(int i = 0;  i < childnum; i++) {
	          //long startsend = System.currentTimeMillis();
	            if(i == childnum-1){
	            		executor.execute(new VerifySignThread(i*len,len+m,latch,tls,tresult,i));
	            }else{
	            		executor.execute(new VerifySignThread(i*len,len,latch,tls,tresult,i));
	            }
	          //long endsend = System.currentTimeMillis();
	        	  //System.out.println("+++++++send sign time="+(endsend - startsend));
	        }
	    }else{
	      for(int j = 0 ; j< size; j++){
	    	  	executor.execute(new VerifySignThread(j,1,latch,tls,tresult,j));
	      }
	    }
		
		try {  
	         latch.await();  
	     } catch (InterruptedException e) {  
	         e.printStackTrace();  
	     }  
		
		
		for(int i = 0; i < size; i++) {
			//if(tresult[i] == false) {
			if(tresult.get(i) == false) {
				b = false;
				break;
			}
		}
		
		tls = null;
		tresult = null;
		latch = null;
		
		return b;
	}
	
	class VerifySignThread implements Runnable{
		private int startIdx = 0;
		private int len = 0;
		private CountDownLatch latch = null;
		Transaction[] tls = null;
		Vector<Boolean> tresult = null;
		int threadnum = -1;
		
		public VerifySignThread(int startIdx,int len,CountDownLatch latch,Transaction[] tls,Vector<Boolean> tresult,int threadnum) {
			this.startIdx = startIdx;
			this.len = len;
			this.latch = latch;
			this.tls = tls;
			this.tresult = tresult;
			this.threadnum = threadnum;
		}
		
		@Override
		public void run() {
	        int count = 0;
	        int tsize = tls.length;
	        
            while(count < len) {
              if(startIdx+count < tsize){
                if(!BlockHelper.checkTransaction(tls[startIdx+count], sr)){
                		//System.out.println("thread-"+systag+"-"+threadnum+"-run error in "+(startIdx+count)+"!");
                    break;
                }else {
                		//tresult[startIdx+count] = true;
                		tresult.set(startIdx+count,true);
                }
              }else{
            	  	//System.out.println("thread-"+systag+"-"+threadnum+"-run error in  out of range!");
                break;
              }
              count += 1;
            }
            //System.out.println("thread-"+systag+"-"+threadnum+"-run ok!");
            latch.countDown();
		}
	}
}



