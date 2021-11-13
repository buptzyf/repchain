package rep.storage;

import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap;
import com.googlecode.concurrentlinkedhashmap.Weighers;

public class IdxCache {
    private ConcurrentLinkedHashMap<String, blockindex> fileIdxs = null;
    private ConcurrentLinkedHashMap<String, Long>  hashcaches = null;

    public IdxCache(int maxSize){
        this.fileIdxs = new ConcurrentLinkedHashMap.Builder<String,blockindex>().maximumWeightedCapacity(maxSize).weigher(Weighers.singleton()).build();
        this.hashcaches = new ConcurrentLinkedHashMap.Builder<String,Long>().maximumWeightedCapacity(maxSize).weigher(Weighers.singleton()).build();
    }

    public blockindex GetByHeight(String key){
        blockindex idx = null;
        if(this.fileIdxs.containsKey(key)){
            idx = this.fileIdxs.get(key);
        }
        return idx;
    }

    public void PutForHeight(String key,blockindex idx){
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
