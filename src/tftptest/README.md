TFTP-Application Tests
======================

Automated unit tests in the /tests/ directory can be run using JUnit.

The following tests were done manually and passed unless otherwise stated.

Test Requirements
=================

Client Requirements

01. Client should successfully write any file to Server [PASS]
02. Client should successfully read any file from Server [PASS]
03. Client should successfully write empty file to Server [FAIL]
04. Client should successfully read empty file from Server []
05. Client should successfully write a file that has size that is a multiple of 512 bytes to the Server [PASS]
06. Client should successfully read a file that has size that is a multiple of 512 bytes from the Server [PASS]
07. Client should successfully write a file that is larger than 33553920 bytes (65535 blocks * 512 bytes/block) to the Server [PASS]
08. Client should successfully read a file that is larger than 33553920 bytes (65535 blocks * 512 bytes/block) from the Server []
09. Client should successfully write files of different file extentions to the Server [PASS]
10. Client should successfully read files of different file extentions to the Server []
11. Client should only support one file transfer at a time [PASS]
12. Client should support a command to exit gracefully [PASS]

Server Requirements (some requirements are handled in previous listings)

01. Server should support multiple concurrent file transfers from different Clients [PASS]
02. Server should overwrite files if one with the same name already exists []
03. Server should support a command to exit gracefully []
04. Server should finish all running file transfers before exiting []
05. Server should ignore any new requests following an exit command []

Test Plan
=========

Client Test Plan:

Client01.
1. From the Client, send a write request to the Server for "small.txt"
2. Verify that Server successfully receives "small.txt"

Client02.
1. From the Client, send a read request to the Server for "small.txt"
2. Verify that Client successfully receives "small.txt"

Client03.
1. From the Client, send a write request to the Server for "empty.txt"
2. Verify that Server successfully receives "empty.txt"

Client04.
1. From the Client, send a read request to the Server for "empty.txt"
2. Verify that Client successfully receives "empty.txt"

Client05.
1. From the Client, send a write request to the Server for "512exact.txt"
2. Verify that Server successfully receives "512exact.txt"
3. From the Client, send a write request to the Server for "1024exact.txt"
4. Verify that Server successfully receives "1024exact.txt"

Client06.
1. From the Client, send a read request to the Server for "512exact.txt"
2. Verify that Client successfully receives "512exact.txt"
3. From the Client, send a read request to the Server for "1024exact.txt"
4. Verify that Server successfully receives "1024exact.txt"

Client07.
1. From the Client, send a write request to the Server for "toolarge.txt"
2. Verify that Server successfully receives "toolarge.txt"
3. Verify that the block number "wraps around" back to 0 after it goes past 65535

Client08.
1. From the Client, send a read request to the Server for "toolarge.txt"
2. Verify that Client successfully receives "toolarge.txt"
3. Verify that the block number "wraps around" back to 0 after it goes past 65535

Client09.
1. From the Client, send a write request to the Server for "menu.png"
2. Verify that Server successfully receives "menu.png"

Client10.
1. From the Client, send a read request to the Server for "menu.png"
2. Verify that Client successfully receives "menu.png"

Client11.
1. From the Client, send a write request to the Server for "large.txt"
2. From the Client, while "large.txt" is being sent, try to send another file
3. Verify that no other file can be sent
2. Verify that Server successfully receives "large.txt"

Client12.
1. From the Client, verify that there are no current transfers are in progress
2. Input the exit command
3. Verify that the Client successfully exits

Server Test Plan:

Server01.
1. From the Client, send a write request to the Server for "large.txt"
2. From a different Client (must use a different port), while "large.txt" is being sent, send a write request to the Server for "small.txt"
3. Verify that Server successfully receives "small.txt"
4. Verify that Server successfully receives "large.txt"

Server02.
1. From the Client, send a write request to the Server for "small.txt"
2. Verify that Server successfully receives "small.txt"
3. Append "success!" to the end of the "small.txt" file
4. From the Client, send a write request to the Server for "small.txt"
5. Verify that Server successfully receives "small.txt"
6. Verify that the "small.txt" on the server now has "success!" appended to file

Server03 && Server04 && Server05.
1. From the Client, send a write request to the Server for "large.txt"
2. From the Server, while "large.txt" is being sent, input the exit command
3. From a different Client (must use a different port), while "large.txt" is being sent, send a write request to the Server for "small.txt"
4. Verify that the Server does NOT receive "small.txt" (Since the server should reject file transfers after exit command) (Client should hang because it is waiting for an ack)
5. Verify that the Server successfully receives "large.txt"
6. Verify that the Server successfully exits after "large.txt" is finished being sent

