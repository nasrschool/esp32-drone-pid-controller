#include <SPI.h>
#include <nRF24L01.h>
#include <RF24.h>

#include "BluetoothSerial.h"

#if !defined(CONFIG_BT_ENABLED) || !defined(CONFIG_BLUEDROID_ENABLED)
#error Bluetooth is not enabled! Please run `make menuconfig` to and enable it
#endif

BluetoothSerial SerialBT;


#include <ESP32Servo.h>  // ESP32Servo library installed by Library Manager
#include "ESC.h"         // RC_ESP library installed by Library Manager

#define ESC_PIN (32)  // connected to ESC control wire
#define ESC_PIN2 (33)
#define ESC_PIN3 (25)
#define ESC_PIN4 (26)

#define LED_BUILTIN (2)  // not defaulted properly for ESP32s/you must define it
#define POT_PIN (34)     // Analog pin used to connect the potentiometer center pin

// Note: the following speeds may need to be modified for your particular hardware.
#define MIN_SPEED 1040  // speed just slow enough to turn motor off
//#define MAX_SPEED 1240  // speed where my motor drew 3.6 amps at 12v.
#define MAX_SPEED 2000

ESC myESC(ESC_PIN, 1000, 2000, 500);  // ESC_Name (PIN, Minimum Value, Maximum Value, Arm Value)
ESC myESC2(ESC_PIN2, 1000, 2000, 500);
ESC myESC3(ESC_PIN3, 1000, 2000, 500);
ESC myESC4(ESC_PIN4, 1000, 2000, 500);



struct Data{
  short P;
  short I;
  short D;
  short thrust;
};

void setup() {
  Serial.begin(115200);
  //pinMode(A1,INPUT);
  SerialBT.begin("ESP32test"); //Bluetooth device name
  Serial.println("The device started, now you can pair it with bluetooth!");
  Serial.println("i think!");


  delay(1000);
  pinMode(ESC_PIN, OUTPUT);
  pinMode(ESC_PIN2, OUTPUT);
  pinMode(ESC_PIN3, OUTPUT);
  pinMode(ESC_PIN4, OUTPUT);

  pinMode(LED_BUILTIN, OUTPUT);
  digitalWrite(LED_BUILTIN, HIGH);  // set led to on to indicate arming
  myESC.arm();                      // Send the Arm command to ESC
  myESC2.arm();
  myESC3.arm();
  myESC4.arm();

  delay(3000);  // Wait a while
  digitalWrite(LED_BUILTIN, LOW);

  for (int i = 0; i < 350; i++) {      // run speed from 840 to 1190
    myESC.speed(MIN_SPEED - 200 + i);  // motor starts up about half way through loop
    myESC2.speed(MIN_SPEED - 200 + i);
    myESC3.speed(MIN_SPEED - 200 + i);
    myESC4.speed(MIN_SPEED - 200 + i);
    delay(10);
  }
}

int buffer = 0;
int value = 0;
int i = 0;
Data data;
void loop() {
  if (Serial.available()) {
    SerialBT.write(Serial.read());
  }
  if (SerialBT.available()) {
    SerialBT.readBytes((char*)&data,sizeof(Data));
    Serial.println(data.P);
    Serial.println(data.I);
    Serial.println(data.D);
    Serial.println(data.thrust);

  }

  myESC.speed(1160);
  myESC3.speed(1160);
  myESC2.speed(1160);
  myESC4.speed(1160);

  delay(10000);

  myESC.speed(1200);
  myESC3.speed(1200);
  myESC2.speed(1200);
  myESC4.speed(1200);

  delay(10000);
  
  myESC.speed(1100);
  myESC3.speed(1100);
  myESC2.speed(1100);
  myESC4.speed(1100);

  delay(10000);


  /*
  Serial.print("value to add to motors");
  Serial.println(value);
  myESC.speed(value);
  myESC3.speed(value);
  myESC2.speed(value);
  myESC4.speed(value);
  */
  delay(200);
}
