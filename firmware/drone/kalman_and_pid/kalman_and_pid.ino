#include<Wire.h>
#define SDA 21
#define SCL 22
#ifndef PI
#define Pi 3.1415
#endif
/*this is a new list in which i will document what i add in this code, sort of like what is new with each version
  -added gyroscope setup
  -added automated gyro calibration
  -added acceleromter setup
  -added kalman filter


*/
/*
  TO DO:
  -add PID controller
  there are ALOT Of other things to add, this is just what i set my mind on at the moment
*/


struct Data{
  short P;
  short I;
  short D;
  short thrust;
}



double RollVel,PitchVel,YawVel;//the rolling velocities at each moment will be put in these variables to be used, their unit is degrees
double RollDegVel = 0;
double PitchDegVel,YawDegVel;// degree values gotten using the gyroscope alone
double CallRollVel,CallPitchVel,CallYawVel;//callibration variables
double CallRollAcc,CallPitchAcc,CallYawAcc;

//int16_t AccX,AccY,AccZ;//variables in which we will store the linear acceleration values
double RollDegAcc,PitchDegAcc;//in which we will store the rotation degrees calculated with acceleration

double kalmanRoll,kalmanPitch,kalmanYaw;// contains the kalman optimal guesses
double kalmanRollUncer,kalmanPitchUncer,kalmanYawUncer;// uncertainly of the state, which the gyro velocities
double kalmanResult[2];// first value contains the kalman angle result and the second contains the updated uncertainty value

int16_t prevMillis = 0;
int16_t currMillis = 0;
double T;// contains iteration duration

//PID
double Kpr;
double Kir;
double Kdr;

double Er = 0;
double Ir = 0; // contains the integral values, so this in this case the sum of reiman of all precedent iterations times the length of that iteration 
double Dr = 0;// contains the derivative
double PrvEr = 0;//previous error
double PIDr = 0;//contains the pid value

double Kpp;
double Kip;
double Kdp;

double Ep = 0;
double Ip = 0;
double Dp = 0;
double Prvp = 0;

float desiredRollAngle = 0;// gotten from the ir module or bluetooth
float desiredPitchAngle = 0;

void calculatedRollPid(){//pid does not give direct motor instructions persay, it only give the difference torque values so that the drone would rotate one way or another
  Er = desiredRollAngle - kalmanRoll;
  Ir += Er * T;
  Dr = (Er - PrevEr) / T;

  PrevEr = Er;
  PIDr = Kpr * Er + Kir * Ir + Kdr * Dr
}

void calculatedPitchPid(){//pid does not give direct motor instructions persay, it only give the difference torque values so that the drone would rotate one way or another
  Ep = desiredPitchAngle - kalmanPitch;
  Ip += Ep * T;
  Dp = (Ep - PrevEp) / T;

  PrevEp = Ep;
  PIDp = Kpp * Ep + Kip * Ip + Kdp * Dp
}


void calculateKalman(){
  static double kalmanGain;

  kalmanRoll = kalmanRoll + RollVel * T;//build a new angle value ontop of an old corrected estimation, then correct this new one
  kalmanRollUncer = kalmanRollUncer + (T * T) * (3 * 3);//build the new error value on the old corrected error value
  kalmanGain = (kalmanRollUncer)/(kalmanRollUncer + 2 * 2);// 2 * 2 is the error of the measurement methode, 
  kalmanRoll = kalmanRoll + kalmanGain * (RollDegAcc - kalmanRoll);//here we correct the newly buit angle by comparing the difference between
  //our uncorrecetd value and our measurement,KG determines the importance of the difference , its a correction more than a simple addition
  kalmanRollUncer = (1-kalmanGain) * kalmanRollUncer;//prepare the uncertainty of the next estimation

  
  kalmanPitch = kalmanPitch + PitchVel * T;
  kalmanPitchUncer = kalmanPitchUncer + (T * T) * (3 * 3);
  kalmanGain = (kalmanPitchUncer)/(kalmanPitchUncer + 2 * 2);
  kalmanPitch = kalmanPitch + kalmanGain * (PitchDegAcc - kalmanPitch);
  kalmanPitchUncer = (1-kalmanGain) * kalmanPitchUncer;
  
  /*
    place for yaw code
    screw yaw code
  */


}

void mpu_signals(void){
  //this to turn on the pass filter
  Wire.beginTransmission(0x68);//0x68 is the address ingraved in the hardware of mpu6050 and is written in register WHO_AM_I
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
  Wire.requestFrom(0x68,6);
  int16_t rotX = Wire.read() << 8 | Wire.read();//merges 2 1-byte registers into 1 value
  int16_t rotY = Wire.read() << 8 | Wire.read();
  int16_t rotZ = Wire.read() << 8 | Wire.read();

  RollVel = (double)rotX/65.5;
  PitchVel = (double)rotY/65.5;
  YawVel = (double)rotZ/65.5;
  //this to select the starting register of the ones string acceleration values
  Wire.beginTransmission(0x68);
  Wire.write(0x3B);
  Wire.endTransmission();
  //this to read the angular velocity data
  Wire.requestFrom(0x68,6);
  int16_t AccX = Wire.read() << 8 | Wire.read();
  int16_t AccY = Wire.read() << 8 | Wire.read();
  int16_t AccZ = Wire.read() << 8 | Wire.read();

  RollDegAcc = atan(AccY / (sqrt((AccX * AccX) + (AccZ * AccZ)))) * (180/PI);
  PitchDegAcc = atan(-AccX / (sqrt((AccY * AccY) + (AccZ * AccZ)))) * (180/PI);

}


void setup() {
  Serial.begin(115200);
  Wire.setClock(400000);//comunication frequncy rating is 400khz for the mpu
  Wire.setTimeout(50); // in case of a luck up, this sets a max wait time
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
  for(int i = 0; i < 3000; i++){
    mpu_signals();
    CallRollVel += RollVel;
    CallPitchVel += PitchVel;
    CallYawVel += YawVel;

    CallRollAcc += RollDegAcc;
    CallPitchAcc += PitchDegAcc;

  }
  CallRollVel /= 3000;
  CallPitchVel /= 3000;
  CallYawVel /= 3000;

  CallRollAcc /= 3000;
  CallPitchAcc /= 3000;

}

void loop() {
  currMillis = millis();
  T = (currMillis - prevMillis) /1000.0;
  if(T > 0.1 || T < - 0.1){
    T = 0.05;// for some dam reason it goes to -65.48
  }
  //Serial.print("prev iteration time: ");
  //Serial.println(currMillis - prevMillis);
  prevMillis = currMillis;


  mpu_signals();
  RollVel -= CallRollVel;
  PitchVel -= CallPitchVel;
  YawVel -= CallYawVel;
  RollDegAcc -= CallRollAcc;
  PitchDegAcc -= CallPitchAcc;
  //yaw is left, need to add another mpu6050 to get it
 
  calculateKalman();

  calculateRollPid();
  calculatePitchPid();


  //Serial.print("Roll value: ");
  
  Serial.println(kalmanRoll);
  //Serial.print("Pitch value: ");
  //Serial.println(kalmanPitch);
  //Serial.print("Roll value: ");
  //Serial.println(YawVel);
  delay(50);

}
