package com.tp.maven;

import javax.microedition.io.Connector;
import javax.microedition.io.StreamConnection;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Scanner;

import static java.lang.Math.abs;
import static java.lang.Math.pow;

/*
    struct Data{
    int P;
    int I;
    int D;
    int thrust;
    }
*/
public class ESP32Bluetooth {
    public static void main(String[] args) {
        try {
            String address = "F4650B592456"; // ESP32 MAC address
            String url = "btspp://" + address + ":1"; // channel 1 usually

            StreamConnection connection = (StreamConnection) Connector.open(url);

            InputStream in = connection.openInputStream();
            OutputStream out = connection.openOutputStream();



            ByteBuffer buffer = ByteBuffer.allocate(4 + 4 + 20);
            buffer.order(ByteOrder.LITTLE_ENDIAN); // VERY IMPORTANT



            Scanner sc = new Scanner(System.in);
            int value;
            int prevValue = 1000;
            int step = 1;

            send(out,1100);
            while(true){
                System.out.println("enter value: ");
                value = (int) sc.nextInt();
                step = (prevValue < value)?(1):(-1);

                if(value > 1700){
                    value = 1100;
                }

                for(int i = prevValue; abs(value - i) > 30; i = i + (10 * step)){
                    send(out,i);
                    System.out.println("value sent: " + i);
                    Thread.sleep(20);
                }
                prevValue = value;
                if(value == 1000){
                    send(out,1000);
                    break;
                }
            }


            // Read data
            /*
            byte[] buffer = new byte[1024];
            int bytesRead = in.read(buffer);
            System.out.println(new String(buffer, 0, bytesRead));
               */
            connection.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void send(OutputStream out,int num)throws Exception {
        for(int i = 3; i >= 0; i--){
            out.write((int) (num / pow(10,i)));// it is sent in reverse
            num %= (int) pow(10,i);
        }
    }
}