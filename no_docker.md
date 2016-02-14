### ZooKeeper Cluster(in a single node) Setup without Docker

-------------------

**Environment:**

- Red Hat Enterprise Linux Server release 6.6 (Santiago)

- Java: JRE 1.7.0 IBM Linux build pxa6470_27sr3fp10-20150708_01(SR3 FP10)

- ZooKeeper:  3.4.6-1569965, built on 02/20/2014 09:09 GMT

-------------------

##### 1. Install ZooKeeper

 Download and Extract ZooKeeper package

```
~ cd /opt
~ wget -q -O - http://apache.mirrors.pair.com/zookeeper/zookeeper-3.4.6/zookeeper-3.4.6.tar.gz | tar -xzf - -C /opt
~ mv /opt/zookeeper-3.4.6 /opt/zookeeper 
```

##### 2. Deploy ZooKeeper Cluster with 3 members

2.1 Make data directory for each cluster member: zk1, zk2 and zk3

```
~ mkdir /root/zoo/zk1
~ mkdir /root/zoo/zk2
~ mkdir /root/zoo/zk3
```

2.2 Create a file named myid in each data directory

```
~ echo "1" > /root/zoo/zk1/myid
~ echo "2" > /root/zoo/zk2/myid
~ echo "3" > /root/zoo/zk3/myid
```

2.3 Modify the cluster member's configuration files one by one


```
~ vi /opt/zookeeper/conf/zk1.cfg
tickTime=2000
initLimit=10
syncLimit=5
dataDir=/root/zoo/zk1
clientPort=2181
server.1=127.0.0.1:2888:3888
server.2=127.0.0.1:2889:3889
server.3=127.0.0.1:2890:3890


~ vi /opt/zookeeper/conf/zk2.cfg
tickTime=2000
initLimit=10
syncLimit=5
dataDir=/root/zoo/zk2
clientPort=2182
server.1=127.0.0.1:2888:3888
server.2=127.0.0.1:2889:3889
server.3=127.0.0.1:2890:3890


~ vi /opt/zookeeper/conf/zk3.cfg
tickTime=2000
initLimit=10
syncLimit=5
dataDir=/root/zoo/zk3
clientPort=2183
server.1=127.0.0.1:2888:3888
server.2=127.0.0.1:2889:3889
server.3=127.0.0.1:2890:3890
```

2.4 Start all the ZooKeeper servers

```
~ /opt/zookeeper/bin/zkServer.sh start zk1.cfg
JMX enabled by default
Using config: /opt/zookeeper/bin/../conf/zk1.cfg
Starting zookeeper ... STARTED


~ /opt/zookeeper/bin/zkServer.sh start zk2.cfg
JMX enabled by default
Using config: /opt/zookeeper/bin/../conf/zk2.cfg
Starting zookeeper ... STARTED


~ /opt/zookeeper/bin/zkServer.sh start zk3.cfg
JMX enabled by default
Using config: /opt/zookeeper/bin/../conf/zk3.cfg
Starting zookeeper ... STARTED
```


2.5 List the data directory structure

```
~ tree
.
├── zk1
│   ├── myid
│   └── version-2
│       ├── acceptedEpoch
│       ├── currentEpoch
│       ├── log.100000001
│       └── snapshot.0
├── zk2
│   ├── myid
│   └── version-2
│       ├── acceptedEpoch
│       ├── currentEpoch
│       └── log.100000001
└── zk3
    ├── myid
    └── version-2
        ├── acceptedEpoch
        ├── currentEpoch
        ├── log.100000001
        ├── snapshot.100000000
        └── zookeeper.out


```


##### 3. Test ZooKeeper Cluster

3.1 Check ZooKeeper servers' status, and find the leader and followers in this cluster

<pre>
~ /opt/zookeeper/bin/zkServer.sh status zk1.cfg
JMX enabled by default
Using config: /opt/zookeeper/bin/../conf/zk1.cfg
Mode: <b>follower</b>

~ /opt/zookeeper/bin/zkServer.sh status zk2.cfg
JMX enabled by default
Using config: /opt/zookeeper/bin/../conf/zk2.cfg
Mode: <b>leader</b>

~ /opt/zookeeper/bin/zkServer.sh status zk3.cfg
JMX enabled by default
Using config: /opt/zookeeper/bin/../conf/zk3.cfg
Mode: <b>follower</b>
</pre>

3.2 Create a new znode in cluster member zk1

```
~ /opt/zookeeper/bin/zkCli.sh -server 127.0.0.1:2181
[zk: 127.0.0.1:2181(CONNECTED) 0] <b>create /test1 abc</b>
Connecting to 127.0.0.1:2181
Created /test1
```

3.3 Check the znode in cluster member zk2

<pre>
~ /opt/zookeeper/bin/zkCli.sh -server 127.0.0.1:2182
Connecting to 127.0.0.1:2182
[zk: 127.0.0.1:2182(CONNECTED) 0]<b> get /test1</b>
<b>abc</b>
cZxid = 0x200000002
ctime = Tue Sep 29 05:06:55 CDT 2015
mZxid = 0x200000002
mtime = Tue Sep 29 05:06:55 CDT 2015
pZxid = 0x200000002
cversion = 0
dataVersion = 0
aclVersion = 0
ephemeralOwner = 0x0
dataLength = 3
numChildren = 0
</pre>


##### 4. Develop java client ZooKeeper Cluster

4.1 Develop the code

```java
package org.cheyang.zookeeper.demo;

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

```


4.2 compile the code

```
~  javac -cp .:/opt/zookeeper/zookeeper-3.4.6.jar org/zookeeper/demo/Demo.java
```

4.3 execute the code

```
~ java -cp /opt/zookeeper/zookeeper-3.4.6.jar:/opt/zookeeper/lib/*:.  org.zookeeper.demo.Demo
```

4.4 stop one cluster member

```
~ /opt/zookeeper/bin/zkServer.sh stop zk1.cfg
```

4.5 execute the code again

```
~ java -cp /opt/zookeeper/zookeeper-3.4.6.jar:/opt/zookeeper/lib/*:.  org.zookeeper.demo.Demo
```

##### Compare with docker solution

**Pros**
1. Comparing docker, better performance in network and storage
2. Easy to debug

**Cons**

1. can't support rolling upgrade
2. static cluster can't add new cluster member in runtime, 3.5.0 can support dynamic cluster(not for docker)