#include<SPI.h>
#include<nRF24L01.h>
#include<RF24.h>

int x;
int y;



RF24 radio(9,10);

const byte address[6] = "00001";
long int pot = 0; 
char msg[32] = "hello brothers"; 

void setup() {
  Serial.begin(9600);
  pinMode(A0, INPUT);
  //pinMode(A1, INPUT);


  radio.begin();
  radio.openWritingPipe(address);
  radio.setPALevel(RF24_PA_MIN);
  //radio.setDataRate(RF24_250KBPS);
  radio.stopListening();

}

int num = 0;
void loop() {
  //x = analogRead(A0);
  //Serial.println(x);
  /*x = map(x,0,1023,0,180); 
  y = analogRead(A1);
  y = map(y,0,1023,0,180);
  */

  //msg = "x: " + String(x) + " | y: " + String(y); 
  pot = analogRead(A1);
  
  num = (pot - 451) / 2;
  if(num < 0){
    num = 0;
  }

  radio.write(&pot,sizeof(pot));
  Serial.print("sent: ");
  Serial.println(num);
  
  delay(200);
}
