#include <SPI.h>
#include <nRF24L01.h>
#include <RF24.h>

/*struct JoystickData { //struct is where we declare all variables inside a package
  long int j1x;
  long int j1y;
  long int j2x;
  long int j2y;
};

JoystickData data;

*/

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

int CSN = 5;
int CE = 4;
//char msg[32];
long int pot = 0;

RF24 radio(CE, CSN);

const byte address[6] = "00001";

void setup() {
  Serial.begin(115200);
  //pinMode(A1,INPUT);

  //SPI.begin(18, 19, 23, 5); // SCK, MISO, MOSI, SS (CSN)
  radio.begin();
  radio.openReadingPipe(0, address);
  radio.setPALevel(RF24_PA_MIN);
  //radio.setDataRate(RF24_250KBPS);
  radio.startListening();


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

void loop() {
  if (radio.available()) {

    radio.read(&pot, sizeof(pot));
    Serial.print("did i get anything? ");
    Serial.println(pot);

    if (pot > 300) {
      pot = 300;
      
    }
  } else {
    Serial.print("not connected!");
    Serial.println(pot);
    
  }
  myESC.speed(1000 + pot);
  myESC3.speed(1000 + pot);
  myESC2.speed(1000 + pot);
  myESC4.speed(1000 + pot);
  delay(200);
}
