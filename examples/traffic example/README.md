This case study is inspired by Complex Event Processing case study presented in SecureScala.  

There are a number of traffic cameras / speed sensors along a route. Whenever a car passes a sensor the license plate, current speed and current time are recorded. 
Based on the ID of the sensor the speed limit in that area is known. The speed values are compared to the speed limit. If a car was faster than the speed limit the difference is calculated and a 'speeding' event is issued. The processing node creates a DeviceCommand with the license plate, speed difference and time of the violation.  

There are three versions of this case study:  
	regular DeviceHive  
	SecureHive  
	a version that performs its processing on homorphically encypted data


In any case the code in device/ runs on a trusted device, it generates data and publishes it as a notification.    
For the SecureHive version the ode in proxy/ runs on a untrusted processing node outside of the enclave, it establishes the connection between the DeviceHive server and the enclave.   
The code in client/ runs on a untrusted processing node. In the case of SecureHive it runs inside an enclave using sgx-lkl. It implements the processing logic for arriving notifications it receives from the proxy and creates encrypted commands that it passes to the proxy to publish. In the homomorphic version it runs in untrusted user space as normal, however it performs the operations on encrypted data. This is possible using the fast ope scheme as implemented by aymanmadkour and the Pallier scheme as implemented in Javallier. 

The data is randomly generated in the same way as presented in SecureScala. 
Each Sensor sends 500 entries. The delay between entries is randomly generated as in SecureScala.   

Data format:  
	license plate    
	speed    
	current time  

The SecureProcessor compares the speed in the notification with internal speed limit for the sensor. If the entry's speed exceeds the limit it responds with an encrypted DeviceCommand containing:  
	license plate    
	speed difference  
	original time stamp  


Resources:    
SecureScala https://dl.acm.org/doi/10.1145/2998392.2998403     
SecureScala Github for case study details i.e. data generation: https://github.com/allprojects/securescala     
OPE Implementation: https://github.com/aymanmadkour/ope     
Javallier: 	https://github.com/n1analytics/javallier     
