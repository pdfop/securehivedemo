Implemented example of the SecureScala CEP case study  


Code in device/ runs on a trusted device, it generates data and publishes it as a notification  
Code in proxy/ runs on a untrusted processing node outside of the enclave, it establishes the connection between the devicehive server and the enclave. it may also implement procssing logic for unencrypted notifications  
Code in client/ runs on a untrusted processing node inside an enclave using sgx-lkl. it implements the processing logic for arriving notifications it receives from the proxy and creates encrypted commands that it passes to the proxy to publish  


Data was randomly generated using a python script and is not realistic  
3 sensors send 100 data packets each to the enclave  
Transmission Rate: 1 packet every 100 ms per sensor  
Data format:  
	license plate  
	speed  
	time from SecureScala example can be gathered from notification timestamp  

Processor compares speed in the notification with internal speed limit for the sensor and responds with:  
	license plate  
	boolean whether the car was speeding  
	if the car was speeding the difference between the car's speed and the speed limit  

