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
