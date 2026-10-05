CXX = x86_64-w64-mingw32-g++
WINDRES = x86_64-w64-mingw32-windres
MAKENSIS = makensis

CXXFLAGS = -std=c++17 -O2 -mwindows -municode -DUNICODE -D_UNICODE -Wall -static -static-libgcc -static-libstdc++ -s
LIBS = -lws2_32 -lbthprops -lcomctl32 -lgdi32 -luser32 -lshell32 -lole32 -ldwmapi -luxtheme

BIN_DIR = ../bin
TARGET = $(BIN_DIR)/Lapdroid.exe
SETUP = $(BIN_DIR)/Lapdroid-Setup.exe

SRCS = src/main.cpp src/bluetooth_manager.cpp src/input_capture.cpp src/ui_window.cpp src/logger.cpp src/stylus_injector.cpp
RES = src/resources.rc
RES_OBJ = src/resources.o

all: $(TARGET) $(SETUP)
	cp -f $(TARGET) $(BIN_DIR)/BluetoothInputBridge.exe
	cp -f $(SETUP) $(BIN_DIR)/BluetoothInputBridge-Setup.exe

$(RES_OBJ): $(RES) res/app.ico res/app.manifest
	$(WINDRES) $(RES) -O coff -o $(RES_OBJ)

$(TARGET): $(SRCS) $(RES_OBJ)
	mkdir -p $(BIN_DIR)
	$(CXX) $(CXXFLAGS) $(SRCS) $(RES_OBJ) -o $(TARGET) $(LIBS)

$(SETUP): $(TARGET) installer/setup.nsi
	$(MAKENSIS) installer/setup.nsi

clean:
	rm -f $(RES_OBJ) $(TARGET) $(SETUP)

.PHONY: all clean
