子模块说明
========================
TODO:在本文件中描述与本模块相关的问题
本存储模块主要的接口操作是StorageMgr类，代码如下：
		   val lh = StorageMgr.GetStorageMgr("testSystem");
		    
			lh.put("a_1001", "nnnn".getBytes());
			lh.put("a_1000", "uuuu".getBytes());
			println("key=a_1000\tvalue="+lh.byteToString(lh.get("a_1000")));
			lh.BeginTrans();
			lh.delete("a_1000");
			System.out.println("key=a_1000\tvalue="+lh.byteToString(lh.get("a_1000")));
			lh.put("c_1000", "dddd".getBytes());
			System.out.println("key=c_1000\tvalue="+lh.byteToString(lh.get("c_1000")));
			lh.put("c_1000", "eeee".getBytes());
			System.out.println("key=c_1000\tvalue="+lh.byteToString(lh.get("c_1000")));
			System.out.println("in trans");
			lh.printmap(lh.FindByLike("a_"));
			System.out.println("");
			lh.printmap(lh.FindByLike("c_"));
			System.out.println("");
			lh.CommitTrans();
			System.out.println("key=a_1000\tvalue="+lh.byteToString(lh.get("a_1000")));
			System.out.println("key=c_1000\tvalue="+lh.byteToString(lh.get("c_1000")));
			System.out.println("out trnas");
			lh.printmap(lh.FindByLike("a_"));
			System.out.println("");
			lh.printmap(lh.FindByLike("c_"));

获取merkle root方法在MerkleHelp类，代码如下：
         val lh = StorageMgr.GetStorageMgr("testSystem");
		  val mh = new MerkleHelp("testSystem");
			lh.put("a_1001", "nnnn".getBytes());
			lh.put("a_1000", "uuuu".getBytes());
			lh.BeginTrans();
			lh.delete("a_1000");
			lh.put("c_1000", "dddd".getBytes());
			lh.put("c_1000", "eeee".getBytes());
			lh.put("c_3000", "kkk".getBytes());
			lh.put("c_2000", "lll".getBytes());
			lh.put("c_1030", "bbb".getBytes());
			lh.put("c_1050", "eecccee".getBytes());
			lh.put("c_1070", "aaaeeee".getBytes());
			lh.put("c_1051", "mmmmeecccee".getBytes());
			val mstr = mh.computeWorldState();
			println("merkle root="+mstr);
			lh.CommitTrans();
			println("merkle root1="+mstr);