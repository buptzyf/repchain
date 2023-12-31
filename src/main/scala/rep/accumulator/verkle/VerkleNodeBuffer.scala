package rep.accumulator.verkle

import com.googlecode.concurrentlinkedhashmap.{ConcurrentLinkedHashMap, Weighers}
import rep.app.system.RepChainSystemContext


class VerkleNodeBuffer(ctx:RepChainSystemContext) {
  final val cacheMaxSize = 100000
  final val leafNodePrefix = "leaf-"
  final val middleNodePrefix = "middle-"
  final protected implicit val cache = new ConcurrentLinkedHashMap.Builder[String, Option[Any]]()
    .maximumWeightedCapacity(cacheMaxSize)
    .weigher(Weighers.singleton[Option[Any]]).build

  private def createLeafNode(nodeId:Array[Int]):LeafNode={
    val key = this.leafNodePrefix+util.verkleTool.getKey(nodeId)
    val leaf_new = new LeafNode(this.ctx, nodeId)
    val leaf = this.cache.putIfAbsent(key, Some(leaf_new))
    if (leaf != null) {
      leaf.get.asInstanceOf[LeafNode]
    } else {
      leaf_new
    }
  }

  def readLeafNode(nodeId:Array[Int]):LeafNode={
    val name = this.leafNodePrefix+util.verkleTool.getKey(nodeId)
    if(this.cache.containsKey(name)){
      val obj = this.cache.get(name)
      if(obj != None){
        obj.get.asInstanceOf[LeafNode]
      }else{
        createLeafNode(nodeId)
      }
    }else{
      createLeafNode(nodeId)
    }
  }

  private def createMiddleNode(nodeId: Array[Int]): MiddleNode = {
    val key = this.middleNodePrefix + (if(nodeId == null) "root" else util.verkleTool.getKey(nodeId))
    val middle_new = new MiddleNode(this.ctx, nodeId)
    val middle = this.cache.putIfAbsent(key, Some(middle_new))
    if (middle != null) {
      middle.get.asInstanceOf[MiddleNode]
    } else {
      middle_new
    }
  }

  def readMiddleNode(nodeId: Array[Int]): MiddleNode = {
    val name = this.middleNodePrefix + (if(nodeId == null) "root" else util.verkleTool.getKey(nodeId))
    if (this.cache.containsKey(name)) {
      val obj = this.cache.get(name)
      if (obj != None) {
        obj.get.asInstanceOf[MiddleNode]
      } else {
        createMiddleNode(nodeId)
      }
    } else {
      createMiddleNode(nodeId)
    }
  }

}
