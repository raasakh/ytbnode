# YTB [![Build Status](https://travis-ci.org/raasakh/ytbnode.svg?branch=master)](https://travis-ci.org/raasakh/ytbnode) [![](https://images.microbadger.com/badges/version/raasakh/ytb-testnet.svg)]

In the master branch there is a code with functions that is under development. The latest release for each network can be found in the [Releases section](https://github.com/raasakh/ytbnode/releases), you can switch to the corresponding tag and build the application.

For further information please refer the official [documentation](https://docs.ytb.one).




# Installation

```
sudo apt-get update 
sudo apt-get upgrade

sudo apt install apt-transport-https
sudo apt install software-properties-common
sudo add-apt-repository -y ppa:webupd8team/java
sudo apt-get update
sudo apt-get -y install oracle-java8-installer

java -version
sudo apt install default-jre
wget https://github.com/raasakh/ytbnode/releases/download/v1.0.0/ytb-node_1.0.0.deb
sudo dpkg -i ytb-node_1.0.0.deb
sudo nano /usr/share/ytb/conf/ytb.conf
```

```
miner {
   # Enable/disable block generation
   enable = yes

   # Required number of connections (both incoming and outgoing) to attempt block generation. Setting this value to 0
   # enables "off-line generation".
   quorum = 2

   # Enable block generation only in the last block if not older the given period of time
   interval-after-last-block-then-generation-is-allowed = 1d

   # Interval between microblocks
   micro-block-interval = 5s

   # Mininmum time interval between blocks
   minimal-block-generation-offset = 1001ms

   # Max amount of transactions in key block
   max-transactions-in-key-block = 0

   # Max amount of transactions in micro block
   max-transactions-in-micro-block = 255

   # Miner references the best microblock which is at least this age
   min-micro-block-age = 6s
 }
```

sudo systemctl enable ytb.service
sudo systemctl restart ytb.service
sudo systemctl start ytb.service
sudo systemctl stop ytb.service
or
sudo service ytb restart
sudo service ytb start
sudo service ytb stop

**juornal**(exit ctrl+o)
sudo journalctl -u ytb.service -f



## Compiling Packages from source

It is only possible to create deb and fat jar packages.

### Install SBT (Scala Build Tool)

For Ubuntu/Debian:

```
echo "deb https://dl.bintray.com/sbt/debian /" | sudo tee -a /etc/apt/sources.list.d/sbt.list
sudo apt-key adv --keyserver hkp://keyserver.ubuntu.com:80 --recv 2EE0EA64E40A89B84B2DF73499E82A75642AC823
sudo apt-get update
sudo apt-get install sbt
```

You can install sbt on Mac OS X using Homebrew.

### Compile Genesis

```
sbt "test:runMain tools.GenesisBlockGenerator genesis.conf"
```

Added genesis data to ytbnet.conf

### Running

sbt "run ytbnet.conf"


### Create Package

Clone this repo and execute

```
sbt packageAll
```

.deb and .jar packages will be in /package folder. To build testnet packages use

```
sbt -Dnetwork=testnet packageAll
```




# Running Tests

`sbt test`

**Note**

If you prefer to work with _SBT_ in the interactive mode, open it with settings:
```bash
SBT_OPTS="${SBT_OPTS} -Xms512M -Xmx1536M -Xss1M -XX:+CMSClassUnloadingEnabled" sbt
```

For Java9 it should be:
```
SBT_OPTS="${SBT_OPTS} -Xms512M -Xmx1536M -Xss1M -XX:+CMSClassUnloadingEnabled --add-modules=java.xml.bind --add-exports java.base/jdk.internal.ref=ALL-UNNAMED" sbt
```

to solve the `Metaspace error` problem.




# Running Integration Tests


## TL;DR

 * Make sure you have [Docker](https://www.docker.com/get-docker) and SBT.
 * `sbt it/test`


## Customizing Tests

By default, `it/test` will do the following:
* Build a container image with the fat jar and a [template.conf](src/it/resources/template.conf). The newly-built image
  will be registered with the local Docker daemon. This image is built with [sbt-docker](https://github.com/marcuslonnberg/sbt-docker)
  plugin.
* Run the test suites from `src/it/scala`, passing docker image ID via `docker.imageId` system property.

### Logging

By [default](src/main/resources/logback.xml) all logs are written to the STDOUT. If you want to write logs, for example,
to JSON files, you should define your own logging configuration and specify a path to it in `conf/application.ini`:

```
-Dlogback.configurationFile=/path/to/your/logback.xml
```

### Debugging

Integration tests run in a forked JVM. To debug test suite code launched by SBT, you will need to add remote debug
options to `javaOptions` in `IntegrationTest` configuration:

```scala
javaOptions in IntegrationTest += "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005"
```

Debugging a node inside a container is a little more complicated: you will need to modify the `MIR_OPTS` environment
variable before starting a container.




# Collecting performance metrics

**Note**: all required tools will be installed though [Docker](https://docs.docker.com) for simplicity.

1. Install [Graphite](https://graphite.readthedocs.io/en/latest/install.html#docker), a service for collecting metrics.
2. Install [Grafana](https://grafana.com/grafana/download?platform=docker) for beautiful graphs.
3. By default all metrics are disabled. So specify _Kamon_ settings through _Java Properties_ and run the node
   with a desired config. For example, we ran _Graphite_ locally and it accepts _StatsD_ information on the `9999` port:

    ```bash
    SBT_OPTS="${SBT_OPTS} -Xms512M -Xmx1536M -Xss1M -XX:+CMSClassUnloadingEnabled \
    -Dkamon.modules.kamon-statsd.auto-start=yes \
    -Dkamon.modules.kamon-system-metrics.auto-start=yes \
    -Dkamon.statsd.hostname=localhost \
    -Dkamon.statsd.port=6565" sbt ytb-testnet.conf
    ```

    Here:
    * `-Dkamon.modules.kamon-statsd.auto-start=yes` enables custom metrics;
    * `-Dkamon.modules.kamon-system-metrics.auto-start=yes` enables metrics of _CPU_, _Memory_ and others;
    * See [application.conf](https://github.com/wavesplatform/Waves/blob/master/src/main/resources/application.conf)
      for more options.




# Acknowledgement

[<img src="https://www.yourkit.com/images/yklogo.png">](http://www.yourkit.com/java/profiler/index.jsp)  
We use YourKit full-featured Java Profiler to make Mir node faster. YourKit, LLC is the creator of innovative and intelligent tools for profiling Java and .NET applications.    
Take a look at YourKit's leading software products: 
<a href="http://www.yourkit.com/java/profiler/index.jsp">YourKit Java Profiler</a> and
<a href="http://www.yourkit.com/.net/profiler/index.jsp">YourKit .NET Profiler</a>.
