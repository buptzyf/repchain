package rep.log.trace;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

public class TimePair {
	private static Logger log = LoggerFactory.getLogger(TimePair.class);
	private String timeTag = "";
	private String nodeName = "";
	private long start = 0l;
	private long end = 0;
	
	public TimePair(String timeTag,String nodeName) {
		this.timeTag = timeTag;
		this.nodeName = nodeName;
	}
	
	public void start(long start) {
		this.start = start;
		this.end = 0l;
	}
	
	public synchronized void finish(long end) {
		this.end = end;
		String str = this.toString();
		//if(nodeName.equalsIgnoreCase("1")) {
			log.info(str);
			System.out.println(str);
		//}
		this.start = 0l;
		this.end = 0l;
	}
	
	public String toString() {
		if(this.start > 0 && this.end > 0)
			return "~"+"timeTag="+this.timeTag+"~"+"start="+(this.start)+"~"+"end="+this.end+"~"+"timespan="+(this.end-this.start);
		else
			return "~"+"timeTag="+this.timeTag+"~"+" not enough time item";
	}
}
