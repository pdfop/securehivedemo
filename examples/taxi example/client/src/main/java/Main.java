import java.util.Collections;
import java.util.List;
import java.util.ArrayList;
import com.github.devicehive.client.model.Parameter;
import com.github.devicehive.client.model.DeviceNotification;
import com.google.gson.JsonObject; 
import java.util.concurrent.*; 
import java.util.Hashtable;
import java.util.Map;
import java.util.Map.Entry;

import org.joda.time.Duration; 
import org.joda.time.DateTime; 

import com.google.common.cache.CacheBuilder; 
import com.google.common.cache.LoadingCache; 
import com.google.common.cache.CacheLoader;
import java.util.concurrent.ConcurrentHashMap; 

// ** this entire class is only necessary with this system. in regular DeviceHive the equivalent would be a DeviceNotificationsCallback passed to the subscribeNotifications() function ** 
public class Main
{
    private static final int[] ports = {6767,6768};   
    private static final String[] deviceIds = {"Taxi", "Requester"}; 
    private LoadingCache<String, Trip> data;   
    public static void main(String[] args) throws Exception
    {
        Main main = new Main(); 
        main.init(); 

    }

    private void init() throws Exception
    {
        CacheLoader<String,Trip> loader = new CacheLoader<String, Trip>() {
            @Override
            public Trip load(String key) {
                return new Trip();
            }
        };
        data = CacheBuilder.newBuilder().expireAfterWrite(10, TimeUnit.MINUTES).build(loader); 

        ExecutorService e = Executors.newFixedThreadPool(deviceIds.length); 

        Taxi t = new Taxi(); 
        int port = ports[0];
        Runnable r = new Runnable()
        {
            @Override
            public void run()
            { 
                try
                {
                    t.init(port); 
                } 
                catch (Exception e) 
                {
                    //TODO: handle exception
                }
                return;
            }
        };
        int port2 = ports[1];
        Requester req = new Requester(); 
        Runnable reqRun = new Runnable()
        {
            @Override
            public void run()
            { 
                try
                {
                    req.init(port2); 
                } 
                catch (Exception e) 
                {
                    //TODO: handle exception
                }
                return;
            }
        };

        e.execute(r);
        e.execute(reqRun); 
        
    }

    private class Taxi extends SecureProcessor
    {
        @Override
        public void process(DeviceNotificationWrapper notification) throws Exception
        {
            JsonObject parameters = getDecryptedParameters(notification); 
            Trip trip = new Trip(); 
            trip.pickupLocation = parameters.get("pickup-location").getAsString();
            trip.dropoffLocation = parameters.get("dropoff-location").getAsString();
            trip.pickupTime = parameters.get("pickup-time").getAsString();
            trip.dropoffTime = parameters.get("dropoff-time").getAsString();
            trip.distance = parameters.get("distance").getAsString();
            trip.cost = parameters.get("cost").getAsString(); 
            data.put(String.valueOf(notification.getId()), trip);   

            

        }
    }

    private class Requester extends SecureProcessor
    {
        @Override
        public void process(DeviceNotificationWrapper notification) throws Exception
        {
            System.out.println("Working on request"); 
            Hashtable<String,ArrayList<Integer>> routes = new Hashtable<String,ArrayList<Integer>>(); 
            ConcurrentMap<String, Trip> freeze = data.asMap(); 
            for(Trip trip : freeze.values())
            {
                String route = trip.pickupLocation + "-" + trip.dropoffLocation; 
                ArrayList<Integer> value = new ArrayList<>(); 
                value.add(0);
                value.add(0);
                value.add(0);
                routes.putIfAbsent(route, value); 
                value = routes.get(route); 
                value.set(0,value.get(0) + 1); 
                value.set(1,value.get(1) + Integer.parseInt(trip.distance)); 
                value.set(2,value.get(2) + Integer.parseInt(trip.cost)); 
                routes.put(route, value); 
            }
            int max = 0; 
            String key = ""; 
            for(Entry<String,ArrayList<Integer>> entry : routes.entrySet())
            {
                ArrayList<Integer> value = entry.getValue();
                if(value.get(0) > max)
                {
                    max = value.get(0); 
                    key = entry.getKey(); 
                }
            }

            ArrayList<Integer> values = routes.get(key);
            List<Parameter> response = new ArrayList<>(); 
            response.add(new Parameter("most-commom-route",key)); 
            response.add(new Parameter("average-distance",String.valueOf(values.get(1) / values.get(0)))); 
            response.add(new Parameter("average-cost",String.valueOf(values.get(2) / values.get(0)))); 

            sendCommand(encryptedCommand("response", response)); 

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
