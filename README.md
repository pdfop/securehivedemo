DeviceHive Security Extension Demo      
Adds the classes found in securehive/src/main/java to the DeviceHive java client library   


Base resources:   

https://docs.devicehive.com/docs     
https://github.com/devicehive/devicehive-java   
https://github.com/lsds/sgx-lkl/tree/legacy     
https://github.com/lsds/sgx-lkl/wiki/Remote-Attestation-and-Remote-Control    


Assumptions:    
a SecureDevice is running on trusted hardware 
a SecureProcessor is running inside an enclave using sgx-lkl-java
the SecureProcessor and SecureProcessorProxy are running on untrusted cloud hardware
the DeviceHive server is running on untrusted cloud hardware   

Added concepts: 
mutual authentication between a SecureProcessor and a SecureDevice via x509 certificates
key exchange protocol to establish a shared AES key between a SecureDevice and SecureProcessor    
AES encryption of DeviceNotification and DeviceCommand parameter names and values   
verification of the freshness of received DeviceNotifications and DeviceCommands   

Guarantees: 
the system works with an unmodified DeviceHive server that can may manage other Devices running on an unmodified DeviceHive client library 
the DeviceHive server and the untrusted SecureProcessorProxy cannot read any messages exchanged between a SecureDevice and SecureProcessor  
a SecureProcessor only accepts connections from trusted SecureDevices
a SecureProcessor verifies the freshness of received DeviceNotifications
a SecureDevice verifies the freshness of received DeviceCommands   

Design notes and motivations:  
all processing of private data happens entirely in the enclave
allowing Devices and processing applications to process both private and regular DeviceNotifications and DeviceCommands
interoperability with the unmodified DeviceHive server code 
minimal changes to the core DeviceHive library  
preserving original functionality as much as possible 
minimal difference in usage of regular and secure functionality   

Known problems, limitations and things being worked on:
currently no exception handling 
the traffic example required conversion from a 1:1 Device:Processor connection to allows for many:1 the current implementation of which negatively affect in-enclave performance 
there should be better ways for data from multiple Devices to be processed using the same Processor logic 
the mechanism of overriding functions to adapt functionality is cumbersome and should probably be replaced by a callback function 
the blocking I/O of Sockets currently requires a proxy to poll and wait for a response after each notification, this should be replaced by signaling a new response or adding a polling function or polling via RMI
there should be a better way of launching the enclave, ideally from within the host application 
configurability needs to be improved, especially for enclave code  
there need to be tools for generating an lkl image, configuration files and the necessary key stores  

