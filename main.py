from PIL import Image
import io
import socket
import struct
import time
import zlib

HOST = '0.0.0.0'
PORT = 5000

class FrameFpsCounter:
    def __init__(self, name):
        self._count = 0
        self._lasttime = time.time_ns()
        self._name = name

    def frame_received(self):
        self._count += 1

        if self._count == 30:
                now = time.time_ns() 
                diff = now - self._lasttime
                fps = 1e9 * self._count / diff

                self._count = 0
                self._lasttime = now

                print(self._name + " FPS {:.2f}".format(fps))


with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
    s.bind((HOST, PORT))
    s.listen()
    conn, addr = s.accept()
    with conn:
        print('Connected by', addr)

        colorFpsCounter = FrameFpsCounter("COLOR")
        depthFpsCounter = FrameFpsCounter("DEPTH")

        while True:
            # Buffer header
            header = bytearray()

            while len(header) < 24:
                missingBytes = 24 - len(header)
                toBuffer = 24 if missingBytes > 24 else missingBytes
                header += conn.recv(toBuffer)

            # Unpack header
            headerIntegers = struct.unpack(">" + ("i"*(len(header)//4)), header)
            frameType = headerIntegers[0]
            frameIndex = headerIntegers[1]
            frameWidth = headerIntegers[2]
            frameHeight = headerIntegers[3]
            frameLengthUncompressed = headerIntegers[4]
            frameLengthCompressed = headerIntegers[5]
            frameBytesPerPixel = frameLengthUncompressed / (frameWidth * frameHeight)

            #print("Type: " + str(frameType) + " Index: " + str(frameIndex) + " Width: " + str(frameWidth) + " Height: " + str(frameHeight) + " Length uncompressed: " + str(frameLengthUncompressed)+ " Length compressed: " + str(frameLengthCompressed) + " Bytes per pixel: " + str(frameBytesPerPixel))

            # Buffer body
            bodyCompressed = bytearray()

            while len(bodyCompressed) < frameLengthCompressed:
                missingBytes = frameLengthCompressed - len(bodyCompressed)
                toBuffer = 1024 if missingBytes > 1024 else missingBytes
                bodyCompressed += conn.recv(toBuffer)

            bodyUncompressed  = zlib.decompress(bodyCompressed)

            # Handle based on frame type 0 = color, 1 = depth
            if frameType == 0:
                colorFpsCounter.frame_received()                
                #image = Image.open(io.BytesIO(bodyUncompressed))
                #image.save("test.jpg") 
            else:
                depthFpsCounter.frame_received()
                #depthPixels = struct.unpack(">" + ("H"*(len(bodyUncompressed)//2)), bodyUncompressed)
