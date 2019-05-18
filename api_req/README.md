## RepChain1.0.0, API测试用例文件的介绍
***
#### 通过接口postTran，xml方式
1. 转账的测试用例(transfer/)   `Invoke`
   * assets6.xml assets6.json 正确转账
   * assets7.xml 目标账户不存在
   * assets8.xml 只能从本账户中转出
   * assets8.xml 自己给自己转账
2. 部署合约测试用例(deploy/)  `Deploy`
   * ContractAssetsTPL_deploy.xml 部署同一合约
   * **TODO** 合约升级的测试
3. 部署合约测试用例(changeState/)   `setState`
   * ContractAssetsTPL_changeState 禁用/启用合约

#### jmeter测试脚本
1. jmx/RepChain_Preview_API.jmx，包含了以下接口的测试用例
   * block，获得块数据：
     > /block/hash/{blockId} <br>
       /block/stream/{blockHeight} <br>
       /block/{blockHeight} 
   * chaininfo，查看块链信息：
     >/chaininfo
   * transaction，检索或提交交易：
     >/transaction/stream/{transactionId} <br>
      /transaction/postTranByString <br>
      /transaction/{transactionId} <br>
      /transaction/postTran <br>
      /transaction/postTranStream

2. 操作步骤
   * 打开jmeter导入jmx脚本
   * 设置repchan服务地址，在HTTP User Defined Variables中设置
   * `ctrl + R` 执行线程组
3. 注意事项
   * 该jmeter测试脚本，执行一次过后，清除一下区块，再进行下一次测试演示
   * 将jmx/7a0fa308-9bba-415f-ae10-39832ef2b52f放到jmeter的目录下，该文件用来测试`/transaction/postTranStream`
   * /transaction/postTran只能在debug模式下使用，在jar部署模式下，该接口不能使用
   * 本脚本中根据transactionId获取交易数据使用的创世块中内置的交易，根据blockId获取块数据以及根据高度获取块数据都是获取的创世块示例。（即、对应于目前项目仓库中的/json/gensis.json）
4. **TODO**  未完待续