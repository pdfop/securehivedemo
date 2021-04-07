package com.n1analytics.paillier; 
import com.github.devicehive.client.model.Parameter;
import com.github.devicehive.client.service.Device;
import com.github.devicehive.client.service.DeviceCommand;
import com.github.devicehive.client.model.DeviceNotification;
import com.github.devicehive.client.model.DHResponse; 
import com.github.devicehive.client.model.NotificationFilter; 
import com.github.devicehive.client.model.CommandFilter;
import com.github.devicehive.client.model.DeviceCommandsCallback;
import com.github.devicehive.client.model.DeviceNotificationsCallback;
import com.n1analytics.paillier.*;
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

import org.joda.time.DateTime;
import com.google.gson.JsonObject; 

import java.io.IOException;
import java.math.BigInteger;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.security.KeyPair;

import com.google.gson.*; 
import com.google.gson.JsonObject;
import com.google.gson.JsonElement; 
import java.util.Base64; 


import ope.util.Encoder; 
import ope.fast.FastOpeKey;  

public class HomomorphicDevice
{   
    private Device device; 
    // mutual authentication 
    private PublicKey enclavePK; 
    private PrivateKey deviceSK; 
    private SecretKey messageKey;  
    private KeyStore store;
    private final char[] pwdArray = "testpw".toCharArray();    
    private final String storePath = "resources/SecureHiveDevice.pkcs12";   

    // homomorphic encryption 
    private  PaillierPrivateKey privKey;
    private  PaillierPublicKey pubKey;
    private PaillierContext cipher; 
    // string encoding 
    private Base64.Decoder decoder = Base64.getDecoder(); 
    private Base64.Encoder encoder = Base64.getEncoder();

    private ope.Key opeKey = new ope.fast.FastOpeCipher().generateKey(); 

    public HomomorphicDevice(Device device) throws Exception
    {
        this.device = device;
        init();  
        keyExchange(); 

    }

    public DHResponse<DeviceNotification> sendEncryptedNotification(String notification, List<Parameter> parameters) throws Exception
    {
        // encrypt license plate with AES 
        Parameter param = parameters.get(0);
        Cipher encrypt = Cipher.getInstance("AES/CBC/PKCS5Padding"); 
        IvParameterSpec iv = generateIV(); 
        encrypt.init(Cipher.ENCRYPT_MODE, messageKey, iv);
        // add the IV used for AES
        parameters.add(new Parameter("iv", encoder.encodeToString(iv.getIV()))); 

        param.setValue(encoder.encodeToString(encrypt.doFinal(param.getValue().getBytes())));
        param.setKey(encoder.encodeToString(encrypt.doFinal(param.getKey().getBytes())));

        // encrypt speed value with pallier to enable addition to calculate difference to limit 
        param = parameters.get(1); 
        EncryptedNumber enc = cipher.encrypt(new BigInteger(param.getValue())); 
        JsonObject jsonEnc = new JsonObject();
        jsonEnc.addProperty("exponent", enc.getExponent()); 
        jsonEnc.addProperty("ciphertext", enc.ciphertext);  
        param.setValue(jsonEnc.toString()); 
        param.setKey(encoder.encodeToString(encrypt.doFinal(param.getKey().getBytes())));

        // encrypt speed value again with OPE scheme to make comparison to limit
        param = parameters.get(2); 
        param.setKey(encoder.encodeToString(encrypt.doFinal(param.getKey().getBytes())));
        param.setValue(encoder.encodeToString(opeKey.encrypt(Encoder.encodeInt(Integer.parseInt(param.getValue()))))); 

        // encrypt timestamp with AES 
        param = parameters.get(3); 
        param.setValue(encoder.encodeToString(encrypt.doFinal(param.getValue().getBytes())));
        param.setKey(encoder.encodeToString(encrypt.doFinal(param.getKey().getBytes())));

        return device.sendNotification(notification, parameters); 
    }

    public JsonObject getDecryptedParameters(DeviceCommand command) throws Exception
    {
        JsonObject parameters = command.getParameters(); 
        JsonObject decryptedParameters = new JsonObject();
        Gson gson = new Gson();  
        Cipher decrypt = Cipher.getInstance("AES/CBC/PKCS5Padding"); 
        IvParameterSpec iv = new IvParameterSpec(decoder.decode(parameters.get("iv").getAsString()));
        decrypt.init(Cipher.DECRYPT_MODE, messageKey, iv);

        java.util.Set<java.util.Map.Entry<java.lang.String,JsonElement>> entries = parameters.entrySet(); 
        for(java.util.Map.Entry<String, JsonElement> entry : entries)
        {
            if(entry.getKey().equals("iv"))
            {
                continue;
            }

            String key = new String(decrypt.doFinal(decoder.decode(entry.getKey()))); 
            // decrypt license plate with AES
            if(key.equals("license-plate") || key.equals("time"))
            {
                decryptedParameters.addProperty(key,
                new String(decrypt.doFinal(decoder.decode(entry.getValue().getAsString()))));

            }

            else if(key.equals("over-limit"))
            {
                JsonObject speed = gson.fromJson(parameters.get(entry.getKey()).getAsString(), JsonObject.class);
                EncryptedNumber s = new EncryptedNumber(cipher, speed.get("ciphertext").getAsBigInteger(), speed.get("exponent").getAsInt()); 
                double difference = privKey.decrypt(s).decodeDouble(); 
                decryptedParameters.addProperty(key, difference); 

            }
        }
         
        return decryptedParameters; 
    }

    private void init() throws Exception
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
            System.out.println("KeyStore could not be loaded. \n" + e.getMessage()); 
        } 
        try
        {
            Certificate enclaveCert = store.getCertificate("enclaveCert");
            assertNotNull(enclaveCert); 
            deviceSK = (PrivateKey) store.getKey(device.getId(), pwdArray);
            assertNotNull(deviceSK);  
            enclavePK = enclaveCert.getPublicKey(); 
        }
        catch(KeyStoreException | AssertionError e)
        {
            System.out.println("Failed to recover all needed Secrets. Verify KeyStore contents."); 
        }         

    }

    private void generateKey() throws Exception
    {
            KeyGenerator keyGen = KeyGenerator.getInstance("AES");
            keyGen.init(256);
            messageKey = keyGen.generateKey(); 
    }

    private IvParameterSpec generateIV()
    {
        byte[] iv = new byte[16];
        new SecureRandom().nextBytes(iv);
        return new IvParameterSpec(iv);
    }

    private void generateKeys() throws Exception
    {
        privKey = PaillierPrivateKey.create(1024); 
        pubKey = privKey.getPublicKey(); 
        cipher = pubKey.createSignedContext(); 
    }

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

                    Signature publicSignature = Signature.getInstance("SHA256withRSA");
                    publicSignature.initVerify(enclavePK);
                    publicSignature.update(timestamp.getBytes());
                    byte[] signatureBytes = decoder.decode(signed);
                    Boolean verified = publicSignature.verify(signatureBytes);
                    if(verified)
                    {
                        generateKey(); 
                        generateKeys();                        
                        Cipher encrypt=Cipher.getInstance("RSA/ECB/PKCS1Padding");
                        encrypt.init(Cipher.PUBLIC_KEY, enclavePK);
                        byte[] encryptedKey = encrypt.doFinal(messageKey.getEncoded());
                        String keyString = encoder.encodeToString(encryptedKey);  

                        Signature privateSignature = Signature.getInstance("SHA256withRSA");
                        privateSignature.initSign(deviceSK);
                        privateSignature.update(timestamp.getBytes());
                        byte[] signature = privateSignature.sign();
                        String signedStamp = encoder.encodeToString(signature); 
                        
                        List<Parameter> notifParams = new ArrayList<Parameter>(); 
                        notifParams.add(new Parameter("key", keyString)); 
                        notifParams.add(new Parameter("signed", signedStamp)); 
                        device.sendNotification("$keyexchange", notifParams); 

                        Cipher encryptAES = Cipher.getInstance("AES/CBC/PKCS5Padding");
                        IvParameterSpec iv = generateIV();
                        encryptAES.init(Cipher.ENCRYPT_MODE, messageKey, iv); 

                        String paillierPK = encoder.encodeToString(encryptAES.doFinal(pubKey.getModulus().toByteArray())); 
                        List<Parameter> paillierParams = new ArrayList<Parameter>(); 
                        paillierParams.add(new Parameter("key", paillierPK));
                        paillierParams.add(new Parameter("iv", encoder.encodeToString(iv.getIV())));  

                        paillierParams.add(new Parameter("opeKey", encoder.encodeToString(encryptAES.doFinal(opeKey.encodeKey())))); 
                        device.sendNotification("$HEK", paillierParams); 
                        sent = true; 
                        break; 
                    }                   
                }
            }
            Thread.sleep(1000);  

        }
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
