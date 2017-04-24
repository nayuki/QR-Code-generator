# 
# Makefile for QR Code generator (C++)
# 
# Copyright (c) Project Nayuki. (MIT License)
# https://www.nayuki.io/page/qr-code-generator-library
# 
# Permission is hereby granted, free of charge, to any person obtaining a copy of
# this software and associated documentation files (the "Software"), to deal in
# the Software without restriction, including without limitation the rights to
# use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
# the Software, and to permit persons to whom the Software is furnished to do so,
# subject to the following conditions:
# - The above copyright notice and this permission notice shall be included in
#   all copies or substantial portions of the Software.
# - The Software is provided "as is", without warranty of any kind, express or
#   implied, including but not limited to the warranties of merchantability,
#   fitness for a particular purpose and noninfringement. In no event shall the
#   authors or copyright holders be liable for any claim, damages or other
#   liability, whether in an action of contract, tort or otherwise, arising from,
#   out of or in connection with the Software or the use or other dealings in the
#   Software.
# 


# ---- Configuration options ----

# External/implicit variables:
# - CXX: The C++ compiler, such as g++ or clang++.
# - CXXFLAGS: Any extra user-specified compiler flags (can be blank).

# Mandatory compiler flags
CXXFLAGS += -std=c++11
# Diagnostics. Adding '-fsanitize=address' is helpful for most versions of Clang and newer versions of GCC.
CXXFLAGS += -Wall -fsanitize=undefined
# Optimization level
CXXFLAGS += -O1


# ---- Controlling make ----

# Clear default suffix rules
.SUFFIXES:

# Don't delete object files
.SECONDARY:

# Stuff concerning goals
.DEFAULT_GOAL = all
.PHONY: all clean


# ---- Targets to build ----

LIBSRC = BitBuffer QrCode QrSegment
MAINS = QrCodeGeneratorDemo QrCodeGeneratorWorker

# Build all binaries
all: $(MAINS)

# Delete build output
clean:
	rm -f -- $(MAINS)

# Executable files
%: %.cpp $(LIBSRC:=.cpp) $(LIBSRC:=.hpp)
	$(CXX) $(CXXFLAGS) -o $@ $< $(LIBSRC:=.cpp)
