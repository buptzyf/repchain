/*
 * Copyright  2018 Blockchain Technology and Application Joint Lab, Linkel Technology Co., Ltd, Beijing, Fintech Research Center of ISCAS.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BA SIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

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
	private String sysName = "";
	//private String systag = "";
	private VerifySignThread[] object4Work = null;
	
	//private Transaction[] tls = null;
	//private boolean[] tresult = null;
	//private CountDownLatch latch = null;
	
	private long[] statis = new long[100];
	//private int count = 0;
	
	public VerifySign4JavaWork(String systag) {
		sysName = systag;
		sr = ImpDataAccess.GetDataAccess(systag);
		//this.systag = systag;
		this.object4Work = new VerifySignThread[childnum]; 
		for(int i = 0; i < childnum; i++) {
			object4Work[i] = new VerifySignThread();
		}
	}
	
	public boolean StartVerify(Transaction[] itls,int[] findflag) {
		boolean b = true;
		if(itls == null) return b;
		
		//long start = System.currentTimeMillis();
		
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
	        		VerifySignThread tmpworker = object4Work[i];
	            if(i == childnum-1){
	            		tmpworker.setInitValue(i*len,len+m,latch,tls,tresult,i,findflag);
	            		//executor.execute(new VerifySignThread(i*len,len+m,latch,tls,tresult,i));
	            }else{
	            		tmpworker.setInitValue(i*len,len,latch,tls,tresult,i,findflag);
	            		//executor.execute(new VerifySignThread(i*len,len,latch,tls,tresult,i));
	            }
	            executor.execute(tmpworker);
	          //long endsend = System.currentTimeMillis();
	        	  //System.out.println("+++++++send sign time="+(endsend - startsend));
	        }
	    }else{
	      for(int j = 0 ; j< size; j++){
	    	  	VerifySignThread tmpworker = object4Work[j];
	    	  	tmpworker.setInitValue(j,1,latch,tls,tresult,j,findflag);
	    	  	//executor.execute(new VerifySignThread(j,1,latch,tls,tresult,j));
	    	  	executor.execute(tmpworker);
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
		
		/*long end = System.currentTimeMillis();
		if(count == 100) {
			StringBuffer sb = new StringBuffer();
			long avg = 0;
			for(int i = 0; i < 100; i++) {
				avg += statis[i];
				sb.append(statis[i]).append(",");
			}
			System.out.println( "avg="+avg/100+" ^^^^^^^^^^real verify time="+sb.toString());
			count = 0;
		}
		statis[count] = end-start;
		count++;*/
		
		return b;
	}
	
	class VerifySignThread implements Runnable{
		private int startIdx = 0;
		private int len = 0;
		private CountDownLatch latch = null;
		Transaction[] tls = null;
		Vector<Boolean> tresult = null;
		int threadnum = -1;
		int[] findflag = null;
		
		public VerifySignThread() {
			clear();
		}
		
		private void clear() {
			this.startIdx = 0;
			this.len = 0;
			this.latch = null;
			this.tls = null;
			this.tresult = null;
			this.threadnum = -1;
		}
		
		public VerifySignThread(int startIdx,int len,CountDownLatch latch,Transaction[] tls,Vector<Boolean> tresult,int threadnum) {
			this.startIdx = startIdx;
			this.len = len;
			this.latch = latch;
			this.tls = tls;
			this.tresult = tresult;
			this.threadnum = threadnum;
		}
		
		public void setInitValue(int startIdx,int len,CountDownLatch latch,Transaction[] tls,Vector<Boolean> tresult,int threadnum,int[] findflag) {
			this.startIdx = startIdx;
			this.len = len;
			this.latch = latch;
			this.tls = tls;
			this.tresult = tresult;
			this.threadnum = threadnum;
			this.findflag = findflag;
		}
		
		@Override
		public void run() {
	        int count = 0;
	        int tsize = tls.length;
	        
            while(count < len) {
              if(startIdx+count < tsize){
	            	  if(findflag[startIdx+count] == 0) {
		                if(!BlockHelper.checkTransaction(tls[startIdx+count], sysName)){
		                		//System.out.println("thread-"+systag+"-"+threadnum+"-run error in "+(startIdx+count)+"!");
		                    break;
		                }else {
		                		//tresult[startIdx+count] = true;
		                		tresult.set(startIdx+count,true);
		                }
	            	  }else {
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



