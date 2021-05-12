package com.n1analytics.paillier; 

import java.util.Scanner;
import java.io.*;
import java.util.Collections;
import java.util.List;
import java.util.ArrayList;
import java.lang.IllegalThreadStateException; 

import com.google.gson.*;
import org.joda.time.DateTime;
import org.joda.time.Duration; 

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

import java.util.concurrent.atomic.AtomicInteger; 
import javax.crypto.spec.IvParameterSpec; 
import java.security.SecureRandom; 
import java.io.FileInputStream; 
import java.io.FileOutputStream; 
import java.security.*;
import java.security.cert.Certificate; 
import javax.crypto.*; 
import java.util.concurrent.*;
import static org.junit.Assert.*; 
import javax.crypto.spec.PBEKeySpec;
import java.security.spec.InvalidKeySpecException;
import java.security.cert.CertificateException; 
import java.util.Base64; 
import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays; 
import java.math.BigInteger;
import com.n1analytics.paillier.*;

public class Main
{	
    // Devicehive Setup 
    private static final String URL = "http://20.67.42.189//api/rest"; 
    private static final String accessToken = "eyJhbGciOiJIUzI1NiJ9.eyJwYXlsb2FkIjp7ImEiOlswXSwiZSI6MTYyMDgwNTQzOTc4NiwidCI6MSwidSI6MSwibiI6WyIqIl0sImR0IjpbIioiXX19.v48hkgchHdkk2x4B4qEF0hz_hfMRnxaj8qluWKEoKMY";
    private static final String refreshToken = "eyJhbGciOiJIUzI1NiJ9.eyJwYXlsb2FkIjp7ImEiOlswXSwiZSI6MTYzNjUyODQzOTc4NiwidCI6MCwidSI6MSwibiI6WyIqIl0sImR0IjpbIioiXX19.HqZp_oLBIhgzTjCSuE7zuSCvguj4MSSUjV-aaE_0unE";
    private Device device; 
    private String deviceId;
    private HomomorphicDevice secureDevice;
    // execution time measurement 
    private List<Duration> times = new ArrayList<>();  
    // data generation
    private AtomicInteger dataIndex = new AtomicInteger(0);
    private List<List<String>> data = new ArrayList<>(); 
    
    public static void main(String[] args) throws Exception
    {
        String deviceIdInput= args[0];
        Main main = new Main(); 
        main.init_device(deviceIdInput); 
        main.loadData();
        main.subscribeCommands(); 
        main.generateAndSendData(); 

    }

    private void init_device(String deviceId) throws Exception
    {
        this.deviceId = deviceId; 
        DeviceHive client = DeviceHive.getInstance().init(URL, refreshToken, accessToken);
        DHResponse<Device> deviceResponse = client.getDevice(deviceId);
        if (!deviceResponse.isSuccessful())
        {
            System.out.println(deviceResponse);
            return;
        }
        device = deviceResponse.getData();
        secureDevice = new HomomorphicDevice(device); 
    }

    private void generateAndSendData() throws Exception
    {    
        ScheduledExecutorService service = Executors.newScheduledThreadPool(1);
        Callable<Void> sendData = new Callable<Void>() {
            public Void call() {
                int current = dataIndex.getAndIncrement();
                try 
                {  
                    // build notification parameters    
                    List<Parameter> params = new ArrayList<Parameter>(); 
                    params.add(new Parameter("license-plate", data.get(current).get(0))); 
                    params.add(new Parameter("speed", data.get(current).get(1)));
                    params.add(new Parameter("speed-ope", data.get(current).get(1)));
                    params.add(new Parameter("time", DateTime.now().toString())); 
                    // encrypt and publish the notification 
		            // **calling senEncryptedNotification rather than sendNotification, note that sendNotification is also available ** 
                    secureDevice.sendEncryptedNotification("data", params);                     
                } 
                catch(Exception e)
                {
                    System.out.println(e.getMessage()); 

                }
                finally
                {
                    // stopping after we processed all available data 
                    if(current >= data.size() -1)
                    {
                        try
                        {
                            System.out.println("Done"); 
                            FileWriter writer = new FileWriter(deviceId +  ".txt"); 
                            for (int i = 0; i < times.size(); i++)
                            {
                                String responseTime = String.valueOf(times.get(i).getMillis()) + "\n";  
                                writer.write(responseTime);     
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
                        service.schedule(this, 500L, TimeUnit.MILLISECONDS);
                        //Long.parseLong(data.get(current).get(2))
                    }   
                }    
             return null;
            }
        };
        service.schedule(sendData,0L, TimeUnit.MILLISECONDS);
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
                for(DeviceCommand command : commands)
                {   
                    try
                    {
                        // measuring time from notification creation until parameter decryption of the response to represent system latency
				        // ** calling secureDevice.getDecryptedParameters(command) rather than command.getParameters() ** 
                        JsonObject parameters = secureDevice.getDecryptedParameters(command);
                        times.add(new Duration(DateTime.parse(parameters.get("time").getAsString()), DateTime.now()));
  
                    }
                    catch(Exception e)
                    {
                        System.out.println(e.getMessage()); 
                       e.printStackTrace(System.out);
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

    /**
     * Load simulated data from a given csv file
     */
    private void loadData()
    {
        try
        {
            BufferedReader br = new BufferedReader(new FileReader("resources/" + deviceId + ".csv"));
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
}   