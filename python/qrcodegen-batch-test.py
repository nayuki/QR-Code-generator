# 
# QR Code generator batch test (Python 3)
# 
# Runs various versions of the QR Code generator test worker as subprocesses,
# feeds each one the same random input, and compares their output for equality.
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

from __future__ import print_function
import itertools, random, subprocess, sys, time
if sys.version_info.major < 3:
	raise RuntimeError("Requires Python 3+")


CHILD_PROGRAMS = [
	["python2", "../python/qrcodegen-worker.py"],  # Python 2 program
	["python3", "../python/qrcodegen-worker.py"],  # Python 3 program
	["java", "-cp", "../java", "io/nayuki/qrcodegen/QrCodeGeneratorWorker"],  # Java program
	["../c/qrcodegen-worker"],  # C program
	["../cpp/QrCodeGeneratorWorker"],  # C++ program
	["../rust/target/debug/examples/qrcodegen-worker"],  # Rust program
]


subprocs = []

def main():
	# Launch workers
	global subprocs
	try:
		for args in CHILD_PROGRAMS:
			subprocs.append(subprocess.Popen(args, universal_newlines=True,
				stdin=subprocess.PIPE, stdout=subprocess.PIPE))
	except FileNotFoundError:
		write_all(-1)
		raise
	
	# Check if any died
	time.sleep(0.3)
	if any(proc.poll() is not None for proc in subprocs):
		for proc in subprocs:
			if proc.poll() is None:
				print(-1, file=proc.stdin)
				proc.stdin.flush()
		sys.exit("Error: One or more workers failed to start")
	
	# Do tests
	for i in itertools.count():
		print("Trial {}: ".format(i), end="")
		do_trial()
		print()


def do_trial():
	mode = random.randrange(4)
	if mode == 0:  # Numeric
		length = round((2 * 7089) ** random.random())
		data = [random.randrange(48, 58) for _ in range(length)]
	elif mode == 1:  # Alphanumeric
		length = round((2 * 4296) ** random.random())
		data = [ord(random.choice("0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ $%*+-./:")) for _ in range(length)]
	elif mode == 2:  # ASCII
		length = round((2 * 2953) ** random.random())
		data = [random.randrange(128) for _ in range(length)]
	elif mode == 3:  # Byte
		length = round((2 * 2953) ** random.random())
		data = [random.randrange(256) for _ in range(length)]
	else:
		raise AssertionError()
	
	write_all(length)
	for b in data:
		write_all(b)
	
	errcorlvl = random.randrange(4)
	minversion = random.randint(1, 40)
	maxversion = random.randint(1, 40)
	if minversion > maxversion:
		minversion, maxversion = maxversion, minversion
	mask = -1
	if random.random() < 0.5:
		mask = random.randrange(8)
	boostecl = int(random.random() < 0.2)
	print("mode={} len={} ecl={} minv={} maxv={} mask={} boost={}".format(mode, length, errcorlvl, minversion, maxversion, mask, boostecl), end="")
	
	write_all(errcorlvl)
	write_all(minversion)
	write_all(maxversion)
	write_all(mask)
	write_all(boostecl)
	flush_all()
	
	version = read_verify()
	print(" version={}".format(version), end="")
	if version == -1:
		return
	size = version * 4 + 17
	for _ in range(size**2):
		read_verify()


def write_all(val):
	for proc in subprocs:
		print(val, file=proc.stdin)

def flush_all():
	for proc in subprocs:
		proc.stdin.flush()

def read_verify():
	val = subprocs[0].stdout.readline().rstrip("\r\n")
	for proc in subprocs[1 : ]:
		if proc.stdout.readline().rstrip("\r\n") != val:
			raise ValueError("Mismatch")
	return int(val)


if __name__ == "__main__":
	main()
