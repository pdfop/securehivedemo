# Usage Examples / Case Studies 
This directory contains the implementations of the case studies presented in the thesis.  
The "with sgx" versions of each case study can be seen as usage examples for SecureHive.   
For an overview of each case study refer to its respective README file. For details and results refer to the written thesis.

Before running each implementation the url, accessToken and refreshToken parameters in the respective Main.java files have to be set to your sever and authentication tokens.  
Tokens can be acquired on http://your-server-ip/admin  with default login dhadmin:dhadmin_#911  
Build each sample application using ```gradle build``` in the respective directories  
Run using ``` java -jar build/libs/*.jar DeviceName ```  where the device name is the same as the .csv file in the respective example/*/device/resources directory  
Run enclave applications using ``` gradle build -> make -> run.sh```   
Follow SGX-LKL instructions to enable Thread Local Storage   
Follow https://github.com/lsds/sgx-lkl/tree/legacy#networking-support to create tap device with IP 10.0.1.1 Port 6767. These values can be changed in the Main.java files in the proxy/ and client/ directories. 
