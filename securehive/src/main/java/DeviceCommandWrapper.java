package com.github.devicehive.client.model; 

import java.io.Serializable; 
import com.github.devicehive.client.model.Parameter;
import java.util.List;
import java.util.ArrayList; 
/**
 * A wrapper class for a DeviceCommand. 
 * This class is used in the enclave processor to represent a constructed command. 
 * The only way to construct a regular DeviceCommand is the sendCommand() method of the Device class. 
 * Since the enclave cannot establish a direct connection to the DeviceHive server it cannot use a Device to send a DeviceCommand. 
 * This wrapper is passed from the enclave processor to the class holding the Device via a SecureProcessorProxy
 * @param deviceId: is included to be able to match the responding command to a Device in a Host App that handles multiple devices. 
*/
public class DeviceCommandWrapper implements Serializable
{
    public String commandName; 
    public List<Parameter> parameters; 
    public String deviceId; 
    public DeviceCommandWrapper(String commandName, List<Parameter> parameters, String deviceId)
    {
        this.commandName = commandName; 
        this.parameters = parameters; 
        this.deviceId = deviceId; 
    }

}
