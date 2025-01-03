## How to build for iOS:
```bash
cd cpp
cmake -B build -G Xcode -DCMAKE_TOOLCHAIN_FILE=../../ios.toolchain.cmake -DPLATFORM=OS64
cmake --build build --config Release
```

## How to build for simulator:
```bash
cd cpp
cmake -B build -G Xcode -DCMAKE_TOOLCHAIN_FILE=../../ios.toolchain.cmake -DPLATFORM=SIMULATORARM64
cmake --build build --config Release
```

