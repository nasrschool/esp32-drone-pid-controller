//This example code is in the Public Domain (or CC0 licensed, at your option.)
//By Evandro Copercini - 2018
//
//This example creates a bridge between Serial and Classical Bluetooth (SPP)
//and also demonstrate that SerialBT have the same functionalities of a normal Serial

#include "BluetoothSerial.h"

#if !defined(CONFIG_BT_ENABLED) || !defined(CONFIG_BLUEDROID_ENABLED)
#error Bluetooth is not enabled! Please run `make menuconfig` to and enable it
#endif

BluetoothSerial SerialBT;

void setup() {
  Serial.begin(115200);
  SerialBT.begin("ESP32test"); //Bluetooth device name
  delay(3000);
  Serial.println("The device started, now you can pair it with bluetooth!");
  delay(2000);
  Serial.println("i think!");
}


int buffer = 0;
int value = 0;
int i = 0;
void loop() {
  if (Serial.available()) {
    SerialBT.write(Serial.read());
  }
  if (SerialBT.available()) {
    int num = SerialBT.read();
    //Serial.write(num);
    //Serial.println(num);

    buffer *= 10;
    buffer += num;
    i++;
    if(i == 4){
      if(buffer > 1500){
        buffer = 1000;
      }
      Serial.println(buffer);
      value = buffer;
      buffer = 0;
      i = 0;

    }
  }




  delay(20);
}