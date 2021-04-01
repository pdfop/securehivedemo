This case study was originally intended to have 2 separate clients and 1 processor.  
One of the clients would be the device, emitting the data.  
The other would be a requester, e.g. a web app requesting the top 5 statistics, most likely from another machine.  
This would be realistic and is functional in the form included in this directory for the sgx version.  
However, in a homomorphic version of this the key used to encrypt the data would need to be shared with the requester in order to decrypt the statistics.  
While possible it was more convenient to keep the entire exchange in one program for the homomorphic version and the SGX version was adapted accordingly.  
The code in this directory remains as a proof of concept to show that communication between multiple devices is possible and easy using the SGX version.  
