/* Note: the following code is a modification of the Knob Example that
* comes with the ESC library, so that it works with an ESP32 DevKit V1
* and my particular ESC (generic 30A) and brushless motor (generic 2200KV)
*/

#include <ESP32Servo.h>  // ESP32Servo library installed by Library Manager
#include "ESC.h"         // RC_ESP library installed by Library Manager

#define ESC_PIN (32)     // connected to ESC control wire
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

long int val;  // variable to read the value from the analog pin

void setup() {
  Serial.begin(9600);
  delay(1000);
  pinMode(POT_PIN, INPUT);

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

  delay(3000);                      // Wait a while
  digitalWrite(LED_BUILTIN, LOW);   // led off to indicate arming completed

  // the following loop turns on the motor slowly, so get ready
  for (int i = 0; i < 350; i++) {      // run speed from 840 to 1190
    myESC.speed(MIN_SPEED - 200 + i);  // motor starts up about half way through loop
    myESC2.speed(MIN_SPEED - 200 + i);
    myESC3.speed(MIN_SPEED - 200 + i);
    myESC4.speed(MIN_SPEED - 200 + i);
    delay(10);
  }
}  // speed will now jump to pot setting

int i = 0;
int flag = 0;
void loop() {
  //val = analogRead(POT_PIN);  // read the pot (value between 0 and 4095 for ESP32 12 bit A to D)
  //Serial.println(val);
  //val = map(val, 0, 4095, MIN_SPEED, MAX_SPEED);  // scale pot reading to valid speed range
  //myESC.speed(val);                               // sets the ESC speed
  if(i < 300){
    myESC.speed(1000 + i);
    myESC2.speed(1000 + i);
    myESC3.speed(1000 + i);
    myESC4.speed(1000 + i);
    i++;
  }else if(flag == 0){
    flag = 1;
    for(int j = 1000 + i; j > 1000 ; j--){
      myESC.speed(j);
      myESC2.speed(j);
      myESC3.speed(j);
      myESC4.speed(j);
      delay(10);
    }
  }

  
  delay(10);                                 // Wait for a while
}