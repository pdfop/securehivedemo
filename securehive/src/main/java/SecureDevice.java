package com.github.devicehive.client.service;

import javax.crypto.spec.IvParameterSpec; 
import java.security.SecureRandom; 
import java.io.FileInputStream; 
import java.io.IOException; 
import java.security.cert.Certificate;
import java.security.cert.CertificateException; 
import static org.junit.Assert.*;  
import java.security.*;
import javax.crypto.*; 
import javax.crypto.spec.PBEKeySpec;
import java.security.spec.InvalidKeySpecException;
import java.util.List;
import java.util.ArrayList; 
import java.util.Base64;

import com.github.devicehive.client.model.Parameter;
import com.github.devicehive.client.service.Device;
import com.github.devicehive.client.service.DeviceCommand;
import com.github.devicehive.client.model.DeviceNotification;
import com.github.devicehive.client.model.DHResponse; 
import com.github.devicehive.client.model.NotificationFilter; 
import com.github.devicehive.client.model.CommandFilter;
import com.github.devicehive.client.model.DeviceCommandsCallback;
import com.github.devicehive.client.model.DeviceNotificationsCallback;

import org.joda.time.DateTime;
import org.joda.time.Duration; 
import com.google.gson.JsonObject;
import com.google.gson.JsonElement; 

/**
 * This class is an Implementation of DeviceInterface with additional methods rather than a subclass of Device 
 * This is done to preserve the original state of the Device Class 
 * Instances of this class have to be constructed using an existing Device to communicate with the DeviceHive Server 
 * This is done to preserve the original state of the server code. 
 * The existence of this class or the difference between it and its underlying Device are unknown to the server.
 */
public class SecureDevice implements DeviceInterface
{
    // authentication
    private PublicKey enclavePK; 
    private PrivateKey deviceSK;
    // encryption
    private SecretKey messageKey;  
    private final Duration NONCE_TIMEOUT;  
    // contains certificatates 
    private KeyStore store;
    private final String storePath; 
    // keystore password 
    private final char[] pwdArray; 
    // sgx remote attestation
    private String SPID, iasSKey, MRENCLAVE, MRSIGNER = null;  
    private final String iasCertPath;
    private final String attestationServer; 
    // initialization & attestation status 
    private Boolean secure = false; 
    private Boolean attested = false; 
    // devicehive connection
    private final Device device;
    private final String deviceId;  
    // encoding for key transport etc. 
    private static final Base64.Decoder decoder = Base64.getDecoder(); 
    private static final Base64.Encoder encoder = Base64.getEncoder(); 

    /**
     * Create a new SecureDevice
     * To be called by the SecureDeviceBuilder
     */
    protected SecureDevice(Device device, boolean attest, String storePass, String storePath, String iasCertPath, String attestationServer, int nonceTimeout) throws Exception
    {
        this.device = device; 
        this.deviceId = device.getName(); 
        this.pwdArray = storePass.toCharArray(); 
        this.iasCertPath = iasCertPath; 
        this.attestationServer = attestationServer; 
        this.storePath = storePath; 
        this.NONCE_TIMEOUT = new Duration(nonceTimeout); 
        initSecurity(); 
        if(attest)
        {
            remoteAttest(); 
        }
        keyExchange(); 
    }

    /**
     * Load Certificates and remote attestation material from the key store
     */
    private void initSecurity() throws Exception
    {
        // load KeyStore from file system 
        FileInputStream fis = null; 
        try 
        {
            store = KeyStore.getInstance("pkcs12");
            fis = new FileInputStream(storePath); 
            store.load(fis, pwdArray);
            fis.close();
        }
        catch(IOException  | KeyStoreException | CertificateException | NoSuchAlgorithmException e)
        {
            store = null; 
            secure = false;
            System.out.println("KeyStore could not be loaded. \n" + e.getMessage()); 
        }
        // load secrets from keystore
        try
        {
            Certificate enclaveCert = store.getCertificate("enclaveCert");
            assertNotNull(enclaveCert); 
            deviceSK = (PrivateKey) store.getKey(deviceId, pwdArray);
            assertNotNull(deviceSK);  
            enclavePK = enclaveCert.getPublicKey(); 
        }
        catch(KeyStoreException | AssertionError e)
        {
            System.out.println("Failed to recover all needed Secrets. Verify KeyStore contents. \n" + e.getMessage()); 
            secure =false; 
        }         
    }

    /**
     * Generate a 256 bit AES Key to be used for message encryption 
     */
    private void generateKey() throws Exception
    {
            KeyGenerator keyGen = KeyGenerator.getInstance("AES");
            keyGen.init(256);
            messageKey = keyGen.generateKey(); 
    }

    /**
     * Generate an Initialization Vector for the AES Cipher 
     */
    private IvParameterSpec generateIV()
    {
        byte[] iv = new byte[16];
        new SecureRandom().nextBytes(iv);
        return new IvParameterSpec(iv);
    }

    /**
     * Perform a key exchange with the enclave processor. 
     * Establishes mutual authentication via signed timestamps before gengerating and transmitting an AES key. 
     */
    private void keyExchange() throws Exception
    {
        Boolean sent = false;
        while(true)
        {
            if(sent)
            {
                break;
            }
            device.sendNotification("$keyrequest", null); 

            List<DeviceCommand> comms = device.getCommands(DateTime.now().minusSeconds(5), DateTime.now(), 50);
            for(DeviceCommand command : comms)
            {
                if(command.getCommandName().equals("$keyexchange"))
                {
                    JsonObject parameters = command.getParameters(); 
                    String signed = parameters.get("signed").getAsString(); 
                    String timestamp = parameters.get("timestamp").getAsString(); 
                    
                    // verify that the included timestamp was signed using the enclave private key 
                    Signature publicSignature = Signature.getInstance("SHA256withRSA");
                    publicSignature.initVerify(enclavePK);
                    publicSignature.update(timestamp.getBytes());
                    byte[] signatureBytes = decoder.decode(signed);
                    Boolean verified = publicSignature.verify(signatureBytes);

                    if(verified)
                    {
                        // generate AES key and encrypt it using the enclave public key 
                        generateKey();                        
                        Cipher encrypt=Cipher.getInstance("RSA/ECB/PKCS1Padding");
                        encrypt.init(Cipher.PUBLIC_KEY, enclavePK);
                        byte[] encryptedKey = encrypt.doFinal(messageKey.getEncoded());
                        String keyString = encoder.encodeToString(encryptedKey);  
                        // sign a timestamp with the device private key to be verified by the enclave 
                        Signature privateSignature = Signature.getInstance("SHA256withRSA");
                        privateSignature.initSign(deviceSK);
                        privateSignature.update(timestamp.getBytes());
                        byte[] signature = privateSignature.sign();
                        String signedStamp = encoder.encodeToString(signature); 
                        
                        List<Parameter> notifParams = new ArrayList<Parameter>(); 
                        notifParams.add(new Parameter("key", keyString)); 
                        notifParams.add(new Parameter("signed", signedStamp)); 
                        device.sendNotification("$keyexchange", notifParams); 
                        sent = true; 
                        secure = true;
                        break; 
                    }                    
                }
            }
            Thread.sleep(1000);  

        }
    }

    /**
     * Load remote attestation material from the key store 
     */
    private void loadAttestationKeys()
    {           
        KeyStore.PasswordProtection storePass = new KeyStore.PasswordProtection(pwdArray);
        
        SPID = getStringSecret("SPID", storePass); 
        iasSKey = getStringSecret("iasSKey", storePass); 
        MRENCLAVE = getStringSecret("MRENCLAVE", storePass); 
        MRSIGNER = getStringSecret("MRSIGNER", storePass); 
        try
        {
            assertNotNull(SPID); 
            assertNotNull(iasSKey); 
            assertNotNull(MRENCLAVE); 
            assertNotNull(MRSIGNER); 
        }
        catch(AssertionError e)
        {
            attested = false; 
            System.out.println("Failed to recover attestation data. Verify KeyStore contents."); 

        }
        
    }

    /**
     * Helper function to extract plaintext String a Key stored in a KeyStore. 
     * This is needed to send plaintext information to the enclave remote attestation server to verify the integrity of the enclave
     */
    private String getStringSecret(String entry, KeyStore.PasswordProtection storePass)
    {
        SecretKeyFactory factory = null; 
        KeyStore.SecretKeyEntry ske = null; 
        try 
        {
            factory = SecretKeyFactory.getInstance("PBE");
            
        } 
        catch (NoSuchAlgorithmException e) 
        {
            
            return null; 
        }

        try 
        {
            ske = (KeyStore.SecretKeyEntry)store.getEntry(entry, storePass);
        } 
        catch (NoSuchAlgorithmException | KeyStoreException | UnrecoverableEntryException e) 
        {
            return null; 
        }

        PBEKeySpec keySpec = null; 
        try
        {
            keySpec = (PBEKeySpec)factory.getKeySpec(
                ske.getSecretKey(),
                PBEKeySpec.class);
        }
        catch(InvalidKeySpecException e)
        {
            return null; 
        }
        char[] password = keySpec.getPassword();
        return new String(password); 
    }

    public Boolean isSecure()
    {
        return secure; 
    }

    public Boolean isAttested()
    {
        return attested; 
    }

    /**
     * Perform remote attestation with the sgx-lkl enclave. 
     */
    public void remoteAttest()
    {
        loadAttestationKeys(); 
        int code = 1;
        String[] config = {"sgx-lkl-ctl",
         "attest",
          "--server=" + attestationServer,
           "--ias-spid=" + SPID,
            "--ias-quote-type=1",
              "--ias-skey=" + iasSKey,
               "--mrenclave=" + MRENCLAVE,
                "--mrsigner=" + MRSIGNER,
                  "--ias-sign-ca-cert=" + iasCertPath};
        ProcessBuilder pb = new ProcessBuilder(config); 
        try
        {
            Process attestation = pb.start(); 
            code = attestation.waitFor();     
        }
        catch(InterruptedException | IOException | IllegalThreadStateException e)
        {
            System.out.println(e.getMessage()); 
        }
        if(code == 0)
        {
            attested = true;
        }
        else
        {
            secure = false; 
            attested = false;
            System.out.println("Remote Attestation Failed"); 
        }
    }

    /**
     * Send an AES encrypted notification. 
     * Encrypts all given parameter names and values. 
     * Base64 Encoding is done to prevent encoding/blocksize problems with different String encodings. 
     * Includes the IV used during encryption as new parameter. 
     * Does not include a Nonce as the notification timestamp may be used as such
     * @returns the DHResponse of the underlying sendNotification call  
     */
    public DHResponse<DeviceNotification> sendEncryptedNotification(String notification, List<Parameter> parameters) throws Exception
    {
        Cipher encrypt = Cipher.getInstance("AES/CBC/PKCS5Padding");
        IvParameterSpec iv = generateIV();
        encrypt.init(Cipher.ENCRYPT_MODE, messageKey, iv); 

        for(Parameter param : parameters)
        {
            // encrypt parameter key and value
            param.setValue(encoder.encodeToString(encrypt.doFinal(param.getValue().getBytes()))); 
            param.setKey(encoder.encodeToString(encrypt.doFinal(param.getKey().getBytes()))); 
        }
        // add IV and Nonce 
        parameters.add(new Parameter("iv", encoder.encodeToString(iv.getIV()))); 
        return device.sendNotification(notification, parameters); 
    }

    /**
     * Decrypt the parameters of a DeviceCommand. 
     * Expects the Command to contain the keys "iv" to initialize the decryption cipher and "nonce" to verify the freshness of the command 
     * @returns a JsonObject of the decrypted parameters, the same as command.getParameters() would 
     */
    public JsonObject getDecryptedParameters(DeviceCommand command) throws Exception
    {
        JsonObject parameters = command.getParameters(); 
        JsonObject decryptedParameters = new JsonObject(); 
        Cipher decrypt = Cipher.getInstance("AES/CBC/PKCS5Padding"); 
        IvParameterSpec iv = new IvParameterSpec(decoder.decode(parameters.get("iv").getAsString()));
        decrypt.init(Cipher.DECRYPT_MODE, messageKey, iv);

        for(java.util.Map.Entry<String, JsonElement> entry : parameters.entrySet())
        {
            if(entry.getKey().equals("iv"))
            {
                continue; 
            }
            if(entry.getKey().equals("nonce"))
            {
                DateTime nonce = DateTime.parse(new String(decrypt.doFinal(decoder.decode(entry.getValue().getAsString())))); 
                if(new Duration(command.getTimestamp(), nonce).isLongerThan(NONCE_TIMEOUT))
                {
                    return null;  
                }
            } 
            else
            {
                decryptedParameters.addProperty(new String(decrypt.doFinal(decoder.decode(entry.getKey()))),
                    new String(decrypt.doFinal(decoder.decode(entry.getValue().getAsString())))
                 );                
            }
        }
        return decryptedParameters; 
    }

    public void save()
    {
        device.save(); 
    }

    public List<DeviceCommand> getCommands(DateTime startTimestamp, DateTime endTimestamp, int maxNumber)
    {
        return device.getCommands(startTimestamp, endTimestamp, maxNumber); 
    }

    public List<DeviceNotification> getNotifications(DateTime startTimestamp, DateTime endTimestamp)
    {
        return device.getNotifications(startTimestamp, endTimestamp); 
    }

    public DHResponse<DeviceCommand> sendCommand(String command, List<Parameter> parameters)
    {
        return device.sendCommand(command, parameters); 
    }

    public DHResponse<DeviceNotification> sendNotification(String notification, List<Parameter> parameters)
    {
        return device.sendNotification(notification, parameters); 
    }

    public void subscribeCommands(CommandFilter commandFilter, DeviceCommandsCallback commandsCallback)
    {
        device.subscribeCommands(commandFilter, commandsCallback); 
    }

    public void subscribeNotifications(NotificationFilter notificationFilter, DeviceNotificationsCallback notificationCallback)
    {
        device.subscribeNotifications(notificationFilter, notificationCallback); 
    }
    public void unsubscribeCommands(CommandFilter commandFilter)
    {
        device.unsubscribeCommands(commandFilter); 
    }

    public void unsubscribeNotifications(NotificationFilter notificationFilter)
    {
        device.unsubscribeNotifications(notificationFilter); 
    }

}