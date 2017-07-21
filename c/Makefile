# 
# Makefile for QR Code generator (C)
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
# - CC: The C compiler, such as gcc or clang.
# - CFLAGS: Any extra user-specified compiler flags (can be blank).

# Mandatory compiler flags
CFLAGS += -std=c99
# Diagnostics. Adding '-fsanitize=address' is helpful for most versions of Clang and newer versions of GCC.
CFLAGS += -Wall -fsanitize=undefined
# Optimization level
CFLAGS += -O1


# ---- Controlling make ----

# Clear default suffix rules
.SUFFIXES:

# Don't delete object files
.SECONDARY:

# Stuff concerning goals
.DEFAULT_GOAL = all
.PHONY: all clean


# ---- Targets to build ----

LIBSRC = qrcodegen
LIBFILE = libqrcodegen.so
MAINS = qrcodegen-demo qrcodegen-test qrcodegen-worker

# Build all binaries
all: $(LIBFILE) $(MAINS)

# Delete build output
clean:
	rm -f -- $(LIBFILE) $(MAINS)

# Shared library
$(LIBFILE): $(LIBSRC:=.c) $(LIBSRC:=.h)
	$(CC) $(CFLAGS) -fPIC -shared -o $@ $(LIBSRC:=.c)

# Executable files
%: %.c $(LIBFILE)
	$(CC) $(CFLAGS) -o $@ $^

# Special executable
qrcodegen-test: qrcodegen-test.c $(LIBSRC:=.c) $(LIBSRC:=.h)
	$(CC) $(CFLAGS) -DQRCODEGEN_TEST -o $@ $< $(LIBSRC:=.c)
