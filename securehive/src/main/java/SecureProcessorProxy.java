import java.io.*; 
import java.net.*; 
import java.util.concurrent.*; 
import org.joda.time.DateTime;  
import com.google.gson.Gson.*;
import com.google.gson.*; 
import java.io.IOException; 
import java.util.List;
import java.util.ArrayList; 

import com.github.devicehive.client.model.DeviceNotification;
import com.github.devicehive.client.model.DeviceNotificationsCallback;
import com.github.devicehive.client.model.FailureData;
import com.github.devicehive.client.model.NotificationFilter;
import com.github.devicehive.client.service.Device; 

import java.nio.channels.*; 
import java.nio.charset.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.net.InetSocketAddress; 
/**
 * Proxy between the Host App holding the Device and DeviceHive server connection and the processor running in the enclave 
 * Passes DeviceNotifications and DeviceCommandWrapper between the Host App and enclave processor 
 * 
 */
public class SecureProcessorProxy
{ 
    private Gson gson; 
    private SocketChannel in; 
    private SocketChannel out;  
    private ByteBuffer buffer = ByteBuffer.allocate(8192);

    // the devices communicating through this proxy
    Device device; 
    // the filter and callback for the device
    NotificationFilter filter; 
    DeviceNotificationsCallback callback;  

    
    /**
     * Intended to be called by the SecureProcessorProxyBuilder
     * Establish a Socket connection to the enclave 
     * Establish in/out streams to pass data around 
     */
    protected SecureProcessorProxy(Device device, NotificationFilter filter, String enclaveIP, int port) throws IOException
    {

        GsonBuilder builder = new GsonBuilder(); 
       
        JsonSerializer<DateTime> dateTimeSerializer = new JsonSerializer<DateTime>()
        {
            @Override 
            public JsonElement serialize(DateTime src, java.lang.reflect.Type typeOfSrc, JsonSerializationContext context)
            {
                return new JsonPrimitive(src.toString());
            } 
        }; 
        builder.registerTypeAdapter(DateTime.class, dateTimeSerializer); 

        JsonDeserializer<DateTime> dateTimeDeserializer = new JsonDeserializer<DateTime>()
        {
            @Override 
            public DateTime deserialize(JsonElement json, java.lang.reflect.Type typeOfT, JsonDeserializationContext context) throws JsonParseException 
            {
                return DateTime.parse(json.getAsString());  
            } 
        }; 
        builder.registerTypeAdapter(DateTime.class, dateTimeDeserializer);
        builder.setLenient();
        gson = builder.create(); 
        out = SocketChannel.open(); 
        in = SocketChannel.open(); 
        in.connect(new InetSocketAddress(enclaveIP, port)); 
        in.configureBlocking(false); 
        out.connect(new InetSocketAddress(enclaveIP, port)); 
        
        this.device = device; 
        this.filter = filter; 
        this.callback = new DeviceNotificationsCallback()
        {
            
            public void onSuccess(List<DeviceNotification> notifications)
            {
                for(DeviceNotification notif : notifications)
                {
                    try 
                    { 
                        passNotification(notif); 
                    } 
                    catch (Exception e)
                    {
                        System.out.println("Failed during Keyexchange. Retrying...."); 
                    }
                    
                } 
            }
             
            public void onFail(FailureData failureData)
            {
                 System.out.println(failureData); 
            }
        };
        keyExchange(); 
    }

    public void pollCommands()
    {
        ScheduledExecutorService service = Executors.newScheduledThreadPool(1);
        // schedule the device to send a notification with variable delay 
        Runnable poll = new Runnable() {
            public void run() 
            {
                try
                {
                    List<DeviceCommandWrapper> wrappers = receiveCommand(); 
                    if(wrappers != null)
                    {
                        for(DeviceCommandWrapper wrapper : wrappers)
                        {
                            device.sendCommand(wrapper.commandName, wrapper.parameters);
                        }
                       
                    }
                }
                catch(Exception e)
                {
                    System.out.println(e.getMessage());
                    e.printStackTrace(System.out);                    
                }
            }             
        };
        service.scheduleAtFixedRate(poll, 0L, 5L, TimeUnit.MILLISECONDS);

    }

    /**
     * pass a DeviceNotification into the out stream to be received by the enclave. 
     */
    public void passNotification(DeviceNotification notification) throws Exception
    {
        try 
        {
            ByteBuffer bytes = StandardCharsets.UTF_8.encode(gson.toJson(notification)); 
            ByteBuffer count = ByteBuffer.allocate(4); 
            count.putInt(bytes.limit());  
            count.position(0); 
            while(count.hasRemaining())
            {
                out.write(count); 
            }
            while(bytes.hasRemaining())
            {
                out.write(bytes);
            } 
        }
        catch(Exception e)
        {
            System.out.println(e.getMessage());
        }    
    }

    /**
     * Read a DeviceCommandWrapper from the in stream and return it to the caller. 
     * Note that this call is blocking until it has read a response from the enclave.
     */
    public List<DeviceCommandWrapper> receiveCommand()
    {     
        try
        {
            int read = in.read(buffer); 
            if(read > 4)
            {
                buffer.limit(buffer.position()); 
                List<DeviceCommandWrapper> commands = new ArrayList<>(); 
                int current = 0; 
                buffer.position(0); 
                int size;

                while(current < buffer.limit())
                {  
                    size = buffer.getInt(); 
                    current += 4; 
                    if(current + size <= buffer.limit())
                    {
                        String s = new String(java.util.Arrays.copyOfRange(buffer.array(), current, current + size)); 
                        current = current + size; 
                        buffer.position(current); 
                        commands.add(gson.fromJson(s.trim(), DeviceCommandWrapper.class)); 
                    }

                    else
                    {
                        current = current - 4;
                        break; 
                    }
                }
                if(current == buffer.limit())
                {
                    buffer.clear(); 
                }

                else
                {
                    byte[] copy = java.util.Arrays.copyOfRange(buffer.array(), current, buffer.limit()); 
                    buffer.clear(); 
                    buffer.put(copy); 
                }

                if(commands.size() > 0)
                {
                    return commands; 
                }
                
                else
                {
                    return null; 
                }

            }

            else
            {
                return null; 
            }

        }
        catch(Exception e)
        {
            System.out.println(e.getMessage());
            e.printStackTrace(System.out);
            return null;
        }
    } 

      

    private void keyExchange()
    {
        // set up a filter for key exchange related notifications 
        NotificationFilter filter = new NotificationFilter(); 
        filter.setNotificationNames("$keyrequest", "$keyexchange"); 
        filter.setStartTimestamp(DateTime.now()); 
        filter.setEndTimestamp(DateTime.now());
        // callback to pass notifications to the device's proxy and receive commands 
        final DeviceNotificationsCallback callback = new DeviceNotificationsCallback()
        {
            
            public void onSuccess(List<DeviceNotification> notifications)
            {
                for(DeviceNotification notif : notifications)
                {
                    try 
                    { 
                        // after this notification is passed the keyexchange is complete 
                        if(notif.getNotification().equals("$keyexchange"))
                        {
                            ByteBuffer bytes = StandardCharsets.UTF_8.encode(gson.toJson(notif)); 
                            out.write(bytes); 
                            // unsubscribe from key exchange related notifications, 
                            device.unsubscribeAllNotifications();
                            // subscribe to the data notifications
                            subscribeData(); 
                            pollCommands();   
  
                        }
                        else
                        {
                            // pass notification and await response 
                            ByteBuffer bytes = StandardCharsets.UTF_8.encode(gson.toJson(notif)); 
                            out.write(bytes); 
                            List<DeviceCommandWrapper> response = receiveCommand();
                            if(response != null) 
                            {
                                device.sendCommand(response.get(0).commandName, response.get(0).parameters); 
                            }

                        }
                    } 
                    catch (Exception e)
                    {
                        System.out.println("Failed during Keyexchange. Retrying...."); 
                    }
                    
                } 
            }
             
            public void onFail(FailureData failureData)
            {
                 System.out.println(failureData); 
            }
        };
        device.subscribeNotifications(filter, callback); 

    }

    private void subscribeData()
    {
       device.subscribeNotifications(filter, callback); 
    }

}
