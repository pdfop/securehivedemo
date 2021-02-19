import com.github.devicehive.client.service.Device;
import java.io.FileInputStream; 
import java.io.IOException; 
import java.io.BufferedReader; 
import java.io.FileReader;

public class SecureDeviceBuilder
{
    /**
     * Overloaded build method to construct a SecureDevice from a configuration file in the default location. 
     * Default location: ../resources/SecureDevice.config
     * Will assume default values for any Parameters not specified in the file. 
     * These default values are:
     * attest : false 
     * storePath : ../resources/SecureHiveDevice.pkcs12
     * storePass : changeit 
     * iasCertPath : ../resources/iasCert.der
     * attestationServer : 10.0.1.1:56000
     * noneTimeout : 500 
     */
    public static SecureDevice build(Device device) throws Exception
    {
       return build(device, "resources/SecureDevice.config");

    }

    /**
     * Overloaded build method to construct a SecureDevice from a configuration file located in a custom path. 
     * Will assume default values for any Parameters not specified in the file. 
     * These default values are:
     * attest : false 
     * storePath : ../resources/SecureHiveDevice.pkcs12
     * storePass : changeit 
     * iasCertPath : ../resources/iasCert.der
     * attestationServer : 10.0.1.1:56000
     * noneTimeout : 500 
     */
    public static SecureDevice build(Device device, String configPath) throws Exception
    {

        boolean attest = false; 
        String storePass = "changeit";  
        String storePath = "resources/SecureHiveDevice.pkcs12"; 
        String iasCertPath = "resources/iasCert.der"; 
        String attestationServer = "10.0.1.1/56000";  
        int nonceTimeout = 500; 

        try (BufferedReader br = new BufferedReader(new FileReader(configPath))) 
        {
            String line;
            while ((line = br.readLine()) != null)
            {
                String[] split;
                switch((split = line.split("="))[0])
                {
                    case "attest":
                        attest = Boolean.parseBoolean(split[1]);  
                        break; 
                    case "storePass":
                        storePass = split[1]; 
                        break; 
                    case "storePath":
                        storePath = split[1]; 
                        break;
                    case "iasCertPath":
                        iasCertPath = split[1]; 
                        break;
                    case "attestationServer":
                        attestationServer = split[1]; 
                        break;
                    case "nonceTimeout":
                        nonceTimeout = Integer.parseInt(split[1]); 
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
        return new SecureDevice(device, attest, storePass,
        storePath, iasCertPath, attestationServer, nonceTimeout); 
    }

    /**
     * Overloaded build method to construct a SecureDevice from its constructor parameters. 
     */
    public static SecureDevice build(Device device, boolean attest, String storePass,
     String storePath, String iasCertPath, String attestationServer, int nonceTimeout) throws Exception
    {
        return new SecureDevice(device, attest, storePass,
         storePath, iasCertPath, attestationServer, nonceTimeout); 
    }

}
