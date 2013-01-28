#RUBTClient Project Assignment #1#

**Completed**   
-Get data from .torrent file (done, using Rob's TorrentInfo   
-HTTP GET request to tracker (response succesfully received)   
   
**Todo**   
-Translate response from Tracker  
-Communicate with peer(s)  
-Download file  

Note:
    Wireshark is extremely useful for this. When I was trying to get the tracker get request correctly, I used wireshark 
    to inspect the packets that the server sent back. The error codes are in human readable ASCII, and made diagnosis of the 
    problem easier. 

    Installation of Wireshark on Ubuntu: http://blog.sudobits.com/2010/06/23/how-to-install-wireshark-on-ubuntu-10-04/
    It looks like wireshark is installed on the iLabs but normal users dont have permissions to run it...

    When you have wireshark open click on start capture on interface: eth0 (if you're using wireless or something, it might be wlan0, type
    ifconfig in terminal to find out all your devices. The one with an ip address that is not 127.0.0.1 should be the one you want)

    Once you are capturing packets, you'll see a bunch of shit, so you'll want to filter the traffic to just between you and the tracker:
        In the filter textbox type **ip.addr == 128.6.5.130** 

