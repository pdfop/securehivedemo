import com.github.devicehive.client.model.CommandFilter;
import com.github.devicehive.client.model.DHResponse;
import com.github.devicehive.client.model.DeviceCommandsCallback;
import com.github.devicehive.client.model.DeviceNotification;
import com.github.devicehive.client.model.DeviceNotificationsCallback;
import com.github.devicehive.client.model.FailureData;
import com.github.devicehive.client.model.NotificationFilter;
import com.github.devicehive.client.model.Parameter;
import com.github.devicehive.client.service.Device;
import com.github.devicehive.client.service.DeviceCommand;
import com.github.devicehive.client.service.DeviceHive;
import com.google.gson.JsonObject;

import org.joda.time.DateTime; 
import java.util.Collections;
import java.util.List;
import java.util.ArrayList; 
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.io.IOException;


import java.util.concurrent.*; 
import java.util.Hashtable;
import java.util.Map;
import java.util.HashMap; 
import java.util.Map.Entry;

import org.joda.time.Duration; 
import org.joda.time.DateTime; 

import com.google.common.cache.CacheBuilder; 
import com.google.common.cache.LoadingCache; 
import com.google.common.cache.CacheLoader;
import java.util.concurrent.ConcurrentHashMap; 

import com.n1analytics.paillier.*;
import java.math.BigInteger;


public class Main
{
    //DeviceHive settings

    private static final String[] deviceIds = {"Taxi", "Requester"}; 
    private static final String URL = "http://20.67.42.189/api/rest"; 
    private static final String accessToken = "eyJhbGciOiJIUzI1NiJ9.eyJwYXlsb2FkIjp7ImEiOlswXSwiZSI6MTYxOTgyMDAwMDAwMCwidCI6MSwidSI6MSwibiI6WyIqIl0sImR0IjpbIioiXX19.zjTBCAtTFqNMfLzW11Xqz44UJ5wPkM5j1AUW_6vzpew";
    private static final String refreshToken = "eyJhbGciOiJIUzI1NiJ9.eyJwYXlsb2FkIjp7ImEiOlswXSwiZSI6MTYxOTgyMDAwMDAwMCwidCI6MCwidSI6MSwibiI6WyIqIl0sImR0IjpbIioiXX19.mMF61xlkbHblaSCwikU0m5J9s7hWDAzSAbuKpPkM0GQ";

    private Device taxi = null;
    private Device requester = null;  
    private DeviceHive client = null; 
    private DataProcessor dataProcessor; 
    private RequestProcessor requestProcessor;
    
    private LoadingCache<String, Trip> data;  

    public static void main(String[] args) throws Exception
    { 
        Main main = new Main(); 
        main.init(); 
        main.subscribeData();
        //main.subscribeRequests();  
    }

    private void init() throws Exception
    {
        //Initiating DeviceHive
        client = DeviceHive.getInstance().init(URL, refreshToken, accessToken);

        DHResponse<Device> response = client.getDevice(deviceIds[0]); 
        taxi = response.getData(); 
       // response = client.getDevice(deviceIds[1]); 
       // requester = response.getData(); 

        dataProcessor = new DataProcessor(taxi); 
       // requestProcessor = new RequestProcessor(taxi); 

        CacheLoader<String,Trip> loader = new CacheLoader<String, Trip>() {
            @Override
            public Trip load(String key) {
                return new Trip();
            }
        };
        data = CacheBuilder.newBuilder().expireAfterWrite(10, TimeUnit.MINUTES).build(loader); 
    }

    private void subscribeData() throws Exception
    {
        NotificationFilter filter = new NotificationFilter(); 
        filter.setNotificationNames("data", "request"); 
        filter.setStartTimestamp(DateTime.now()); 
        filter.setEndTimestamp(DateTime.now());

        final DeviceNotificationsCallback callback = new DeviceNotificationsCallback()
        {
            
            public void onSuccess(List<DeviceNotification> notifications)
            {
                for(DeviceNotification notif : notifications)
                {
                    try 
                    { 
                        dataProcessor.process(notif); 
                    } 
                    catch (Exception e)
                    {
                        //TODO: handle exception
                    }
                    
                } 
            }
             
            public void onFail(FailureData failureData)
            {
                 System.out.println(failureData); 
            }
        };
        taxi.subscribeNotifications(filter,callback);
    }

    private void subscribeRequests() throws Exception
    {
        NotificationFilter filter = new NotificationFilter(); 
        filter.setNotificationNames("request"); 
        filter.setStartTimestamp(DateTime.now()); 
        filter.setEndTimestamp(DateTime.now());

        final DeviceNotificationsCallback callback = new DeviceNotificationsCallback()
        {
            
            public void onSuccess(List<DeviceNotification> notifications)
            {
                for(DeviceNotification notif : notifications)
                {
                    try 
                    { 
                        if(notif.getNotification().equals("request"))
                        {
                            requestProcessor.process(notif); 
                        }
                    } 
                    catch (Exception e)
                    {
                        //TODO: handle exception
                    }
                    
                } 
            }
             
            public void onFail(FailureData failureData)
            {
                 System.out.println(failureData); 
            }
        };
        requester.subscribeNotifications(filter,callback);
    }

    private class DataProcessor extends HomomorphicProcessor
    {
        public DataProcessor(Device device) throws Exception
        {
            super(device); 
        }

        @Override
        public void process(DeviceNotification notification)
        {
            if(notification.getNotification().equals("data"))
            {
                try
                {
                    JsonObject parameters = notification.getParameters(); 
                    Trip trip = new Trip(); 
                    trip.pickupLocation = parameters.get("$pickup-location").getAsBigInteger();
                    trip.dropoffLocation = parameters.get("$dropoff-location").getAsBigInteger();

                    trip.startExponent = parameters.get("$pickup-locationexponent").getAsInt(); 
                    trip.destExponent = parameters.get("$dropoff-locationexponent").getAsInt(); 
                    

                    trip.pickupTime = parameters.get("pickup-time").getAsString(); 
                    trip.dropoffTime = parameters.get("dropoff-time").getAsString(); 
        
                    trip.distance = parameters.get("$distance").getAsBigInteger();
                    trip.distanceExp = parameters.get("$distanceexponent").getAsInt();

                    trip.cost = parameters.get("$cost").getAsBigInteger();
                    trip.costExp = parameters.get("$costexponent").getAsInt(); 
                    data.put(String.valueOf(notification.getId()), trip);   
    
                }
                catch (Exception e)
                {
                    System.out.println(e.getMessage());
                    e.printStackTrace(System.out);  
                }

            }

            if(notification.getNotification().equals("request"))
            {
                Map<String, CounterEntry> routes = new HashMap<>();
                ConcurrentMap<String, Trip> freeze = data.asMap();

                for(Trip t : freeze.values())
                {
                    String route = t.pickupLocation + ">>>" + t.dropoffLocation;
                    CounterEntry entry = routes.get(route);

                    if (entry == null) 
                    {
                        routes.put(route, new CounterEntry(t.distance, t.distanceExp, t.cost, t.costExp, t.startExponent, t.destExponent)); 
                    }
                    else 
                    {
                        entry.increment(); 
                        entry.addDist(t.distance, t.distanceExp); 
                        entry.addCost(t.cost, t.costExp); 
                    }
                }

                List<Entry<String,CounterEntry>> list = new ArrayList<>(routes.entrySet());
                list.sort(Entry.comparingByValue());
                Collections.reverse(list);
                for(int i =  0; i < Math.min(list.size(), 5); i++)
                {
                    Entry<String,CounterEntry> entry = list.get(i); 
                    String[] route = entry.getKey().split(">>>"); 
                    List<Parameter> response = new ArrayList<>();

                    EncodedNumber e = cipher.getEncodingScheme().encode((double) 1/entry.getValue().get()); 
                    EncryptedNumber avgDist = entry.getValue().distance.multiply(e); 
                    EncryptedNumber avgCost =  entry.getValue().cost.multiply(e); 
                    response.add(new Parameter("rank", String.valueOf(i+1))); 

                    response.add(new Parameter("$start",route[0]));   
                    response.add(new Parameter("$startexponent",String.valueOf(entry.getValue().startExponent))); 

                    response.add(new Parameter("$destination",route[1])); 
                    response.add(new Parameter("$destinationexponent",String.valueOf(entry.getValue().destExponent))); 


                    response.add(new Parameter("$average-distance", avgDist.calculateCiphertext().toString()));  
                    response.add(new Parameter("$average-cost", avgCost.calculateCiphertext().toString()));  

                    response.add(new Parameter("$average-distanceexponent", String.valueOf(avgDist.getExponent())));  
                    response.add(new Parameter("$average-costexponent", String.valueOf(avgCost.getExponent()))); 
                    device.sendCommand("response", response); 
                }

            }
        }


        private class CounterEntry implements Comparable<CounterEntry>
        {
            int counter = 1; 
            EncryptedNumber distance; 
            EncryptedNumber cost; 
            int startExponent; 
            int destExponent; 
    
            public CounterEntry(BigInteger dist, int distExp, BigInteger cost, int costExp, int startExp, int destExp)
            {
                distance = new EncryptedNumber(cipher, dist, distExp);  
                this.cost = new EncryptedNumber(cipher, cost, costExp); 
                startExponent = startExp; 
                destExponent = destExp; 
            }
    
            public void increment()
            {
                ++counter; 
            }
    
            public void addDist(BigInteger dist, int distExp)
            {
                distance = distance.add(new EncryptedNumber(cipher, dist, distExp));  
            }
    
            public void addCost(BigInteger cost, int costExp)
            {
                this.cost = this.cost.add(new EncryptedNumber(cipher, cost, costExp)); 
            }
    
            public int get()
            {
                return counter;
            }
    
            @Override
            public int compareTo(CounterEntry other)
            {
                if(this.counter == other.get())
                {
                    return 0; 
                }
                else
                {
                    return this.counter > other.get() ? 1 :-1; 
                }
            }
    
            @Override
            public String toString()
            {
                return String.valueOf(this.counter); 
            }
        }
        
    }

    private class RequestProcessor extends HomomorphicProcessor
    {
        public RequestProcessor(Device device) throws Exception
        {
            super(device); 
        }

        @Override
        public void process(DeviceNotification notifiation)
        {
            Map<String, CounterEntry> routes = new HashMap<>();
            ConcurrentMap<String, Trip> freeze = data.asMap();

            for(Trip t : freeze.values())
            {
                String route = t.pickupLocation + ">>>" + t.dropoffLocation;
                CounterEntry entry = routes.get(route);

                if (entry == null) 
                {
                    routes.put(route, new CounterEntry(t.distance, t.distanceExp, t.cost, t.costExp)); 
                }
                else 
                {
                    entry.increment(); 
                    entry.addDist(t.distance, t.distanceExp); 
                    entry.addCost(t.cost, t.costExp); 
                }
            }

            List<Entry<String,CounterEntry>> list = new ArrayList<>(routes.entrySet());
            list.sort(Entry.comparingByValue());
            Collections.reverse(list);
            for(int i =  0; i < Math.min(list.size(), 5); i++)
            {
                Entry<String,CounterEntry> entry = list.get(i); 
                String[] route = entry.getKey().split(">>>"); 
                List<Parameter> response = new ArrayList<>();

                EncodedNumber e = cipher.getEncodingScheme().encode((double) 1/entry.getValue().get()); 
                EncryptedNumber avgDist = entry.getValue().distance.multiply(e); 
                EncryptedNumber avgCost =  entry.getValue().cost.multiply(e); 
                response.add(new Parameter("rank", String.valueOf(i+1)));  
                response.add(new Parameter("$start",route[0]));   
                response.add(new Parameter("$destination",route[1])); 
                response.add(new Parameter("$average-distance", avgDist.calculateCiphertext().toString()));  
                response.add(new Parameter("$average-cost", avgCost.calculateCiphertext().toString()));  

                response.add(new Parameter("$average-distanceexponent", String.valueOf(avgDist.getExponent())));  
                response.add(new Parameter("$average-costexponent", String.valueOf(avgCost.getExponent()))); 
                device.sendCommand("response", response); 
            }

        }

        private class CounterEntry implements Comparable<CounterEntry>
        {
            int counter = 1; 
            EncryptedNumber distance; 
            EncryptedNumber cost; 
    
            public CounterEntry(BigInteger dist, int distExp, BigInteger cost, int costExp)
            {
                distance = new EncryptedNumber(cipher, dist, distExp);  
                this.cost = new EncryptedNumber(cipher, cost, costExp); 
            }
    
            public void increment()
            {
                ++counter; 
            }
    
            public void addDist(BigInteger dist, int distExp)
            {
                distance = distance.add(new EncryptedNumber(cipher, dist, distExp));  
            }
    
            public void addCost(BigInteger cost, int costExp)
            {
                this.cost = this.cost.add(new EncryptedNumber(cipher, cost, costExp)); 
            }
    
            public int get()
            {
                return counter;
            }
    
            @Override
            public int compareTo(CounterEntry other)
            {
                if(this.counter == other.get())
                {
                    return 0; 
                }
                else
                {
                    return this.counter > other.get() ? 1 :-1; 
                }
            }
    
            @Override
            public String toString()
            {
                return String.valueOf(this.counter); 
            }
        }
    
    }

}
