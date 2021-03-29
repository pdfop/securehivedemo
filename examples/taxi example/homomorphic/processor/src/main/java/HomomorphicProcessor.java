import java.io.*; 
import java.net.*;


import com.google.gson.JsonObject;
import com.google.gson.JsonElement;
import com.google.gson.Gson.*;
import com.google.gson.*; 
import org.joda.time.DateTime;

import com.github.devicehive.client.service.DeviceCommand;
import com.github.devicehive.client.model.DeviceNotification;
import com.github.devicehive.client.model.FailureData;
import com.github.devicehive.client.model.Parameter;
import com.github.devicehive.client.service.Device;


import java.util.Collections;
import java.util.List;
import java.util.ArrayList; 

import java.io.FileInputStream; 
import java.io.FileOutputStream; 
import java.security.*;
import java.security.cert.Certificate; 
import javax.crypto.*; 
import java.io.*; 
import static org.junit.Assert.*; 
import javax.crypto.spec.PBEKeySpec;
import java.security.spec.InvalidKeySpecException;
import java.security.cert.CertificateException; 
import java.nio.charset.StandardCharsets;
import java.util.Base64; 
import javax.crypto.spec.SecretKeySpec; 
import javax.crypto.spec.IvParameterSpec; 
import java.nio.channels.*; 
import java.nio.charset.*;
import java.nio.ByteBuffer;

import java.math.BigInteger;
import java.security.KeyPair;

import com.google.gson.*; 
import com.google.gson.JsonObject;
import com.google.gson.JsonElement; 
import java.util.Base64; 

import com.n1analytics.paillier.*;


public class HomomorphicProcessor
{
    // mutual auth 
    private PublicKey devicePK;   
    private PrivateKey enclaveSK;
    private SecretKey messageKey;  
    // TODO: get this as user input rather than hardcode it 
    private KeyStore store; 
    private char[] pwdArray = "testpw".toCharArray(); 

    // encoding 
    private Base64.Decoder decoder = Base64.getDecoder(); 
    private Base64.Encoder encoder = Base64.getEncoder(); 
 
    // homomorphic encryption 
    private PaillierPublicKey pubKey;
    protected PaillierContext cipher; 

    protected Device device; 

    public HomomorphicProcessor(Device device) throws Exception
    {
        this.device = device; 
        init(); 
        keyExchange(); 
    }

    private void init() throws Exception
    {
        // load KeyStore from file system 
        FileInputStream fis = null; 
        try 
        {
            store = KeyStore.getInstance("pkcs12");
            fis = new FileInputStream("SecureHive.pkcs12"); 
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
            Certificate deviceCert = store.getCertificate("deviceCert");
            assertNotNull(deviceCert); 
            KeyStore.PasswordProtection storePass = new KeyStore.PasswordProtection(pwdArray);
            enclaveSK = (PrivateKey) store.getKey("enclaveCert", pwdArray);
            assertNotNull(enclaveSK);  
            devicePK = deviceCert.getPublicKey(); 
        }
        catch(KeyStoreException | AssertionError | NoSuchAlgorithmException | UnrecoverableKeyException e)
        {
            System.out.println("Failed to recover all needed Secrets. Verify KeyStore contents."); 
        }     
    }

    private void keyExchange() throws Exception
    {

        DeviceNotification notification = null;
        List<DeviceNotification> notifications; 
        while(true)
        {
            notifications = device.getNotifications(DateTime.now().minusSeconds(1), DateTime.now()); 
            for(DeviceNotification notif : notifications)
            {
                if(notif.getNotification().equals("$keyrequest"))
                {
                    notification = notif; 
                    break; 
                }
            }
            if(notification != null)
            {
                break; 
            }
            Thread.sleep(500); 
        }

        String timestamp = DateTime.now().toString(); 
        List<Parameter> params = new ArrayList<Parameter>(); 
        params.add(new Parameter("timestamp", timestamp)); 

        Signature privateSignature = Signature.getInstance("SHA256withRSA");
        privateSignature.initSign(enclaveSK);
        privateSignature.update(timestamp.getBytes());
        byte[] signature = privateSignature.sign();
        String signedStamp = Base64.getEncoder().encodeToString(signature);
        params.add(new Parameter("signed",signedStamp));
        device.sendCommand("$keyexchange", params);   

        notification = null; 
        while(true)
        {
            notifications = device.getNotifications(DateTime.now().minusSeconds(1), DateTime.now()); 
            for(DeviceNotification notif : notifications)
            {
                if(notif.getNotification().equals("$keyexchange"))
                {
                    JsonObject parameters = notif.getParameters(); 
                    Signature publicSignature = Signature.getInstance("SHA256withRSA");
                    publicSignature.initVerify(devicePK);
                    publicSignature.update(timestamp.getBytes());
                    byte[] signatureBytes = decoder.decode(parameters.get("signed").getAsString());
                    Boolean verified = publicSignature.verify(signatureBytes);
                    if(verified)
                    { 
                        Cipher decrypt=Cipher.getInstance("RSA/ECB/PKCS1Padding");
                        decrypt.init(Cipher.PRIVATE_KEY, enclaveSK);
                        byte[] decodedKey = decrypt.doFinal(decoder.decode(parameters.get("key").getAsString()));
                        messageKey = new SecretKeySpec(decodedKey, 0, decodedKey.length, "AES"); 
                    } 
                }
                if(notif.getNotification().equals("$HEK"))
                {
                    notification = notif; 
                    break; 
                }
            }
            if(notification != null)
            {
                JsonObject parameters = notification.getParameters();  
                Cipher decryptAES = Cipher.getInstance("AES/CBC/PKCS5Padding"); 
                IvParameterSpec iv = new IvParameterSpec(decoder.decode(parameters.get("iv").getAsString()));
                decryptAES.init(Cipher.DECRYPT_MODE, messageKey, iv);
                BigInteger modulus = new BigInteger(decryptAES.doFinal(decoder.decode(parameters.get("key").getAsString()))); 
                pubKey = new PaillierPublicKey(modulus); 
                cipher = pubKey.createSignedContext(); 
                return; 
            }
            Thread.sleep(500); 
        }         
    }


    public void process(DeviceNotification notification) throws Exception
    {   
        
    }

}