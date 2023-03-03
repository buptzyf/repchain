package rep.storage;

import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap;
import com.googlecode.concurrentlinkedhashmap.Weighers;
import rep.storage.chain.block.BlockIndex;

public class IdxCache {
    private ConcurrentLinkedHashMap<String, BlockIndex> fileIdxs = null;
    private ConcurrentLinkedHashMap<String, Long>  hashcaches = null;

    public IdxCache(int maxSize){
        this.fileIdxs = new ConcurrentLinkedHashMap.Builder<String,BlockIndex>().maximumWeightedCapacity(maxSize).weigher(Weighers.singleton()).build();
        this.hashcaches = new ConcurrentLinkedHashMap.Builder<String,Long>().maximumWeightedCapacity(maxSize).weigher(Weighers.singleton()).build();
    }

    public BlockIndex GetByHeight(String key){
        BlockIndex idx = null;
        if(this.fileIdxs.containsKey(key)){
            idx = this.fileIdxs.get(key);
        }
        return idx;
    }

    public void PutForHeight(String key,BlockIndex idx){
        this.fileIdxs.put(key,idx);
    }

    public void PutForHash(String key,Long height){
        this.hashcaches.put(key,height);
    }

    public Long GetHeightByHash(String hash){
        Long h = -1l;
        if(this.hashcaches.containsKey(hash)){
            h = this.hashcaches.get(hash);
        }
        return h;
    }
}
