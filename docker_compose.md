## ZooKeeper Cluster(on a single node) Setup with Docker Compose

-------------------

**Environment:**

- Red Hat Enterprise Linux Server release 6.6 (Santiago)

- Kernel: 2.6.32-504.23.4.el6.x86_64

- Docker: Server version 1.7.1
          Storage Driver devicemapper
          Execution Driver native-0.2

- Docker Compose:       

- ZooKeeper:  3.4.6-1569965, built on 02/20/2014 09:09 GMT


-------------------

**[Docker Compose](https://docs.docker.com/compose/)** is an orchestration tool that makes spinning up multi-container applications effortless.
`Dockerfile` makes user define and manage a single container, while `Compose` helps user to define a group of containers with dependency in a single `yaml` file,
and 

#### 0. Install docker

Follow the [installation guide](https://docs.docker.com/installation/) to install docker in your system

##### 1. Install Docker compose 1.4.2

1.1 Enter the `curl` command in your termial.

The command has the following format:

```bash
curl -L https://github.com/docker/compose/releases/download/1.4.2/docker-compose-`uname -s`-`uname -m` > /usr/local/bin/docker-compose
chmod +x /usr/local/bin/docker-compose
```

1.2 Apply executable permissions to the binary:

```bash
chmod +x /usr/local/bin/docker-compose
```

1.3 Test the installation

```bash
~ docker-compose --version
docker-compose version: 1.4.2

```

##### 2. Define the ZooKeeper cluster in docker compose

2.1 Create `common.yml` to define the common sections of ZooKeeper cluster members

```
zookeeper:
  image: garland/zookeeper
  net: "host"
  environment:
    - ADDITIONAL_ZOOKEEPER_1=server.1=localhost:2888:3888
    - ADDITIONAL_ZOOKEEPER_2=server.2=localhost:2889:3889
    - ADDITIONAL_ZOOKEEPER_3=server.3=localhost:2890:3890
```

2.2 Create `docker-compose.yml` to define the cluster

```
zk1:
  extends:
    file: common.yml
    service: zookeeper
  volumes:
    - /data/zk1:/tmp/zookeeper
  environment:
    - SERVER_ID=1
    - ADDITIONAL_ZOOKEEPER_4=clientPort=2181
zk2:
  extends:
    file: common.yml
    service: zookeeper
  volumes:
    - /data/zk2:/tmp/zookeeper
  environment:
    - SERVER_ID=2
    - ADDITIONAL_ZOOKEEPER_4=clientPort=2182
zk3:
  extends:
    file: common.yml
    service: zookeeper
  volumes:
    - /data/zk2:/tmp/zookeeper
  environment:
    - SERVER_ID=3
    - ADDITIONAL_ZOOKEEPER_4=clientPort=2183  
```

##### 3. Start ZooKeeper Cluster with docker compose


3.1 Put `common.yml` and `docker-compose.yml` into a directory, for example

```bash
~ pwd
/zookeeper_compose
~ ls
common.yml  docker-compose.yml
```

3.2 Start the containers with docker compose


```bash
~ docker-compose up -d
Creating zookeepercompose_zk1_1...
Creating zookeepercompose_zk2_1...
Creating zookeepercompose_zk3_1...
```

3.3 Check the status

```bash
~ docker-compose ps
         Name              Command     State   Ports
----------------------------------------------------
zookeepercompose_zk1_1   /opt/run.sh   Up
zookeepercompose_zk2_1   /opt/run.sh   Up
zookeepercompose_zk3_1   /opt/run.sh   Up
```

##### 4. Test ZooKeeper Cluster

4.1 Create a new znode in cluster member zookeepercompose_zk1_1

```
~ docker exec -it zookeepercompose_zk1_1 bash
~ /opt/zookeeper/bin/zkCli.sh -server 127.0.0.1:2181 (inside container zookeepercompose_zk1_1)
[zk: 127.0.0.1:2181(CONNECTED) 0]create /test1 abc
Connecting to 127.0.0.1:2181
Created /test1
```

4.2 Check the znode in cluster member zookeepercompose_zk2_1

<pre>
~ docker exec -it zookeepercompose_zk2_1 bash
~ /opt/zookeeper/bin/zkCli.sh -server 127.0.0.1:2182 (inside container zookeepercompose_zk2_1)
Connecting to 127.0.0.1:2182
[zk: 127.0.0.1:2182(CONNECTED) 0]<b> get /test1</b>
<b>abc</b>
[zk: 127.0.0.1:2182(CONNECTED) 0] get /test1
abc
cZxid = 0x100000004
ctime = Tue Oct 13 14:44:04 UTC 2015
mZxid = 0x100000004
mtime = Tue Oct 13 14:44:04 UTC 2015
pZxid = 0x100000004
cversion = 0
dataVersion = 0
aclVersion = 0
ephemeralOwner = 0x0
dataLength = 3
numChildren = 0
</pre>

##### 5. Develop java client ZooKeeper Cluster

5.1 Develop the code

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


5.2 compile the code

```
~  javac -cp .:/opt/zookeeper/zookeeper-3.4.6.jar org/zookeeper/demo/Demo.java
```

5.3 execute the code

```
~ java -cp /opt/zookeeper/zookeeper-3.4.6.jar:/opt/zookeeper/lib/*:.  org.zookeeper.demo.Demo
```

5.4 stop one cluster member

```
~ /opt/zookeeper/bin/zkServer.sh stop zk1.cfg
```

5.5 execute the code again

```
~ java -cp /opt/zookeeper/zookeeper-3.4.6.jar:/opt/zookeeper/lib/*:.  org.zookeeper.demo.Demo
```

##### 6. Delete ZooKeeper Cluster when finished using it

```
~  docker-compose stop
Stopping zookeepercompose_zk3_1... done
Stopping zookeepercompose_zk2_1... done
Stopping zookeepercompose_zk1_1... done

~  docker-compose rm -f
Going to remove zookeepercompose_zk3_1, zookeepercompose_zk2_1, zookeepercompose_zk1_1
Removing zookeepercompose_zk3_1... done
Removing zookeepercompose_zk2_1... done
Removing zookeepercompose_zk1_1... done

```


**Pros**

1. easy to automate deployment, to start, to stop,  to destroy
2. portable 

**Cons**

1. Have the same weakness as docker
2. Docker team doesn't suggest to put it in production

