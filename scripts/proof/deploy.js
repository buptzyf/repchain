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

function write(pn,pv){
	shim.setVal(pn,pv);
}
function read(pn){
	return shim.getVal(pn);
}

function put_proof(pn,pv){	
	//先检查该hash是否已经存在,如果已存在,抛异常
	var pv0 = read(pn);
//	if(pv0)
//		throw '['+pn+']已存在，当前值['+pv0+']';
	shim.setVal(pn,pv);
	print('putProof:'+pn+':'+pv);
}
function signup(cert,inf){
	return shim.signup(cert,inf);
}