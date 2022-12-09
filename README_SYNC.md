### [同步节点](https://gitee.com/BTAJL/repchain/tree/dev_jdk13_2.0.0.0_sync/)

同步节点不加入RepChain组网，不参与共识、背书。可以将同步节点理解为一个客户端，用来同步指定节点（RepChain网络内的节点）的区块数据，以及向该节点提交交易。

### 使用说明

#### 同步节点的[配置](https://gitee.com/BTAJL/repchain/blob/dev_jdk13_2.0.0.0_sync/conf/921000006e0012v696.node5/system.conf)

* 种子节点需配置为自己

  ```yaml
  seed-nodes = ["akka://Repchain@127.0.0.1:22526"] // 其中ip和port分别为本机的ip以及所绑定的端口
  ```

* 同步相关的配置

  ```yaml
  mode {
      sync = true  // 是否是同步记账节点模式，默认为false，如果设置为true，则种子节点要设置为本节点
      host = "localhost:9081"  // 向该host去同步区块，目标节点(RepChain网络内节点)的api服务host
      http_mode = 0 // 默认使用http，目标节点(RepChain网络内节点)的api服务所使用的方式：http协议模式（向指定节点请求的方式） 0->http; 1->https
      ssl {
        key-store = "jks/sync/121000005l35120456.node1.jks"
        key-password = "123"
        key-store-password = "123"
        trust-store = "jks/sync/mytruststore.jks"
        trust-store-password = "changeme"
      }
    }
  ```

#### 启动方式

* 打包时设置主类为`Repchain_Single`或`RepChain_Management`

  ```shell
  // 假设节点921000006e0012v696.node5为同步节点
  java -Dlogback.configurationFile=conf/logback.xml -jar RepChain.jar 921000006e0012v696.node5
  ```


#### 其他注意事项

* 客户端向同步节点提交交易，同步节点会将接受到的交易转发到RepChain网络中指定的节点，并将结果原样的返回给客户端
* 同步相关配置里的ssl配置项，指的是同步节点是使用http还是https向指定节点发送请求
