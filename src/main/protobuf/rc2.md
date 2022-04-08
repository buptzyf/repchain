# Protocol Documentation
<a name="top"></a>

## Table of Contents

- [proto/rc2.proto](#proto_rc2-proto)
    - [ActionResult](#rep-proto-ActionResult)
    - [Authorize](#rep-proto-Authorize)
    - [BindCertToAuthorize](#rep-proto-BindCertToAuthorize)
    - [Block](#rep-proto-Block)
    - [Block.StatesGetEntry](#rep-proto-Block-StatesGetEntry)
    - [Block.StatesSetEntry](#rep-proto-Block-StatesSetEntry)
    - [BlockHeader](#rep-proto-BlockHeader)
    - [BlockchainInfo](#rep-proto-BlockchainInfo)
    - [CertId](#rep-proto-CertId)
    - [Certificate](#rep-proto-Certificate)
    - [ChaincodeDeploy](#rep-proto-ChaincodeDeploy)
    - [ChaincodeId](#rep-proto-ChaincodeId)
    - [ChaincodeInput](#rep-proto-ChaincodeInput)
    - [Credential](#rep-proto-Credential)
    - [CredentialContentMetadata](#rep-proto-CredentialContentMetadata)
    - [Event](#rep-proto-Event)
    - [MetadataDefine](#rep-proto-MetadataDefine)
    - [Operate](#rep-proto-Operate)
    - [Signature](#rep-proto-Signature)
    - [Signer](#rep-proto-Signer)
    - [StateProof](#rep-proto-StateProof)
    - [Transaction](#rep-proto-Transaction)
    - [TransactionError](#rep-proto-TransactionError)
    - [TransactionResult](#rep-proto-TransactionResult)
    - [TransactionResult.StatesGetEntry](#rep-proto-TransactionResult-StatesGetEntry)
    - [TransactionResult.StatesSetEntry](#rep-proto-TransactionResult-StatesSetEntry)
  
    - [Authorize.TransferType](#rep-proto-Authorize-TransferType)
    - [Certificate.CertType](#rep-proto-Certificate-CertType)
    - [ChaincodeDeploy.CodeType](#rep-proto-ChaincodeDeploy-CodeType)
    - [ChaincodeDeploy.ContractClassification](#rep-proto-ChaincodeDeploy-ContractClassification)
    - [ChaincodeDeploy.RunType](#rep-proto-ChaincodeDeploy-RunType)
    - [ChaincodeDeploy.StateType](#rep-proto-ChaincodeDeploy-StateType)
    - [Event.Action](#rep-proto-Event-Action)
    - [Operate.OperateType](#rep-proto-Operate-OperateType)
    - [Transaction.Type](#rep-proto-Transaction-Type)
  
- [Scalar Value Types](#scalar-value-types)



<a name="proto_rc2-proto"></a>
<p align="right"><a href="#top">Top</a></p>

## proto/rc2.proto



<a name="rep-proto-ActionResult"></a>

### ActionResult
合约方法返回


| Field | Type | Label | Description |
| ----- | ---- | ----- | ----------- |
| code | [int32](#int32) |  | 异常编号 |
| reason | [string](#string) |  | 异常原因 |






<a name="rep-proto-Authorize"></a>

### Authorize
授权


| Field | Type | Label | Description |
| ----- | ---- | ----- | ----------- |
| id | [string](#string) |  | 权限id |
| grant | [string](#string) |  | 授权者，credit code或者rdid |
| granted | [string](#string) | repeated | 操作权限的被授予者,credit code或者rdid |
| op_id | [string](#string) | repeated | 操作id |
| is_transfer | [Authorize.TransferType](#rep-proto-Authorize-TransferType) |  | 权限让渡类型- |
| create_time | [google.protobuf.Timestamp](#google-protobuf-Timestamp) |  | 建立时间 |
| disable_time | [google.protobuf.Timestamp](#google-protobuf-Timestamp) |  | 禁用时间 |
| authorize_valid | [bool](#bool) |  | 是否有效，默认有效，否则为禁用，值=false |
| version | [string](#string) |  | 版本号,默认1.0 |






<a name="rep-proto-BindCertToAuthorize"></a>

### BindCertToAuthorize
授权目标账户证书


| Field | Type | Label | Description |
| ----- | ---- | ----- | ----------- |
| authorize_id | [string](#string) |  | 权限id |
| granted | [CertId](#rep-proto-CertId) |  | 目标账户的证书Id |
| version | [string](#string) |  | 版本号，默认1.0 |






<a name="rep-proto-Block"></a>

### Block



| Field | Type | Label | Description |
| ----- | ---- | ----- | ----------- |
| header | [BlockHeader](#rep-proto-BlockHeader) |  | 区块头 |
| transactions | [Transaction](#rep-proto-Transaction) | repeated | 顺序排列的交易 |
| transaction_errors | [TransactionError](#rep-proto-TransactionError) | repeated | 错误交易类型及原因 |
| states_get | [Block.StatesGetEntry](#rep-proto-Block-StatesGetEntry) | repeated | 轻节点验证：区块读取状态集合，参与hashOfBlock |
| states_set | [Block.StatesSetEntry](#rep-proto-Block-StatesSetEntry) | repeated | 轻节点验证：区块写入/迁移状态集合，参与hashOfBlock |
| reg_tx | [Transaction](#rep-proto-Transaction) |  | 内容监管：监管结论交易，带区块高度参数，配置为是否强制， 可以是针对本区块的监管结论，也可以是针对历史区块的监管结论 |






<a name="rep-proto-Block-StatesGetEntry"></a>

### Block.StatesGetEntry



| Field | Type | Label | Description |
| ----- | ---- | ----- | ----------- |
| key | [string](#string) |  |  |
| value | [bytes](#bytes) |  |  |






<a name="rep-proto-Block-StatesSetEntry"></a>

### Block.StatesSetEntry



| Field | Type | Label | Description |
| ----- | ---- | ----- | ----------- |
| key | [string](#string) |  |  |
| value | [bytes](#bytes) |  |  |






<a name="rep-proto-BlockHeader"></a>

### BlockHeader



| Field | Type | Label | Description |
| ----- | ---- | ----- | ----------- |
| version | [uint32](#uint32) |  | version - 区块数据版本,以便数据格式升级 |
| height | [uint64](#uint64) |  | 区块高度, 从1开始 |
| commit_tx | [bytes](#bytes) |  | 区块内交易存在性承诺，root value |
| commit_tx_err | [bytes](#bytes) |  | 出错交易非成员承诺，由于出错交易也会打入区块， 所以在出具交易存在性证明同时，需要出具该交易不在出错交易集合中的证明 区块内错误交易存在性承诺 |
| hash_present | [bytes](#bytes) |  | 当前区块Hash |
| hash_previous | [bytes](#bytes) |  | 前一区块Hash |
| commit_state | [bytes](#bytes) |  | 状态在区块内的存在性承诺 |
| commit_state_global | [bytes](#bytes) |  | 全局非过期状态不存在承诺，证明某个状态当前没有更新的值。 将更新的状态复合名（状态名&#43;版本号）写入到该承诺 |
| height_expired | [uint64](#uint64) |  | 过期区块高度 |
| endorsements | [Signature](#rep-proto-Signature) | repeated | 背书签名集合,必须包含出块人签名,不参与hashOfBlock |






<a name="rep-proto-BlockchainInfo"></a>

### BlockchainInfo
链概要信息


| Field | Type | Label | Description |
| ----- | ---- | ----- | ----------- |
| height | [uint64](#uint64) |  | 当前高度 |
| totalTransactions | [uint64](#uint64) |  | 签名交易总数 |
| currentBlockHash | [bytes](#bytes) |  | 当前区块Hash |
| previousBlockHash | [bytes](#bytes) |  | 前一区块Hash |
| currentStateHash | [bytes](#bytes) |  | 当前账本状态承诺 |






<a name="rep-proto-CertId"></a>

### CertId
账户证书标示


| Field | Type | Label | Description |
| ----- | ---- | ----- | ----------- |
| credit_code | [string](#string) |  | 社会信用代码（个人或机构）或者DID，推荐使用DID |
| cert_name | [string](#string) |  | 当前用户注册证书的名称 |
| version | [string](#string) |  | 默认1.0 |






<a name="rep-proto-Certificate"></a>

### Certificate
账户证书标示


| Field | Type | Label | Description |
| ----- | ---- | ----- | ----------- |
| certificate | [string](#string) |  | 内含签发机构 |
| alg_type | [string](#string) |  | 直接填写具体算法，如:ECDSA下的SHA1withECDSA,SHA512withECDSA等 |
| cert_valid | [bool](#bool) |  | 证书是否有效，true 有效；false 无效；protobuf默认该值为false |
| reg_Time | [google.protobuf.Timestamp](#google-protobuf-Timestamp) |  | 注册时间 |
| unreg_Time | [google.protobuf.Timestamp](#google-protobuf-Timestamp) |  | 注销时间 |
| cert_type | [Certificate.CertType](#rep-proto-Certificate-CertType) |  | 证书类型 |
| id | [CertId](#rep-proto-CertId) |  | 证书名称 |
| cert_hash | [string](#string) |  | 证书的SHA256 |
| version | [string](#string) |  | 默认1.0 |






<a name="rep-proto-ChaincodeDeploy"></a>

### ChaincodeDeploy
合约定义,部署/升级


| Field | Type | Label | Description |
| ----- | ---- | ----- | ----------- |
| timeout | [int32](#int32) |  | 部署超时 |
| code_package | [string](#string) |  | 完整的代码内容 |
| legal_prose | [string](#string) |  | 合约规则的法律描述文本 |
| c_type | [ChaincodeDeploy.CodeType](#rep-proto-ChaincodeDeploy-CodeType) |  | 合约语言类型 |
| r_type | [ChaincodeDeploy.RunType](#rep-proto-ChaincodeDeploy-RunType) |  | 合约串行/并行调度类型 |
| s_type | [ChaincodeDeploy.StateType](#rep-proto-ChaincodeDeploy-StateType) |  | 合约状态证明模式 |
| init_parameter | [string](#string) |  | 合约初始化参数 |
| cclassification | [ChaincodeDeploy.ContractClassification](#rep-proto-ChaincodeDeploy-ContractClassification) |  | 合约等级 |






<a name="rep-proto-ChaincodeId"></a>

### ChaincodeId
指定合约名称和版本,确定唯一的合约代码标识


| Field | Type | Label | Description |
| ----- | ---- | ----- | ----------- |
| chaincodeName | [string](#string) |  | 合约名称 |
| version | [int32](#int32) |  | 合约版本号 |






<a name="rep-proto-ChaincodeInput"></a>

### ChaincodeInput
合约方法调用及实际参数


| Field | Type | Label | Description |
| ----- | ---- | ----- | ----------- |
| function | [string](#string) |  | 合约方法名称 |
| args | [string](#string) | repeated | 合约方法参数 |






<a name="rep-proto-Credential"></a>

### Credential
凭据


| Field | Type | Label | Description |
| ----- | ---- | ----- | ----------- |
| id | [string](#string) |  | 凭据id，用于查询方便 |
| credential_type | [string](#string) |  | 凭据数据的类型 |
| credential_type_version | [string](#string) |  | 凭据数据的类型的版本 |
| granter | [CertId](#rep-proto-CertId) |  | 凭据的授予人 |
| grant_time | [google.protobuf.Timestamp](#google-protobuf-Timestamp) |  | 凭据的发放授予时间 |
| credential_holder | [string](#string) |  | 凭据的持有人，使用did |
| credential_content | [string](#string) |  | 凭据的内容 |
| credential_content_hash | [string](#string) |  | 凭据内容的hash，算法为SHA256 |
| signature_alg_type | [string](#string) |  | 签名算法 |
| signature_value | [string](#string) |  | 签名值 |
| credential_valid | [bool](#bool) |  | 凭据是否有效 |
| version | [string](#string) |  | 默认1.0 |






<a name="rep-proto-CredentialContentMetadata"></a>

### CredentialContentMetadata
凭据内容元数据


| Field | Type | Label | Description |
| ----- | ---- | ----- | ----------- |
| id | [string](#string) |  | ccm id 凭据内容元数据id |
| publisher | [string](#string) |  | 凭据元数据的发布者的did |
| metadata | [MetadataDefine](#rep-proto-MetadataDefine) | repeated | 凭据元数据定义 |
| meta_version | [string](#string) |  | 元数据版本 |
| update_time | [google.protobuf.Timestamp](#google-protobuf-Timestamp) |  | 建立时间 |
| version | [string](#string) |  | 凭据内容版本 |






<a name="rep-proto-Event"></a>

### Event
Event 用于图形展示的事件


| Field | Type | Label | Description |
| ----- | ---- | ----- | ----------- |
| from | [string](#string) |  | 来源addr |
| to | [string](#string) |  | 发送addr，如果是广播（需要定义一个） |
| action | [Event.Action](#rep-proto-Event-Action) |  |  |
| blk | [Block](#rep-proto-Block) |  | 事件关联区块 |






<a name="rep-proto-MetadataDefine"></a>

### MetadataDefine
凭据元数据定义


| Field | Type | Label | Description |
| ----- | ---- | ----- | ----------- |
| key | [string](#string) |  | 元数据名 |
| is_open | [bool](#bool) |  | 是否可见 |
| childs | [MetadataDefine](#rep-proto-MetadataDefine) | repeated | 子凭据元数据定义 |






<a name="rep-proto-Operate"></a>

### Operate
授权操作


| Field | Type | Label | Description |
| ----- | ---- | ----- | ----------- |
| op_id | [string](#string) |  | 操作的id，通过uuid自动生成，合约需要查重 |
| description | [string](#string) |  | 操作的描述，用于显示 |
| register | [string](#string) |  | 操作的注册者，使用credit code或者rdid |
| is_publish | [bool](#bool) |  | 操作是否属于公开，公开表示不需要授权，只要拥有rdid就可以调用；不公开，需要授权；默认false |
| operate_type | [Operate.OperateType](#rep-proto-Operate-OperateType) |  | 操作类型表示是否属于服务类型； |
| operate_service_name | [string](#string) | repeated | 服务名称 |
| operate_endpoint | [string](#string) |  | 服务器地址 |
| auth_full_name | [string](#string) |  | 限制内容，具体操作限制，如访问的链的合约的方法 |
| create_time | [google.protobuf.Timestamp](#google-protobuf-Timestamp) |  | 建立时间 |
| disable_time | [google.protobuf.Timestamp](#google-protobuf-Timestamp) |  | 禁用时间 |
| op_valid | [bool](#bool) |  | 是否有效，默认有效，否则为禁用，值=false |
| version | [string](#string) |  | 默认1.0 |






<a name="rep-proto-Signature"></a>

### Signature
签名，可用于Transaction签名和Block签名


| Field | Type | Label | Description |
| ----- | ---- | ----- | ----------- |
| cert_id | [CertId](#rep-proto-CertId) |  | 证书标识 |
| tm_local | [google.protobuf.Timestamp](#google-protobuf-Timestamp) |  | 签名时间 |
| signature | [bytes](#bytes) |  | 签名数据 |






<a name="rep-proto-Signer"></a>

### Signer
账户


| Field | Type | Label | Description |
| ----- | ---- | ----- | ----------- |
| name | [string](#string) |  | 注册者的名称或者昵称 |
| credit_code | [string](#string) |  | 社会信用代码（个人或机构）或者DID，推荐使用DID |
| mobile | [string](#string) |  | 手机号码,用于接收通知 |
| cert_names | [string](#string) | repeated | 存放用户所有证书的名称，用户具体的证书单独存放，方便证书的操作，用户在增加证书的时候，在这个列表中增加证书的名称，did_certname。 |
| authorize_ids | [string](#string) | repeated | 存放用户所有授权的操作id。 |
| operate_ids | [string](#string) | repeated | 存放用户所有注册的操作id。 |
| credential_metadata_ids | [string](#string) | repeated | 存放用户凭据id。 |
| authentication_certs | [Certificate](#rep-proto-Certificate) | repeated | 存放身份验证证书 |
| signer_info | [string](#string) |  | 存放用户身份的详细信息，采用json格式。通常不用存储，由身份管理者存储 |
| create_time | [google.protobuf.Timestamp](#google-protobuf-Timestamp) |  | 建立时间 |
| disable_time | [google.protobuf.Timestamp](#google-protobuf-Timestamp) |  | 禁用时间 |
| signer_valid | [bool](#bool) |  | 是否有效，默认有效，否则为禁用，值=false |
| version | [string](#string) |  | 默认1.0 |






<a name="rep-proto-StateProof"></a>

### StateProof
状态存在性证明


| Field | Type | Label | Description |
| ----- | ---- | ----- | ----------- |
| key | [string](#string) |  | 状态名 |
| value | [bytes](#bytes) |  | 状态值 |
| proof | [bytes](#bytes) |  | 状态存在性证明 |






<a name="rep-proto-Transaction"></a>

### Transaction
签名交易


| Field | Type | Label | Description |
| ----- | ---- | ----- | ----------- |
| id | [string](#string) |  |  |
| type | [Transaction.Type](#rep-proto-Transaction-Type) |  | 签名交易类型 |
| cid | [ChaincodeId](#rep-proto-ChaincodeId) |  | 合约标识 |
| spec | [ChaincodeDeploy](#rep-proto-ChaincodeDeploy) |  | 部署合约的签名交易方法参数 |
| ipt | [ChaincodeInput](#rep-proto-ChaincodeInput) |  | 调用合约的签名交易方法参数 |
| state | [bool](#bool) |  | 启用/禁用合约的签名交易方法参数 |
| gas_limit | [uint32](#uint32) |  | 可选，如果有设置按照预设的资源消耗,超出则终止执行；否则不限制。 |
| oid | [string](#string) |  | 重放举证：交易实例Id，可选, 导出实例时，要求提供同一区块内部， 同一合约实例的交易顺序及证明 默认空为全局的实例id， |
| signature | [Signature](#rep-proto-Signature) |  | 交易签发者签名 |






<a name="rep-proto-TransactionError"></a>

### TransactionError
异常交易出错描述


| Field | Type | Label | Description |
| ----- | ---- | ----- | ----------- |
| txId | [string](#string) |  | 唯一标识一个Transaction |
| code | [int32](#int32) |  | 异常代码 |
| reason | [string](#string) |  | 异常原因描述 |






<a name="rep-proto-TransactionResult"></a>

### TransactionResult
交易执行结果


| Field | Type | Label | Description |
| ----- | ---- | ----- | ----------- |
| txId | [string](#string) |  | 唯一标识一个Transaction |
| states_get | [TransactionResult.StatesGetEntry](#rep-proto-TransactionResult-StatesGetEntry) | repeated | 交易读取状态集合 |
| states_set | [TransactionResult.StatesSetEntry](#rep-proto-TransactionResult-StatesSetEntry) | repeated | 交易写入/迁移状态集合 |
| err | [ActionResult](#rep-proto-ActionResult) |  |  |






<a name="rep-proto-TransactionResult-StatesGetEntry"></a>

### TransactionResult.StatesGetEntry



| Field | Type | Label | Description |
| ----- | ---- | ----- | ----------- |
| key | [string](#string) |  |  |
| value | [bytes](#bytes) |  |  |






<a name="rep-proto-TransactionResult-StatesSetEntry"></a>

### TransactionResult.StatesSetEntry



| Field | Type | Label | Description |
| ----- | ---- | ----- | ----------- |
| key | [string](#string) |  |  |
| value | [bytes](#bytes) |  |  |





 


<a name="rep-proto-Authorize-TransferType"></a>

### Authorize.TransferType


| Name | Number | Description |
| ---- | ------ | ----------- |
| TRANSFER_DISABLE | 0 | 权限让渡类型-不能让渡； |
| TRANSFER_ONCE | 1 | 权限让渡类型-让渡一次； |
| TRANSFER_REPEATEDLY | 2 | 权限让渡类型-可以无限次让渡 |



<a name="rep-proto-Certificate-CertType"></a>

### Certificate.CertType


| Name | Number | Description |
| ---- | ------ | ----------- |
| CERT_UNDEFINED | 0 | 未定义证书类型 |
| CERT_AUTHENTICATION | 1 | 1=普通证书 |
| CERT_CUSTOM | 2 | 2=身份验证证书 |



<a name="rep-proto-ChaincodeDeploy-CodeType"></a>

### ChaincodeDeploy.CodeType
合约语言类型

| Name | Number | Description |
| ---- | ------ | ----------- |
| CODE_UNDEFINED | 0 | 未定义合约语言 |
| CODE_JAVASCRIPT | 1 | JavaScript合约语言 |
| CODE_SCALA | 2 | Scala合约语言 |
| CODE_VCL_DLL | 3 | 合约多语言：vcl合约 VCL 以动态库加载执行 |
| CODE_VCL_EXE | 4 | VCL 以可执行代码加载执行 |
| CODE_VCL_WASM | 5 | VCL 以WASM加载执行 |
| CODE_WASM | 6 | WASM字节码合约 |



<a name="rep-proto-ChaincodeDeploy-ContractClassification"></a>

### ChaincodeDeploy.ContractClassification
合约分级 系统合约 用户合约

| Name | Number | Description |
| ---- | ------ | ----------- |
| CONTRACT_UNDEFINED | 0 | 未定义等级 |
| CONTRACT_SYSTEM | 1 | 系统内置合约 |
| CONTRACT_CUSTOM | 2 | 用户合约 |



<a name="rep-proto-ChaincodeDeploy-RunType"></a>

### ChaincodeDeploy.RunType
指定串行并行执行

| Name | Number | Description |
| ---- | ------ | ----------- |
| RUN_UNDEFINED | 0 |  |
| RUN_SERIAL | 1 | 指定串行执行 |
| RUN_PARALLEL | 2 | 指定并行执行 |
| RUN_OPTIONAL | 3 | 由调度模块决定 |



<a name="rep-proto-ChaincodeDeploy-StateType"></a>

### ChaincodeDeploy.StateType
轻节点证明：合约可决定是否需要在全局状态中维护存在性证明
例如:存证类合约，由于状态值不会反复修改，无需全局存在性证明

| Name | Number | Description |
| ---- | ------ | ----------- |
| STATE_UNDEFINED | 0 | 未定义模式 |
| STATE_BLOCK | 1 | 区块内状态证明，用于存证类合约 |
| STATE_GLOBAL | 2 | 全局状态证明，用于资产类合约 |



<a name="rep-proto-Event-Action"></a>

### Event.Action
event事件

| Name | Number | Description |
| ---- | ------ | ----------- |
| SUBSCRIBE_TOPIC | 0 | 订阅主题 |
| TRANSACTION | 1 | 签名交易 |
| BLOCK_NEW | 2 | 新出区块 |
| BLOCK_ENDORSEMENT | 3 | 区块背书请求 |
| ENDORSEMENT | 4 | 背书 |
| MEMBER_UP | 5 | 组网节点入网 |
| MEMBER_DOWN | 6 | 组网节点离网 |
| CANDIDATOR | 7 | 下轮抽签 |
| GENESIS_BLOCK | 8 | 创世块加载 |
| BLOCK_SYNC | 9 | 区块同步请求 |
| BLOCK_SYNC_DATA | 10 | 区块同步 |
| BLOCK_SYNC_SUC | 11 | 区块同步完成 |



<a name="rep-proto-Operate-OperateType"></a>

### Operate.OperateType


| Name | Number | Description |
| ---- | ------ | ----------- |
| OPERATE_UNDEFINED | 0 | 未定义操作类型 |
| OPERATE_CONTRACT | 1 | 合约操作类型，需要提供合约方法的访问； |
| OPERATE_SERVICE | 2 | 服务操作类型，服务类型只需要提供服务器和服务名就可以； |



<a name="rep-proto-Transaction-Type"></a>

### Transaction.Type


| Name | Number | Description |
| ---- | ------ | ----------- |
| UNDEFINED | 0 |  |
| CHAINCODE_DEPLOY | 1 | 用于部署合约的签名交易 |
| CHAINCODE_INVOKE | 2 | 用于调用合约方法的签名交易 |
| CHAINCODE_SET_STATE | 3 | 用于启用/禁用合约的签名交易 |


 

 

 



## Scalar Value Types

| .proto Type | Notes | C++ | Java | Python | Go | C# | PHP | Ruby |
| ----------- | ----- | --- | ---- | ------ | -- | -- | --- | ---- |
| <a name="double" /> double |  | double | double | float | float64 | double | float | Float |
| <a name="float" /> float |  | float | float | float | float32 | float | float | Float |
| <a name="int32" /> int32 | Uses variable-length encoding. Inefficient for encoding negative numbers – if your field is likely to have negative values, use sint32 instead. | int32 | int | int | int32 | int | integer | Bignum or Fixnum (as required) |
| <a name="int64" /> int64 | Uses variable-length encoding. Inefficient for encoding negative numbers – if your field is likely to have negative values, use sint64 instead. | int64 | long | int/long | int64 | long | integer/string | Bignum |
| <a name="uint32" /> uint32 | Uses variable-length encoding. | uint32 | int | int/long | uint32 | uint | integer | Bignum or Fixnum (as required) |
| <a name="uint64" /> uint64 | Uses variable-length encoding. | uint64 | long | int/long | uint64 | ulong | integer/string | Bignum or Fixnum (as required) |
| <a name="sint32" /> sint32 | Uses variable-length encoding. Signed int value. These more efficiently encode negative numbers than regular int32s. | int32 | int | int | int32 | int | integer | Bignum or Fixnum (as required) |
| <a name="sint64" /> sint64 | Uses variable-length encoding. Signed int value. These more efficiently encode negative numbers than regular int64s. | int64 | long | int/long | int64 | long | integer/string | Bignum |
| <a name="fixed32" /> fixed32 | Always four bytes. More efficient than uint32 if values are often greater than 2^28. | uint32 | int | int | uint32 | uint | integer | Bignum or Fixnum (as required) |
| <a name="fixed64" /> fixed64 | Always eight bytes. More efficient than uint64 if values are often greater than 2^56. | uint64 | long | int/long | uint64 | ulong | integer/string | Bignum |
| <a name="sfixed32" /> sfixed32 | Always four bytes. | int32 | int | int | int32 | int | integer | Bignum or Fixnum (as required) |
| <a name="sfixed64" /> sfixed64 | Always eight bytes. | int64 | long | int/long | int64 | long | integer/string | Bignum |
| <a name="bool" /> bool |  | bool | boolean | boolean | bool | bool | boolean | TrueClass/FalseClass |
| <a name="string" /> string | A string must always contain UTF-8 encoded or 7-bit ASCII text. | string | String | str/unicode | string | string | string | String (UTF-8) |
| <a name="bytes" /> bytes | May contain any arbitrary sequence of bytes. | string | ByteString | str | []byte | ByteString | string | String (ASCII-8BIT) |

