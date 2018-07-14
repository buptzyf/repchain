/*
 * Copyright  2018 Blockchain Technology and Application Joint Lab, Fintech Research Center of ISCAS.
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
 */

package rep.storage.util;

import java.io.File;


/**
 * @author jiangbuyun
 * @version	1.0
 * @since	2017-09-28
 * */
public class pathUtil {
	public static boolean MkdirAll(String path)throws Exception{
		boolean b = false;
		try{
			File f = new File(path);
			if(f.isDirectory()){
				b = true;
				return b;
			}
			b = f.mkdirs();
		}catch(Exception e){
			throw e;
		}
		return b;
	}
	
	
	
	public static boolean RemoveAll(String path){
    	boolean b = false;
        try {
            if(delAllFile(path)){ //删除完里面所有内容
	            java.io.File pf = new java.io.File(path);
	            b = pf.delete(); //删除空文件夹
            }
        }
        catch (Exception e) {
        }
        return b;
    }
	
	private static boolean delAllFile(String path) {
    	boolean b = false;
        File file = new File(path);
        if (!file.exists()) {
            return b;
        }
        if (!file.isDirectory()) {
            return b;
        }
        boolean iserror = false;
        String[] tempList = file.list();
        File temp = null;
        for (int i = 0; i < tempList.length; i++) {
            if (path.endsWith(File.separator)) {
                temp = new File(path + tempList[i]);
            }else{
                temp = new File(path + File.separator + tempList[i]);
            }
            
            if (temp.isFile()) {
                if(!temp.delete()){
                	iserror = true;
                	break;
                }
            }
            if (temp.isDirectory()) {
                if(!RemoveAll(path+File.separator+ tempList[i])){
                	iserror = true;
                	break;
                }
            }
        }
        if(!iserror) b = true;
        return b;
    }

	public static boolean hasPathSuffix(String dirPath){
		boolean b = false;
		if(dirPath.endsWith(String.valueOf(File.separatorChar))){
			b = true;
		}
		return b;
	}
	
	public static String Join(String path,String sub){
		String rstr = "";
		if(!hasPathSuffix(path)){
			path = path + File.separator;
		}
		rstr = path + sub;
		return rstr;
	}
	
	public static long FileExists(String filePath)throws Exception{    	
		long l = -1;
		try{
	    	File f = new File(filePath);    
	    	if(f.exists()){
	    		l = f.length();
	    	}
		}catch(Exception e){
			throw e;
		}
    	return l;
    }
	
	public static int bytesToInt(byte[] inputs){
		if(inputs == null) return 0;
		if(inputs.length < 4) return 0;
		return  (inputs[0] & 0xff) << 24
				| (inputs[1] & 0xff) << 16
				| (inputs[2] & 0xff) << 8
				| (inputs[3] & 0xff) << 0 ;
	}
	
	public static boolean IsPrime(int n){
       if (n <= 3) {
            return n > 1;
        }
        
       for(int i=2;i<=Math.sqrt(n);i++){
           if(n%i == 0)
               return false;
       }
       return true;
	}
}
