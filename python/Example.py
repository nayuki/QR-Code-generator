#You can use this file as an example to see how exactly the project Works by creating a simple Qr Code with only 2 Varbles Size and Text
#You can run this file's code easily by adding python3 <path of file>
#Example : python3 Desktop/QrProject/QR-Code-generator/python/Example.py




from typing import List
from qrcodegen import QrCode, QrSegment


def main() -> None:
	Run()



#Enter Size of border
qrcode_size = int(input("Chose qr code size (Recommended from 1-5) : "))
#Enter Qr code data
input = input("PLease Enter a Valid Data (Link , a Message or a Code) : " )


# Qr Code generator Example

def Run() -> None:
	global input
	text = input
	error = QrCode.Ecc.LOW

	# Creating QrCode
	qr = QrCode.encode_text(text, error)
	print_qr(qr)




def print_qr(qrcode: QrCode) -> None:
	global qrcode_size
	border = qrcode_size
	for y in range(-border, qrcode.get_size() + border):
		for x in range(-border, qrcode.get_size() + border):
			print("\u2588 "[1 if qrcode.get_module(x,y) else 0] * 2, end="")
		print()
	print()


# Run the main program
if __name__ == "__main__":
	main()
