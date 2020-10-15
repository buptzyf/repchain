/*
 * Copyright  2019 Blockchain Technology and Application Joint Lab, Linkel Technology Co., Ltd, Beijing, Fintech Research Center of ISCAS.
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

package rep.app.conf

import java.util.concurrent.ConcurrentHashMap

import com.typesafe.config.Config
//import collection.JavaConversions._
//import scala.collection.immutable._
import java.util.List
import java.util.ArrayList

/**
  * 系统配置信息缓存对象
  * @author shidianyue
  * @version	0.7
  * @update 2018-05 jiangbuyun
  * */
object SystemProfile {

  /**
    * 交易创建类型
    */
  case object Trans_Create_Type_Enum {
    val MANUAL = 0 //API创建
    val AUTO = 1 //自动创建
  }

  private[this] var _LIMIT_BLOCK_TRANS_NUM: Int = 0//块内最多交易数
  private[this] var _MIN_BLOCK_TRANS_NUM: Int = 0//块内最少交易数
  private[this] var _VOTE_NODE_MIN: Int = 0//投票最少参与人数
  private[this] var _TRAN_CREATE_DUR: Int = 0//交易创建时间间隔-针对自动创建
  private[this] var _TRANS_CREATE_TYPE: Int = 0//交易创建类型
  private[this] var _RETRY_TIME: Int = 0//投票重试次数限制
  private[this] var _MAX_CATCH_TRANS_NUM: Int = 0//交易最多缓存数量
  private[this] var _DISKSPACE_ALARM_NUM:Long=0//磁盘剩余空间预警 单位=M
  //private[this] var _SERVERPORT:Int=8081//http服务的端口，默认为8081
  private[this] var _CHECKCERTVALIDATE:Int=0//是否检查证书的有效性，0不检查，1检查
  private[this] var _CONTRACTOPERATIONMODE = 0//设置合约的运行方式，0=debug方式，1=deploy，默认为debug方式，如果发布部署，必须使用deploy方式。
  private[this] var _VOTENODELIST : List[String] = new ArrayList[String]
  private[this] var _ACCOUNTCHAINCODENAEM : String = "ACCOUNTCHAINCODENAME"
  private[this] var _ACCOUNTCHAINCODEVERSION: Int = 1
  private[this] var _CertStatusChangeFunction : String = "UpdateCertStatus"
  private[this] var _GENESISNODENAME:String = ""
  private[this] var _BLOCK_LENGTH: Int = 120000//区块的最大长度
  private[this] var _NUMBER_OF_TRANSPROCESSOR = 100 //
  private[this] var _HAS_PRELOAD_TRANS_OF_API = true
  private[this] var _IS_VERIFY_OF_ENDORSEMENT = true//is_verify_of_endorsement
  private[this] var _NUMBER_OF_ENDORSEMENT: Int = 2
  private[this] var _TYPE_OF_CONSENSUS:String = "PBFT"
  private[this] var _BLOCKNUMBER_BLOCKER = 5 //
  private[this] var _ISSTREAM = 1
  private[this] var _HTTPSERVICEACTORNUMBER= 5//httpServiceActorNumber
  private[this] var _ISBROADCASTTRANSACTION = 1 //isbroadcasttransaction

  //zhj
  private[this] var _PBFT_F: Int = 1

  private[this] var _BLOCKNUMBER_OF_RAFT: Int = 100
  
  private[this] var _DBPATH:String = "" //leveldb数据库文件路径
  private[this] var _BLOCKPATH:String = ""//区块文件的路径
  private[this] var _FILEMAX: Int = 200000000//区块文件的最大长度
  
  
  //实时图的事件是否发送，如果不发送，前端实时图将收不到任何消息。
  private[this] var _REALTIMEGRAPH_ENABLE = 1 ////0 unable;1 enable; default 1
  
  
  
  
  //private def SERVERPORT :Int = _SERVERPORT
  private var SERVERPORT = new ConcurrentHashMap[String,Int]()
  private def CHECKCERTVALIDATE:Int = _CHECKCERTVALIDATE
  private def DISKSPACE_ALARM_NUM :Long = _DISKSPACE_ALARM_NUM
  private def CONTRACTOPERATIONMODE:Int=_CONTRACTOPERATIONMODE
  private def GENESISNODENAME:String = _GENESISNODENAME
  
  private def VOTENODELIST : List[String] = _VOTENODELIST
  private def ACCOUNTCHAINCODENAEM = _ACCOUNTCHAINCODENAEM
  private def ACCOUNTCHAINCODVERSION = _ACCOUNTCHAINCODEVERSION
  private def CertStatusChangeFunction = _CertStatusChangeFunction
  
  private def NUMBER_OF_TRANSPROCESSOR = _NUMBER_OF_TRANSPROCESSOR
  
  private def HAS_PRELOAD_TRANS_OF_API = _HAS_PRELOAD_TRANS_OF_API
  
  private def IS_VERIFY_OF_ENDORSEMENT = _IS_VERIFY_OF_ENDORSEMENT
  
  private def NUMBER_OF_ENDORSEMENT = _NUMBER_OF_ENDORSEMENT
  private def BLOCKNUMBER_OF_RAFT = _BLOCKNUMBER_OF_RAFT
  
  private def REALTIMEGRAPH_ENABLE = _REALTIMEGRAPH_ENABLE

  private def TYPE_OF_CONSENSUS : String = _TYPE_OF_CONSENSUS

  private def BLOCKNUMBER_BLOCKER : Int = _BLOCKNUMBER_BLOCKER
  private def ISSTREAM : Int = _ISSTREAM
  private def HTTPSERVICEACTORNUMBER : Int = _HTTPSERVICEACTORNUMBER
  private def ISBROADCASTTRANSACTION : Int = _ISBROADCASTTRANSACTION

  //zhj
  private def PBFT_F = _PBFT_F

  private def DBPATH:String = _DBPATH
  private def BLOCKPATH:String = _BLOCKPATH
  private def FILEMAX: Int = _FILEMAX

  private def TYPE_OF_CONSENSUS_=(value:String):Unit={
    _TYPE_OF_CONSENSUS = value
  }

  private def DBPATH_=(value:String):Unit={
    _DBPATH = value
  }
  
  private def REALTIMEGRAPH_ENABLE_=(value:Int):Unit={
    _REALTIMEGRAPH_ENABLE = value
  }
  
  private def BLOCKPATH_=(value:String):Unit={
    _BLOCKPATH = value
  }
  
  private def FILEMAX_=(value:Int):Unit={
    _FILEMAX = value
  }
  
  private def GENESISNODENAME_=(value:String):Unit={
    _GENESISNODENAME = value
  }

  private def BLOCKNUMBER_BLOCKER_=(value:Int):Unit={
    this._BLOCKNUMBER_BLOCKER = value
  }
  
  private def NUMBER_OF_TRANSPROCESSOR_=(value:Int):Unit={
    _NUMBER_OF_TRANSPROCESSOR = value
  }

  private def ISSTREAM_=(value:Int):Unit={
    _ISSTREAM = value
  }

  private def HTTPSERVICEACTORNUMBER_=(value:Int):Unit={
    _HTTPSERVICEACTORNUMBER = value
  }

  private def ISBROADCASTTRANSACTION_=(value:Int):Unit={
    _ISBROADCASTTRANSACTION = value
  }
  private def HAS_PRELOAD_TRANS_OF_API_=(value:Boolean):Unit={
    _HAS_PRELOAD_TRANS_OF_API = value
  }
  
  private def IS_VERIFY_OF_ENDORSEMENT_=(value:Boolean):Unit={
    _IS_VERIFY_OF_ENDORSEMENT = value
  }
  
  private def NUMBER_OF_ENDORSEMENT_=(value:Int):Unit={
    _NUMBER_OF_ENDORSEMENT = value
  }
  
  private def BLOCKNUMBER_OF_RAFT_=(value:Int):Unit={
    _BLOCKNUMBER_OF_RAFT = value
  }
  
  private def VOTENODELIST_=(value: List[String]): Unit = {
      _VOTENODELIST = value
  }
  
  private def ACCOUNTCHAINCODENAEM_=(value:String):Unit={
    _ACCOUNTCHAINCODENAEM = value
  }
  
  private def CertStatusChangeFunction_=(value:String):Unit={
    _CertStatusChangeFunction = value
  }

  private def ACCOUNTCHAINCODEVERSION_=(value:Int):Unit={
    _ACCOUNTCHAINCODEVERSION = value
  }
  
  /*private def SERVERPORT_=(value: Int): Unit = {
    _SERVERPORT = value
  }*/

  private def CHECKCERTVALIDATE_=(value: Int): Unit = {
    _CHECKCERTVALIDATE = value
  }
  
  private def CONTRACTOPERATIONMODE_=(value: Int): Unit = {
    _CONTRACTOPERATIONMODE = value
  }
  
  private def DISKSPACE_ALARM_NUM_=(value: Long): Unit = {
    _DISKSPACE_ALARM_NUM = value
  }
  
  private def MAX_CATCH_TRANS_NUM: Int = _MAX_CATCH_TRANS_NUM

  private def MAX_CATCH_TRANS_NUM_=(value: Int): Unit = {
    _MAX_CATCH_TRANS_NUM = value
  }

  private def RETRY_TIME: Int = _RETRY_TIME
  

  private def RETRY_TIME_=(value: Int): Unit = {
    _RETRY_TIME = value
  }


  private def TRANS_CREATE_TYPE: Int = _TRANS_CREATE_TYPE

  private def TRANS_CREATE_TYPE_=(value: Int): Unit = {
    _TRANS_CREATE_TYPE = value
  }

  private def TRAN_CREATE_DUR: Int = _TRAN_CREATE_DUR

  private def TRAN_CREATE_DUR_=(value: Int): Unit = {
    _TRAN_CREATE_DUR = value
  }

  private def VOTE_NODE_MIN: Int = _VOTE_NODE_MIN

  private def VOTE_NODE_MIN_=(value: Int): Unit = {
    _VOTE_NODE_MIN = value
  }

  private def MIN_BLOCK_TRANS_NUM: Int = _MIN_BLOCK_TRANS_NUM

  private def MIN_BLOCK_TRANS_NUM_=(value: Int): Unit = {
    _MIN_BLOCK_TRANS_NUM = value
  }

  private def LIMIT_BLOCK_TRANS_NUM: Int = _LIMIT_BLOCK_TRANS_NUM

  private def LIMIT_BLOCK_TRANS_NUM_=(value: Int): Unit = {
    _LIMIT_BLOCK_TRANS_NUM = value
  }

  private def BLOCK_LENGTH : Int = _BLOCK_LENGTH
  
  private def BLOCK_LENGTH_=(value: Int): Unit = {
    _BLOCK_LENGTH = value
  }
  
  /**
    * 初始化配饰信息
    * @param config
    */
  def initConfigSystem(config:Config,SystemName:String): Unit ={
    LIMIT_BLOCK_TRANS_NUM_=(config.getInt("system.block.trans_num_limit"))
    BLOCK_LENGTH_=(config.getInt("system.block.block_length"))
    MIN_BLOCK_TRANS_NUM_=(config.getInt("system.block.trans_num_min"))
    RETRY_TIME_=(config.getInt("system.block.retry_time"))
    VOTE_NODE_MIN_=(config.getInt("system.vote.vote_node_min"))
    VOTENODELIST_=(config.getStringList("system.vote.vote_node_list"))
    TRAN_CREATE_DUR_=(config.getInt("system.transaction.tran_create_dur"))
    MAX_CATCH_TRANS_NUM_=(config.getInt("system.transaction.max_cache_num"))
    TRANS_CREATE_TYPE_=(config.getInt("system.trans_create_type"))
    DISKSPACE_ALARM_NUM_=(config.getInt("system.diskspaceManager.diskspacealarm"))
    //SERVERPORT_=(config.getInt("system.httpServicePort"))
    val tmp = config.getInt("system.httpServicePort")
    this.SERVERPORT.put(SystemName,tmp)
    HTTPSERVICEACTORNUMBER_=(config.getInt("system.httpServiceActorNumber"))
    ISBROADCASTTRANSACTION_=(config.getInt("system.isbroadcasttransaction"))
    CHECKCERTVALIDATE_=(config.getInt("system.checkCertValidate"))
    CONTRACTOPERATIONMODE_=(config.getInt("system.contractOperationMode"))
    ACCOUNTCHAINCODENAEM_= (config.getString("system.account.chaincodename"))
    ACCOUNTCHAINCODEVERSION_=(config.getInt("system.account.chaincodeversion"))
    CertStatusChangeFunction_= (config.getString("system.account.CertStatusChangeFunction"))
    BLOCKNUMBER_BLOCKER_=(config.getInt("system.block.block_number_blocker"))
    
    GENESISNODENAME_=(config.getString("system.genesis_node_name"))
    NUMBER_OF_TRANSPROCESSOR_=(config.getInt("system.number_of_transProcessor"))
    HAS_PRELOAD_TRANS_OF_API_=(config.getBoolean("system.has_preload_trans_of_api"))
    IS_VERIFY_OF_ENDORSEMENT_=(config.getBoolean("system.is_verify_of_endorsement"))
    NUMBER_OF_ENDORSEMENT_=(config.getInt("system.number_of_endorsement"))
    BLOCKNUMBER_OF_RAFT_=(config.getInt("system.consensus.blocknumberofraft"))
    TYPE_OF_CONSENSUS_=(config.getString("system.consensus.type"))
    ISSTREAM_=(config.getInt("system.consensus.isstream"))
    
    DBPATH_= (config.getString("system.storage.dbpath"))
    BLOCKPATH_= (config.getString("system.storage.blockpath"))
    FILEMAX_=(config.getInt("system.storage.filemax"))
    REALTIMEGRAPH_ENABLE_=(config.getInt("system.realtimegraph_enable"))
  }

  //zhj
  def getPbftF = PBFT_F

  def getIsStream : Int = ISSTREAM

  def getHttpServiceActorNumber : Int = HTTPSERVICEACTORNUMBER

  def getIsBroadcastTransaction : Int = ISBROADCASTTRANSACTION

  def getRealtimeGraph = REALTIMEGRAPH_ENABLE
  
  def getDBPath = DBPATH
  
  def getBlockPath = BLOCKPATH
  
  def getFileMax = FILEMAX
  
  def getLimitBlockTransNum = LIMIT_BLOCK_TRANS_NUM
  
  def getNumberOfTransProcessor = NUMBER_OF_TRANSPROCESSOR
  
  def getBlockNumberOfRaft = BLOCKNUMBER_OF_RAFT

  def getTypeOfConsensus : String = TYPE_OF_CONSENSUS
  
  def getHasPreloadTransOfApi = HAS_PRELOAD_TRANS_OF_API
  
  def getIsVerifyOfEndorsement = IS_VERIFY_OF_ENDORSEMENT
  
  def getNumberOfEndorsement = NUMBER_OF_ENDORSEMENT
  
  def getBlockLength = BLOCK_LENGTH

  def getMinBlockTransNum = MIN_BLOCK_TRANS_NUM

  def getVoteNodeMin = VOTE_NODE_MIN

  def getTranCreateDur = TRAN_CREATE_DUR

  def getMaxCacheTransNum = MAX_CATCH_TRANS_NUM

  def getTransCreateType = TRANS_CREATE_TYPE

  def getRetryTime = RETRY_TIME
  
  def getDiskSpaceAlarm = DISKSPACE_ALARM_NUM
  
  def getHttpServicePort(SystemName:String):Int={
    this.SERVERPORT.get(SystemName)
  }
  
  def getCheckCertValidate = CHECKCERTVALIDATE
  
  def getContractOperationMode = CONTRACTOPERATIONMODE
  
  def getVoteNodeList = VOTENODELIST
  
  def getAccountChaincodeName = ACCOUNTCHAINCODENAEM
  
  def getCertStatusChangeFunction = CertStatusChangeFunction

  def getAccountChaincodeVersion = ACCOUNTCHAINCODVERSION
  
  def getGenesisNodeName = GENESISNODENAME

  def getBlockNumberOfBlocker = BLOCKNUMBER_BLOCKER
}
