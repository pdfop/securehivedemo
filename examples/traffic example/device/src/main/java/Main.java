import java.util.Scanner;
import java.io.*;
import java.util.Collections;
import java.util.List;
import java.util.ArrayList;
import java.lang.IllegalThreadStateException; 
import org.joda.time.DateTime; 
import com.github.devicehive.client.model.DHResponse;
import com.github.devicehive.client.model.FailureData;
import com.github.devicehive.client.service.Device;
import com.github.devicehive.client.model.Parameter;
import com.github.devicehive.client.service.DeviceHive;
import com.github.devicehive.client.model.DeviceNotificationsCallback; 
import com.github.devicehive.client.model.DeviceNotification; 
import com.github.devicehive.client.model.DeviceCommandsCallback;
import com.github.devicehive.client.model.CommandFilter; 
import com.github.devicehive.client.service.DeviceCommand;

import javax.crypto.spec.IvParameterSpec; 
import java.security.SecureRandom; 
import java.io.FileInputStream; 
import java.io.FileOutputStream; 
import java.security.*;
import java.security.cert.Certificate; 
import javax.crypto.*; 
import java.io.*; 
import java.util.concurrent.*;
import java.util.Arrays; 
import static org.junit.Assert.*; 
import javax.crypto.spec.PBEKeySpec;
import java.security.spec.InvalidKeySpecException;
import java.security.cert.CertificateException; 
import java.util.Base64; 
import com.google.gson.*;
import java.nio.charset.StandardCharsets;

import java.util.concurrent.atomic.AtomicInteger; 

public class Main
{
    // Devicehive server connection 
    private static final String URL = "http://desktop-nedtc0v.fritz.box/api/rest"; 
    private static final String accessToken = "eyJhbGciOiJIUzI1NiJ9.eyJwYXlsb2FkIjp7ImEiOlswXSwiZSI6MTYxNzE0MTYwMDAwMCwidCI6MSwidSI6MSwibiI6WyIqIl0sImR0IjpbIioiXX19.07-pTHlWIvEEi5lWCU6-dVe2is6eB1fwrTTFv1ssMoM";
    private static final String refreshToken = "eyJhbGciOiJIUzI1NiJ9.eyJwYXlsb2FkIjp7ImEiOlswXSwiZSI6MTYxNzE0MTYwMDAwMCwidCI6MCwidSI6MSwibiI6WyIqIl0sImR0IjpbIioiXX19.16ocpHfZA11AE-3xXXxK4Y5Ld9pmXql7XFD8CgT5dAE";
    private DeviceHive client; 
    // single device to send notifications and receive commands
    // **declaring a SecureDevice rather than a Device **  
    private SecureDevice secureDevice;
    private String deviceId; 
    // execution time measurement 
    private List<Long> times = new ArrayList<Long>();
    private List <Long> encTimes = new ArrayList<Long>();
    private AtomicInteger timerIndex = new AtomicInteger();   
    // data loading 
    private int dataIndex = 0; 
    private List<List<String>> data = new ArrayList<>(); 
 
    public static void main(String[] args) throws InterruptedException, IllegalThreadStateException, Exception
    {
        String deviceIdInput= args[0];
        Main main = new Main(); 
        main.init_device(deviceIdInput); 
        main.loadData();
        main.subscribeCommands(); 
        main.generateAndSendData(); 
    
    }

    /**
     * Initialize Connection to the DeviceHive Server, get a Device from the Server and get a secureDevice 
     */
    private void init_device(String deviceId) throws Exception
    {
        this.deviceId = deviceId;
        client = DeviceHive.getInstance().init(URL, refreshToken, accessToken);
        DHResponse<Device> deviceResponse = client.getDevice(deviceId);
        if (!deviceResponse.isSuccessful())
        {
            System.out.println(deviceResponse);
            return;
        }
	// ** using the device from the server's response to create a SecureDevice ** 
        secureDevice = SecureDeviceBuilder.build(deviceResponse.getData());  
    }

    /**
     * Load simulated data from a given csv file
     */
    private void loadData()
    {
        try
        {
            BufferedReader br = new BufferedReader(new FileReader("../resources/" + deviceId + ".csv"));
            String line;
            while ((line = br.readLine()) != null)
            {
                String[] values = line.split(",");
                data.add(Arrays.asList(values));
            }
        }   
        catch(IOException e)
        {
            System.out.println("Failed to read Data. " + e.getMessage()); 
        }
    }

    /**
     * Subscribe the Device to the Commands sent by the processor 
     */
    private void subscribeCommands() throws Exception
    {
        CommandFilter filter = new CommandFilter();
        // time filter for the most recent command  
        filter.setStartTimestamp(DateTime.now()); 
        filter.setEndTimestamp(DateTime.now());
        // set command names according to the name set in the processor  
        filter.setCommandNames("response"); 
        // callback method when receiving a now command. all reactions to the commands by the processor start here 
        final DeviceCommandsCallback callback = new DeviceCommandsCallback()
        {
            public void onSuccess(List<DeviceCommand> commands)
            {   
                // assert that a command has been found, not stricly necessary as this method should now trigger without a new command arriving 
                // however I have seen some missfires with this triggering on commands that didn't match the filter and thus were not in the list 
                // TODO: do another test run to see if this still happens 
                if(!(commands == null || commands.isEmpty()))
                {
                    for(DeviceCommand command : commands)
                    {   
                        try
                        {
                            // also not necessary, for now still in here because of the issue described above 
                            if(command.getCommandName().equals("response"))
                            {
                                // measuring time from notification creation until parameter decryption of the response to represent system latency
				// ** calling secureDevice.getDecryptedParameters(command) rather than command.getParameters() ** 
                                JsonObject parameters = secureDevice.getDecryptedParameters(command);
                                long end = System.nanoTime(); 
                                int current = timerIndex.getAndIncrement(); 
                                times.set(current, TimeUnit.NANOSECONDS.toMillis(end - times.get(current))); 
                                System.out.println(current); 
                            }
                            
                        }
                        catch(Exception e)
                        {
                            e.printStackTrace(System.out);
                        }
                    }
                }
            }

            public void onFail(FailureData fail)
            {
                System.out.println(fail); 
            }
        }; 
        secureDevice.subscribeCommands(filter, callback); 

    }

    private void generateAndSendData()
    {
        ScheduledExecutorService service = Executors.newScheduledThreadPool(1);
        // schedule the device to send a notification with variable delay 
        Callable<Void> sendData = new Callable<Void>() {
            public Void call() {
                try 
                { 
                    // time stamp for the start of the lifecycle of this notification 
                    long start = System.nanoTime(); 
                    // build notification parameters    
                    List<Parameter> params = new ArrayList<Parameter>(); 
                    params.add(new Parameter("license-plate", data.get(dataIndex).get(0))); 
                    params.add(new Parameter("speed", data.get(dataIndex).get(1)));
                    // encrypt and publish the notification 
		    // **calling senEncryptedNotification rather than sendNotification, note that sendNotification is also available ** 
                    secureDevice.sendEncryptedNotification("data", params);
                    // addtional statistic for the length of just the creation, encryption and sending time, relevant for comparison with homomorphic encyption
                    encTimes.add(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start));
                    times.add(start);
                    
                } 
                catch(Exception e)
                {
                    System.out.println(e.getMessage()); 

                }
                finally
                {
                    // stopping after we sampled 100 times 
                    if(timerIndex.get() >= 99)
                    {
                        try
                        {
                            System.out.println("Done"); 
                            FileWriter writer = new FileWriter(deviceId +  ".txt"); 
                            for (int i = 0; i < times.size(); i++)
                            {
                                String data = times.get(i).toString() + "," + encTimes.get(i).toString() + "\n"; 
                                writer.write(data);     
                            }   
                            writer.close(); 
                            System.out.println("File Written"); 
                        }
                        catch(Exception e)
                        {
                            System.out.println(e.getMessage()); 
                        }
                    }
                    // reschedule this data generation 
                    else
                    {
                        // here we can make the scheduling rate variable e.g. by additionally reading a measurement delay from our data csv 
                        service.schedule(this, 100, TimeUnit.MILLISECONDS);
                        dataIndex++; 
                    }

                }    
             return null;
            }
        };
        service.schedule(sendData,0L, TimeUnit.MILLISECONDS);
    }




    
}   


