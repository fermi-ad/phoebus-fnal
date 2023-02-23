# phoebus-fnal
Phoebus (https://github.com/ControlSystemStudio/phoebus) product for FNAL.

- Fermilab specific source code.
- Set of install and build scripts to setup FNAL-phoebus product on the FNAL controls/internal n/w.
- Settings and configuration needed to run cs-studio effectively on the FNAL n/w.

## Requirements
- Java
- mvn 3
- Phoebus source code `git clone https://github.com/ControlSystemStudio/phoebus.git lib/phoebus`

## Build & Run with Maven
```
mvn -DskipTests clean install
java -jar product-fnal/target/product-fnal-*.jar
````

## Install Phoebus-fnal

Clone the phoebus-fnal product repo to the installation location.

```
git clone https://ghe-pip2.fnal.gov/epics-controls/phoebus-fnal.git
./build.sh
```

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
   For now, we are assuming that internal dependencies are previously installed into the build server.
   Example to include ACsys DPM library:
   ```
    <dependency>
    <groupId>gov.fnal</groupId>
    <artifactId>dae</artifactId>
    <version>1.2.3</version>
    </dependency>
   ```


## Useful links
- [Example of Phoebus product](https://github.com/ControlSystemStudio/phoebus/tree/master/phoebus-product)
- [Phoebus Architecture pdf](https://epics.anl.gov/meetings/2018-06/talks/06-14/AM/4.5-Phoebus-Architecture.pdf)
