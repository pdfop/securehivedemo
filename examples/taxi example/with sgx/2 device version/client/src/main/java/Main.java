import java.util.Collections;
import java.util.List;
import java.util.ArrayList;
import com.github.devicehive.client.model.Parameter;
import com.github.devicehive.client.model.DeviceNotification;
import com.google.gson.JsonObject; 
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
    }

    private class Requester extends SecureProcessor
    {
        @Override
        public void process(DeviceNotificationWrapper notification) throws Exception
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
            System.out.println("Unique Entries: " + String.valueOf(list.size())); 
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
                System.out.println("Sent Response " + String.valueOf(i)); 
                sendCommand(encryptedCommand("response", response)); 
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
