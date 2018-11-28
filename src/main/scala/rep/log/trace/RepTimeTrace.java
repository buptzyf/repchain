package rep.log.trace;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class RepTimeTrace {
	private static RepTimeTrace timetrace = null;
	
	private ConcurrentHashMap<String,TimePair> nodeTimeTracer = null;
	private AtomicBoolean  isOpenTimeTrace = null;
		
	private RepTimeTrace() {
		this.nodeTimeTracer = new ConcurrentHashMap<String,TimePair>();
		this.isOpenTimeTrace = new AtomicBoolean(false);
	}
	
	public static synchronized RepTimeTrace getRepTimeTrace() {
		if(timetrace == null) {
			timetrace = new RepTimeTrace();
		}
		return timetrace;
	}
	
	public void addTimeTrace(String nodeName,String timetag,long time,boolean isstart) {
		if(this.isOpenTimeTrace.get()) {
			TimePair rtd = null;
			if(this.nodeTimeTracer.containsKey(nodeName+"_"+timetag)) {
				rtd = this.nodeTimeTracer.get(nodeName+"_"+timetag);
			}else {
				rtd = new TimePair(nodeName+"_"+timetag,nodeName);
				this.nodeTimeTracer.put(nodeName+"_"+timetag,rtd);
			}
			if(isstart) {
				rtd.start(time);
			}else {
				rtd.finish(time);
			}
		}
	}
	
	public void openTimeTrace() {
		this.isOpenTimeTrace.set(true);
	}
	
	public void closeTimeTrace() {
		this.isOpenTimeTrace.set(false);
	}
}
