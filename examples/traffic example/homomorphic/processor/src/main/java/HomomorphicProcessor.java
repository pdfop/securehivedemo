package com.n1analytics.paillier; 

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

import org.joda.time.DateTime; 
import org.joda.time.Duration; 

import com.n1analytics.paillier.*;

import ope.util.Encoder; 
import ope.fast.FastOpeKey; 

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
    private PaillierContext cipher; 

    // sample specific data 
    private EncodedNumber speedlimit; 
    
    // ope 
    private byte[] speedLimitOPE;  
    private FastOpeKey opeKey; 

    private String limit; 
    private Device device;

    public HomomorphicProcessor(Device device, String limit)
    {
        this.device = device; 
        this.limit = limit;
        try
        {
            init(); 
            keyExchange();  
        }
        catch(Exception e)
        {
            System.out.println(e.getMessage()); 
            e.printStackTrace(System.out); 
        }
    }

    private void init() throws Exception
    {
        // load KeyStore from file system 
        FileInputStream fis = null; 
        try 
        {
            store = KeyStore.getInstance("pkcs12");
            fis = new FileInputStream("resources/SecureHiveClient.pkcs12"); 
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
            Certificate deviceCert = store.getCertificate(device.getId());
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
                speedlimit = cipher.getEncodingScheme().encode(new BigInteger(limit)); 

                ByteBuffer opeKeyBuffer = ByteBuffer.wrap(decryptAES.doFinal(decoder.decode(parameters.get("opeKey").getAsString().getBytes()))); 
                opeKey = new FastOpeKey(opeKeyBuffer.getLong(), opeKeyBuffer.getDouble(), opeKeyBuffer.getDouble(), opeKeyBuffer.getLong()); 
                speedLimitOPE = opeKey.encrypt(Encoder.encodeInt(130)); 
                return; 
            }
            Thread.sleep(500); 
        }         
    }

    private IvParameterSpec generateIV() {
        byte[] iv = new byte[16];
        new SecureRandom().nextBytes(iv);
        return new IvParameterSpec(iv);
    }

    private int compareOPE(byte[] c1, byte[] c2) {
		for (int i = 0; i < c1.length; i++) 
        {
			int b1 = Byte.toUnsignedInt(c1[i]);
			int b2 = Byte.toUnsignedInt(c2[i]);
			if (b1 < b2) { return -1; }
			else if (b1 > b2) { return 1; }
		}
		return 0;
	}

    public void process(DeviceNotification notification) throws Exception
    {   
        if(notification.getNotification().equals("data"))
        { 
            Gson gson = new Gson(); 
            JsonObject parameters = notification.getParameters(); 
            List<Parameter> responseParam = new ArrayList<Parameter>(); 
            boolean speeding = false; 

            Cipher decrypt = Cipher.getInstance("AES/CBC/PKCS5Padding"); 
            IvParameterSpec ivDec = new IvParameterSpec(decoder.decode(parameters.get("iv").getAsString()));
            decrypt.init(Cipher.DECRYPT_MODE, messageKey, ivDec);

            Cipher encrypt = Cipher.getInstance("AES/CBC/PKCS5Padding"); 
            encrypt.init(Cipher.ENCRYPT_MODE, messageKey, ivDec);

            java.util.Set<java.util.Map.Entry<java.lang.String,JsonElement>> entries = parameters.entrySet(); 
            for(java.util.Map.Entry<String, JsonElement> entry : entries)
            {
                if(entry.getKey().equals("iv"))
                {
                    continue;
                }
                String key = new String(decrypt.doFinal(decoder.decode(entry.getKey()))); 

                // AES encrypted values are just added back in with the same IV 
                if(key.equals("license-plate") || key.equals("time"))
                {
                    Parameter param = new Parameter("", ""); 
                    param.setKey(encoder.encodeToString(encrypt.doFinal(key.getBytes())));
                    param.setValue(entry.getValue().getAsString()); 
                    responseParam.add(param); 
                }

                // speed difference is calculated by subtracting the encoded speed limit from the speed 
                else if(key.equals("speed"))
                {
                    JsonObject speed = gson.fromJson(parameters.get(entry.getKey()).getAsString(), JsonObject.class);
                    EncryptedNumber s = new EncryptedNumber(cipher, speed.get("ciphertext").getAsBigInteger(), speed.get("exponent").getAsInt()); 
                    EncryptedNumber result = cipher.subtract(s,speedlimit); 
        
                    JsonObject jsonEnc = new JsonObject();
                    jsonEnc.addProperty("exponent", result.getExponent()); 
                    jsonEnc.addProperty("ciphertext", result.ciphertext);  
                    responseParam.add(new Parameter(encoder.encodeToString(encrypt.doFinal("over-limit".getBytes())), jsonEnc.toString()));  
                    
                } 

                else if(key.equals("speed-ope"))
                {
                   int cmp = compareOPE(decoder.decode(entry.getValue().getAsString().getBytes()), speedLimitOPE); 
                   if(cmp == 1)
                   {
                       speeding = true; 
                   }
                }   
            } 

            if(speeding)
            {
                responseParam.add(new Parameter("iv", parameters.get("iv").getAsString())); 
                device.sendCommand("response", responseParam); 
            }
        }
    }
}