#include<SPI.h>
#include<nRF24L01.h>
#include<RF24.h>

/*struct JoystickData { //struct is where we declare all variables inside a package
  long int j1x;
  long int j1y;
  long int j2x;
  long int j2y;
};

JoystickData data;

*/


int CSN = 5;
int CE = 4;
//char msg[32];
long int pot = 0;

RF24 radio(CE,CSN);

const byte address[6] = "00001"; 

void setup() {
  Serial.begin(115200);
  //pinMode(A1,INPUT);

  //SPI.begin(18, 19, 23, 5); // SCK, MISO, MOSI, SS (CSN)
  radio.begin();
  radio.openReadingPipe(0,address);
  radio.setPALevel(RF24_PA_MAX);
  radio.setDataRate(RF24_250KBPS);
  radio.startListening();

}

void loop() {
  if(radio.available()){
    
    radio.read(&pot,sizeof(pot));
    Serial.print("did i get anything? ");
    Serial.println(pot);
    /*Serial.print("JS1: "); // JS = joistick
    Serial.print(data.j1x);
    Serial.print(" , ");
    Serial.print(data.j1y);
    Serial.print(" | JS2: ");
    Serial.print(data.j2x);
    Serial.print(" , ");
    Serial.println(data.j2y); //Prints joystick values for serial monitor
    */
  }else{
    Serial.println("not connected!");
  }

  delay(200);
}
