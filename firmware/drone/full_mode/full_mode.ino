#include<SPI.h>
#include<nRF24L01.h>
#include<RF24.h>

#define CSN 5
#define CE 4

RF24 radio(CE,CSN);

const byte address[6] = "00001"; 

int leftX = 0;
int leftY = 0;
int rightX = 0;
int rightY = 0;
int i = 0;
String msg;

void setup() {
  Serial.begin(115200);

  radio.begin();
  radio.openReadingPipe(0,address);
  radio.setPALevel(RF24_PA_MIN);
  radio.startListening();

}

int getSubString(){
  String tmpStr = "";
  while(msg[i] != '|' && msg[i] != '\0'){
    tmpStr += msg[i];
    i++;
  }

  i++;
  return tmpStr.toInt();
}

void castValues(){
  leftX = getSubString();
  leftY = getSubString();
  rightX = getSubString();
  rightY = getSubString();
  i = 0;
}


void loop() {
  if(radio.available()){
    radio.read(&msg,sizeof(msg));
    castValues();
  }else{
    Serial.println("not connected!");
  }

  delay(1000);

}
