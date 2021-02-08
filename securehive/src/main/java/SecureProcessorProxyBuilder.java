package com.github.devicehive.client.service;

import com.github.devicehive.client.service.Device;
import com.github.devicehive.client.model.NotificationFilter;

import java.io.FileInputStream; 
import java.io.IOException; 
import java.io.BufferedReader; 
import java.io.FileReader;

public class SecureProcessorProxyBuilder
{
    /**
     * Overloaded build method to construct a SecureProcessorProxy from a config file in the default location. 
     * Default location : resources/SecureProcessorProxy.config
     * Will assume default values if none are given in the file
     * enclaveIP : 10.0.1.1
     * port : 6767
     */
    public static SecureProcessorProxy build(Device device, NotificationFilter filter) throws IOException
    {
       return build(device, filter, "resources/SecureProcessorProxy.config");

    }

    /**
     * Overloaded build method to construct a SecureProcessorProxy from a config file at a given location. 
     * Will assume default values if none are given in the file
     * enclaveIP : 10.0.1.1
     * port : 6767
     */
    public static SecureProcessorProxy build(Device device, NotificationFilter filter, String configPath) throws IOException
    {
        String enclaveIP = "10.0.1.1"; 
        int port = 6767; 

        try (BufferedReader br = new BufferedReader(new FileReader(configPath))) 
        {
            String line;
            while ((line = br.readLine()) != null)
            {
                String[] split;
                switch((split = line.split("="))[0])
                {
                    case "enclaveIP":
                        enclaveIP = split[1];  
                        break; 
                    case "port":
                        port = Integer.parseInt(split[1]);
                        break; 
                    default:
                        break; 

                }
            }
        }
        catch(IOException e)
        {
            // if file reading failed assume default values
        }
        return new SecureProcessorProxy(device, filter, enclaveIP, port); 
    }

    /**
     * Overloaded build method to construct a SecureProcessorProxy from its constructor parameters. 
     */
    public static SecureProcessorProxy build(Device device, NotificationFilter filter, String enclaveIP, int port) throws IOException
    {
        return new SecureProcessorProxy(device, filter, enclaveIP, port); 
    }
}
