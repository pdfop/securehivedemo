This directory serves as a showcase of the remote attestation mechanism of SecureHive.  


The developer measures the enclaves MRSIGNER and MRENCLAVE values when deploying the system.  
These values alongside the Service Provider ID for the SGX license and the Subscription Key are added to the SecureDevice's key store. 
When remote attestation is enabled for an instance of a SecureDevice it will work with the enclave and the Intel Attestation Service to verify the status of the enclave before beginning the authetication and key exchange protocol.  
Requests are made using the 'sgx-lkl-ctl' tool. The sgx-lkl attestation server is configured during enclave deployment and can be passed to the SecureDevice as parameters during creation.  
Remote attestation is deemed successful when sgx-lkl-ctl exits with code 0.  
For exit conditions and more configuration options for this process refer to https://github.com/lsds/sgx-lkl/wiki/Remote-Attestation-and-Remote-Control  