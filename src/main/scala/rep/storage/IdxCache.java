package rep.storage;

import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap;
import com.googlecode.concurrentlinkedhashmap.Weighers;

public class IdxCache {
    private ConcurrentLinkedHashMap<String, blockindex> fileIdxs = null;

    public IdxCache(int maxSize){
         this.fileIdxs = new ConcurrentLinkedHashMap.Builder<String,blockindex>().maximumWeightedCapacity(maxSize).weigher(Weighers.singleton()).build();
    }

    public blockindex Get(String key){
        blockindex idx = null;
        if(this.fileIdxs.containsKey(key)){
            idx = this.fileIdxs.get(key);
        }
        return idx;
    }

    public void Put(String key,blockindex idx){
        this.fileIdxs.put(key,idx);
    }
}
