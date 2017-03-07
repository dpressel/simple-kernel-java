## simple-kernel-java

This is a port of https://github.com/dsblank/simple_kernel/ using java, jeromq and jackson.  It shows how to build the simplest kernel possible for Jupyter.

### Building it

Build the kernel and install it:
```
dpressel@dpressel:~/dev/work/simple-kernel-java$ ./gradlew build && ./gradlew fatJar
:compileJava
Note: /home/dpressel/dev/work/simple-kernel-java/src/main/java/Message.java uses unchecked or unsafe operations.
Note: Recompile with -Xlint:unchecked for details.
:processResources UP-TO-DATE
:classes
:jar
:assemble
:compileTestJava UP-TO-DATE
:processTestResources UP-TO-DATE
:testClasses UP-TO-DATE
:test UP-TO-DATE
:check UP-TO-DATE
:build

BUILD SUCCESSFUL

Total time: 1.923 secs
:compileJava UP-TO-DATE
:processResources UP-TO-DATE
:classes UP-TO-DATE
:fatJar

BUILD SUCCESSFUL

Total time: 2.149 secs
dpressel@dpressel:~/dev/work/simple-kernel-java$ ./install_script.sh 
```
### Running it

```
dpressel@dpressel:~/dev/work$ jupyter-notebook 
```

Or

```
dpressel@dpressel:~/dev/work$ jupyter lab

```

You should see simple-kernel-java in the list of kernels