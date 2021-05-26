# Secure Stream Processing with Intel SGX Bachelor Thesis Patrick Dzubba

## SecureHive: A DeviceHive Security Extension using Intel SGX  

SecureHive extends the open source IoT communication framework DeviceHive with additional security guarantees.   
The system offers DeviceHive users private computation of their data on untrusted hardware e.g. cloud servers.   
This privacy is provided by running the processing logic inside SGX enclaves. SGX-LKL is used to enable unmodified java programs to run in an SGX enclave.   
SecureHive provides extensions to the regular DeviceHive java client library. The regular library remains unmodified. The extensions assume the client library version 3.1.2. 

SecureHive makes the following assumptions underlying DeviceHive system:   
  * The data is generated and published by a trusted Device    
  * The Device knows which processing node it will be communicating with  
  * The DeviceHive server may run on untrusted hardware
  * No modifications to the client library or server Software are necessary for SecureHive to work, but an attacker may modify the server code    
  * The data is processed on a SGX-capable cloud node 

SecureHive adds the following concepts and mechanisms to DeviceHive:  
  * Mutual authentication between a SecureDevice and a SecureProcessor  
  * Key exchange between a SecureDevice and a SecureProcessor  
  * AES encryption of all parameter names and values for DeviceNotifications and DeviceCommands  
  * Verification of the freshness of received notifications and commands through nonces  
  * Verification of the integrity of messages using SHA512 HMAC  

Additional information about the details of the system can be found in the README in securehive/   
For usage guidelines also refer to the README in securehive/  
For usage examples refer to the "with sgx" versions on the various case study implementations in examples/ 
 

Resources:   
https://docs.devicehive.com/docs     
https://github.com/devicehive/devicehive-java   
https://github.com/lsds/sgx-lkl/tree/legacy     
https://github.com/lsds/sgx-lkl/wiki/Remote-Attestation-and-Remote-Control    

