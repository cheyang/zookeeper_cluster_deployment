## ZooKeeper Cluster(on a single node) Setup with Docker

-------------------

**Environment:**

- Red Hat Enterprise Linux Server release 6.6 (Santiago)

- Kernel: 2.6.32-504.23.4.el6.x86_64

- Docker: Server version 1.7.1
          Storage Driver devicemapper
          Execution Driver native-0.2

- ZooKeeper:  3.4.6-1569965, built on 02/20/2014 09:09 GMT


### Why use Docker Host Network Setting 
-------------------


ZooKeeper cluster runs on multiple instances in a replicated mode, also called a **ZooKeeper ensemble**. The minimum recommended number of servers is three,
and five is the most common in a production environment. All the instances in ZooKeeper cluster connect to each others on peer-to-peer communication. 

First, the member of ZooKeeper cluster needs to know its peers' ip address and port information. We know that Docker has [Container linking](https://docs.docker.com/userguide/dockerlinks/).
It's really helpful for target container to get the source container's info through Environment variables, so it can be used to describe one-way dependency relationship.(Tomcat call database,
Mysql master/slave). But because container linking only supports one-way dependency, the source container can't get information from the target container. So it can't support the application
which depends on peer-to-peer communication. 

Docker supports 4 kinds of Network Setting: none, bridge, host and container

#### Mode: none

ZooKeeper can't work without external network access

#### Mode: bridge

Bridge is the default network mode of docker, the container has independent network stack and is based on NAT. 

##### Issues

1. Each time container starts, docker daemon assigns a new network namespace and new ip address to it.  But the zookeeper cluster configuration should be created before containers create and start.

2. NAT makes the network performance bad

#### Mode: container

The container will share the network set of another container. With this mode, we can create 3 containers in bridge mode first, and we get network setting. Then we can create another 3 ZooKeeper containers
to share their network setting.

##### **Issues**

1. the container in bridge mode can't be restarted, because it will also change the ip address of the container

```
~ docker run --name test -d busybox /bin/sh -c "while true; do sleep 1; done"
~ docker inspect --format '{{ .NetworkSettings.IPAddress }}' test
172.17.0.39

~ docker restart test
test

~ docker inspect --format '{{ .NetworkSettings.IPAddress }}' test
172.17.0.40


```

#### Mode: host

container will share the host’s network stack and all interfaces from the host will be available to the container. The container’s hostname will match the hostname on the host system. But container's file 
system and process still has its own namespace. It has the same network performance as the processes.

##### **Issues**

Docker's storage driver in Redhat is devicemapper whose performance is [not so good](http://jpetazzo.github.io/assets/2015-03-03-not-so-deep-dive-into-docker-storage-drivers.html#2)


### Deploy ZooKeeper cluster in a single machine with Docker Host Network Setting
-------------------

#### 0. Install docker

Follow the [installation guide](https://docs.docker.com/installation/) to install docker in your system

#### 1. Install docker image

Pull docker image from Docker hub: [ZooKeeper docker image](https://registry.hub.docker.com/u/garland/zookeeper/) 

```
docker pull garland/zookeeper
```

Or we can build our docker image by following (https://github.com/sekka1/mesosphere-docker/blob/master/zookeeper/Dockerfile)


#### 2. Start ZooKeeper Cluster with 3 docker containers


2.1 Use the script below to avoid the port conflict of the ZooKeeper cluster members

* container zk1 is listening on localhost: 2181，2888，3888
* container zk2 is listening on localhost: 2182，2889，3889
* container zk3 is listening on localhost: 2183，2890，3890
    
```
docker run -d \
 --name=zk1 \
 --net=host \
 -v /data/zk1:/tmp/zookeeper \
 -e SERVER_ID=1 \
 -e ADDITIONAL_ZOOKEEPER_1=server.1=localhost:2888:3888 \
 -e ADDITIONAL_ZOOKEEPER_2=server.2=localhost:2889:3889 \
 -e ADDITIONAL_ZOOKEEPER_3=server.3=localhost:2890:3890 \
 -e ADDITIONAL_ZOOKEEPER_4=clientPort=2181 \
 garland/zookeeper

docker run -d \
 --name=zk2 \
 --net=host \
 -v /data/zk2:/tmp/zookeeper \
 -e SERVER_ID=2 \
 -e ADDITIONAL_ZOOKEEPER_1=server.1=localhost:2888:3888 \
 -e ADDITIONAL_ZOOKEEPER_2=server.2=localhost:2889:3889 \
 -e ADDITIONAL_ZOOKEEPER_3=server.3=localhost:2890:3890 \
 -e ADDITIONAL_ZOOKEEPER_4=clientPort=2182 \
 garland/zookeeper

docker run -d \
 --name=zk3 \
 --net=host \
 -v /data/zk3:/tmp/zookeeper \
 -e SERVER_ID=3 \
 -e ADDITIONAL_ZOOKEEPER_1=server.1=localhost:2888:3888 \
 -e ADDITIONAL_ZOOKEEPER_2=server.2=localhost:2889:3889 \
 -e ADDITIONAL_ZOOKEEPER_3=server.3=localhost:2890:3890 \
 -e ADDITIONAL_ZOOKEEPER_4=clientPort=2183 \
 garland/zookeeper
 ```
 
 
2.2. Check the ZooKeeper containers' status
 
```
~ docker ps
CONTAINER ID        IMAGE               COMMAND             CREATED             STATUS              PORTS               NAMES
c4c68cc4250b        garland/zookeeper   "/opt/run.sh"       17 seconds ago      Up 17 seconds                           zk3
0f595e47c2cc        garland/zookeeper   "/opt/run.sh"       18 seconds ago      Up 17 seconds                           zk2
1c7c6f420335        garland/zookeeper   "/opt/run.sh"       19 seconds ago      Up 18 seconds                           zk1
```

 2.3 List data directories which are exported to the host
 
 ```
~ pwd
/data
~ tree
.
├── zk1
│   ├── myid
│   └── version-2
│       ├── acceptedEpoch
│       ├── currentEpoch
│       └── snapshot.0
├── zk2
│   ├── myid
│   └── version-2
│       ├── acceptedEpoch
│       └── currentEpoch
└── zk3
    ├── myid
    └── version-2
        ├── acceptedEpoch
        ├── currentEpoch
        └── snapshot.100000000
```

##### 3. Test ZooKeeper Cluster

3.1 Create a new znode in cluster member zk1

```
~ docker exec -it zk1 bash
~ /opt/zookeeper/bin/zkCli.sh -server 127.0.0.1:2181 (inside container zk1)
[zk: 127.0.0.1:2181(CONNECTED) 0]create /test1 abc
Connecting to 127.0.0.1:2181
Created /test1
```

3.2 Check the znode in cluster member zk2

<pre>
~ docker exec -it zk2 bash
~ /opt/zookeeper/bin/zkCli.sh -server 127.0.0.1:2182 (inside container zk2)
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


##### 5. Delete ZooKeeper Cluster when finished using it

```
docker rm -f zk1
docker rm -f zk2
docker rm -f zk3
```
        
**Pros**

1. quick to start
2. support rolling update easily

**Cons**

1. It's difficult to handle logging and debugging
2. performance reduction due to IO virtualization
3. Docker image may be a concern: should we build it from scratch, or leverage docker image from docker 
hub which has potential security issue
 
    