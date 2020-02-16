#!/bin/bash
cd "$(dirname "$0")"
make -j
$CXX $CXXFLAGS encodeBinary_fuzzer.c -I. \
    -o $OUT/encodeBinary_fuzzer \
    $LIB_FUZZING_ENGINE -L. -lqrcodegen
