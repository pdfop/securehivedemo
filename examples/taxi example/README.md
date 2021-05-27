This case study is inspired by a version presented in STYX. 

Taxis record meta information about their fares and publish it to a processing node. The node keeps the most recent 15 minutes of data in a cache. A monitoring application makes requests at a rate of 1/second. Whenever the processing node receives a request it calculates the 5 most common routes in the last 15 minutes. A route is represented by the starting and ending grid cell of the trip. It additionally calculates the average distance and average cost per fare per route.  

It uses a taxi trip data set published by New York city TLC. It is available here: https://www1.nyc.gov/site/tlc/about/tlc-trip-record-data.page   


There are again 3 versions of this case study: 
    regular DeviceHive
    SecureHive 
    a version that performs the necessary processing on encrypted data using the Pallier scheme as implemented in Javallier 

The data frequency is determined by the rate at which taxis finished their fares in the data set. The data is sorted by dropoff time. The difference between the dropoff time of two consecutive entries is the data set is the delay between their publication in the system.  

Data format:  
  * pickup location  
  * pickup time    
  * dropoff location  
  * dropoff time  
  * distance  
  * cost  

Response format:  
  * rank based on popularity within the last 15 minutes  
  * pickup location  
  * dropoff location  
  * average distance for fares with the same pickup and dropoff locations  
  * average cost for fares with the same pickup and dropoff locations  


Resources:        
STYX: https://dl.acm.org/doi/10.1145/2987550.2987574  
Javallier: https://github.com/n1analytics/javallier     
