
##后端模块划分说明
- `api` 用于检索交易、区块的restAPI
- `app` 主程序，程序入口
- `chaincode` 链码的sandbox实现
- `consensus` 共识逻辑
- `crypto` 加／解密、签名／验证
- `log` 日志和Event
- `network` 节点之间的消息交换，包括pub/sub、p2p两种模式
- `storage` 块链、worldState的存取
- `ui` 为web前端提供双向通信

##文件目录说明
- `protobuf` 消息格式定义
- `resrouces/web` web资源
- `test` 单元测试

##Finished
- 后端模块划分
- Event的ws推送，实时状态图
- 检索api集成
- storage集成
- 实时状态图显示抽签结果显示

##Todo
- app仿真member down存在内存泄漏
- 支持加入信任证书交易，现有节点发起交易并背书即可。
- 随机抽签改为抽证书
- 创世块预置合约，证书即账户，支持创世块代币发行。账户之间代币转移。可配置交易燃烧代币，
- 检索支持对deploy交易的，chaincodeID检索，以便从持久化恢复chaincodeID。
- sandbox全局变量之间的干扰

