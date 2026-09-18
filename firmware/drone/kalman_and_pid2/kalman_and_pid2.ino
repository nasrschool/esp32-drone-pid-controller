#include <Wire.h>
#define SDA 21
#define SCL 22
#ifndef PI
#define PI 3.1415
#endif

#include "BluetoothSerial.h"

#if !defined(CONFIG_BT_ENABLED) || !defined(CONFIG_BLUEDROID_ENABLED)
#error Bluetooth is not enabled! Please run `make menuconfig` to and enable it
#endif

BluetoothSerial SerialBT;

#include <ESP32Servo.h>  // ESP32Servo library installed by Library Manager
//#include "ESC.h"         // RC_ESP library installed by Library Manager


#define ESC_PIN1 (32)  // left up
#define ESC_PIN2 (33)  // left down 
#define ESC_PIN3 (25)  // right down
#define ESC_PIN4 (26)  // right up

/*
  for the cables they are in the following order from up to down
  1st) 33 - bottom left   M2
  2nd) 32 - upper left    M1
  3rd) 26 - upper right   M4
  4th) 25 - bottom right  M3

  for pitch, that is for some reason roll in our case, we have
  when turning right the degree increases positively, and assuming we stabelise to 0, error gets negative meaning pid too gets negative, which means we
  have to add that pid to the left part to make it weaker, and subtract it from the right to make it stronger 

 so in short, the pid is added to the direction in which the degree become negatif

*/

#define LED_BUILTIN (2)  // not defaulted properly for ESP32s/you must define it

// Note: the following speeds may need to be modified for your particular hardware.
#define MIN_SPEED 1040  // speed just slow enough to turn motor off
//#define MAX_SPEED 1240  // speed where my motor drew 3.6 amps at 12v.
#define MAX_SPEED 2000
/*
ESC myESC(ESC_PIN, 1000, 2000, 500);  // ESC_Name (PIN, Minimum Value, Maximum Value, Arm Value)
ESC myESC2(ESC_PIN2, 1000, 2000, 500);
ESC myESC3(ESC_PIN3, 1000, 2000, 500);
ESC myESC4(ESC_PIN4, 1000, 2000, 500);
*/
Servo myESC1;
Servo myESC2;
Servo myESC3;
Servo myESC4;

struct Data {
  float P;
  float I;
  float D;
  float thrust;
  float desiredAngle;
};

struct Response {
  float M1;
  float M2;
  float M3;
  float M4;
  float Angle;
  float PID;
};



double RollVel, PitchVel, YawVel;  //the rolling velocities at each moment will be put in these variables to be used, their unit is degrees
double RollDegVel = 0;
double PitchDegVel, YawDegVel;                 // degree values gotten using the gyroscope alone
double CallRollVel, CallPitchVel, CallYawVel;  //callibration variables
double CallRollAcc, CallPitchAcc, CallYawAcc;

//int16_t AccX,AccY,AccZ;//variables in which we will store the linear acceleration values
double RollDegAcc, PitchDegAcc;  //in which we will store the rotation degrees calculated with acceleration

double kalmanRoll, kalmanPitch, kalmanYaw;                 // contains the kalman optimal guesses
double kalmanRollUncer, kalmanPitchUncer, kalmanYawUncer;  // uncertainly of the state, which the gyro velocities
double kalmanResult[2];                                    // first value contains the kalman angle result and the second contains the updated uncertainty value

unsigned long prevMillis = 0;
unsigned long currMillis = 0;
double T;  // contains iteration duration

//PID
float Kpr;
float Kir;
float Kdr;

float Er = 0;
float Ir = 0;     // contains the integral values, so this in this case the sum of reiman of all precedent iterations times the length of that iteration
float Dr = 0;     // contains the derivative
float PrvEr = 0;  //previous error
float PIDr = 0;   //contains the pid value

float Kpp;
float Kip;
float Kdp;

float Ep = 0;
float Ip = 0;
float Dp = 0;
float PrvEp = 0;
float PIDp = 0;

float desiredRollAngle = 0;  // gotten from the ir module or bluetooth
float desiredPitchAngle = 0;

float Kpy = 5, Kiy = 0, Kdy = 0.2;
float Ey = 0, Iy = 0, Dy = 0, PrvEy = 0, PIDy = 0;
float desiredYawRate = 0;
void calculateRollPid() {  //pid does not give direct motor instructions persay, it only give the difference torque values so that the drone would rotate one way or another
  Er = desiredRollAngle - kalmanRoll;
  Ir += Er * T;

  if (Ir > 400) {
    Ir = 400;
  }
  if (Ir < -400) {
    Ir = -400;
  }

  Dr = (Er - PrvEr) / T;

  PrvEr = Er;
  PIDr = Kpr * Er + Kir * Ir + Kdr * Dr;
}

void calculatePitchPid() {  //pid does not give direct motor instructions persay, it only give the difference torque values so that the drone would rotate one way or another
  Ep = desiredPitchAngle - kalmanPitch;
  Ip += Ep * T;
  if (Ip > 400) {
    Ip = 400;
  }
  if (Ip < -400) {
    Ip = -400;
  }
  Dp = (Ep - PrvEp) / T;

  PrvEp = Ep;
  PIDp = Kpp * Ep + Kip * Ip + Kdp * Dp;
}

void calculateYawPid() {
    // error is how fast it's currently spinning (should be 0)
    Ey = desiredYawRate - YawVel;
    
    Iy += Ey * T;
    if (Iy > 400) Iy = 400;
    if (Iy < -400) Iy = -400;
    
    Dy = (Ey - PrvEy) / T;
    PrvEy = Ey;
    
    PIDy = Kpy * Ey + Kiy * Iy + Kdy * Dy;
}

void calculateKalman() {
  static double kalmanGain;

  kalmanRoll = kalmanRoll + RollVel * T;                             //build a new angle value ontop of an old corrected estimation, then correct this new one
  kalmanRollUncer = kalmanRollUncer + (T * T) * (3 * 3);             //build the new error value on the old corrected error value
  kalmanGain = (kalmanRollUncer) / (kalmanRollUncer + 2 * 2);        // 2 * 2 is the error of the measurement methode,
  kalmanRoll = kalmanRoll + kalmanGain * (RollDegAcc - kalmanRoll);  //here we correct the newly buit angle by comparing the difference between
  //our uncorrecetd value and our measurement,KG determines the importance of the difference , its a correction more than a simple addition
  kalmanRollUncer = (1 - kalmanGain) * kalmanRollUncer;  //prepare the uncertainty of the next estimation


  kalmanPitch = kalmanPitch + PitchVel * T;
  kalmanPitchUncer = kalmanPitchUncer + (T * T) * (3 * 3);
  kalmanGain = (kalmanPitchUncer) / (kalmanPitchUncer + 2 * 2);
  kalmanPitch = kalmanPitch + kalmanGain * (PitchDegAcc - kalmanPitch);
  kalmanPitchUncer = (1 - kalmanGain) * kalmanPitchUncer;

  /*
    place for yaw code
    screw yaw code
  */

  
}

void mpu_signals(void) {
  //this to turn on the pass filter
  Wire.beginTransmission(0x68);  //0x68 is the address ingraved in the hardware of mpu6050 and is written in register WHO_AM_I
  Wire.write(0x1A);
  Wire.write(0x5);
  Wire.endTransmission();
  //this to set the sensitivity of the mpu to 65.5LSB/degree/second
  Wire.beginTransmission(0x68);
  Wire.write(0x1B);
  Wire.write(0x8);
  Wire.endTransmission();
  //this to set the accelerometer sensitivity to 4096/g
  Wire.beginTransmission(0x68);
  Wire.write(0x1C);
  Wire.write(0x10);
  Wire.endTransmission();
  //this to select the starting register of the 6 that store the andgular velocity data
  Wire.beginTransmission(0x68);
  Wire.write(0x43);
  Wire.endTransmission();
  //this to read the angular velocity data
  Wire.requestFrom(0x68, 6);
  int16_t rotX = Wire.read() << 8 | Wire.read();  //merges 2 1-byte registers into 1 value
  int16_t rotY = Wire.read() << 8 | Wire.read();
  int16_t rotZ = Wire.read() << 8 | Wire.read();

  RollVel = (double)rotX / 65.5;
  PitchVel = (double)rotY / 65.5;
  YawVel = (double)rotZ / 65.5;
  //this to select the starting register of the ones string acceleration values
  Wire.beginTransmission(0x68);
  Wire.write(0x3B);
  Wire.endTransmission();
  //this to read the angular velocity data
  Wire.requestFrom(0x68, 6);
  int16_t AccX = Wire.read() << 8 | Wire.read();
  int16_t AccY = Wire.read() << 8 | Wire.read();
  int16_t AccZ = Wire.read() << 8 | Wire.read();

  RollDegAcc = atan(AccY / (sqrt((AccX * AccX) + (AccZ * AccZ)))) * (180 / PI);
  PitchDegAcc = atan(-AccX / (sqrt((AccY * AccY) + (AccZ * AccZ)))) * (180 / PI);
}

const int freq = 400;           // 500Hz frequency
const int resolution = 16;     // 16-bit (0-65535)
const int periodUs = 2500; 
const uint32_t maxDuty = (1 << resolution) - 1;

void setup() {
  Serial.begin(115200);

  Serial.println(SerialBT.begin("ESP32test"));
  Serial.println("The device started, now you can pair it with bluetooth!");
  Serial.println("i think!");

  Wire.setClock(400000);  //comunication frequncy rating is 400khz for the mpu
  Wire.setTimeout(50);    // in case of a luck up, this sets a max wait time
  Wire.begin();
  delay(250);


  //set mpu on power mode
  Wire.beginTransmission(0x68);
  Wire.write(0x6B);
  Wire.write(0x0);
  Wire.endTransmission();
  //calibration
  CallRollVel = 0;
  CallPitchVel = 0;
  CallYawVel = 0;

  CallRollAcc = 0;
  CallPitchAcc = 0;
  CallYawAcc = 0;
  for (int i = 0; i < 3000; i++) {
    mpu_signals();
    CallRollVel += RollVel;
    CallPitchVel += PitchVel;
    CallYawVel += YawVel;

    CallRollAcc += RollDegAcc;
    CallPitchAcc += PitchDegAcc;
    delay(1);
  }
  CallRollVel /= 3000;
  CallPitchVel /= 3000;
  CallYawVel /= 3000;

  CallRollAcc /= 3000;
  CallPitchAcc /= 3000;



  delay(1000);
  
  pinMode(ESC_PIN1, OUTPUT);
  pinMode(ESC_PIN2, OUTPUT);
  pinMode(ESC_PIN3, OUTPUT);
  pinMode(ESC_PIN4, OUTPUT);
  
  
  myESC1.setPeriodHertz(400);
  myESC2.setPeriodHertz(400);
  myESC3.setPeriodHertz(400);
  myESC4.setPeriodHertz(400);
  

  delay(1000);
  
  myESC1.attach(ESC_PIN1,1000,2000);
  myESC2.attach(ESC_PIN2,1000,2000);
  myESC3.attach(ESC_PIN3,1000,2000);
  myESC4.attach(ESC_PIN4,1000,2000);

  delay(1000);

  //arming process
  Serial.println("start arming: ");
  myESC1.writeMicroseconds(1000);
  myESC2.writeMicroseconds(1000);
  myESC3.writeMicroseconds(1000);
  myESC4.writeMicroseconds(1000);
  delay(4000);


  myESC1.writeMicroseconds(1160);
  delay(1000);
  myESC1.writeMicroseconds(1100);
  myESC4.writeMicroseconds(1160);
  delay(1000);
  myESC4.writeMicroseconds(1100);
  myESC3.writeMicroseconds(1160);
  delay(1000);
  myESC3.writeMicroseconds(1100);
  myESC2.writeMicroseconds(1160);
  delay(1000);
  myESC2.writeMicroseconds(1100);


  Serial.println("start: ");
}

Data data;
Response response;
int thrust = 0;
int base;
int pureBase = 0;
int tmp;
int netGravity;
int M1 = 0;
int M2 = 0;
int M3 = 0;
int M4 = 0;

float F = 0;
float kalmanPitchInRadian = 0;
float kalmanRollInRadian = 0;
float projector = 0;



int bluetoothInterval = 0;
void loop() {
  currMillis = millis();
  if (currMillis - prevMillis >= 4) {
    if(bluetoothInterval < 100){
      bluetoothInterval += (currMillis - prevMillis);
    }
    T = (currMillis - prevMillis) / 1000.0;
    //Serial.println(currMillis - prevMillis);
    prevMillis = currMillis;

    
    mpu_signals();
    RollVel -= CallRollVel;
    PitchVel -= CallPitchVel;
    YawVel -= CallYawVel;
    RollDegAcc -= CallRollAcc;
    PitchDegAcc -= CallPitchAcc;
    //yaw is left, need to add another mpu6050 to get it
    
    
    if (SerialBT.available() >= sizeof(Data)) {
      SerialBT.readBytes((char*)&data, sizeof(Data));

      Kpp = data.P;
      Kip = data.I;
      Kdp = data.D;

      Kpr = data.P;
      Kir = data.I;
      Kdr = data.D;

      thrust = data.thrust;
      desiredPitchAngle = data.desiredAngle;

      Serial.print("Kpp: ");
      Serial.print(Kpp);
      Serial.print("\t");
      Serial.print("Kip: ");
      Serial.print(Kip);
      Serial.print("\t");
      Serial.print("Kdp: ");
      Serial.print(Kdp);
      Serial.print("\t");
      Serial.print("thrust: ");
      Serial.print(thrust);
      Serial.print("\t");
      Serial.print("DA: ");
      Serial.println(desiredPitchAngle);
    }
    // this part is for calculations
    calculateKalman();

    calculateRollPid();
    calculatePitchPid();
    calculateYawPid();

    base = thrust;
    pureBase = base - 1100;

    kalmanPitchInRadian = (kalmanPitch * PI) / 180;
    kalmanRollInRadian = (kalmanRoll * PI) / 180;


    projector = cos(kalmanPitchInRadian) * cos(kalmanRollInRadian);
    //projector = cos(kalmanPitchInRadian);

    if(projector < 0.1){
      projector = 0.1;
    }

    F = 1100 + (pureBase / projector);


    M1 = (int)(F + (PIDp / 4) + (PIDr / 4) + (PIDy / 4));

    M2 = (int)(F + (PIDp / 4) - (PIDr / 4) - (PIDy / 4));
    M3 = (int)(F - (PIDp / 4) - (PIDr / 4) + (PIDy / 4));
    
    M4 = (int)(F - (PIDp / 4) + (PIDr / 4) - (PIDy / 4));

    if (M1 < 1040) M1 = 1040;
    if (M2 < 1040) M2 = 1040;
    if (M3 < 1040) M3 = 1040;
    if (M4 < 1040) M4 = 1040;

    if (M1 > 1900) M1 = 1900;
    if (M2 > 1900) M2 = 1900;
    if (M3 > 1900) M3 = 1900;
    if (M4 > 1900) M4 = 1900;
  
    if (kalmanPitch > 45.0 || kalmanPitch < -45.0 || kalmanRoll > 45.0 || kalmanRoll < -45.0) {
      M1 = 1040; 
      M2 = 1040;
      M3 = 1040;
      M4 = 1040;
    }

    

    
    myESC1.writeMicroseconds(M1);
    myESC2.writeMicroseconds(M2);
    myESC3.writeMicroseconds(M3);
    myESC4.writeMicroseconds(M4);
    

    response.M1 = (int)M1;
    response.M2 = (int)M2;
    response.M3 = (int)M3;
    response.M4 = (int)M4;
    response.Angle = YawVel;
    response.PID = PIDy;

    
    SerialBT.write(0xAA);
    SerialBT.write((uint8_t*)&response, sizeof(response));
    
    
    if (thrust <= 1100) {
      Ip = 0;
      Ir = 0;
    }
    
  }
  
  
    
}
