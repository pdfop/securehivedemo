
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


import com.google.common.cache.CacheBuilder; 
import com.google.common.cache.LoadingCache; 
import com.google.common.cache.CacheLoader;
import java.util.concurrent.ConcurrentHashMap; 


public class Main {

    //DeviceHive settings
    private static final String URL = "http://20.67.42.189/api/rest"; 
    private static final String accessToken = "eyJhbGciOiJIUzI1NiJ9.eyJwYXlsb2FkIjp7ImEiOlswXSwiZSI6MTYxOTgyMDAwMDAwMCwidCI6MSwidSI6MSwibiI6WyIqIl0sImR0IjpbIioiXX19.zjTBCAtTFqNMfLzW11Xqz44UJ5wPkM5j1AUW_6vzpew";
    private static final String refreshToken = "eyJhbGciOiJIUzI1NiJ9.eyJwYXlsb2FkIjp7ImEiOlswXSwiZSI6MTYxOTgyMDAwMDAwMCwidCI6MCwidSI6MSwibiI6WyIqIl0sImR0IjpbIioiXX19.mMF61xlkbHblaSCwikU0m5J9s7hWDAzSAbuKpPkM0GQ";

    private Device device = null; 
    private DeviceHive client = null;
    private LoadingCache<String, Trip> data;  

    public static void main(String[] args) 
    { 
        String deviceIdInput= args[0];
        Main main = new Main(); 
        main.init(deviceIdInput); 
        main.subscribe(); 
    }

    private void init(String deviceId)
    {
        CacheLoader<String,Trip> loader = new CacheLoader<String, Trip>() {
            @Override
            public Trip load(String key) {
                return new Trip();
            }
        };
        data = CacheBuilder.newBuilder().expireAfterWrite(10, TimeUnit.MINUTES).build(loader); 

        //Initiating DeviceHive
        client = DeviceHive.getInstance().init(URL, refreshToken, accessToken);
        DHResponse<Device> response = client.getDevice(deviceId); 
        if(!response.isSuccessful())
        {
            System.out.println("Device Failed"); 
            return; 
        }
        device = response.getData(); 
    }

    private void subscribe()
    {
        NotificationFilter filter = new NotificationFilter(); 
        filter.setNotificationNames("data", "request"); 
        filter.setStartTimestamp(DateTime.now()); 
        filter.setEndTimestamp(DateTime.now());

        final DeviceNotificationsCallback callback = new DeviceNotificationsCallback()
        {
            
            public void onSuccess(List<DeviceNotification> notifications)
            {
                for(DeviceNotification notification : notifications)
                {
                    process(notification); 
                }
            }
             
            public void onFail(FailureData failureData)
            {
                 System.out.println(failureData); 
            }
        };

        device.subscribeNotifications(filter,callback);
    }

    private void process(DeviceNotification notification)
    {
        if(notification.getNotification().equals("data"))
        {            
            JsonObject parameters = notification.getParameters(); 
            Trip trip = new Trip(); 
            trip.pickupLocation = parameters.get("pickup-location").getAsString();
            trip.dropoffLocation = parameters.get("dropoff-location").getAsString();
            trip.pickupTime = parameters.get("pickup-time").getAsString();
            trip.dropoffTime = parameters.get("dropoff-time").getAsString();
            trip.distance = parameters.get("distance").getAsString();
            trip.cost = parameters.get("cost").getAsString(); 
            data.put(String.valueOf(notification.getId()), trip);  

        }
        
        if(notification.getNotification().equals("request"))
        {
            Map<String, CounterEntry> routes = new HashMap<>();
            ConcurrentMap<String, Trip> freeze = data.asMap();

            for(Trip t : freeze.values())
            {
                String route = t.pickupLocation + "-" + t.dropoffLocation;
                CounterEntry entry = routes.get(route);

                if (entry == null) 
                {
                    routes.put(route, new CounterEntry(Double.parseDouble(t.distance), Double.parseDouble(t.cost)));
                }
                else 
                {
                    entry.increment(); 
                    entry.addDist(Double.parseDouble(t.distance)); 
                    entry.addCost(Double.parseDouble(t.cost)); 
                }
            }

            List<Entry<String,CounterEntry>> list = new ArrayList<>(routes.entrySet());
            list.sort(Entry.comparingByValue());
            Collections.reverse(list);
            //System.out.println("Unique Entries: " + String.valueOf(list.size())); 
            for(int i =  0; i < Math.min(list.size(), 5); i++)
            {
                Entry<String,CounterEntry> entry = list.get(i); 
                String[] route = entry.getKey().split("-"); 
                List<Parameter> response = new ArrayList<>();
                response.add(new Parameter("rank", String.valueOf(i+1)));  
                response.add(new Parameter("start",route[0]));   
                response.add(new Parameter("destination",route[1])); 
                response.add(new Parameter("average-distance",String.valueOf(entry.getValue().distance / entry.getValue().get()))); 
                response.add(new Parameter("average-cost",String.valueOf(entry.getValue().cost / entry.getValue().get()))); 
                device.sendCommand("response", response); 
            }

        }


    }

    private class CounterEntry implements Comparable<CounterEntry>
    {
        int counter = 1; 
        double distance; 
        double cost; 

        public CounterEntry(double dist, double c)
        {
            distance = dist; 
            cost = c; 
        }

        public void increment()
        {
            ++counter; 
        }

        public void addDist(double x)
        {
            distance += x; 
        }

        public void addCost(double x)
        {
            cost += x; 
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

    private class Trip
    {
        public String pickupLocation; 
        public String pickupTime;
        public String dropoffLocation;
        public String dropoffTime;
        public String distance;
        public String cost;

        public Trip()
        {
            
        }
    }

}
