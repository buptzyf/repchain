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


import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.StringTokenizer;

public class LogTraceOption {
	private static LogTraceOption traceOptionObj = null;
	
	private ConcurrentHashMap<String,Boolean> lhm = null;
	private ConcurrentHashMap<String,Boolean> nodeLog = null;
	private AtomicBoolean  isOpen = null;
	
	private LogTraceOption() {
		this.lhm = new ConcurrentHashMap<String,Boolean>();
		this.nodeLog = new ConcurrentHashMap<String,Boolean>();
		this.isOpen = new AtomicBoolean(true);
	}
	
	public static synchronized LogTraceOption getLogTraceOption() {
		if(traceOptionObj == null) {
			traceOptionObj = new LogTraceOption();
		}
		return traceOptionObj;
	}
	
	public void addLogOption(String packageName,Boolean status) {
		lhm.put(packageName, status);
	}
	
	public void addNodeLogOption(String nodeName,Boolean status) {
		nodeLog.put(nodeName, status);
	}
	
	public boolean isOpenOutputLog(String nodeName,String packageName) {
		boolean rb = true;
		
		if(this.isOpen.get()) {
			if(nodeName != null) {
				if(isNodeClose(nodeName)) {
					rb = isClose(packageName);
				}else {
					rb = false;
				}
			}else {
				rb = isClose(packageName);
			}
		}else {
			rb = false;
		}
		
		return rb;
	}
	
	private boolean isNodeClose(String nodeNme) {
		boolean isclose = true;
		if(this.nodeLog.isEmpty()) {
			return isclose;
		}else {
			if(!this.nodeLog.containsKey(nodeNme)) {
				return isclose;
			}else {
				Boolean b = this.nodeLog.get(nodeNme);
				isclose = b.booleanValue();
				return isclose;
			}
		}
	}
	
	
	private boolean isClose(String packageName) {
		boolean isclose = true;
		
		if(this.lhm.isEmpty()) {
			return isclose;
		}else {
			String pn = packageName;
			while(!pn.equalsIgnoreCase("")) {
				if(!this.lhm.containsKey(pn)) {
					pn = getParent(pn);
				}else {
					Boolean b = this.lhm.get(pn);
					isclose =  b.booleanValue();
					break;
				}
			}
			return isclose;
		}
		
	}
	
	/*private boolean getValueForName(String packageName) {
		if(!this.lhm.containsKey(packageName)) {
			return false;
		}else {
			Boolean b = this.lhm.get(packageName);
			return  b.booleanValue();
		}
	}*/
	
	private String getParent(String name) {
		String rs = "";
		
		StringTokenizer st = new StringTokenizer(name,".",false);
		if(st.countTokens() == 1) {
			return rs;
		}else {
			int len = st.countTokens() - 1;
			for(int i = 0; i < len; i++) {
				if(i == 0) {
					rs = rs + st.nextToken();
				}else {
					rs = rs + "."+st.nextToken();
				}
			}
		}
		
		return rs;
	}
	
	public void CloseAll() {
		this.isOpen.set(false);
	}
	
	public void OpenAll() {
		this.isOpen.set(true);
	}
	
}
