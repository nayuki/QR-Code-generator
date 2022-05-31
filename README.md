
# QR Code Generator    [![Badge License]][License]

*A Library For A Multitude Of Languages.*

<br>

<div align = center>

<img
    src = 'Resources/QRCode.svg'
    title = 'QRCode With Link To This Repository'
    width = 160
/>    

---

[![Button Demo]][Demo]   
[![Button Website]][Website]   
[![Button Languages]][Languages]

---

</div>

<br>

## Introduction

This project aims to be the best, clearest QR <br>
Code generator library in multiple languages.

#### Primary Goals

- Flexible options
- Absolute correctness

#### Secondary Goals

- Compact implementation size
- Good documentation comments

<br>
<br>

## Languages

*The library is available in multiple languages* <br>
*with mostly the same amount of functionality.*

<br>

[![Badge TypeScript]][TypeScript] 
[![Badge JavaScript]][Javascript] 

[![Badge C++]][C++] 
[![Badge C]][C] 

[![Badge Python]][Python] 

[![Badge Java]][Java] 

[![Badge Rust]][Rust] 

<br>
<br>

## Features

#### Core

- Significantly shorter code but more documentation <br>
  comments compared to competing libraries.

- Supports encoding all 40 versions (sizes) and all 4 error <br>
  correction levels, as per the QR Code Model 2 standard.

- Output format: Raw modules/pixels of the QR symbol

- Detects finder-like penalty patterns more <br>
  accurately than other implementations.

- Encodes numeric and special-alphanumeric <br>
  strings into less space than general text.

<br>

#### Parameters

- Minimum / Maximum allowed version number can be <br>
  specified, which the library will use to automatically <br>
  choose smallest version in the range that fits the data.

- User can specify mask pattern manually, otherwise <br>
  library will automatically evaluate all 8 masks and <br>
  select the optimal one.

- User can specify absolute error correction level, or <br>
  allow the library to boost it if it doesn't increase the <br>
  version number

- User can create a list of data segments <br>
  manually and add ECI segments.

<br>

#### Advanced   <kbd> Optional </kbd> <kbd> Java Only </kbd>

- Encodes Japanese Unicode text in kanji <br>
  mode to save a lot of space compared <br>
  to UTF-8 bytes.
  
- Computes optimal segment mode switching <br>
  for text with mixed numeric / alphanumeric <br>
  / general / kanji parts.

<br>

*Check the project website for more information.*

<br>
<br>

## Example   [![Badge Java]][Java Demo]

While the code below is written in Java, the <br>
ports to other languages are designed with <br>
essentially the same API naming / behavior.

<br>

```Java
import java.awt.image.BufferedImage;
import java.util.List;
import java.io.File;

import javax.imageio.ImageIO;
import io.nayuki.qrcodegen.*;


//  Simple Operation

QrCode codeA = QrCode.encodeText("Hello, world!",QrCode.Ecc.MEDIUM);
BufferedImage image = toImage(codeA,4,10);
ImageIO.write(image,"png",new File("qr-code.png"));


//  Manual Operation

List<QrSegment> segments = QrSegment.makeSegments("3141592653589793238462643383");
QrCode codeB = QrCode.encodeSegments(segments,QrCode.Ecc.HIGH,5,5,2,false);

for(int y = 0;y < codeB.size;y++)
    for(int x = 0;x < codeB.size;x++){
        ...
        paint codeB.getModule(x,y)
        ...
    }
```

<br>
  
  
<!----------------------------------------------------------------------------->

[Website]: https://www.nayuki.io/page/qr-code-generator-library
[Demo]: https://www.nayuki.io/page/qr-code-generator-library#live-demo-javascript

[Languages]: #Languages 'Language Selection'
[Java Demo]: java/QrCodeGeneratorDemo.java 'Java Example'
[License]: LICENSE


<!-------------------------------{ Languages }--------------------------------->

[TypeScript]: typescript-javascript 'TypeScript Overview'
[JavaScript]: typescript-javascript 'JavaScript Overview'
[Python]: python 'Python Overview'
[Java]: java 'Java Overview'
[Rust]: rust 'Rust Overview'
[C++]: cpp 'C++ Overview'
[C]: c 'C Overview'


<!--------------------------------{ Badges }----------------------------------->

[Badge License]: https://img.shields.io/badge/License-MIT-yellow.svg?style=for-the-badge

[Badge TypeScript]: https://img.shields.io/badge/TypeScript-3178C6?style=for-the-badge&logoColor=white&logo=TypeScript
[Badge JavaScript]: https://img.shields.io/badge/JavaScript-cfbb1b?style=for-the-badge&logoColor=white&logo=JavaScript
[Badge Python]: https://img.shields.io/badge/Python-3776AB?style=for-the-badge&logoColor=white&logo=Python
[Badge Rust]: https://img.shields.io/badge/Rust-f44a00?style=for-the-badge&logoColor=white&logo=Rust
[Badge Java]: https://img.shields.io/badge/Java-c00711?style=for-the-badge&logoColor=white&logo=CoffeeScript
[Badge C++]: https://img.shields.io/badge/C++-00599C?style=for-the-badge&logoColor=white&logo=CPlusPlus
[Badge C]: https://img.shields.io/badge/C-999999?style=for-the-badge&logoColor=white&logo=C


<!--------------------------------{ Buttons }---------------------------------->

[Button Languages]: https://img.shields.io/badge/Languages-A22846?style=for-the-badge&logoColor=white&logo=ROS
[Button Website]: https://img.shields.io/badge/Website-4298B8?style=for-the-badge&logoColor=white&logo=Apostrophe
[Button Demo]: https://img.shields.io/badge/Demo-006600?style=for-the-badge&logoColor=white&logo=AppleArcade
