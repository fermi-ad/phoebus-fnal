# phoebus-fnal
Phoebus (https://github.com/ControlSystemStudio/phoebus) product for FNAL.

- Fermilab specific source code, including Phoebus/ACsys interface
- Set of install and build scripts to setup FNAL-phoebus product on the FNAL controls/internal n/w.
- Settings and configuration needed to run cs-studio effectively on the FNAL n/w.

## Build Requirements
- Java/JDK 17 or later, full JDK distribution including *javac* compiler.  Recommended JDK 21
- mvn maven 3.8.6 or later
- Access to https://github.com

- 
- Phoebus source code `git clone https://github.com/ControlSystemStudio/phoebus.git lib/phoebus`

## Install and Build phoebus-fnal

A full build will include at least 2GB of disk space, so find an appropriate area.

### If you are building inside the AD network set up an ssh tunnel to the outside world from your build node:

```
  ssh -fN -D 1080 outback
  export HTTPS_PROXY='socks5://localhost:1080'
```
   You will be automatically returned to your build node.  This needs to be done only once until the ssh session crashes.

### Clone the phoebus-fnal product repo to the installation location.
  git clone https://ghe-pip2.fnal.gov/epics-controls/phoebus-fnal.git



### Run FNAL Phoebus

```
./run-phoebus
```

If installing on a multi-user host, edit the run-phoebus TOP to point to the phoebus installation folder.  


### Include FNAL modules

The Phoebus framework has modularity that allows to have FNAL site-specific code separate from Phoebus source code.

Under the folder  `product ` there is the FNAL source code.


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

This documentation uses the ACsus plugin as an example on how to include new source code into the FNAL Phoebus product.

1. Clone this repository
```
git clone https://ghe-pip2.fnal.gov/epics-controls/phoebus-fnal.git
```
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
