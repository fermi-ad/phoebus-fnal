# phoebus-fnal
FNAL phoebus product

A set of install and build scripts to setup FNAL-phoebus product on the FNAL controls/internal n/w.  
It also consists of settings and configuration needed to run cs-studio effectively on the FNAL n/w.  


### Install Phoebus-fnal

Clone the phoebus-fnal product repo to the installation location.

```
git clone https://github.com/hanlet/phoebus-fnal
./build.sh
```


### Run FNAL Phoebus

```
./run-phoebus
```

If installing on a multi-user host, edit the run-phoebus TOP to point to the phoebus installation folder.  
