This directory contains two different versions of a heart data related case study. 
The case study calculates the heartrate in beats per minute and the risk of Long QT Syndrome based on QT and RR interval data. 
A similar case study was conducted in STYX. The LQTS calculation is inspired by a study by Kocabas & Soyata.   
There is no homomorphic comparison for this study as there is no native java implementation for the scheme used by Kobacas.  

Papers:   
STYX: https://dl.acm.org/doi/10.1145/2987550.2987574  
Kobacas: https://ieeexplore.ieee.org/document/7214088  

The code in  the respective device/ subdirectories runs on a trusted device, it generates data and publishes it as a notification.    

For the 'with sgx/' version the code in proxy/ runs on a untrusted processing node outside of the enclave, it establishes the connection between the Devicehive server and the enclave.  
The code in client/ runs on a untrusted processing node inside an enclave using sgx-lkl. It implements the processing logic for arriving notifications it receives from the proxy and creates encrypted commands that it passes to the proxy to publish.   

Data is taken from a heart data measurement data set available at: https://www.openml.org/d/5          
One sensor sends 450 data packets to the enclave.     
The data rate is dependent on the qt interval value in the data sample, simulating sending data each time a heartbeat is recorded. (resulting in sending rates of ~60-100 messages / minute)    

Data Format:    
	RR interval value in milliseconds     
	QT interval value in milliseconds  
The processor uses theses values to calculate the current heartrate in bpm and analyses the QT interval for LQTS using Bazett's formula  
Response:    
	heartrate in bpm    
	boolean value indicating risk of LQTS   

