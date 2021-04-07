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
*/
public class DeviceCommandWrapper implements Serializable
{
    public String commandName; 
    public List<Parameter> parameters; 
    public DeviceCommandWrapper(String commandName, List<Parameter> parameters)
    {
        this.commandName = commandName; 
        this.parameters = parameters; 
    }

    public String getCommandName()
    {
        return this.commandName; 
    }

    public List<Parameter> getParameters()
    {
        return this.parameters; 
    }
}
