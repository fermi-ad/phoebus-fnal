# phoebus-fnal
Phoebus (https://github.com/ControlSystemStudio/phoebus) product for FNAL.

- Fermilab specific source code, including Phoebus/ACsys interface
- Set of install and build scripts to setup FNAL-phoebus product on the FNAL controls/internal n/w.
- For production settings and configuration files, see **epics-controls/Config/CSS/Phoebus**

## Running Phoebus
Our Fermilab specific Phoebus is available on all Linux **clx** nodes and can run on **cns** Windows nodes by installing **Xpra**

Steps:
1. **ssh** to outland or outback, including the **-C** ssh arguement for compression (important)
2. Make sure X11 forwarding is enabled
3. Type **launch auto** to be directed to a **clx** node selected for you
4. Type **phoebus_remote** to launch **Phoebus**

One can also launch Phoebus directly with the **/usr/local/epics/Config/CSS/Phoebus/run-phoebus.sh** script

## Build Requirements
- Java/JDK 17 or later, with full JDK distribution including *javac* compiler.  Recommended JDK 21
- mvn maven 3.8.6 or later
- Access to https://github.com
- Building the package is __only__ necessary for developers of Phoebus source code.
  
## Install and Build phoebus-fnal

A full build will include at least 2GB of disk space, so find an appropriate area.  The instructions below were run on node _vclx4_ in a private directory on the **/scratch** disk partition.  

### If you are building inside the AD network set up an ssh tunnel to the outside world from your build node:

```
  ssh -fN -D 1080 outback
  # export HTTPS_PROXY='socks5://localhost:1080'  # Don't do both
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
   proxychains git clone -b v5.0.0 https://github.com/ControlSystemStudio/phoebus.git lib/phoebus
```
Currently we are using Phoebus production version v5.0.0

### Select JDK 21 [recommended]
```
   export JAVA_HOME=/usr/lib/jvm/java-21-openjdk
   export PATH=${JAVA_HOME}/bin:${PATH}
```
   Type _javac -version_ to make sure you really picked it up JDK 21 [important!]

### GitHub Packages Authentication

When building, Maven needs to authenticate with GitHub Packages to download dependencies. This requires a [GitHub Personal Access Token (PAT)](https://docs.github.com/en/authentication/keeping-your-account-and-data-secure/creating-a-personal-access-token) with `read:packages` scopes.

You can configure Maven to use your PAT by adding a `<server>` entry to your `~/.m2/settings.xml` file.

1. Create a Personal Access Token (PAT)
2. Configure Maven's `settings.xml`:
   * If you don't have a `~/.m2/settings.xml` file, create one.
   * Add the following relevant fields, replacing `USERNAME` and `TOKEN` with your actual GitHub username and the PAT you just generated.

```xml
   <settings xmlns="http://maven.apache.org/SETTINGS/1.0.0"
  xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
  xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0
                      http://maven.apache.org/xsd/settings-1.0.0.xsd">
  
    <activeProfiles>
      <activeProfile>github</activeProfile>
    </activeProfiles>
  
    <profiles>
      <profile>
        <id>github</id>
        <repositories>
          <repository>
            <id>central</id>
            <url>https://repo1.maven.org/maven2</url>
          </repository>
          <repository>
            <id>github</id>
            <url>https://maven.pkg.github.com/OWNER/REPOSITORY</url>
            <snapshots>
              <enabled>true</enabled>
            </snapshots>
          </repository>
        </repositories>
      </profile>
    </profiles>
  
    <servers>
      <server>
        <id>github</id>
        <username>USERNAME</username>
        <password>TOKEN</password>
      </server>
    </servers>
  </settings>
```

### From the root phoebus-fnal directory build with maven:
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

4. Optional: add dependencies to `product/pom.xml`. 
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
 

## Useful links
- [Example of Phoebus product](https://github.com/ControlSystemStudio/phoebus/tree/master/phoebus-product)
- [Phoebus Architecture pdf](https://epics.anl.gov/meetings/2018-06/talks/06-14/AM/4.5-Phoebus-Architecture.pdf)
