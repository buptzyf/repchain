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
