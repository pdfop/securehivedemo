Implementation example of a heart data analysis inspired by the FHE medical paper  

Code in device/ runs on a trusted device, it generates data and publishes it as a notification  
Code in proxy/ runs on a untrusted processing node outside of the enclave, it establishes the connection between the devicehive server and the enclave. it may also implement procssing logic for unencrypted notifications  
Code in client/ runs on a untrusted processing node inside an enclave using sgx-lkl. it implements the processing logic for arriving notifications it receives from the proxy and creates encrypted commands that it passes to the proxy to publish  

Data is taken from a real data set available at:   
One sensor sends 450 data packets to the enclave  
the data rate is dependent on the qt interval value in the data sample, simulating sending data each time a heartbeat is recorded (resulting in sending rates of ~60-100 messages / minute)  

Data Format:  
	rr interval value  
	qt interval value  
The processor uses theres values to calculate the current heartrate in bpm and analyses the qt interval for lqts using Bazett's formula  
Response:  
	heartrate in bpm  
	boolean value indicating risk of lqts  

