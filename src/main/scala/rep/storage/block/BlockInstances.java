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

package rep.storage.block;

import java.util.concurrent.ConcurrentHashMap;

/**
 * @author jiangbuyun
 * @version	1.0
 * @since	2017-09-28
 * */
public class BlockInstances {
	private static BlockInstances instance = null;
	
	private ConcurrentHashMap<String,BlockStorageHelp> instances = null;
	
	private BlockInstances(){
		this.instances = new ConcurrentHashMap<String,BlockStorageHelp>();
	}
	
	public static synchronized BlockInstances getDBInstance(){
		if(instance == null){
			instance = new BlockInstances();
		}
		return instance;
	}
	
	public BlockStorageHelp getBlockHelp(String SystemName){
		BlockStorageHelp bhelp = null;
		bhelp = this.instances.get(SystemName);
		if(bhelp == null){
			bhelp = CreateBlockHelp(SystemName);
		}
		return bhelp;
	}
	
	private synchronized BlockStorageHelp CreateBlockHelp(String SystemName){
		BlockStorageHelp lhelp = null;
		try{
			lhelp = new BlockStorageHelp(SystemName);
			this.instances.put(SystemName, lhelp);
		}catch(Exception e){
			e.printStackTrace();
		}
		return lhelp;
	}
	
	public static void main(String[] args){
		try{
			BlockStorageHelp bh = BlockInstances.getDBInstance().getBlockHelp("testSystem");
			String aa = "sdfsdfkjklsdfklsdflsflsdkflsdflsdfkljdflsdkf";
			int aalen = aa.getBytes().length;
			bh.writeBlock(0, 0, aa.getBytes());
			byte[] raa = bh.readBlock(0, 0, aalen);
			System.out.println(new String(raa));
			String bb = "kaaaakkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkka";
			int bblen = bb.getBytes().length;
			int start = aalen;
			bh.writeBlock(0, start, bb.getBytes());
			byte[] rbb = bh.readBlock(0, start, bblen);
			System.out.println(new String(rbb));
		}catch(Exception e){
			e.printStackTrace();
		}
		
	}
}