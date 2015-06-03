SYSC 3303 Group Project
========================

(last modified README.txt June 2, 2015)


Group 4, Iteration 2
--------------------

Maryn Marsland 		[PROGRAMMER]
Yuan Sun 		[TESTER]
Brandon To 		[TEAM LEADER/UML DIAGRAM MAKER]
Tianming Zhuang		[PROGRAMMER]


FILES:

============

Source files:
src/tftp/Client.java
src/tftp/ErrorSimulator.java
src/tftp/Exitable.java
src/tftp/Repl.java
src/tftp/Request.java
src/tftp/Server.java
src/tftp/TFTP.java

Unit test source files:
src/tftptest/TFTPTest.java

Unit test dependencies:
lib/junit-4.12.jar
lib/hamcrest-core-1.3.jar

Other:
TEST.txt (testing instructions and summary for this and previous iterations)
diagrams.pdf - Class diagram, timing diagrams, and unchanged diagrams from previous iteration
uml-class-diagram.jpg


INSTRUCTIONS FOR RUNNING:

=========================

Running the application:

1. Create the directories that you want to use for the client and the server for testing on the system that you are using.

2. Move the neccesary files that you will use to test the file transfer into the above directories (There are test files supplied in the included test.zip file).

3. In Eclipse: File > New > Project > Java Project 

4. Name the project whatever you like, but make sure "Use default location" is unchecked and that the filepath points to the root of the project files.

5. Since we used Junit as the testing framework, eclipse will need to have these library files included.
NOTE: It is likely that these JAR files will be automatically included when it detects that there are unit tests present. They should be visible in the eclipse project Package Explorer pane under Reference Libraries.

If they are not present, in Eclipse click on:

Project > Properties > Java Build Path > Libraries > Add External JAR

And add the following files found in /lib/:
    junit-4.12.jar
    hamcrest-core-1.3.jar


6. To run the application:


    start the server by right clicking Server.java > Run as > Java Application

    start the simulator by right clicking ErrorSimulator > Run as > Java Application

    start the client by right clicking Client > Run as > Java Application


7. In the server console window, enter path of the directory you would like to read from and write to on the server. (As stated in step 1, please ensure the the directory already exists beforehand.)

8. In the client console window, enter path of the directory you would like to read from and write to on the client. (As stated in step 1, please ensure the the directory already exists beforehand.)

9. The client will prompt you to enter one of the following operations:
    -read (to send a RRQ)
    -write (to send a WRQ)
    -quit (to exit the client)

10. The client will prompt you to enter the FILE NAME (not FILE PATH) of the file that you wish to transfer. For read requests, the file must exist on the server-side directory specified in step 7. For write requests, the file must exist on the client-side directory specified in step 8.


9. After entering the operation and file to transfer, the client, simulator, and server
will work together to transfer the file.

10. Once complete, the client will reprompt you to enter a new operation and file.
(In eclipse, sometimes the server and simulator output will cause the console window to change. In this case, just change it back to the client before entering a new transfer)

11. To shutdown the client and server, type exit in the applicable console window and press enter.

12. To shutdown the error simulator, click on the red square representing STOP in eclipse.



Running the unit tests:

1. Make sure that you properly included the Junit files as per step 5. above

2. To run the unit test:

    start the unit test framework by right clicking PacketTest.java > Run as > JUnit Test

3. The results will be shown in a window within eclipse called "JUnit"

4. These are tests for the TFTP.java functions. The rest of the testing was done manually and the process can be found in the TEST.txt document.


NOTES:

============
We decided to allow overwrites on the server to allow file modification on server.

We decided to NOT allow overwrites on the client because the user will have more control his own file system.
(The user could move conflicting files to circumvent such sitatuations, whereas he may not have permission to do so on the server.)

We decided to "wrap around" the block number if the file is larger than 512 * 65535 bytes

The error simulator and server DOES support multiple clients concurrently

You can find our test plan and test files that we used in the /src/tftptest folder

For changing the write access of a file, please use only the "Read-only" checkbox under the General tab of the file property. Java's standard library for checking write access is bugged, and the implementation was chosen as thus because it was desired to check the file privileges before file transfers between server and client.

For any packets, if the deleyed time is longer than 6 seconds (3 tries with each of 2 seconds wait cycle), the connection should abort and the file transfer process will be terminated.
