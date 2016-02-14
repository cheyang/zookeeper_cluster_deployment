package org.zookeeper.demo;

import java.io.IOException;

import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooDefs.Ids;
import org.apache.zookeeper.ZooKeeper;

public class Demo {
	
	public static void main(String[] args) throws  IOException, KeeperException, InterruptedException{
		
	
		ZooKeeper zk = new ZooKeeper("localhost:2181,localhost:2182,localhost:2183", 60000, new Watcher() {
            // monitor all the events
            public void process(WatchedEvent event) {
                System.out.println("EVENT:" + event.getType());
            }
        });
		
		String key = "/test";
		
		String value = "abc";
		
		
		// create or get znode
		if(zk.exists(key, false) == null){
			
			String createPath = zk.create(key, value.getBytes(),  
                    Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);  

		    System.out.println("Created znode:"+createPath);  
			
		}else{
			byte[] data = zk.getData(key, false, null);
			
			System.out.print("Get znode:"); 
			System.out.printf("[%s,%s]", key, new String(data)); 
			System.out.println(""); 
		}

        // check root znode
        System.out.println("ls / => " + zk.getChildren("/", true));

        zk.close();
	}
	
	
}
