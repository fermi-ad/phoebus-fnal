# phoebus-fnal
Phoebus (https://github.com/ControlSystemStudio/phoebus) product for FNAL.

- Fermilab specific source code, including Phoebus/ACsys interface
- Set of install and build scripts to setup FNAL-phoebus product on the FNAL controls/internal n/w.
- For production settings and configuration files, see **epics-controls/Config/CSS/Phoebus**

## Build Requirements
- Java/JDK 17 or later, with full JDK distribution including *javac* compiler.  Recommended JDK 21
- mvn maven 3.8.6 or later
- Access to https://github.com
  
## Install and Build phoebus-fnal

A full build will include at least 2GB of disk space, so find an appropriate area.  The instructions below were run on node _vclx4_ in a private directory on the **/scratch** disk partition.

### If you are building inside the AD network set up an ssh tunnel to the outside world from your build node:

```
  ssh -fN -D 1080 outback
  export HTTPS_PROXY='socks5://localhost:1080'
```
   You will be automatically returned to your build node after a brief login to outland.  This needs to be done only once until the ssh session crashes or your server reboots.  This tunnel enables the use of the *proxychains* prefix command seen below.  

### Clone the phoebus-fnal product repo to the build location.
```
  proxychains git clone https://github.com/fermi-ad/phoebus-fnal.git
```
The proxychains command uses the above ssh tunnel to access github.com

### Download the full base Phoebus and Select version
```
   cd phoebus-fnal
   mkdir lib
   cd lib
   proxychains git clone https://github.com/ControlSystemStudio/phoebus.git
   cd phoebus
   proxychains git checkout v4.7.3
```
Currently we are using Phoebus production version 4.7.3

### Select JDK 21 [recommended]
```
   export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-21.0.3.0.9-1.el9.alma.1.x86_64
   export PATH=${JAVA_HOME}/bin:${PATH}
```
   Type _javac --version_ to make sure you really picked it up JDK 21 [important!]

### Go back up to the phoebus-fnal directory and build with maven:
```
   proxychains mvn clean install -DskipTests=true --batch-mode \
     -Ddocs=lib/phoebus/docs
```
Watch for errors.

### Run Test Version of FNAL Phoebus

```
./test-phoebus.sh
```
This script is intended for testing locally developed Phoebus builds, and as such differs from the run-phoebus.sh production launch script.
Production installation involves merging a feature branch and building in the github build enviroment.

### Include FNAL modules

The Phoebus framework has modularity that allows to have FNAL site-specific code separate from Phoebus source code.

Under the folder  `product ` there is the FNAL source code.  The following may be extended to provide more Fermilab specific features in the future


```
└── src
    └── main
        ├── java
        │   └── org
        │       └── phoebus
        │           └── <module>
        │               └── <New module>
        │                   ├── *.java
        └── resources
            └── META-INF
                └── services
                    └── org.phoebus.*

```

#### Step-by-step

This documentation uses the ACsys plugin as an example on how to include new source code into the FNAL Phoebus product.

1. Clone this repository as shown above

2. Create a new directory and add your source code:
    1. Using phoebus structure, identify which kind of module is the new app and add the new source code following the same directory structure.
`products/src/main/java/org/phoebus/<core|pv|applications>/<app_name>/`
    Example:
    ```
    └── src
    └── main
        ├── java
        │   └── org
        │       └── phoebus
        │           └── pv
        │               └── acsys
        │                   ├── ACsys_PVConn.java
        │                   ├── ACsys_PVFactory.java
        │                   └── ACsys_PV.java
    ```
3. Register the class in the phoebus configuration files.
    - Under `products/src/resources/META-INF/services/` create a configuration file using the same name as the phoebus framework. Should be something like `org.phoebus.<module>.<parent class>`
    - Add the name of the new app class into the `org.phoebus.<module>.<parent class>` . 
    Example to register ACsys into the Phoebus PVFactory:
    ```
    # File : org.phoebus.pv.PVFactory
    org.phoebus.pv.acsys.ACsys_PVFactory
    ```

3. Optional: add dependencies to `product/pom.xml`. 
   Example to include ACsys DPM library:
   ```
    <dependency>
    <groupId>gov.fnal</groupId>
    <artifactId>dae</artifactId>
    <version>1.2.3</version>
    </dependency>
   ```

  >**NOTE** 
  The phoebus framework is Maven-centric and external dependencies
  defined in the pox.xml can be downloaded.
  The 'dependencies/install-jar' directory allows adding dependencies that 
  cannot be downloaded.
  See more information at: 
  https://github.com/ControlSystemStudio/phoebus/tree/master/dependencies


## Useful links
- [Example of Phoebus product](https://github.com/ControlSystemStudio/phoebus/tree/master/phoebus-product)
- [Phoebus Architecture pdf](https://epics.anl.gov/meetings/2018-06/talks/06-14/AM/4.5-Phoebus-Architecture.pdf)
