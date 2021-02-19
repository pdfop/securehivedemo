import java.io.*; 
import java.net.*; 
import java.util.concurrent.*; 
import org.joda.time.DateTime;  
import com.google.gson.Gson.*;
import com.google.gson.*; 
import java.io.IOException; 
import java.util.List;
import com.github.devicehive.client.model.DeviceNotification;
import com.github.devicehive.client.model.DeviceNotificationsCallback;
import com.github.devicehive.client.model.FailureData;
import com.github.devicehive.client.model.NotificationFilter;
import com.github.devicehive.client.service.Device; 


/**
 * Proxy between the Host App holding the Device and DeviceHive server connection and the processor running in the enclave 
 * Passes DeviceNotifications and DeviceCommandWrapper between the Host App and enclave processor 
 * 
 */
public class SecureProcessorProxy
{ 
    // proxy connection
    private Socket clientSocket; 
    private ObjectInputStream in; 
    private ObjectOutputStream out; 

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
        clientSocket = new Socket(enclaveIP, port); 
        out = new ObjectOutputStream(new BufferedOutputStream(clientSocket.getOutputStream())); 
        out.flush();
        in = new ObjectInputStream(new BufferedInputStream(clientSocket.getInputStream())); 
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
                        DeviceCommandWrapper command = receiveCommand(); 
                        device.sendCommand(command.commandName, command.parameters); 
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

    /**
     * pass a DeviceNotification into the out stream to be received by the enclave. 
     */
    public void passNotification(DeviceNotification notification) throws Exception
    {
        out.writeObject(notification); 
        out.flush();
    }

    /**
     * Read a DeviceCommandWrapper from the in stream and return it to the caller. 
     * Note that this call is blocking until it has read a response from the enclave.
     */
    public DeviceCommandWrapper receiveCommand() throws Exception
    {     
        DeviceCommandWrapper command = null;
        command = (DeviceCommandWrapper) in.readObject();  
        return command; 
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
                            passNotification(notif);
                            // unsubscribe from key exchange related notifications, 
                            device.unsubscribeAllNotifications();
                            // subscribe to the data notifications
                            subscribeData();   
  
                        }
                        else
                        {
                            // pass notification and await response 
                            passNotification(notif); 
                            DeviceCommandWrapper response = receiveCommand(); 
                            device.sendCommand(response.commandName, response.parameters); 

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
