__author__ = 'Tyler Kosaski & Brad Whitfield'
import bluetooth
import time
import RPi.GPIO as GPIO

# setup the pins for use!
GPIO.setmode(GPIO.BCM)
#turn off warnings
GPIO.setwarnings(False)


GPIO.setup(13,GPIO.OUT) #headlights

#motor right
GPIO.setup(17,GPIO.OUT)
GPIO.setup(18,GPIO.OUT)

GPIO.setup(19,GPIO.OUT) #underglow

#motor left
GPIO.setup(22,GPIO.OUT)
GPIO.setup(23,GPIO.OUT)

GPIO.setup(21,GPIO.OUT) #right brake light
GPIO.setup(20,GPIO.OUT) #left brake light
GPIO.setup(26,GPIO.OUT) #reverse lights

UUID = "8dc4b250-2483-4e66-b10c-1b8a60de64e2"
SERVICE_NAME = "BluetoothRPiRemote"

#make sure the lights are off
headLightsOn = False
glowLightsOn = False
leftSigOn = False
rightSigOn = False

#the amount of time before executing next instruction
sleeptime = 0.025

while True:

    #turn on brake lights
    GPIO.output(20, True)
    GPIO.output(21, True)

    serverSocket = bluetooth.BluetoothSocket(bluetooth.RFCOMM)
    serverSocket.bind(("", bluetooth.PORT_ANY))
    serverSocket.listen(1)

    port = serverSocket.getsockname()[1]

    bluetooth.advertise_service(serverSocket, SERVICE_NAME, UUID,
                                service_classes=[UUID, bluetooth.SERIAL_PORT_CLASS],
                                profiles=[bluetooth.SERIAL_PORT_PROFILE],
                                )

    #Attempt Bluetooth Connection
    print("Waiting for user to connect.")
    clientSocket, clientInfo = serverSocket.accept()
    print("WE GOTS A CONNECTION! ", clientInfo)

    while True:
        print("Waiting for stuff from Bluetooth.")

        try: #execute instruction
            data = clientSocket.recv(1024)
            print("Received data: [%s]" % data)
            
            if data == b'8':

                #turn off brakelights

                GPIO.output(20, False)
                GPIO.output(21, False)

                # Makes the motor spin forward

                GPIO.output(17, True)
                GPIO.output(18, False)
                GPIO.output(22, True)
                GPIO.output(23, False)
                time.sleep(sleeptime)

            elif data == b'2':

                #turn off brakelights

                GPIO.output(20, False)
                GPIO.output(21, False)

                # Spins backwards
                GPIO.output(17, False)
                GPIO.output(18, True)
                GPIO.output(22, False)
                GPIO.output(23, True)
                # reverse lights
                GPIO.output(26, True)


                time.sleep(sleeptime)
                GPIO.output(26, False)


            elif data == b'4':

                #turn off brakelights

                GPIO.output(20, False)
                GPIO.output(21, False)

                #turns left
                GPIO.output(17, False)
                GPIO.output(18, True)
                GPIO.output(22, True)
                GPIO.output(23, False)
                time.sleep(sleeptime)


            elif data == b'6':

                #turn off brakelights

                GPIO.output(20, False)
                GPIO.output(21, False)

                #turns right
                GPIO.output(17, True)
                GPIO.output(18, False)
                GPIO.output(22, False)
                GPIO.output(23, True)
                time.sleep(sleeptime)

            elif data == b'1': #headlights
                if(headLightsOn):
                    GPIO.output(13, False)
                    headLightsOn = False
                else:
                    GPIO.output(13, True)
                    headLightsOn = True

            elif data == b'0': #glowlights
                if(glowLightsOn):
                    GPIO.output(19, False)
                    glowLightsOn = False
                else:
                    GPIO.output(19, True)
                    glowLightsOn = True


            elif data == b'7': #left blinker
                if(leftSigOn):
                    GPIO.output(20, False)
                    leftSigOn = False
                else:
                    leftSigOn = True
                    while leftSigOn:
                        GPIO.output(20, True)
                        time.sleep(.5)
                        GPIO.output(20, False)
                        time.sleep(.5)
                        GPIO.output(20, True)
                        #TODO: Make thread
                        leftSigOn = False


            elif data == b'9': #right blinker
                if(rightSigOn):
                    GPIO.output(21, False)
                    rightSigOn = False
                else:
                    rightSigOn = True
                    while rightSigOn:
                        GPIO.output(21, True)
                        time.sleep(.5)
                        GPIO.output(21, False)
                        time.sleep(.5)
                        GPIO.output(21, True)
                        #TODO: Make thread
                        rightSigOn = False
						
            elif data == b'5': #stop

              #brakelights on
              GPIO.output(20, True)
              GPIO.output(21, True)

              #stop motors
              GPIO.output(17, False)
              GPIO.output(18, False)
              GPIO.output(22, False)
              GPIO.output(23, False)
              
            elif data == b'-1': #restart
			
			  #stop everything
              GPIO.output(13, False)
              GPIO.output(19, False)
              GPIO.output(17, False)
              GPIO.output(18, False)
              GPIO.output(20, False)
              GPIO.output(21, False)
              GPIO.output(22, False)
              GPIO.output(23, False)
              GPIO.output(26, False)
              break

            else:
                print("That was not a valid entry")

        except IOError:
            print("IOERROR")
            #brakelights on
            GPIO.output(13, False)
            GPIO.output(19, False)
            GPIO.output(17, False)
            GPIO.output(18, False)
            GPIO.output(20, False)
            GPIO.output(21, False)
            GPIO.output(22, False)
            GPIO.output(23, False)
			
			#close server and client
            serverSocket.close()
            clientSocket.close()
            break

        except KeyboardInterrupt:
            print("Closing out!")

            serverSocket.close()
            clientSocket.close()

            # If a keyboard interrupt is detected then it exits cleanly!
            print('Finishing up!')
            # turn off everything
            GPIO.output(13, False)
            GPIO.output(19, False)
            GPIO.output(17, False)
            GPIO.output(18, False)
            GPIO.output(20, False)
            GPIO.output(21, False)
            GPIO.output(22, False)
            GPIO.output(23, False)
			
			#close server and client
            serverSocket.close()
            clientSocket.close()
            break

	#close server and client
    serverSocket.close()
    clientSocket.close()
