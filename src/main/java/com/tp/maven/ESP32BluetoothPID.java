package com.tp.maven;

import com.fazecast.jSerialComm.SerialPort;

import javax.microedition.io.Connector;
import javax.microedition.io.StreamConnection;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.Arrays;
import java.util.Scanner;

import static java.lang.Math.abs;
import static java.lang.Math.pow;

/*
    struct Data{
    float P;
    float I;
    float D;
    float thrust;
    float desiredAngle;
    }
*/
public class ESP32BluetoothPID {
    public static void main(String[] args) {
        try {

            SerialPort port = SerialPort.getCommPort("COM9");
            port.setBaudRate(115200);

            if (port.openPort()) {
                System.out.println("Connected!");
            } else {
                System.out.println("Failed.");
            }


            System.out.println("Connecting...");

            //StreamConnection conn = (StreamConnection) Connector.open(URL);

            InputStream in = port.getInputStream();
            OutputStream out = port.getOutputStream();

            /*
            String address = "F4650B592456"; // ESP32 MAC address
            String url = "btspp://" + address + ":1"; // channel 1 usually

            StreamConnection connection = (StreamConnection) Connector.open(url);

            InputStream in = connection.openInputStream();
            OutputStream out = connection.openOutputStream();
            */

            Scanner sc = new Scanner(System.in);


            while(true){
                String str = sc.next();
                float[] values = getValues(str);
                ByteBuffer buffer = ByteBuffer.allocate(4 * 5);
                buffer.order(ByteOrder.LITTLE_ENDIAN);

                for(int i = 0; i < values.length;i++){
                    buffer.put(convert(values[i]));
                    System.out.print(Arrays.toString(convert(values[i])));
                }
                System.out.println(buffer.array());
                out.write(buffer.array());

            }

            // Read data
            /*
            byte[] buffer = new byte[1024];
            int bytesRead = in.read(buffer);
            System.out.println(new String(buffer, 0, bytesRead));
               */
            //connection.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    public static float[] getValues(String str){
        String[] strs = str.split(",");
        float[] output = new float[5];
        for(int i = 0; i < 5; i++){
            output[i] = Float.parseFloat(strs[i]);
        }

        return output;
    }

    public static byte[] convert(float n) {
        int intBits =  Float.floatToIntBits(n);
        return new byte[] {
                (byte) intBits,
                (byte)(intBits >> 8),
                (byte)(intBits >> 16),
                (byte)(intBits >> 24)
        };
    }

}